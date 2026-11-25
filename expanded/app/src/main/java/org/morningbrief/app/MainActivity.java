package org.morningbrief.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int PAGE_MORNING = 0;
    private static final int PAGE_NATIONAL = 1;
    private static final int PAGE_WEEKEND = 2;
    private static final int PAGE_FUNNIES = 3;

    private WebView web;
    private FunniesPuzzles funnies;
    private WeekendReading weekend;
    private NationalNews national;
    private TextView title;
    private int currentPage = PAGE_MORNING;
    private ProgressBar progress;
    private TextView progressStatus;
    private volatile boolean refreshing = false;
    private boolean uiDark = false;
    private boolean cachedSupportsTheme = true;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SettingsStore.isSetup(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        SavedNews.migrate(this);
        buildUi();
        loadCached();
        Scheduler.ensureScheduled(this);
        refresh(false);
    }

    private void buildUi() {
        uiDark = SettingsStore.useDark(this);
        int background = uiDark ? Color.rgb(16, 17, 15) : Color.rgb(250, 250, 246);
        int ink = uiDark ? Color.rgb(240, 239, 232) : Color.rgb(17, 17, 17);
        int muted = uiDark ? Color.rgb(184, 184, 176) : Color.rgb(88, 88, 82);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(uiDark ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(6), dp(6), dp(6));
        bar.setBackgroundColor(background);

        title = new TextView(this);
        title.setText("THE MORNING BRIEF");
        title.setTextColor(ink);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setOnClickListener(v -> showCurrentSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        root.addView(bar);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));

        progressStatus = new TextView(this);
        progressStatus.setTextColor(muted);
        progressStatus.setTextSize(12);
        progressStatus.setPadding(dp(12), dp(5), dp(12), dp(6));
        progressStatus.setVisibility(View.GONE);
        root.addView(progressStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        web.setBackgroundColor(background);
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setLoadsImagesAutomatically(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setBuiltInZoomControls(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                if ("morningbrief.local".equals(uri.getHost())) {
                    if ("/save".equals(uri.getPath())) {
                        long date=0;
                        try {date=Long.parseLong(uri.getQueryParameter("date"));}catch(Exception ignored){}
                        boolean saved=SavedNews.save(MainActivity.this,uri.getQueryParameter("source"),uri.getQueryParameter("title"),
                            uri.getQueryParameter("summary"),uri.getQueryParameter("url"),date,"Local");
                        Toast.makeText(MainActivity.this,saved?"Moved to Features":"Unable to save article",Toast.LENGTH_SHORT).show();
                        if(saved)loadCached();
                        return true;
                    }
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });

        FrameLayout pages = new FrameLayout(this);
        pages.addView(web, new FrameLayout.LayoutParams(-1, -1));

        funnies = new FunniesPuzzles(this, uiDark);
        pages.addView(funnies.view(), new FrameLayout.LayoutParams(-1, -1));
        funnies.view().setVisibility(View.GONE);

        weekend = new WeekendReading(this, uiDark);
        pages.addView(weekend.view(), new FrameLayout.LayoutParams(-1, -1));
        weekend.view().setVisibility(View.GONE);

        national = new NationalNews(this, uiDark);
        pages.addView(national.view(), new FrameLayout.LayoutParams(-1, -1));
        national.view().setVisibility(View.GONE);

        root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(background);

        // Android 15 enforces edge-to-edge for targetSdk 35. Keep our app chrome
        // outside the status/navigation bar areas instead of letting the phone's
        // system controls cover the header or bottom navigation.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            bar.setPadding(dp(12), dp(6) + topInset, dp(6), dp(6));
            tabs.setPadding(0, 0, 0, bottomInset);
            return insets;
        });

        Button morningTab = tabButton("Local");
        Button nationalTab = tabButton("National");
        Button weekendTab = tabButton("Features");
        Button funniesTab = tabButton("Leisure");
        tabs.addView(morningTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        tabs.addView(nationalTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        tabs.addView(weekendTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        tabs.addView(funniesTab, new LinearLayout.LayoutParams(0, dp(58), 1));
        root.addView(tabs);

        morningTab.setOnClickListener(v -> switchPage(PAGE_MORNING));
        nationalTab.setOnClickListener(v -> switchPage(PAGE_NATIONAL));
        funniesTab.setOnClickListener(v -> switchPage(PAGE_FUNNIES));
        weekendTab.setOnClickListener(v -> switchPage(PAGE_WEEKEND));

        setContentView(root);
    }

    private Button tabButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void switchPage(int page) {
        currentPage = page;
        web.setVisibility(page == PAGE_MORNING ? View.VISIBLE : View.GONE);
        national.view().setVisibility(page == PAGE_NATIONAL ? View.VISIBLE : View.GONE);
        funnies.view().setVisibility(page == PAGE_FUNNIES ? View.VISIBLE : View.GONE);
        weekend.view().setVisibility(page == PAGE_WEEKEND ? View.VISIBLE : View.GONE);
        if (page == PAGE_MORNING) {
            title.setText("THE MORNING BRIEF");
            loadCached();
        } else if (page == PAGE_NATIONAL) {
            title.setText("NATIONAL");
            national.render();
        } else if (page == PAGE_FUNNIES) {
            title.setText("LEISURE");
            funnies.render();
        } else {
            title.setText("FEATURES");
            weekend.render();
        }
    }

    private void showCurrentSettings() {
        if (currentPage == PAGE_NATIONAL) {
            national.showSettings();
            return;
        }
        if (currentPage == PAGE_FUNNIES) {
            funnies.showSettings();
            return;
        }
        if (currentPage == PAGE_WEEKEND) {
            weekend.showSettings();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Morning Brief settings")
                .setItems(new String[]{"News, weather & appearance", "Rebuild current edition"}, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, SetupActivity.class).putExtra("edit", true));
                    } else {
                        refresh(true);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web == null || !SettingsStore.isSetup(this)) return;
        boolean nowDark = SettingsStore.useDark(this);
        if (nowDark != uiDark) {
            recreate();
            return;
        }
        if (currentPage == PAGE_MORNING) loadCached();
        else if (currentPage == PAGE_NATIONAL) national.render();
        else if (currentPage == PAGE_FUNNIES) funnies.render();
        else weekend.render();
    }

    private void loadCached() {
        String html = SettingsStore.readCachedEdition(this);
        // Upgrade the previous edition in place, without refetching or losing already shown stories.
        if (!html.contains("class='actions'") && html.contains("class='lead-copy'")) {
            try {
                java.util.List<Story> previous=SettingsStore.loadStoryCache(this);
                if(!previous.isEmpty()){
                    html=EditionBuilder.build(SettingsStore.getLocation(this),SettingsStore.loadWeatherCache(this),previous,
                        !html.contains(" · CACHED"),SettingsStore.lastRefresh(this),SettingsStore.theme(this));
                    SettingsStore.writeCachedEdition(this,html);
                }
            }catch(Exception ignored){}
        }
        cachedSupportsTheme = html.contains("theme-") && html.contains("--page:");
        if (html.trim().isEmpty()) {
            boolean dark = SettingsStore.useDark(this);
            String bg = dark ? "#10110f" : "#fafaf6";
            String fg = dark ? "#f0efe8" : "#111";
            html = "<html><meta name='viewport' content='width=device-width'><body style='font-family:serif;padding:30px;background:" + bg + ";color:" + fg + "'><h1>The Morning Brief</h1><p>Building your first edition…</p></body></html>";
            cachedSupportsTheme = false;
        } else if (cachedSupportsTheme) {
            String desired = SettingsStore.theme(this);
            html = html.replaceFirst("<html class='theme-(?:system|light|dark)'>", "<html class='theme-" + desired + "'>");
        }
        web.loadDataWithBaseURL("https://morningbrief.local/", SavedNews.hideMoved(this,html), "text/html", "UTF-8", null);
    }

    private void refresh(boolean userRequested) {
        if (refreshing || !DailyRefresh.begin(this, DailyRefresh.LOCAL, userRequested, SettingsStore.lastRefresh(this))) return;
        refreshing = true;
        showProgress("Starting refresh…", 1);
        new Thread(() -> {
            try {
                EditionEngine.Result r = EditionEngine.refresh(this, (status, percent) ->
                        runOnUiThread(() -> showProgress(status, percent)));
                runOnUiThread(() -> {
                    cachedSupportsTheme = true;
                    web.loadDataWithBaseURL("https://morningbrief.local/", SavedNews.hideMoved(this,r.html), "text/html", "UTF-8", null);
                    if (userRequested || r.failedSources > 0 || !r.weatherFresh) {
                        String msg = "Updated · " + r.storyCount + " stories";
                        if (r.failedSources > 0) msg += " · " + r.failedSources + " feed(s) unavailable";
                        if (!r.weatherFresh) msg += " · cached weather";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Refresh failed; keeping the last edition. " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                DailyRefresh.end(DailyRefresh.LOCAL);
                refreshing = false;
                runOnUiThread(this::hideProgress);
            }
        }, "manual-refresh").start();
    }

    private void showProgress(String status, int percent) {
        progress.setVisibility(View.VISIBLE);
        progressStatus.setVisibility(View.VISIBLE);
        progress.setProgress(Math.max(0, Math.min(100, percent)));
        progressStatus.setText(status);
    }

    private void hideProgress() {
        progress.setVisibility(View.GONE);
        progressStatus.setVisibility(View.GONE);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
