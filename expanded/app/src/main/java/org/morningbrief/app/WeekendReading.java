package org.morningbrief.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Independently persisted RSS queue. Opening a link NEVER marks it read. */
final class WeekendReading {
    private static final String FEEDS = "weekend_feeds_v1";
    private static final String QUEUE = "weekend_queue_v1";
    private static final String DISMISSED = "weekend_dismissed_v1";
    private static final String[] SUGGESTED_NAMES = {
            "The Conversation", "Atlas Obscura", "Ars Technica — Features", "RetroRGB"
    };
    private static final String[] SUGGESTED_URLS = {
            "https://theconversation.com/us/articles.atom",
            "https://www.atlasobscura.com/feeds/latest",
            "https://feeds.arstechnica.com/arstechnica/features",
            "https://retrorgb.com/category/retrorgb/feed"
    };
    private final MainActivity activity;
    private final LinearLayout screen;
    private final int ink;
    private final int muted;
    private final int background;
    private boolean busy = false;

    WeekendReading(MainActivity activity, boolean dark) {
        this.activity = activity;
        ink = dark ? Color.rgb(240,239,232) : Color.rgb(17,17,17);
        muted = dark ? Color.rgb(184,184,176) : Color.rgb(88,88,82);
        background = dark ? Color.rgb(16,17,15) : Color.rgb(250,250,246);
        screen = new LinearLayout(activity);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(background);
        render();
    }

    View view() { return screen; }

