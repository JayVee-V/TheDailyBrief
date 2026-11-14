package org.morningbrief.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small, deterministic editorial layer. It deliberately avoids ML/runtime
 * dependencies: the goal is a useful local front page, not a generic RSS dump.
 */
final class EditorialEngine {
    private EditorialEngine() {}

    static final int HARD_CAP = 25;
    static final double QUALITY_FLOOR = 35.0;

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a","an","and","are","as","at","be","by","for","from","has","have","in","into","is","it",
            "its","of","on","or","that","the","their","this","to","was","were","will","with","after","before",
            "over","under","new","says","say","amid","about","how","why","what","when","where","who"
    ));

    static List<Story> select(LocationInfo loc, List<Story> input, long now) {
        List<Scored> scored = new ArrayList<>();
        for (Story s : input) {
            if (s == null || s.title == null || s.title.trim().isEmpty()) continue;
            Scored x = new Scored();
            x.story = s;
            x.topic = topicFor(s);
            x.locality = localityScore(s, loc);
            x.base = baseScore(s, loc, now, x.topic, x.locality);
            scored.add(x);
        }

        // Sort by intrinsic quality before clustering. When several publishers
        // cover the same event, the strongest/nonprofit source becomes the rep.
        scored.sort((a, b) -> Double.compare(b.base, a.base));
        List<Scored> clustered = new ArrayList<>();
        for (Scored candidate : scored) {
            boolean duplicate = false;
            for (Scored rep : clustered) {
                if (sameEvent(candidate.story, rep.story)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) clustered.add(candidate);
        }

        List<Story> out = new ArrayList<>();
        Map<String, Integer> sourceCounts = new HashMap<>();
        Map<String, Integer> topicCounts = new HashMap<>();
        Set<Scored> remaining = new LinkedHashSet<>(clustered);

        while (!remaining.isEmpty() && out.size() < HARD_CAP) {
            Scored best = null;
            double bestAdjusted = Double.NEGATIVE_INFINITY;
            for (Scored x : remaining) {
                int sourceN = sourceCounts.getOrDefault(sourceKey(x.story), 0);
                int topicN = topicCounts.getOrDefault(x.topic, 0);
                double adjusted = x.base - sourcePenalty(sourceN) - topicPenalty(topicN);
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = x;
                }
            }
            if (best == null || bestAdjusted < QUALITY_FLOOR) break;

            remaining.remove(best);
            Story chosen = best.story;
            chosen.editorialScore = bestAdjusted;
            chosen.topic = best.topic;
            chosen.section = sectionFor(chosen, loc, best.locality, out.size());
            out.add(chosen);
            sourceCounts.put(sourceKey(chosen), sourceCounts.getOrDefault(sourceKey(chosen), 0) + 1);
            topicCounts.put(best.topic, topicCounts.getOrDefault(best.topic, 0) + 1);
        }
        return out;
    }

    private static double baseScore(Story s, LocationInfo loc, long now, String topic, int locality) {
        double score;
        String mode = s.sourceMode == null ? "" : s.sourceMode;
        if (mode.equals("preferred-direct")) score = 44;
        else if (mode.equals("preferred-google")) score = 38;
        else if (mode.equals("inn-direct")) score = 34;
        else if (mode.equals("inn-google")) score = 28;
        else if (mode.endsWith("direct")) score = 18;
        else score = 5;

        score += freshnessScore(s, now);
        score += locality;
        score += topicScore(topic, s);

        String hay = hay(s);
        if (s.summary != null && s.summary.trim().length() >= 100) score += 2;
        if (containsAny(hay, "sponsored", "advertisement", "paid content", "partner content")) score -= 25;
        if (containsAny(hay, "lottery", "winning numbers", "mega millions", "powerball")) score -= 12;
        if (containsAny(hay, "deal of the day", "amazon deal", "shopping guide", "coupon")) score -= 12;
        if (containsAny(hay, "watch live", "watch:", "video:", "photo gallery")) score -= 4;
        if (containsAny(hay, "shooting", "homicide", "murder", "police chase", "arrested")) score -= 2;
        if (s.cached) score -= 10;
        return score;
    }

    private static double freshnessScore(Story s, long now) {
        if (s.publishedEpoch <= 0) return 2;
        long age = Math.max(0, now - s.publishedEpoch);
        long h = age / (60L * 60L * 1000L);
        if (h <= 12) return 12;
        if (h <= 24) return 10;
        if (h <= 48) return 7;
        if (h <= 72) return 4;
        if (h <= 96) return 1;
        return -10;
    }

    private static int localityScore(Story s, LocationInfo loc) {
        String hay = hay(s);
        int score = 0;
        String city = cleanPlace(loc == null ? "" : loc.city);
        String county = cleanPlace(loc == null ? "" : loc.county);
        if (!city.isEmpty() && hay.contains(city)) score += 12;
        if (!county.isEmpty() && hay.contains(county)) score += 10;

        if (loc != null && "MI".equalsIgnoreCase(loc.state == null ? "" : loc.state.trim())) {
            if (hay.contains("michigan")) score += 5;
            if (isWestMichigan(loc) && containsAny(hay,
                    "west michigan", "grand rapids", "kent county", "ottawa county", "holland", "grand haven",
                    "wyoming", "kentwood", "walker", "grandville", "hudsonville", "jenison")) score += 8;
        }
        return Math.min(score, 20);
    }

    private static boolean isWestMichigan(LocationInfo loc) {
        String c = cleanPlace(loc.city);
        String county = cleanPlace(loc.county);
        return county.contains("kent") || county.contains("ottawa")
                || containsAny(c, "grand rapids", "wyoming", "kentwood", "walker", "grandville", "jenison",
                "hudsonville", "holland", "grand haven");
    }

    private static String topicFor(Story s) {
        String h = hay(s);
        if (containsAny(h, "city council", "county board", "commission", "commissioner", "legislature", "senate",
                "house bill", "governor", "mayor", "budget", "ballot", "election", "zoning", "ordinance",
                "public records", "foia", "government", "millage", "tax proposal")) return "public affairs";
        if (containsAny(h, "school", "district", "teacher", "student", "college", "university", "education")) return "education";
        if (containsAny(h, "housing", "apartment", "development", "developer", "zoning", "transit", "bus route",
                "road", "bridge", "infrastructure", "construction")) return "growth & infrastructure";
        if (containsAny(h, "environment", "water", "river", "lake", "pfas", "climate", "conservation", "wildlife")) return "environment";
        if (containsAny(h, "hospital", "health", "clinic", "mental health", "public health", "medicaid")) return "health";
        if (containsAny(h, "business", "economy", "jobs", "employer", "labor", "union", "wages", "factory", "plant")) return "economy";
        if (containsAny(h, "court", "lawsuit", "judge", "supreme court", "appeal")) return "courts";
        if (containsAny(h, "arts", "museum", "library", "festival", "community", "neighborhood", "nonprofit")) return "community";
        if (containsAny(h, "shooting", "homicide", "murder", "arrest", "police", "fire department", "crash")) return "public safety";
        return "general";
    }

    private static int topicScore(String topic, Story s) {
        return switch (topic) {
            case "public affairs" -> 10;
            case "education" -> 7;
            case "growth & infrastructure", "environment", "health", "economy", "courts" -> 6;
            case "community" -> 4;
            case "public safety" -> 1;
            default -> 0;
        };
    }

    private static String sectionFor(Story s, LocationInfo loc, int locality, int index) {
        if (index < 3) return "TOP STORIES";
        String source = s.source == null ? "" : s.source.toLowerCase(Locale.US);
        if (locality >= 8 || source.contains("rapidian") || source.contains("ottawa news network")) return "LOCAL";
        String hay = hay(s);
        if ((loc != null && "MI".equalsIgnoreCase(loc.state == null ? "" : loc.state.trim()))
                && (hay.contains("michigan") || source.contains("bridge michigan"))) return "MICHIGAN";
        return "MORE TO KNOW";
    }

    private static double sourcePenalty(int previous) {
        return switch (previous) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 9;
            case 4 -> 14;
            case 5 -> 20;
            default -> 20 + (previous - 5) * 7.0;
        };
    }

    private static double topicPenalty(int previous) {
        return switch (previous) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 6;
            case 4 -> 10;
            default -> 10 + (previous - 4) * 5.0;
        };
    }

    private static boolean sameEvent(Story a, Story b) {
        String an = normalize(a.title);
        String bn = normalize(b.title);
        if (an.equals(bn)) return true;
        if (an.length() >= 28 && bn.length() >= 28 && (an.contains(bn) || bn.contains(an))) return true;
        Set<String> at = headlineTokens(a.title);
        Set<String> bt = headlineTokens(b.title);
        if (at.size() < 3 || bt.size() < 3) return false;
        int common = 0;
        for (String t : at) if (bt.contains(t)) common++;
        int union = at.size() + bt.size() - common;
        double jaccard = union == 0 ? 0 : (double) common / union;
        double overlap = (double) common / Math.min(at.size(), bt.size());
        return jaccard >= 0.52 || (overlap >= 0.72 && common >= 3);
    }

    private static Set<String> headlineTokens(String title) {
        Set<String> out = new HashSet<>();
        for (String t : normalize(title).split("\\s+")) {
            if (t.length() < 3 || STOPWORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private static String sourceKey(Story s) {
        return normalize(s.source == null ? "" : s.source);
    }

    private static String hay(Story s) {
        return ((s.title == null ? "" : s.title) + " " + (s.summary == null ? "" : s.summary)).toLowerCase(Locale.US);
    }

    private static String cleanPlace(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String s) {
        return (s == null ? "" : s.toLowerCase(Locale.US)).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) if (hay.contains(n)) return true;
        return false;
    }

    private static final class Scored {
        Story story;
        double base;
        String topic;
        int locality;
    }
}
