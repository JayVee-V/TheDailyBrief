package org.morningbrief.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends Activity {
    private EditText zip;
    private TextView area;
    private TextView status;
    private LinearLayout sourceBox;
    private ProgressBar progress;
    private CheckBox sports, obits, opinion, recipes;
    private Spinner appearance;
    private LocationInfo resolvedLocation;
    private final List<CheckBox> sourceChecks = new ArrayList<>();
    private boolean darkUi;
    private int inkColor;
    private int mutedColor;
    private int pageColor;
    private int panelColor;
    private int borderColor;
    private int accentColor;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        darkUi = SettingsStore.useDark(this);
        inkColor = darkUi ? Color.rgb(244,243,236) : Color.rgb(17,17,17);
        mutedColor = darkUi ? Color.rgb(190,190,181) : Color.rgb(82,82,76);
        pageColor = darkUi ? Color.rgb(14,15,13) : Color.rgb(246,246,241);
        panelColor = darkUi ? Color.rgb(29,31,27) : Color.WHITE;
        borderColor = darkUi ? Color.rgb(64,66,59) : Color.rgb(218,218,210);
        accentColor = darkUi ? Color.rgb(216,199,124) : Color.rgb(36,36,32);

        getWindow().setStatusBarColor(pageColor);
        getWindow().setNavigationBarColor(pageColor);
        getWindow().getDecorView().setSystemUiVisibility(darkUi ? 0 : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        buildUi();
        if (SettingsStore.isSetup(this)) loadExisting();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(pageColor);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(30));
        root.setBackgroundColor(pageColor);
        scroll.addView(root);

        TextView head = text("THE MORNING BRIEF", 28, true);
        root.addView(head);
        TextView sub = text("Local news, assembled on your phone.", 17, false);
        sub.setTextColor(mutedColor);
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        LinearLayout areaPanel = sectionPanel();
        areaPanel.addView(label("YOUR AREA"));
        zip = new EditText(this);
        zip.setHint("ZIP code");
        zip.setInputType(InputType.TYPE_CLASS_NUMBER);
        styleInput(zip);
        areaPanel.addView(zip, match());
        Button discover = button("Find my area and local sources", false);
        discover.setOnClickListener(v -> discover());
        areaPanel.addView(discover, match());
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42));
        pp.topMargin = dp(6);
        areaPanel.addView(progress, pp);
        area = text("", 16, true);
        area.setPadding(0, dp(8), 0, dp(5));
        areaPanel.addView(area);
        status = text("Enter a ZIP code. The app uses it for weather and source discovery; it stays stored on this device.", 14, false);
        status.setTextColor(mutedColor);
        areaPanel.addView(status);
        addPanel(root, areaPanel);

        LinearLayout newsPanel = sectionPanel();
        newsPanel.addView(label("LOCAL NEWS SOURCES"));
        TextView newsHelp = text("Preferred nonprofit and community outlets are favored in the finished edition. Commercial and Google sources fill gaps.", 14, false);
        newsHelp.setTextColor(mutedColor);
        newsHelp.setPadding(0, dp(4), 0, dp(8));
        newsPanel.addView(newsHelp);
        sourceBox = new LinearLayout(this);
        sourceBox.setOrientation(LinearLayout.VERTICAL);
        newsPanel.addView(sourceBox, match());
        Button manual = button("Add an RSS feed manually", false);
        manual.setOnClickListener(v -> addManual());
        LinearLayout.LayoutParams mp = match();
        mp.topMargin = dp(8);
        newsPanel.addView(manual, mp);
        addPanel(root, newsPanel);

        LinearLayout appearancePanel = sectionPanel();
        appearancePanel.addView(label("APPEARANCE"));
        TextView appearanceHelp = text("Choose how the newspaper and app controls are displayed.", 14, false);
        appearanceHelp.setTextColor(mutedColor);
        appearanceHelp.setPadding(0, dp(4), 0, dp(8));
        appearancePanel.addView(appearanceHelp);
        appearance = new Spinner(this);
        ArrayAdapter<String> appearanceAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Use system setting", "Light", "Dark"}) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(v, false);
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                styleSpinnerText(v, true);
                return v;
            }
        };
        appearanceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appearance.setAdapter(appearanceAdapter);
        appearance.setSelection(SettingsStore.themeIndex(this));
        appearance.setBackground(panelDrawable(darkUi ? Color.rgb(42,44,39) : Color.rgb(247,247,243), borderColor, 8));
        appearance.setPopupBackgroundDrawable(panelDrawable(panelColor, borderColor, 8));
        appearancePanel.addView(appearance, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        addPanel(root, appearancePanel);

        LinearLayout filterPanel = sectionPanel();
        filterPanel.addView(label("OPTIONAL CONTENT"));
        TextView filterHelp = text("These are excluded by default so the brief stays focused.", 14, false);
        filterHelp.setTextColor(mutedColor);
        filterHelp.setPadding(0, dp(4), 0, dp(6));
        filterPanel.addView(filterHelp);
        sports = checkbox("Include sports");
        obits = checkbox("Include obituaries");
        opinion = checkbox("Include opinion / letters");
        recipes = checkbox("Include recipes / food columns");
        filterPanel.addView(sports); filterPanel.addView(obits); filterPanel.addView(opinion); filterPanel.addView(recipes);
        addPanel(root, filterPanel);

        Button save = button("SAVE & BUILD MY MORNING BRIEF", true);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams sp = match();
        sp.topMargin = dp(18);
        sp.height = dp(54);
        root.addView(save, sp);

        setContentView(scroll);
    }

    private void loadExisting() {
        resolvedLocation = SettingsStore.getLocation(this);
        zip.setText(resolvedLocation.zip);
        area.setText(resolvedLocation.label());
        List<SourceConfig> sources = FeedDiscovery.mergePreferredSources(resolvedLocation, SettingsStore.getSources(this));
        SettingsStore.saveSources(this, sources);
        showSources(sources);
        sports.setChecked(SettingsStore.includeSports(this));
        obits.setChecked(SettingsStore.includeObits(this));
        opinion.setChecked(SettingsStore.includeOpinion(this));
        recipes.setChecked(SettingsStore.includeRecipes(this));
        appearance.setSelection(SettingsStore.themeIndex(this));
        status.setText("Preferred nonprofit/community sources are favored first. Edit sources or run discovery again for a new ZIP code.");
    }

    private void discover() {
        String z = zip.getText().toString().trim();
        if (z.length() < 5) {
            Toast.makeText(this, "Enter a ZIP code first.", Toast.LENGTH_SHORT).show();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        status.setText("Finding nonprofit/community outlets first, then probing other local publishers…");
        sourceBox.removeAllViews();
        sourceChecks.clear();
        new Thread(() -> {
            try {
                LocationInfo loc = GeoService.lookupZip(z);
                List<SourceConfig> found = FeedDiscovery.discover(loc);
                runOnUiThread(() -> {
                    resolvedLocation = loc;
                    area.setText(loc.label() + (loc.county.trim().isEmpty() ? "" : " · " + loc.county));
                    showSources(found);
                    status.setText(found.size() + " source(s) found. Nonprofit/direct sources get a strong editorial boost; Google News fills coverage holes.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Discovery failed: " + e.getMessage()));
            } finally {
                runOnUiThread(() -> progress.setVisibility(View.GONE));
            }
        }, "source-discovery").start();
    }

    private void showSources(List<SourceConfig> sources) {
        sourceBox.removeAllViews();
        sourceChecks.clear();
        if (sources.isEmpty()) {
            TextView none = text("No sources yet. Run discovery or add an RSS feed manually.", 14, false);
            none.setTextColor(mutedColor);
            sourceBox.addView(none);
            return;
        }
        for (SourceConfig s : sources) addSourceCheck(s, s.enabled);
    }

    private void addSourceCheck(SourceConfig s, boolean checked) {
        CheckBox cb = checkbox("");
        String detail;
        if ("preferred-direct".equals(s.mode)) detail = "Preferred nonprofit · direct publisher feed";
        else if ("preferred-google".equals(s.mode)) detail = "Preferred nonprofit · Google News fallback";
        else if ("inn-direct".equals(s.mode)) detail = "Nonprofit (INN) · direct publisher feed";
        else if ("inn-google".equals(s.mode)) detail = "Nonprofit (INN) · Google News fallback";
        else if (s.isDirect()) detail = "Direct publisher feed";
        else detail = "Google News fallback";
        cb.setText(s.cleanName() + "\n" + detail);
        cb.setTextSize(16);
        cb.setPadding(0, dp(5), 0, dp(5));
        cb.setChecked(checked);
        cb.setTag(s);
        sourceChecks.add(cb);
        sourceBox.addView(cb, match());
    }

    private void addManual() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(4), dp(20), 0);
        EditText name = new EditText(this); name.setHint("Source name"); styleDialogInput(name); box.addView(name);
        EditText feed = new EditText(this); feed.setHint("https://example.com/feed/"); feed.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); styleDialogInput(feed); box.addView(feed);
        new AlertDialog.Builder(this)
                .setTitle("Add RSS / Atom feed")
                .setView(box)
                .setPositiveButton("Add", (d, which) -> {
                    String n = name.getText().toString().trim();
                    String f = feed.getText().toString().trim();
                    if (n.isEmpty() || f.isEmpty()) return;
                    SourceConfig s = new SourceConfig(n, f, homepageFrom(f), "direct");
                    if (sourceChecks.isEmpty()) sourceBox.removeAllViews();
                    addSourceCheck(s, true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void save() {
        String currentZip = zip.getText().toString().trim();
        if (resolvedLocation == null || !currentZip.equals(resolvedLocation.zip)) {
            Toast.makeText(this, "Run source discovery for this ZIP code first.", Toast.LENGTH_LONG).show();
            return;
        }
        List<SourceConfig> allSources = new ArrayList<>();
        int enabledCount = 0;
        for (CheckBox cb : sourceChecks) {
            SourceConfig s = (SourceConfig) cb.getTag();
            s.enabled = cb.isChecked();
            if (s.enabled) enabledCount++;
            allSources.add(s);
        }
        if (enabledCount == 0) {
            Toast.makeText(this, "Select at least one news source.", Toast.LENGTH_LONG).show();
            return;
        }
        SettingsStore.saveLocation(this, resolvedLocation);
        SettingsStore.saveSources(this, allSources);
        SettingsStore.saveFilters(this, sports.isChecked(), obits.isChecked(), opinion.isChecked(), recipes.isChecked());
        String theme = appearance.getSelectedItemPosition() == 2 ? "dark" : (appearance.getSelectedItemPosition() == 1 ? "light" : "system");
        SettingsStore.saveTheme(this, theme);
        SettingsStore.setSetup(this, true);
        SettingsStore.setLastRefresh(this, 0L);
        Scheduler.scheduleNext(this);

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    private String homepageFrom(String feed) {
        try {
            java.net.URL u = new java.net.URL(feed);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) { return feed; }
    }

    private LinearLayout sectionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(panelDrawable(panelColor, borderColor, 12));
        return panel;
    }

    private void addPanel(LinearLayout root, LinearLayout panel) {
        LinearLayout.LayoutParams p = match();
        p.topMargin = dp(12);
        root.addView(panel, p);
    }

    private GradientDrawable panelDrawable(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(primary ? 15 : 14);
        if (darkUi) {
            int fill = primary ? accentColor : Color.rgb(44,46,41);
            int text = primary ? Color.rgb(18,18,16) : inkColor;
            b.setTextColor(text);
            b.setBackground(panelDrawable(fill, primary ? accentColor : borderColor, 8));
        } else if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackground(panelDrawable(accentColor, accentColor, 8));
        }
        return b;
    }

    private TextView label(String s) {
        TextView v = text(s, 13, true);
        v.setLetterSpacing(.08f);
        v.setTextColor(darkUi ? Color.rgb(220,207,153) : mutedColor);
        return v;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(inkColor);
        v.setTypeface(android.graphics.Typeface.SERIF, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return v;
    }

    private CheckBox checkbox(String s) {
        CheckBox c = new CheckBox(this);
        c.setText(s); c.setTextSize(16); c.setTextColor(inkColor);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
            int[] colors = new int[] { accentColor, darkUi ? Color.rgb(170,172,164) : Color.rgb(90,90,86) };
            c.setButtonTintList(new ColorStateList(states, colors));
        }
        return c;
    }

    private void styleInput(EditText e) {
        e.setTextColor(inkColor);
        e.setHintTextColor(mutedColor);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            e.setBackgroundTintList(new ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_focused }, new int[] {} },
                    new int[] { accentColor, borderColor }));
        }
    }

    private void styleDialogInput(EditText e) {
        e.setTextColor(Color.rgb(20,20,20));
        e.setHintTextColor(Color.rgb(105,105,105));
    }

    private void styleSpinnerText(TextView v, boolean dropdown) {
        v.setTextColor(inkColor);
        v.setTextSize(16);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (dropdown) v.setBackgroundColor(panelColor);
    }

    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