    private static final class Feed {
        String name, url;
        boolean enabled;
        Feed(String n, String u, boolean e) { name=n; url=u; enabled=e; }
    }
    private static final class Card {
        String source, title, summary, url;
        long date;
        Card(String s, String t, String d, String u, long p) {
            source=s; title=t; summary=d; url=u; date=p;
        }
    }
    private String stored(String key, String fallback) {
        return SettingsStore.prefs(activity).getString(key, fallback);
    }
    private void put(String key, String value) {
        SettingsStore.prefs(activity).edit().putString(key, value).apply();
    }
    private List<Feed> feeds() {
        List<Feed> list = new ArrayList<>();
        String raw=stored(FEEDS, "");
        if (raw.isEmpty()) {
            for (int i=0;i<SUGGESTED_NAMES.length;i++)
                list.add(new Feed(SUGGESTED_NAMES[i], SUGGESTED_URLS[i], i < 2));
            return list;
        }
        try {
            JSONArray a=new JSONArray(raw);
            for (int i=0;i<a.length();i++) {
                JSONObject o=a.optJSONObject(i); if(o==null)continue;
                String u=o.optString("url","");
                if(!u.isEmpty()) list.add(new Feed(o.optString("name","Feed"),u,o.optBoolean("enabled",false)));
            }
        } catch(Exception ignored) {}
        return list;
    }
    private void saveFeeds(List<Feed> list) {
        JSONArray a=new JSONArray();
        try {
            for(Feed f:list) {
                JSONObject o=new JSONObject();
                o.put("name",f.name);o.put("url",f.url);o.put("enabled",f.enabled);a.put(o);
            }
        }catch(Exception ignored){}
        put(FEEDS,a.toString());
    }
    private List<Card> cards() {
        List<Card> result=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(stored(QUEUE,"[]"));
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.optJSONObject(i);if(o==null)continue;
                result.add(new Card(o.optString("source"),o.optString("title"),
                        o.optString("summary"),o.optString("url"),o.optLong("date")));
            }
        }catch(Exception ignored){}
        return result;
    }
    private void saveCards(List<Card> list) {
        JSONArray a=new JSONArray();
        try {
            for(Card c:list) {
                JSONObject o=new JSONObject();
                o.put("source",c.source);o.put("title",c.title);
                o.put("summary",c.summary);o.put("url",c.url);o.put("date",c.date);a.put(o);
            }
        }catch(Exception ignored){}
        put(QUEUE,a.toString());
    }
    private Set<String> dismissed() {
        Set<String> ids=new HashSet<>();
        try {
            JSONArray a=new JSONArray(stored(DISMISSED,"[]"));
            for(int i=0;i<a.length();i++)ids.add(a.optString(i));
        }catch(Exception ignored){}
        return ids;
    }
    private void saveDismissed(Set<String> ids) {
        JSONArray a=new JSONArray();
        for(String id:ids)a.put(id);
        put(DISMISSED,a.toString());
    }
    private static String id(String url) {
        // Ignore common tracking parameters while retaining identity across refreshes.
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost()==null?"":u.getHost().toLowerCase();
            String path=u.getPath()==null?"":u.getPath();
            return host+path.replaceAll("/+$","");
        }catch(Exception e){return url;}
    }
    private int dp(int v) { return Math.round(v*activity.getResources().getDisplayMetrics().density); }
    private TextView text(String value,int sp,boolean bold,int color) {
        TextView t=new TextView(activity);
        t.setText(value); t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(android.graphics.Typeface.SERIF,android.graphics.Typeface.BOLD);
        return t;
    }
    void render() {
        screen.removeAllViews();
        LinearLayout actions=new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(12),dp(7),dp(12),dp(7));
        Button manage=new Button(activity);manage.setText("Feeds");
        manage.setOnClickListener(v->manage());
        actions.addView(manage,new LinearLayout.LayoutParams(0,dp(48),1));
        Button reload=new Button(activity);reload.setText(busy?"Loading…":"Collect");
        reload.setEnabled(!busy);reload.setOnClickListener(v->collect());
        actions.addView(reload,new LinearLayout.LayoutParams(0,dp(48),1));
        screen.addView(actions);
        ScrollView sc=new ScrollView(activity);
        LinearLayout column=new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(8),dp(16),dp(24));
        TextView header=text("WEEKEND READING",22,true,ink);
        column.addView(header);
        List<Card> list=cards();
        TextView help=text(list.size()+" saved articles · Open a story without removing it. Dismiss when finished or uninterested.",13,false,muted);
        help.setPadding(0,dp(8),0,dp(16));column.addView(help);
        if(list.isEmpty()) {
            TextView empty=text("Your reading shelf is empty. Choose feeds, then tap Collect to add stories.",16,false,muted);
            empty.setPadding(0,dp(22),0,dp(20));column.addView(empty);
        }
        for(Card c:list) {
            LinearLayout card=new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(14),dp(12),dp(14));
            TextView source=text(c.source.toUpperCase(),11,false,muted);card.addView(source);
            TextView headline=text(c.title,19,true,ink);
            headline.setPadding(0,dp(6),0,dp(6));card.addView(headline);
            String brief=c.summary==null?"":c.summary;
            if(brief.length()>240)brief=brief.substring(0,237)+"…";
            TextView summary=text(brief,14,false,muted);card.addView(summary);
            LinearLayout buttons=new LinearLayout(activity);
            Button open=new Button(activity);open.setText("Read article");
            open.setOnClickListener(v->{
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(c.url))); }
                catch(Exception e){Toast.makeText(activity,"Unable to open article",Toast.LENGTH_SHORT).show();}
            });
            buttons.addView(open,new LinearLayout.LayoutParams(0,dp(46),1));
            Button remove=new Button(activity);remove.setText("Dismiss");
            remove.setOnClickListener(v->dismiss(c));
            buttons.addView(remove,new LinearLayout.LayoutParams(0,dp(46),1));
            card.addView(buttons);
            column.addView(card);
            View line=new View(activity);line.setBackgroundColor(muted);
            column.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        sc.addView(column);
        screen.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void dismiss(Card card) {
        List<Card> list=cards();
        list.removeIf(c->id(c.url).equals(id(card.url)));
        saveCards(list);
        Set<String> ids=dismissed();ids.add(id(card.url));saveDismissed(ids);
        render();
        Toast.makeText(activity,"Removed from Weekend Reading",Toast.LENGTH_SHORT).show();
    }
    private void manage() {
        List<Feed> list=feeds();
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),0);
        ScrollView scroll=new ScrollView(activity);scroll.addView(box);
        for(Feed f:list) {
            LinearLayout line=new LinearLayout(activity);line.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox toggle=new CheckBox(activity);toggle.setText(f.name);toggle.setTextColor(ink);
            toggle.setChecked(f.enabled);toggle.setOnCheckedChangeListener((button,v)->{f.enabled=v;saveFeeds(list);});
            line.addView(toggle,new LinearLayout.LayoutParams(0,dp(52),1));
            if(!isSuggested(f.url)) {
                Button delete=new Button(activity);delete.setText("Remove");
                delete.setOnClickListener(v->{list.remove(f);saveFeeds(list);manage();});
                line.addView(delete);
            }
            box.addView(line);
        }
        new AlertDialog.Builder(activity).setTitle("Weekend Reading feeds")
                .setView(scroll).setPositiveButton("Done",(d,w)->render())
                .setNeutralButton("Add RSS feed",(d,w)->addFeed())
                .show();
    }
    private static boolean isSuggested(String u) {
        for(String s:SUGGESTED_URLS)if(s.equals(u))return true;
        return false;
    }
    private void addFeed() {
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        EditText name=new EditText(activity);name.setHint("Publication name");box.addView(name);
        EditText url=new EditText(activity);url.setHint("https://example.com/feed.xml");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);box.addView(url);
        new AlertDialog.Builder(activity).setTitle("Add RSS / Atom feed").setView(box)
                .setNegativeButton("Cancel",null).setPositiveButton("Validate and add",(d,w)->{
                    String n=name.getText().toString().trim(),u=url.getText().toString().trim();
                    Uri parsed=Uri.parse(u);
                    if(n.isEmpty()||!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null){
                        Toast.makeText(activity,"Enter a name and HTTPS feed URL",Toast.LENGTH_LONG).show();return;
                    }
                    new Thread(()->{
                        try {
                            SourceConfig src=new SourceConfig(n,u,u,"direct");
                            FeedReader.read(src); // A valid empty feed is allowed.
                            activity.runOnUiThread(()->{
                                List<Feed> fs=feeds();
                                for(Feed f:fs)if(f.url.equals(u)){
                                    Toast.makeText(activity,"Feed already added",Toast.LENGTH_SHORT).show();return;
                                }
                                fs.add(new Feed(n,u,true));saveFeeds(fs);render();
                                Toast.makeText(activity,"Feed added",Toast.LENGTH_SHORT).show();
                            });
                        }catch(Exception e){
                            activity.runOnUiThread(()->Toast.makeText(activity,
                                    "Could not read RSS/Atom feed: "+e.getMessage(),Toast.LENGTH_LONG).show());
                        }
                    },"validate-weekend-feed").start();
                }).show();
    }
    void collect() {
        if(busy)return;
        busy=true;render();
        new Thread(()->{
            int failures=0,added=0;
            List<Card> list=cards();
            Set<String> seen=dismissed();
            for(Card c:list)seen.add(id(c.url));
            for(Feed f:feeds()) {
                if(!f.enabled)continue;
                try {
                    SourceConfig source=new SourceConfig(f.name,f.url,f.url,"direct");
                    for(Story st:FeedReader.read(source)) {
                        if(st.url==null||!st.url.startsWith("http"))continue;
                        String key=id(st.url);if(seen.contains(key))continue;
                        seen.add(key);
                        list.add(new Card(st.source,st.title,st.summary,st.url,st.publishedEpoch));
                        added++;
                    }
                }catch(Exception ignored){failures++;}
            }
            Collections.sort(list,(a,b)->Long.compare(b.date,a.date));
            // Limit visible queue; never discard user-selected stories just because a feed is busy.
            saveCards(list);
            int count=added,errors=failures;
            activity.runOnUiThread(()->{
                busy=false;render();
                Toast.makeText(activity,"Collected "+count+" articles"+(errors>0?" · "+errors+" feed(s) failed":""),
                        Toast.LENGTH_LONG).show();
            });
        },"collect-weekend").start();
    }
}
