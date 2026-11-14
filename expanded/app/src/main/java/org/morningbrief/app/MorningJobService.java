package org.morningbrief.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class MorningJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                if (SettingsStore.isSetup(this)) EditionEngine.refresh(this);
            } catch (Exception ignored) {
                // Keep the last successful edition. The next foreground open can retry.
            } finally {
                Scheduler.scheduleNext(this);
                jobFinished(params, false);
            }
        }, "morning-brief-refresh").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return true;
    }
}
