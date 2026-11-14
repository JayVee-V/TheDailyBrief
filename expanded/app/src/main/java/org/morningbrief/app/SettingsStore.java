package org.morningbrief.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class SettingsStore {
    private static final String PREFS = "morning_brief";
    private static final String K_LOCATION = "location";
    private static final String K_SOURCES = "sources";
    private static final String K_SETUP = "setup_complete";
    private static final String K_LAST_REFRESH = "last_refresh";
    private static final String K_SPORTS = "include_sports";
    private static final String K_OBITS = "include_obits";
    private static final String K_OPINION = "include_opinion";
    private static final String K_RECIPES = "include_recipes";
    private static final String K_THEME = "appearance_theme";

    private SettingsStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isSetup(Context c) { return prefs(c).getBoolean(K_SETUP, false); }
    static void setSetup(Context c, boolean ready) { prefs(c).edit().putBoolean(K_SETUP, ready).apply(); }

    static String theme(Context c) {
        String value = prefs(c).getString(K_THEME, "system");
        if (!"light".equals(value) && !"dark".equals(value)) return "system";
        return value;
    }

    static void saveTheme(Context c, String value) {
        if (!"light".equals(value) && !"dark".equals(value)) value = "system";
        prefs(c).edit().putString(K_THEME, value).apply();
    }

    static boolean useDark(Context c) {
        String value = theme(c);
        if ("dark".equals(value)) return true;
        if ("light".equals(value)) return false;
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    static int themeIndex(Context c) {
        String value = theme(c);
        if ("light".equals(value)) return 1;
        if ("dark".equals(value)) return 2;
        return 0;
    }

    static LocationInfo getLocation(Context c) {
        LocationInfo out = new LocationInfo();
        String raw = prefs(c).getString(K_LOCATION, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        try {
            JSONObject o = new JSONObject(raw);
            out.zip = o.optString("zip", "");
            out.city = o.optString("city", "");
            out.state = o.optString("state", "");
            out.county = o.optString("county", "");
            out.timezone = o.optString("timezone", "America/Detroit");
            out.latitude = o.optDouble("latitude", 0);
            out.longitude = o.optDouble("longitude", 0);
        } catch (Exception ignored) {}
        return out;
    }

    static void saveLocation(Context c, LocationInfo loc) {
        try {
            JSONObject o = new JSONObject();
            o.put("zip", loc.zip);
            o.put("city", loc.city);
            o.put("state", loc.state);
            o.put("county", loc.county);
            o.put("timezone", loc.timezone);
            o.put("latitude", loc.latitude);
            o.put("longitude", loc.longitude);
            prefs(c).edit().putString(K_LOCATION, o.toString()).apply();
        } catch (Exception ignored) {}
    }

    static List<SourceConfig> getSources(Context c) {
        List<SourceConfig> out = new ArrayList<>();
        String raw = prefs(c).getString(K_SOURCES, "[]");
        try {
            JSONArray a = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                SourceConfig s = new SourceConfig();
                s.name = o.optString("name", "Source");
                s.feedUrl = o.optString("feedUrl", "");
                s.homepageUrl = o.optString("homepageUrl", "");
                s.mode = o.optString("mode", "direct");
                s.enabled = o.optBoolean("enabled", true);
                s.failures = o.optInt("failures", 0);
                if (!s.feedUrl.trim().isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveSources(Context c, List<SourceConfig> sources) {
        JSONArray a = new JSONArray();
        try {
            for (SourceConfig s : sources) {
                JSONObject o = new JSONObject();
                o.put("name", s.name);
                o.put("feedUrl", s.feedUrl);
                o.put("homepageUrl", s.homepageUrl);
                o.put("mode", s.mode);
                o.put("enabled", s.enabled);
                o.put("failures", s.failures);
                a.put(o);
            }
        } catch (Exception ignored) {}
        prefs(c).edit().putString(K_SOURCES, a.toString()).apply();
    }

    static boolean includeSports(Context c) { return prefs(c).getBoolean(K_SPORTS, false); }
    static boolean includeObits(Context c) { return prefs(c).getBoolean(K_OBITS, false); }
    static boolean includeOpinion(Context c) { return prefs(c).getBoolean(K_OPINION, false); }
    static boolean includeRecipes(Context c) { return prefs(c).getBoolean(K_RECIPES, false); }

    static void saveFilters(Context c, boolean sports, boolean obits, boolean opinion, boolean recipes) {
        prefs(c).edit()
                .putBoolean(K_SPORTS, sports)
                .putBoolean(K_OBITS, obits)
                .putBoolean(K_OPINION, opinion)
                .putBoolean(K_RECIPES, recipes)
                .apply();
    }

    static long lastRefresh(Context c) { return prefs(c).getLong(K_LAST_REFRESH, 0L); }
    static void setLastRefresh(Context c, long value) { prefs(c).edit().putLong(K_LAST_REFRESH, value).apply(); }

    static File editionFile(Context c) { return new File(c.getFilesDir(), "morning-brief.html"); }

    static String readCachedEdition(Context c) {
        try {
            File f = editionFile(c);
            if (!f.exists()) return "";
            try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) { if (n > 0) out.write(buf, 0, n); }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "";
        }
    }

    static void writeCachedEdition(Context c, String html) throws Exception {
        try (FileOutputStream out = new FileOutputStream(editionFile(c))) {
            out.write(html.getBytes(StandardCharsets.UTF_8));
        }
    }

    static void saveStoryCache(Context c, List<Story> stories) {
        JSONArray a = new JSONArray();
        try {
            for (Story st : stories) {
                JSONObject o = new JSONObject();
                o.put("source", st.source);
                o.put("sourceMode", st.sourceMode);
                o.put("title", st.title);
                o.put("url", st.url);
                o.put("summary", st.summary);
                o.put("imageUrl", st.imageUrl);
                o.put("publishedEpoch", st.publishedEpoch);
                o.put("editorialScore", st.editorialScore);
                o.put("topic", st.topic);
                o.put("section", st.section);
                a.put(o);
            }
            prefs(c).edit().putString("story_cache", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    static List<Story> loadStoryCache(Context c) {
        List<Story> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("story_cache", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Story st = new Story();
                st.source = o.optString("source", "");
                st.sourceMode = o.optString("sourceMode", "");
                st.title = o.optString("title", "");
                st.url = o.optString("url", "");
                st.summary = o.optString("summary", "");
                st.imageUrl = o.optString("imageUrl", "");
                st.publishedEpoch = o.optLong("publishedEpoch", 0L);
                st.editorialScore = o.optDouble("editorialScore", 0);
                st.topic = o.optString("topic", "general");
                st.section = o.optString("section", "MORE TO KNOW");
                st.cached = true;
                if (!st.title.trim().isEmpty() && !st.url.trim().isEmpty()) out.add(st);
            }
        } catch (Exception ignored) {}
        return out;
    }

    static void saveWeatherCache(Context c, WeatherData w) {
        if (w == null || !w.available) return;
        try {
            JSONObject root = new JSONObject();
            root.put("label", w.label);
            JSONArray hours = new JSONArray();
            for (WeatherData.Hour h : w.hours) {
                JSONObject o = new JSONObject();
                o.put("label", h.label); o.put("temp", h.temp); o.put("rain", h.rain); o.put("condition", h.condition);
                hours.put(o);
            }
            JSONArray days = new JSONArray();
            for (WeatherData.Day d : w.days) {
                JSONObject o = new JSONObject();
                o.put("label", d.label); o.put("high", d.high); o.put("low", d.low); o.put("rain", d.rain); o.put("condition", d.condition);
                days.put(o);
            }
            root.put("hours", hours); root.put("days", days); root.put("saved", System.currentTimeMillis());
            prefs(c).edit().putString("weather_cache", root.toString()).apply();
        } catch (Exception ignored) {}
    }

    static WeatherData loadWeatherCache(Context c) {
        WeatherData w = new WeatherData();
        try {
            String raw = prefs(c).getString("weather_cache", "");
            if (raw == null || raw.trim().isEmpty()) return w;
            JSONObject root = new JSONObject(raw);
            w.label = root.optString("label", "WEATHER");
            JSONArray hours = root.optJSONArray("hours");
            if (hours != null) for (int i = 0; i < hours.length(); i++) {
                JSONObject o = hours.optJSONObject(i); if (o == null) continue;
                WeatherData.Hour h = new WeatherData.Hour();
                h.label = o.optString("label", ""); h.temp = o.optInt("temp"); h.rain = o.optInt("rain"); h.condition = o.optString("condition", "");
                w.hours.add(h);
            }
            JSONArray days = root.optJSONArray("days");
            if (days != null) for (int i = 0; i < days.length(); i++) {
                JSONObject o = days.optJSONObject(i); if (o == null) continue;
                WeatherData.Day d = new WeatherData.Day();
                d.label = o.optString("label", ""); d.high = o.optInt("high"); d.low = o.optInt("low"); d.rain = o.optInt("rain"); d.condition = o.optString("condition", "");
                w.days.add(d);
            }
            w.available = !w.hours.isEmpty() || !w.days.isEmpty();
        } catch (Exception ignored) {}
        return w;
    }
}
