package org.morningbrief.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.time.Duration;
import java.time.ZonedDateTime;

final class Scheduler {
    static final int JOB_ID = 61021;
    private Scheduler() {}

    static void ensureScheduled(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(JOB_ID) != null) return;
        scheduleNext(c);
    }

    static void scheduleNext(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime target = now.withHour(4).withMinute(45).withSecond(0).withNano(0);
        if (!target.isAfter(now.plusMinutes(5))) target = target.plusDays(1);
        long delay = Math.max(60_000L, Duration.between(now, target).toMillis());

        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, MorningJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(delay)
                .setOverrideDeadline(delay + 2L * 60L * 60L * 1000L)
                .build();
        js.schedule(job);
    }
}
