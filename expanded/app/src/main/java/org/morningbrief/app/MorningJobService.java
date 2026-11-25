package org.morningbrief.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class MorningJobService extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                if (SettingsStore.isSetup(this)) {
                    if (DailyRefresh.begin(this,DailyRefresh.LOCAL,false,SettingsStore.lastRefresh(this))) {
                        try { EditionEngine.refresh(this); }
                        catch (Exception ignored) { /* Keep the last successful edition. */ }
                        finally { DailyRefresh.end(DailyRefresh.LOCAL); }
                    }
                    NationalNews.refreshScheduled(this);
                    ComicEdition.refresh(getApplicationContext(),false);
                }
            } catch (Exception ignored) {
                // No automatic retry until the next edition day.
            } finally {
                Scheduler.scheduleNext(this);
                jobFinished(params, false);
            }
        }, "morning-brief-refresh").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
