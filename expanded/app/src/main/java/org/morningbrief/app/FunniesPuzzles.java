package org.morningbrief.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class FunniesPuzzles {
    private static final String SHOW_MEMES = "funnies_show_memes";
    private static final String SHOW_CROSSWORD = "funnies_show_crossword";
    private static final String SHOW_LOGIC = "funnies_show_logic";

    private static final String MEMES_URL = "https://imgur.com/user/deafkitten/posts";
    private static final String CROSSWORD_URL = "https://www.playminicrossword.com/";
    private static final String LOGIC_URL = "https://dailylogicpuzzles.com/";

    private final MainActivity activity;
    private final LinearLayout screen;
    private final int ink;
    private final int muted;
    private final int background;
    private final int panel;

    FunniesPuzzles(MainActivity activity, boolean dark) {
        this.activity = activity;
        ink = dark ? Color.rgb(240,239,232) : Color.rgb(17,17,17);
        muted = dark ? Color.rgb(184,184,176) : Color.rgb(88,88,82);
        background = dark ? Color.rgb(16,17,15) : Color.rgb(250,250,246);
        panel = dark ? Color.rgb(37,39,37) : Color.rgb(239,239,233);
        screen = new LinearLayout(activity);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(background);
        render();
    }

    View view() { return screen; }

    void render() {
        screen.removeAllViews();

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(18), dp(16), dp(30));

        TextView header = text("The back page", 27, true, ink);
        column.addView(header);
        TextView intro = text("A few things worth opening when the news can wait.", 14, false, muted);
        intro.setPadding(0, dp(5), 0, dp(20));
        column.addView(intro);

        boolean any = false;
        if (enabled(SHOW_MEMES)) {
            any = true;
            column.addView(card(
                    "Today's meme collection",
                    "deafkitten on Imgur",
                    "Open the newest daily collection",
                    MEMES_URL));
        }
        if (enabled(SHOW_CROSSWORD)) {
            any = true;
            column.addView(card(
                    "Mini Crossword",
                    "A quick daily 5×5 crossword",
                    "Play today's crossword",
                    CROSSWORD_URL));
        }
        if (enabled(SHOW_LOGIC)) {
            any = true;
            column.addView(card(
                    "Daily Logic Puzzles",
                    "Sudoku, nonograms, Minesweeper and more",
                    "Choose a puzzle",
                    LOGIC_URL));
        }

        if (!any) {
            TextView empty = text("Everything is hidden right now. Use Settings above to bring something back.", 16, false, muted);
            empty.setPadding(dp(16), dp(22), dp(16), dp(22));
            empty.setBackground(panelDrawable());
            column.addView(empty);
        }

        scroll.addView(column);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    }

    void showSettings() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));
        box.setBackgroundColor(background);

        TextView heading = text("Funnies & Puzzles", 22, true, ink);
        box.addView(heading);
        TextView help = text("Choose which back-page links appear.", 14, false, muted);
        help.setPadding(0, dp(5), 0, dp(12));
        box.addView(help);

        CheckBox memes = check("Daily meme collection", SHOW_MEMES);
        CheckBox crossword = check("Mini Crossword", SHOW_CROSSWORD);
        CheckBox logic = check("Daily Logic Puzzles", SHOW_LOGIC);
        box.addView(memes);
        box.addView(crossword);
        box.addView(logic);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setPositiveButton("Done", (d, which) -> {
                    SettingsStore.prefs(activity).edit()
                            .putBoolean(SHOW_MEMES, memes.isChecked())
                            .putBoolean(SHOW_CROSSWORD, crossword.isChecked())
                            .putBoolean(SHOW_LOGIC, logic.isChecked())
                            .apply();
                    render();
                })
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(background));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ink);
        });
        dialog.show();
    }

    private CheckBox check(String label, String key) {
        CheckBox c = new CheckBox(activity);
        c.setText(label);
        c.setTextColor(ink);
        c.setTextSize(16);
        c.setPadding(0, dp(4), 0, dp(4));
        c.setChecked(enabled(key));
        c.setButtonTintList(new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ink, muted}));
        return c;
    }

    private boolean enabled(String key) {
        return SettingsStore.prefs(activity).getBoolean(key, true);
    }

    private View card(String title, String eyebrow, String detail, String url) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(panelDrawable());

        TextView source = text(eyebrow.toUpperCase(java.util.Locale.US), 11, true, muted);
        source.setLetterSpacing(0.08f);
        card.addView(source);

        TextView headline = text(title, 21, true, ink);
        headline.setPadding(0, dp(6), 0, dp(7));
        card.addView(headline);

        TextView description = text(detail, 14, false, muted);
        description.setPadding(0, 0, 0, dp(12));
        card.addView(description);

        TextView open = text("OPEN  ›", 12, true, ink);
        open.setLetterSpacing(0.06f);
        card.addView(open);

        View.OnClickListener launch = v -> open(url);
        card.setOnClickListener(launch);
        headline.setOnClickListener(launch);
        open.setOnClickListener(launch);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    private void open(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView t = new TextView(activity);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);
        return t;
    }

    private GradientDrawable panelDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(panel);
        d.setCornerRadius(dp(14));
        return d;
    }

    private int dp(int n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }
}
