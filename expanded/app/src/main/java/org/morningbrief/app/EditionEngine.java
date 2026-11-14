package org.morningbrief.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class EditionEngine {
    private EditionEngine() {}

    interface ProgressListener {
        void onProgress(String status, int percent);
    }

    static final class Result {
        String html;
        int storyCount;
        int failedSources;
        boolean weatherFresh;
    }

    static Result refresh(Context context) throws Exception {
        return refresh(context, null);
    }

    static synchronized Result refresh(Context context, ProgressListener listener) throws Exception {
        LocationInfo loc = SettingsStore.getLocation(context);
        List<SourceConfig> sources = FeedDiscovery.mergePreferredSources(loc, SettingsStore.getSources(context));
        SettingsStore.saveSources(context, sources);
        if (sources.isEmpty()) throw new Exception("No news sources selected");

        report(listener, "Updating weather…", 4);
        WeatherData weather;
        boolean weatherFresh = true;
        try {
            weather = WeatherService.fetch(loc);
            SettingsStore.saveWeatherCache(context, weather);
        } catch (Exception e) {
            weather = SettingsStore.loadWeatherCache(context);
            weatherFresh = false;
        }

        List<SourceConfig> enabled = new ArrayList<>();
        for (SourceConfig source : sources) if (source.enabled) enabled.add(source);
        if (enabled.isEmpty()) throw new Exception("No news sources selected");

        report(listener, "Checking sources · 0 of " + enabled.size(), 8);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(5, Math.max(1, enabled.size())));
        List<Future<FetchResult>> futures = new ArrayList<>();
        for (SourceConfig source : enabled) {
            futures.add(pool.submit(new Callable<>() {
                @Override public FetchResult call() { return fetchSource(source, loc); }
            }));
        }
        pool.shutdown();

        List<Story> gathered = new ArrayList<>();
        int failures = 0;
        int completed = 0;
        for (Future<FetchResult> f : futures) {
            try {
                FetchResult r = f.get();
                if (r.ok) gathered.addAll(r.stories); else failures++;
            } catch (Exception e) {
                failures++;
            }
            completed++;
            int pct = 8 + (int) Math.round(52.0 * completed / Math.max(1, enabled.size()));
            report(listener, "Checking sources · " + completed + " of " + enabled.size(), pct);
        }
        SettingsStore.saveSources(context, sources);

        report(listener, "Filtering " + gathered.size() + " stories…", 66);
        long now = System.currentTimeMillis();
        long maxAge = 96L * 60L * 60L * 1000L;
        List<Story> poolStories = new ArrayList<>();
        Set<String> exactSeen = new HashSet<>();

        // A newspaper does not reprint yesterday's story. Seed the permanent
        // shown-history from the last v0.4.1 cache, then exclude anything that
        // has already appeared in a completed edition.
        SettingsStore.rememberShownStories(context, SettingsStore.loadStoryCache(context));
        Set<String> shownStories = SettingsStore.loadShownStoryKeys(context);
        for (Story s : gathered) {
            if (!StoryFilter.keep(context, s)) continue;
            if (s.publishedEpoch > 0 && now - s.publishedEpoch > maxAge) continue;
            if (s.sourceMode == null || s.sourceMode.trim().isEmpty()) s.sourceMode = inferMode(s.source, sources);
            s.cached = false;
            String key = normalize(s.title);
            if (key.isEmpty() || !exactSeen.add(key)) continue;
            if (SettingsStore.wasStoryShown(shownStories, s)) continue;
            poolStories.add(s);
        }

        report(listener, "Comparing coverage and removing duplicates…", 76);
        List<Story> selected = EditorialEngine.select(loc, poolStories, now);
        report(listener, "Choosing the strongest mix…", 89);

        report(listener, "Building your Daily Brief…", 96);
        String html = EditionBuilder.build(loc, weather, selected, weatherFresh, now, SettingsStore.theme(context));
        SettingsStore.writeCachedEdition(context, html);
        if (!selected.isEmpty()) {
            SettingsStore.saveStoryCache(context, selected);
            SettingsStore.rememberShownStories(context, selected);
        }
        SettingsStore.setLastRefresh(context, now);
        report(listener, "Edition ready", 100);

        Result result = new Result();
        result.html = html;
        result.storyCount = selected.size();
        result.failedSources = failures;
        result.weatherFresh = weatherFresh;
        return result;
    }

    private static void report(ProgressListener listener, String status, int percent) {
        if (listener != null) listener.onProgress(status, Math.max(0, Math.min(100, percent)));
    }

    private static FetchResult fetchSource(SourceConfig source, LocationInfo loc) {
        FetchResult out = new FetchResult();
        try {
            out.stories = FeedReader.read(source);
            source.failures = 0;
            out.ok = true;
            return out;
        } catch (Exception first) {
            source.failures++;
        }

        if (source.isDirect() && source.failures >= 3 && !source.homepageUrl.trim().isEmpty()) {
            try {
                String repaired = FeedDiscovery.discoverDirectFeed(source.homepageUrl);
                if (repaired != null && !repaired.trim().isEmpty()) {
                    source.feedUrl = repaired;
                    out.stories = FeedReader.read(source);
                    source.failures = 0;
                    out.ok = true;
                    return out;
                }
            } catch (Exception ignored) {}

            String prefix = source.isPreferred() ? "preferred-" : (source.isNonprofit() ? "inn-" : "");
            source.mode = prefix + "google";
            source.feedUrl = FeedDiscovery.googleSourceFeed(source.cleanName(), loc);
            if (!source.name.endsWith(" (via Google News)")) source.name += " (via Google News)";
            try {
                out.stories = FeedReader.read(source);
                source.failures = 0;
                out.ok = true;
                return out;
            } catch (Exception ignored) {}
        }
        out.ok = false;
        return out;
    }

    private static String inferMode(String sourceName, List<SourceConfig> sources) {
        for (SourceConfig s : sources) {
            if (s.cleanName().equalsIgnoreCase(sourceName == null ? "" : sourceName.trim())) return s.mode;
        }
        return "";
    }

    private static String normalize(String s) {
        return (s == null ? "" : s.toLowerCase(Locale.US)).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static final class FetchResult {
        boolean ok;
        List<Story> stories = new ArrayList<>();
    }
}
