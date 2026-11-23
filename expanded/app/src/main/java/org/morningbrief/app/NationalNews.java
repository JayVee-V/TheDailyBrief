package org.morningbrief.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

/** Separate US-focused edition, with its own source choices and cache. */
final class NationalNews {
    private static final String PREF = "national_edition_v1";
    private static final String[] NAMES = {"Associated Press", "NPR", "PBS NewsHour", "The Hill", "ProPublica"};
    private static final String[] URLS = {
        "https://news.google.com/rss/search?q=site%3Aapnews.com%2Fus-news+when%3A3d&hl=en-US&gl=US&ceid=US%3Aen",
        "https://feeds.npr.org/1003/rss.xml",
        "https://www.pbs.org/newshour/feeds/rss/headlines",
        "https://thehill.com/feed/",
        "https://www.propublica.org/feeds/propublica/main"
    };
    private static final String[] NOTES = {
        "US news from AP via Google News",
        "Public radio reporting and national affairs",
        "Public-service journalism and national reporting",
        "Washington, Congress and federal policy",
        "Investigative reporting and accountability"
    };
    private final Context context;
    private final LinearLayout body;
    private final boolean dark;
    private final int ink, muted;
    private volatile boolean loading;
    private static final String[] DOMAINS = {"apnews.com/us-news", "npr.org", "pbs.org/newshour", "thehill.com", "propublica.org"};
    private volatile boolean attempted;
    NationalNews(Context context, boolean dark) {
        this.context = context; this.dark = dark;
        ink = dark ? Color.WHITE : Color.rgb(25,25,25);
        muted = dark ? 0xffb8b8b0 : 0xff666666;
        ScrollView scroll = new ScrollView(context);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(20, 14, 20, 40);
        body.setBackgroundColor(dark ? 0xff10110f : 0xfffafaf6);
        scroll.addView(body);
        this.scroll = scroll;
    }
    private final ScrollView scroll;
    View view() { return scroll; }
    private android.content.SharedPreferences prefs() { return context.getSharedPreferences(PREF, 0); }
    private TextView text(String value, int size, boolean bold) {
        TextView v = new TextView(context);
        v.setText(value); v.setTextSize(size); v.setTextColor(ink);
        v.setPadding(0, 8, 0, 8);
        if (bold) v.setTypeface(android.graphics.Typeface.SERIF, 1);
        return v;
    }
    void render() {
        body.removeAllViews();
        body.addView(text("NATIONAL", 30, true));
        long updated = prefs().getLong("updated", 0);
        body.addView(text(updated == 0 ? "No edition yet" :
            "Edition updated " + new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US).format(new Date(updated))
            + (System.currentTimeMillis()-updated > 86400000L ? " · Cached / older edition" : ""), 12, false));
        String status = prefs().getString("status", "");
        if (!status.isEmpty()) body.addView(text(status, 12, false));
        Button refresh = new Button(context); refresh.setText(loading ? "Updating…" : "Update edition");
        refresh.setEnabled(!loading); refresh.setOnClickListener(v -> update()); body.addView(refresh);
        try {
            JSONArray entries = new JSONArray(prefs().getString("stories", "[]"));
            if (entries.length()==0) body.addView(text("No national stories cached. Update to collect today's coverage.", 15, false));
            for (int i=0;i<entries.length();i++) {
                JSONObject item = entries.getJSONObject(i);
                final String url=item.optString("url");
                if (i==0) body.addView(text("TOP STORY", 12, true));
                TextView headline=text(item.optString("title"), i==0?23:18, true);
                headline.setOnClickListener(v -> open(url));
                body.addView(headline);
                body.addView(text(item.optString("source") + (item.optString("also").isEmpty()?"":" · Also: "+item.optString("also")), 12, false));
                Button save=new Button(context); save.setAllCaps(false); save.setText("Save for later");
                save.setOnClickListener(v -> {
                    try {
                        JSONArray saved=new JSONArray(context.getSharedPreferences("national_saved",0).getString("items","[]"));
                        for(int j=0;j<saved.length();j++) if(url.equals(saved.getJSONObject(j).optString("url"))) return;
                        saved.put(item);
                        context.getSharedPreferences("national_saved",0).edit().putString("items",saved.toString()).apply();
                        android.widget.Toast.makeText(context,"Saved",android.widget.Toast.LENGTH_SHORT).show();
                    } catch(Exception ignored){}
                });
                body.addView(save);
            }
        } catch(Exception ignored){}
        if (updated==0 && !loading && !attempted) update();
    }
    private void open(String url) {
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){}
    }
    void showSettings() {
        boolean[] checked=new boolean[NAMES.length];
        StringBuilder[] labels=new StringBuilder[NAMES.length];
        String[] options=new String[NAMES.length];
        for(int i=0;i<NAMES.length;i++) {
            checked[i]=prefs().getBoolean("source_"+i,true);
            options[i]=NAMES[i]+" — "+NOTES[i];
        }
        new AlertDialog.Builder(context).setTitle("National sources")
            .setMultiChoiceItems(options,checked,(d,which,on)->checked[which]=on)
            .setPositiveButton("Save",(d,w)->{
                android.content.SharedPreferences.Editor edit=prefs().edit();
                for(int i=0;i<checked.length;i++) edit.putBoolean("source_"+i,checked[i]);
                edit.apply(); update();
            }).setNegativeButton("Cancel",null)
            .setNeutralButton("Saved articles",(d,w)->showSaved()).show();
    }
    private void showSaved() {
        try {
            JSONArray saved=new JSONArray(context.getSharedPreferences("national_saved",0).getString("items","[]"));
            String[] labels=new String[saved.length()];
            for(int i=0;i<labels.length;i++) labels[i]=saved.getJSONObject(i).optString("title");
            new AlertDialog.Builder(context).setTitle("Saved national articles")
                .setItems(labels,(d,i)->open(saved.optJSONObject(i).optString("url")))
                .setPositiveButton("Close",null).show();
        } catch(Exception ignored){}
    }
    private void update() {
        if(loading)return;
        loading=true;
        attempted=true;
        render();
        final java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(()->{
            ArrayList<JSONObject> found=new ArrayList<>();
            ArrayList<String> diagnostics=new ArrayList<>();
            java.util.concurrent.ExecutorService feeds=Executors.newFixedThreadPool(URLS.length);
            ArrayList<java.util.concurrent.Future<String>> jobs=new ArrayList<>();
            for(int i=0;i<URLS.length;i++) {
                if(!prefs().getBoolean("source_"+i,true))continue;
                final int index=i;
                jobs.add(feeds.submit(()->{
                    String source=NAMES[index];
                    String failure="";
                    List<Story> stories=Collections.emptyList();
                    boolean fallback=index==0;
                    try {
                        stories=FeedReader.read(new SourceConfig(source,URLS[index],"","direct"));
                    } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); }
                    if(stories.isEmpty() && index!=0) {
                        try {
                            String query=java.net.URLEncoder.encode("site:"+DOMAINS[index]+" US when:3d","UTF-8");
                            String url="https://news.google.com/rss/search?q="+query+"&hl=en-US&gl=US&ceid=US%3Aen";
                            stories=FeedReader.read(new SourceConfig(source,url,"","direct"));
                            fallback=true;
                        } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); }
                    }
                    int accepted=0;
                    for(Story story:stories) {
                        if(story.url==null || !story.url.startsWith("https://")) continue;
                        if(story.publishedEpoch>0 && System.currentTimeMillis()-story.publishedEpoch>4L*86400000L) continue;
                        try {
                            JSONObject o=new JSONObject();
                            o.put("title",story.title); o.put("url",story.url);
                            o.put("source",source+(fallback?" (via Google News)":""));
                            o.put("time",story.publishedEpoch);
                            synchronized(found){found.add(o);}
                            if(++accepted>=35)break;
                        } catch(Exception ignored){}
                    }
                    return source+": "+accepted+" stories"+(accepted==0&&!failure.isEmpty()?" ("+failure+")":fallback?" (Google News)":"");
                }));
            }
            feeds.shutdown();
            for(java.util.concurrent.Future<String> job:jobs) {
                try { diagnostics.add(job.get()); }
                catch(Exception e) { diagnostics.add("Source failed: "+e.getMessage()); }
            }
            found.sort((a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
            JSONArray result=new JSONArray();
            ArrayList<String> tokens=new ArrayList<>();
            for(JSONObject item:found) {
                String normalized=item.optString("title").toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
                Set<String> words=new HashSet<>(Arrays.asList(normalized.split(" ")));
                boolean duplicate=false;
                for(int j=0;j<tokens.size();j++) {
                    Set<String> prior=new HashSet<>(Arrays.asList(tokens.get(j).split(" ")));
                    Set<String> overlap=new HashSet<>(words);overlap.retainAll(prior);
                    if(overlap.size()>=4 && overlap.size()>=Math.min(words.size(),prior.size())*0.65) {
                        JSONObject original=result.optJSONObject(j);
                        try { original.put("also",original.optString("also").isEmpty()?item.optString("source"):original.optString("also")+", "+item.optString("source")); } catch(Exception ignored) {}
                        duplicate=true;break;
                    }
                }
                if(!duplicate && result.length()<25) {result.put(item);tokens.add(normalized);}
            }
            android.content.SharedPreferences.Editor edit=prefs().edit();
            if(result.length()>0) edit.putString("stories",result.toString()).putLong("updated",System.currentTimeMillis());
            edit.putString("status",(result.length()==0?"No new stories retrieved; retaining cached edition.":"Sources checked:") + "\n"+String.join("\n",diagnostics)).apply();
            loading=false;
            ((android.app.Activity)context).runOnUiThread(this::render);
        });
        worker.shutdown();
    }
}
