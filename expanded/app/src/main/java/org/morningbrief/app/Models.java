package org.morningbrief.app;

import java.util.ArrayList;
import java.util.List;

final class SourceConfig {
    String name = "";
    String feedUrl = "";
    String homepageUrl = "";
    String mode = "direct"; // preferred-direct, inn-direct, direct, preferred-google, inn-google, google
    boolean enabled = true;
    int failures = 0;

    SourceConfig() {}
    SourceConfig(String name, String feedUrl, String homepageUrl, String mode) {
        this.name = name;
        this.feedUrl = feedUrl;
        this.homepageUrl = homepageUrl;
        this.mode = mode;
    }

    String cleanName() { return name.replace(" (via Google News)", ""); }
    boolean isPreferred() { return mode != null && mode.startsWith("preferred-"); }
    boolean isNonprofit() { return isPreferred() || (mode != null && mode.startsWith("inn-")); }
    boolean isGoogle() { return mode != null && mode.contains("google"); }
    boolean isDirect() { return mode != null && mode.endsWith("direct"); }
}

final class LocationInfo {
    String zip = "";
    String city = "";
    String state = "";
    String county = "";
    String timezone = "America/Detroit";
    double latitude = 0;
    double longitude = 0;

    String label() {
        if (!city.trim().isEmpty() && !state.trim().isEmpty()) return city + ", " + state;
        return zip;
    }
}

final class Story {
    String source = "";
    String sourceMode = "";
    String title = "";
    String url = "";
    String summary = "";
    String imageUrl = "";
    long publishedEpoch = 0L;
    boolean cached = false;
    double editorialScore = 0;
    String topic = "general";
    String section = "MORE TO KNOW";

    boolean isPreferred() { return sourceMode != null && sourceMode.startsWith("preferred-"); }
    boolean isNonprofit() { return isPreferred() || (sourceMode != null && sourceMode.startsWith("inn-")); }
    boolean isGoogle() { return sourceMode != null && sourceMode.contains("google"); }
    boolean isDirect() { return sourceMode != null && sourceMode.endsWith("direct"); }
}

final class WeatherData {
    static final class Hour {
        String label = "";
        int temp;
        int rain;
        String condition = "";
    }
    static final class Day {
        String label = "";
        int high;
        int low;
        int rain;
        String condition = "";
    }
    String label = "WEATHER";
    List<Hour> hours = new ArrayList<>();
    List<Day> days = new ArrayList<>();
    boolean available = false;
}
