package org.morningbrief.app;

import android.content.Context;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/** Persistent daily attempt gate shared by foreground and scheduled jobs. */
final class DailyRefresh {
    static final String LOCAL="local", NATIONAL="national";
    private static final Set<String> running=new HashSet<>();
    private DailyRefresh() {}
    private static String day(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString();
    }
    static synchronized boolean begin(Context c,String section,boolean manual,long lastEdition) {
        if(running.contains(section))return false;
        String today=LocalDate.now().toString();
        android.content.SharedPreferences p=SettingsStore.prefs(c);
        String key="daily_attempt_"+section;
        String previous=p.getString(key,"");
        if(!manual && (today.equals(previous) || (lastEdition>0 && today.equals(day(lastEdition)))))return false;
        // Record before networking, so a failed or interrupted fetch cannot loop on each launch.
        if(!p.edit().putString(key,today).commit())return false;
        running.add(section);
        return true;
    }
    static synchronized void end(String section) { running.remove(section); }
}
