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
    private final ReaderStyle style;
    private volatile boolean loading;
    private static final String[] DOMAINS = {"apnews.com/us-news", "npr.org", "pbs.org/newshour", "thehill.com", "propublica.org"};
    NationalNews(Context context, boolean dark) {
        this.context = context; this.dark = dark;
        style = new ReaderStyle(context,dark);
        ink = style.ink; muted = style.muted;
        ScrollView scroll = new ScrollView(context);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(28));
        body.setBackgroundColor(style.paper);
        scroll.setFillViewport(true);
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
        body.addView(style.text("National news", 26, true, ink));
        long updated = prefs().getLong("updated", 0);
        body.addView(text(updated == 0 ? "No edition yet" :
            "Edition updated " + new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US).format(new Date(updated))
            + (System.currentTimeMillis()-updated > 86400000L ? " · Cached / older edition" : ""), 12, false));
        if(loading) body.addView(style.text("Updating national edition…",13,false,muted));
        try {
            JSONArray entries = new JSONArray(prefs().getString("stories", "[]"));
            boolean lead=true;
            for(int i=0;i<entries.length();i++){
                JSONObject item=entries.getJSONObject(i);
                final String url=item.optString("url");
                if(SavedNews.moved(context,url))continue;
                String source=item.optString("source");
                String also=item.optString("also");
                String summary=item.optString("summary");
                if(!also.isEmpty())summary+=(summary.isEmpty()?"":"\n\n")+"Also reporting: "+also;
                body.addView(style.card(source,item.optString("title"),summary,lead,"Read",()->open(url),"Save",()->{
                    if(SavedNews.save(context,item.optString("source"),item.optString("title"),item.optString("summary"),url,item.optLong("time"),"National")){
                        android.widget.Toast.makeText(context,"Moved to Features",android.widget.Toast.LENGTH_SHORT).show();render();
                    }else android.widget.Toast.makeText(context,"Unable to save article",android.widget.Toast.LENGTH_SHORT).show();
                }));
                lead=false;
            }
            if(lead&&!loading)body.addView(style.text("No stories in this edition. Use Settings to update or check sources.",16,false,muted));
        }catch(Exception ignored){}
        if (!loading) update(false);
    }
    private void open(String url) {
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){}
    }
    void showSettings() {
        new AlertDialog.Builder(context).setTitle("National settings")
            .setItems(new String[]{"Choose sources",loading?"Updating…":"Update edition","Source diagnostics"},(dialog,which)->{
                if(which==0)chooseSources();
                else if(which==1)update(true);
                else new AlertDialog.Builder(context).setTitle("Source diagnostics").setMessage(prefs().getString("status","No refresh completed yet.")).setPositiveButton("Close",null).show();
            }).setNegativeButton("Cancel",null).show();
    }
    private void chooseSources(){
        boolean[] checked=new boolean[NAMES.length];
        String[] options=new String[NAMES.length];
        for(int i=0;i<NAMES.length;i++){checked[i]=prefs().getBoolean("source_"+i,true);options[i]=NAMES[i]+" — "+NOTES[i];}
        new AlertDialog.Builder(context).setTitle("National sources").setMultiChoiceItems(options,checked,(d,i,on)->checked[i]=on)
            .setPositiveButton("Save",(d,w)->{
                android.content.SharedPreferences.Editor edit=prefs().edit();
                for(int i=0;i<checked.length;i++)edit.putBoolean("source_"+i,checked[i]);
                edit.apply();render();
            }).setNegativeButton("Cancel",null).show();
    }
    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF,0);
    }
    static long lastEdition(Context c) { return prefs(c).getLong("updated",0); }

    private void update(boolean manual) {
        if(loading || !DailyRefresh.begin(context,DailyRefresh.NATIONAL,manual,lastEdition(context)))return;
        loading=true;
        render();
        new Thread(()->{
            try { fetchEdition(context.getApplicationContext()); }
            catch(Exception e) { prefs(context).edit().putString("status","Refresh failed: "+e.getMessage()).apply(); }
            finally {
                DailyRefresh.end(DailyRefresh.NATIONAL);
                ((android.app.Activity)context).runOnUiThread(()->{loading=false;render();});
            }
        },"national-daily-edition").start();
    }

    static void refreshScheduled(Context c) {
        if(!DailyRefresh.begin(c,DailyRefresh.NATIONAL,false,lastEdition(c)))return;
        try { fetchEdition(c); }
        catch(Exception e) { prefs(c).edit().putString("status","Refresh failed: "+e.getMessage()).apply(); }
        finally { DailyRefresh.end(DailyRefresh.NATIONAL); }
    }

    private static void fetchEdition(Context context) {
            ArrayList<JSONObject> found=new ArrayList<>();
            ArrayList<String> diagnostics=new ArrayList<>();
            java.util.concurrent.ExecutorService feeds=Executors.newFixedThreadPool(URLS.length);
            ArrayList<java.util.concurrent.Future<String>> jobs=new ArrayList<>();
            for(int i=0;i<URLS.length;i++) {
                if(!prefs(context).getBoolean("source_"+i,true))continue;
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
                            o.put("title",story.title); o.put("url",story.url); o.put("summary",story.summary);
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
            android.content.SharedPreferences.Editor edit=prefs(context).edit();
            if(result.length()>0) edit.putString("stories",result.toString()).putLong("updated",System.currentTimeMillis());
            edit.putString("status",(result.length()==0?"No new stories retrieved; retaining cached edition.":"Sources checked:") + "\n"+String.join("\n",diagnostics)).apply();
    }
}
