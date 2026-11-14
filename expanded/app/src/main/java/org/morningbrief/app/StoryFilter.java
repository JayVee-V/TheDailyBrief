package org.morningbrief.app;

import android.content.Context;

import java.util.Locale;

final class StoryFilter {
    private StoryFilter() {}

    static boolean keep(Context c, Story s) {
        String hay = (s.title + " " + s.summary).toLowerCase(Locale.US);
        if (!SettingsStore.includeObits(c) && containsAny(hay, "obituary", "obituaries", "funeral notice", "death notice")) return false;
        if (!SettingsStore.includeRecipes(c) && containsAny(hay, "recipe:", "recipes:", "recipe of the", "pen & pantry", "pen and pantry")) return false;
        if (!SettingsStore.includeOpinion(c) && containsAny(hay,
                "opinion:", "guest column", "column:", "letter to the editor", "letters to the editor", "editorial:")) return false;
        if (!SettingsStore.includeSports(c) && looksSports(hay)) return false;
        return true;
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private static boolean looksSports(String s) {
        return containsAny(s,
                "high school football", "high school basketball", "varsity", "district championship",
                "conference title", "season opener", "playoff game", "final score", "wins  ",
                "beats ", "defeats ", "soccer team", "baseball team", "softball team", "volleyball team");
    }
}
