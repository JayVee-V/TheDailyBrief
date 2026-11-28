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
    private TextView title, settingsButton;
    private TextView[] tabButtons;
    private ReaderStyle readerStyle;
    private FrameLayout pages;
    private View settingsPage;
    private Runnable settingsClosed;
    private boolean returningFromLocalSettings, pendingLocalUpdate;
    private static final String[] SECTION_NAMES={"LOCAL","NATIONAL","FEATURES","LEISURE"};
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
        boolean force=getIntent().getBooleanExtra("update_after_theme",false);
        getIntent().removeExtra("update_after_theme");
        refresh(force);
    }

    private void buildUi() {
        uiDark = SettingsStore.useDark(this);
        readerStyle=new ReaderStyle(this,uiDark);
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

        LinearLayout masthead = new LinearLayout(this);
        masthead.setOrientation(LinearLayout.VERTICAL);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = new TextView(this);
        brand.setText("NewPaper");
        brand.setTextColor(ink);
        brand.setTextSize(30);
        brand.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        brand.setIncludeFontPadding(false);
        masthead.addView(brand);
        title = new TextView(this);
        title.setText("LOCAL");
        title.setTextColor(muted);
        title.setTextSize(12);
        title.setLetterSpacing(.12f);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(3), 0, 0);
        masthead.addView(title);
        bar.addView(masthead, new LinearLayout.LayoutParams(0, -2, 1));

        settingsButton=readerStyle.action("Settings",()->{
            if(settingsPage!=null)closeSettings();else showCurrentSettings();
        });
        bar.addView(settingsButton,new LinearLayout.LayoutParams(-2,dp(44)));
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

        pages = new FrameLayout(this);
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
        tabs.setPadding(dp(8),dp(6),dp(8),dp(6));

        // Android 15 enforces edge-to-edge for targetSdk 35. Keep our app chrome
        // outside the status/navigation bar areas instead of letting the phone's
        // system controls cover the header or bottom navigation.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            bar.setPadding(dp(12), dp(6) + topInset, dp(6), dp(6));
            tabs.setPadding(dp(8),dp(6),dp(8),dp(6)+bottomInset);
            return insets;
        });

        String[] labels={"Local","National","Features","Leisure"};
        tabButtons=new TextView[labels.length];
        for(int i=0;i<labels.length;i++){
            final int page=i;
            TextView tab=readerStyle.action(labels[i],()->switchPage(page));
            tab.setTextSize(12);tab.setPadding(dp(2),0,dp(2),0);
            tab.setSingleLine(true);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
            if(i>0)p.leftMargin=dp(5);
            tabs.addView(tab,p);tabButtons[i]=tab;
        }
        root.addView(tabs);
        paintTabs();

        setContentView(root);
    }

    private void paintTabs(){
        if(tabButtons==null)return;
        for(int i=0;i<tabButtons.length;i++){
            boolean active=i==currentPage;
            tabButtons[i].setSelected(active);
            tabButtons[i].setBackground(readerStyle.surface(active?readerStyle.ink:readerStyle.paper,8,true));
            tabButtons[i].setTextColor(active?readerStyle.paper:readerStyle.ink);
        }
    }

    private void switchPage(int page) {
        if(settingsPage!=null)closeSettings();
        if(page==currentPage)return;
        currentPage = page;
        paintTabs();
        title.setText(SECTION_NAMES[page]);
        web.setVisibility(page == PAGE_MORNING ? View.VISIBLE : View.GONE);
        national.view().setVisibility(page == PAGE_NATIONAL ? View.VISIBLE : View.GONE);
        funnies.view().setVisibility(page == PAGE_FUNNIES ? View.VISIBLE : View.GONE);
        weekend.view().setVisibility(page == PAGE_WEEKEND ? View.VISIBLE : View.GONE);
        if (page == PAGE_MORNING) { loadCached();refresh(false); }
        else if (page == PAGE_NATIONAL) national.render();
        else if (page == PAGE_FUNNIES) funnies.render();
        else weekend.render();
    }

    void openSettings(SettingsPage page,Runnable onClose){
        if(settingsPage!=null)pages.removeView(settingsPage);
        settingsPage=page.view;settingsClosed=onClose;
        pages.addView(settingsPage,new FrameLayout.LayoutParams(-1,-1));
        settingsPage.setClickable(true);
        title.setText(SECTION_NAMES[currentPage]+" SETTINGS");
        settingsButton.setText("Done");
    }
    boolean isSettingsOpen(){return settingsPage!=null;}
    void closeSettings(){
        if(settingsPage==null)return;
        pages.removeView(settingsPage);settingsPage=null;
        Runnable done=settingsClosed;settingsClosed=null;
        title.setText(SECTION_NAMES[currentPage]);settingsButton.setText("Settings");
        if(done!=null)done.run();
    }
    @Override public void onBackPressed(){
        if(settingsPage!=null)closeSettings();else super.onBackPressed();
    }
    private void showCurrentSettings() {
        if(currentPage==PAGE_NATIONAL)national.showSettings();
        else if(currentPage==PAGE_FUNNIES)funnies.showSettings();
        else if(currentPage==PAGE_WEEKEND)weekend.showSettings();
        else startActivityForResult(new Intent(this,SetupActivity.class).putExtra("edit",true),101);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==101){
            returningFromLocalSettings=true;
            pendingLocalUpdate=result==RESULT_OK&&data!=null&&data.getBooleanExtra("update",false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (web == null || !SettingsStore.isSetup(this)) return;
        boolean nowDark = SettingsStore.useDark(this);
        if (nowDark != uiDark) {
            if(pendingLocalUpdate)getIntent().putExtra("update_after_theme",true);
            recreate();
            return;
        }
        if(returningFromLocalSettings){
            returningFromLocalSettings=false;
            if(pendingLocalUpdate){pendingLocalUpdate=false;refresh(true);}
            return;
        }
        if(settingsPage!=null)return;
        if (currentPage == PAGE_MORNING) { loadCached(); refresh(false); }
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
        // Rebrand cached editions without contacting sources or resetting the daily cycle.
        html = html.replace("<h1>THE MORNING BRIEF</h1>", "").replace("<h1>The Morning Brief</h1>", "");
        cachedSupportsTheme = html.contains("theme-") && html.contains("--page:");
        if (html.trim().isEmpty()) {
            boolean dark = SettingsStore.useDark(this);
            String bg = dark ? "#10110f" : "#fafaf6";
            String fg = dark ? "#f0efe8" : "#111";
            html = "<html><meta name='viewport' content='width=device-width'><body style='font-family:serif;padding:30px;background:" + bg + ";color:" + fg + "'><h2>Local edition</h2><p>Building your first edition…</p></body></html>";
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
