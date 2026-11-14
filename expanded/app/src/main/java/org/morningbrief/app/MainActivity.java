package org.morningbrief.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView web;
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
        buildUi();
        loadCached();
        Scheduler.ensureScheduled(this);
        if (!cachedSupportsTheme || System.currentTimeMillis() - SettingsStore.lastRefresh(this) > 4L * 60L * 60L * 1000L) {
            refresh(false);
        }
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

        TextView title = new TextView(this);
        title.setText("THE MORNING BRIEF");
        title.setTextColor(ink);
        title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button refresh = new Button(this);
        refresh.setText("Refresh");
        refresh.setOnClickListener(v -> refresh(true));
        bar.addView(refresh, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));

        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class).putExtra("edit", true)));
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
                if ("morningbrief.local".equals(uri.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (web == null || !SettingsStore.isSetup(this)) return;
        boolean nowDark = SettingsStore.useDark(this);
        if (nowDark != uiDark) {
            recreate();
            return;
        }
        loadCached();
    }

    private void loadCached() {
        String html = SettingsStore.readCachedEdition(this);
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
        web.loadDataWithBaseURL("https://morningbrief.local/", html, "text/html", "UTF-8", null);
    }

    private void refresh(boolean userRequested) {
        if (refreshing) return;
        refreshing = true;
        showProgress("Starting refresh…", 1);
        new Thread(() -> {
            try {
                EditionEngine.Result r = EditionEngine.refresh(this, (status, percent) ->
                        runOnUiThread(() -> showProgress(status, percent)));
                runOnUiThread(() -> {
                    cachedSupportsTheme = true;
                    web.loadDataWithBaseURL("https://morningbrief.local/", r.html, "text/html", "UTF-8", null);
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
