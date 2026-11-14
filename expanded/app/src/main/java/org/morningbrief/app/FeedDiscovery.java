package org.morningbrief.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FeedDiscovery {
    private FeedDiscovery() {}

    private static final Pattern LINK_TAG = Pattern.compile("<link\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(['\"])(.*?)\\2", Pattern.DOTALL);
    private static final Pattern INN_ORG_LINK = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*(['\"])(?:https?://(?:www\\.)?findyournews\\.org)?(/organization/[^'\"?#]+/?)\\1[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HREF_ANCHOR = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*(['\"])(https?://[^'\"]+)\\1[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    static List<SourceConfig> discover(LocationInfo loc) throws Exception {
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();

        // First: INN's public Find Your News directory. This is specifically
        // designed to surface nonprofit public-service newsrooms by place.
        addInnPlace(candidates, loc.city, 30);
        if (loc.county != null && !loc.county.trim().isEmpty()) {
            addInnPlace(candidates, loc.county, 28);
        }

        // Second: Google News discovery. Multiple query shapes matter because
        // smaller community/nonprofit outlets often do not rank in a generic
        // "local news" query even when they are actively publishing.
        List<QuerySpec> queries = new ArrayList<>();
        queries.add(new QuerySpec(loc.city + " " + loc.state + " local news", 10));
        queries.add(new QuerySpec(loc.city + " " + loc.state + " community news", 15));
        queries.add(new QuerySpec(loc.city + " " + loc.state + " nonprofit news", 18));
        queries.add(new QuerySpec(loc.city + " " + loc.state + " independent local news", 14));
        queries.add(new QuerySpec(loc.city + " " + loc.state + " public media news", 12));

        if (loc.county != null && !loc.county.trim().isEmpty()) {
            queries.add(new QuerySpec(loc.county + " " + loc.state + " local news", 11));
            queries.add(new QuerySpec(loc.county + " " + loc.state + " nonprofit news", 18));
            queries.add(new QuerySpec(loc.county + " " + loc.state + " community news", 15));
        }

        for (QuerySpec q : queries) {
            try {
                for (Candidate c : discoverGoogleCandidates(q.query)) {
                    addCandidate(candidates, c.name, c.homepage, q.weight, false);
                }
            } catch (Exception ignored) {
                // Discovery layers are deliberately independent. A temporary
                // Google failure must not erase INN results and vice versa.
            }
        }

        List<Candidate> probe = new ArrayList<>(candidates.values());
        for (Candidate c : probe) c.score += sourceHintScore(c, loc);
        probe.sort(Comparator
                .comparingInt((Candidate c) -> c.score).reversed()
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.hits).reversed())
                .thenComparing(c -> c.name.toLowerCase(Locale.US)));

        // Setup is infrequent, so it is worth probing more than the old first-12
        // cutoff. Scoring keeps nonprofit/community results near the front.
        if (probe.size() > 24) probe = new ArrayList<>(probe.subList(0, 24));

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<SourceConfig>> futures = new ArrayList<>();
        for (Candidate c : probe) {
            final Candidate item = c;
            futures.add(pool.submit(new Callable<>() {
                @Override public SourceConfig call() {
                    try {
                        String feed = discoverDirectFeed(item.homepage);
                        if (feed != null && !feed.trim().isEmpty()) {
                            return new SourceConfig(
                                    item.name,
                                    feed,
                                    item.homepage,
                                    item.inn ? "inn-direct" : "direct");
                        }
                    } catch (Exception ignored) {}

                    String google = googleSourceFeed(item.name, loc);
                    return new SourceConfig(
                            item.name + " (via Google News)",
                            google,
                            item.homepage,
                            item.inn ? "inn-google" : "google");
                }
            }));
        }
        pool.shutdown();

        List<SourceConfig> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // Known public-service sources get a direct-feed fast path. This keeps
        // strong nonprofit outlets from being buried by generic discovery and
        // also makes upgrades self-healing for existing West Michigan installs.
        for (SourceConfig preferred : preferredLocalSources(loc)) {
            String key = domainKey(preferred.homepageUrl);
            if (key.isEmpty()) key = preferred.feedUrl;
            if (seen.add(key)) out.add(preferred);
        }

        for (Future<SourceConfig> f : futures) {
            try {
                SourceConfig s = f.get();
                if (s == null) continue;
                String key = domainKey(s.homepageUrl);
                if (key.isEmpty()) key = s.feedUrl;
                if (seen.add(key)) out.add(s);
            } catch (Exception ignored) {}
        }

        if (out.isEmpty()) {
            out.add(new SourceConfig(
                    loc.label() + " Local News (Google News)",
                    googleFeed(loc.city + " " + loc.state + " local news"),
                    "https://news.google.com/",
                    "google-local"));
        }
        return out;
    }

    static List<SourceConfig> mergePreferredSources(LocationInfo loc, List<SourceConfig> existing) {
        List<SourceConfig> preferred = preferredLocalSources(loc);
        if (preferred.isEmpty()) return existing;

        List<SourceConfig> out = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();

        for (SourceConfig pref : preferred) {
            String prefKey = domainKey(pref.homepageUrl);
            SourceConfig match = null;
            for (SourceConfig current : existing) {
                String currentKey = domainKey(current.homepageUrl);
                if ((!prefKey.isEmpty() && prefKey.equals(currentKey))
                        || pref.cleanName().equalsIgnoreCase(current.cleanName())) {
                    match = current;
                    break;
                }
            }
            if (match != null) {
                boolean enabled = match.enabled;
                boolean alreadyPreferred = match.mode != null && match.mode.startsWith("preferred-");
                match.name = pref.name;
                match.homepageUrl = pref.homepageUrl;
                if (!alreadyPreferred) {
                    match.feedUrl = pref.feedUrl;
                    match.mode = pref.mode;
                    match.failures = 0;
                }
                match.enabled = enabled;
                out.add(match);
            } else {
                out.add(pref);
            }
            if (!prefKey.isEmpty()) used.add(prefKey);
        }

        for (SourceConfig current : existing) {
            String key = domainKey(current.homepageUrl);
            boolean duplicate = !key.isEmpty() && used.contains(key);
            if (!duplicate) {
                for (SourceConfig pref : preferred) {
                    if (pref.cleanName().equalsIgnoreCase(current.cleanName())) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate) out.add(current);
        }
        return out;
    }

    private static List<SourceConfig> preferredLocalSources(LocationInfo loc) {
        List<SourceConfig> out = new ArrayList<>();
        if (loc == null || loc.state == null || !"MI".equalsIgnoreCase(loc.state.trim())) return out;

        // Bridge is statewide and explicitly serves West Michigan as well.
        out.add(new SourceConfig(
                "Bridge Michigan",
                "https://bridgemi.com/feed/",
                "https://bridgemi.com/",
                "preferred-direct"));

        String county = loc.county == null ? "" : loc.county.toLowerCase(Locale.US);
        String city = loc.city == null ? "" : loc.city.toLowerCase(Locale.US);
        boolean kent = county.contains("kent");
        boolean ottawa = county.contains("ottawa");
        boolean grandRapidsMetro = kent || ottawa
                || city.contains("grand rapids") || city.contains("wyoming")
                || city.contains("kentwood") || city.contains("walker")
                || city.contains("grandville") || city.contains("jenison")
                || city.contains("hudsonville");

        if (grandRapidsMetro) {
            out.add(new SourceConfig(
                    "The Rapidian",
                    "https://therapidian.org/feed/",
                    "https://therapidian.org/",
                    "preferred-direct"));
        }
        if (ottawa || city.contains("jenison") || city.contains("hudsonville")
                || city.contains("grand haven") || city.contains("holland")) {
            out.add(new SourceConfig(
                    "Ottawa News Network",
                    "https://www.ottawanewsnetwork.org/rss/",
                    "https://www.ottawanewsnetwork.org/",
                    "preferred-direct"));
        }
        return out;
    }

    private static void addInnPlace(Map<String, Candidate> candidates, String place, int weight) {
        if (place == null || place.trim().isEmpty()) return;
        try {
            for (Candidate c : discoverInnCandidates(place.trim())) {
                addCandidate(candidates, c.name, c.homepage, weight, true);
            }
        } catch (Exception ignored) {}
    }

    private static void addCandidate(Map<String, Candidate> candidates, String name, String homepage,
                                     int weight, boolean inn) {
        String key = domainKey(homepage);
        if (key.isEmpty()) return;
        Candidate existing = candidates.get(key);
        if (existing == null) {
            existing = new Candidate(name, homepage);
            candidates.put(key, existing);
        }
        existing.hits++;
        existing.score += weight;
        existing.inn = existing.inn || inn;
        if ((existing.name == null || existing.name.trim().isEmpty()) && name != null) existing.name = name;
    }

    private static int sourceHintScore(Candidate c, LocationInfo loc) {
        int score = 0;
        String n = (c.name + " " + c.homepage).toLowerCase(Locale.US);
        if (c.inn) score += 25;
        if (domainKey(c.homepage).endsWith(".org")) score += 4;
        if (n.contains("nonprofit") || n.contains("non-profit")) score += 7;
        if (n.contains("community")) score += 4;
        if (n.contains("public media") || n.contains("public radio")) score += 4;
        if (n.contains("independent")) score += 3;
        if (loc.city != null && !loc.city.isEmpty() && n.contains(loc.city.toLowerCase(Locale.US))) score += 5;
        if (loc.county != null && !loc.county.isEmpty() && n.contains(loc.county.toLowerCase(Locale.US))) score += 5;
        return score;
    }

    /**
     * Query the public FindYourNews.org directory by a covered place, then resolve
     * each newsroom card to its publisher website. Failure here is non-fatal; the
     * Google discovery layer still runs.
     */
    private static List<Candidate> discoverInnCandidates(String place) throws Exception {
        String url = "https://findyournews.org/explore/?city_covered="
                + URLEncoder.encode(place, StandardCharsets.UTF_8.toString());
        String html = Net.getString(url, 900_000);

        LinkedHashMap<String, String> orgs = new LinkedHashMap<>();
        Matcher m = INN_ORG_LINK.matcher(html);
        while (m.find()) {
            String path = m.group(2);
            String name = cleanHtml(m.group(3));
            if (!name.isEmpty()) orgs.putIfAbsent(path, name);
            if (orgs.size() >= 12) break;
        }

        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, String> e : orgs.entrySet()) {
            try {
                String orgUrl = "https://findyournews.org" + e.getKey();
                String orgHtml = Net.getString(orgUrl, 650_000);
                String publisher = findInnPublisherWebsite(orgHtml);
                if (publisher != null && !publisher.isEmpty()) {
                    out.add(new Candidate(e.getValue(), publisher));
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static String findInnPublisherWebsite(String html) {
        Matcher m = HREF_ANCHOR.matcher(html);
        while (m.find()) {
            String href = decodeHtml(m.group(2)).trim();
            String text = cleanHtml(m.group(3)).toLowerCase(Locale.US);
            if (!text.equals("website") && !text.contains("visit website")) continue;
            String host = domainKey(href);
            if (host.isEmpty()) continue;
            if (host.endsWith("findyournews.org") || host.endsWith("inn.org")) continue;
            return href;
        }
        return null;
    }

    static String discoverDirectFeed(String homepage) throws Exception {
        if (homepage == null || homepage.trim().isEmpty()) return null;
        String html = Net.getString(homepage, 700_000);
        Matcher tags = LINK_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            Map<String, String> attrs = attrs(tag);
            String rel = attrs.getOrDefault("rel", "").toLowerCase(Locale.US);
            String type = attrs.getOrDefault("type", "").toLowerCase(Locale.US);
            String href = attrs.getOrDefault("href", "");
            if (rel.contains("alternate") && !href.trim().isEmpty()
                    && (type.contains("rss") || type.contains("atom") || type.contains("xml"))) {
                String resolved = new URL(new URL(homepage), decodeHtml(href)).toString();
                if (looksLikeFeed(resolved)) return resolved;
            }
        }

        URL base = new URL(homepage);
        String root = base.getProtocol() + "://" + base.getHost();
        String[] common = {"/feed/", "/feed", "/rss", "/rss/", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml"};
        for (String path : common) {
            String candidate = root + path;
            try {
                if (looksLikeFeed(candidate)) return candidate;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean looksLikeFeed(String url) throws Exception {
        String s = Net.getString(url, 160_000).trim().toLowerCase(Locale.US);
        return s.contains("<rss") || s.contains("<feed") || s.contains("<rdf:rdf");
    }

    private static Map<String, String> attrs(String tag) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(tag);
        while (m.find()) out.put(m.group(1).toLowerCase(Locale.US), decodeHtml(m.group(3)));
        return out;
    }

    private static List<Candidate> discoverGoogleCandidates(String query) throws Exception {
        byte[] xml = Net.getBytes(googleFeed(query), 900_000);
        XmlPullParser p = Xml.newPullParser();
        p.setInput(new ByteArrayInputStream(xml), "UTF-8");
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "source".equalsIgnoreCase(p.getName())) {
                String homepage = attr(p, "url");
                String name = safeNextText(p);
                if (!homepage.trim().isEmpty() && !name.trim().isEmpty()) {
                    String key = domainKey(homepage);
                    if (!key.trim().isEmpty() && seen.add(key)) out.add(new Candidate(name, homepage));
                }
            }
            event = p.next();
        }
        return out;
    }

    static String googleFeed(String query) {
        try {
            return "https://news.google.com/rss/search?q="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    + "&hl=en-US&gl=US&ceid=US:en";
        } catch (Exception e) {
            return "https://news.google.com/rss/search?q=local+news&hl=en-US&gl=US&ceid=US:en";
        }
    }

    static String googleSourceFeed(String sourceName, LocationInfo loc) {
        String clean = sourceName.replace("\"", "");
        return googleFeed("\"" + clean + "\" " + loc.city + " " + loc.state);
    }

    private static String cleanHtml(String value) {
        if (value == null) return "";
        return decodeHtml(TAGS.matcher(value).replaceAll(" "))
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String decodeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String domainKey(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.US);
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    private static String attr(XmlPullParser p, String name) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (name.equalsIgnoreCase(p.getAttributeName(i))) return p.getAttributeValue(i);
        }
        return "";
    }

    private static String safeNextText(XmlPullParser p) {
        try { return p.nextText().trim(); } catch (Exception e) { return ""; }
    }

    private static final class QuerySpec {
        final String query;
        final int weight;
        QuerySpec(String query, int weight) { this.query = query; this.weight = weight; }
    }

    private static final class Candidate {
        String name;
        final String homepage;
        int score = 0;
        int hits = 0;
        boolean inn = false;
        Candidate(String name, String homepage) {
            this.name = name;
            this.homepage = homepage;
        }
    }
}
