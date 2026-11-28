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
    private static final String[] NAMES = {"Associated Press", "NPR", "PBS NewsHour", "The Hill", "ProPublica",
        "Reuters", "BBC News", "ABC News", "CBS News", "NBC News", "CNN", "Fox News", "USA Today", "The Guardian", "The Washington Post", "The New York Times", "Politico", "Axios", "The Christian Science Monitor", "Stateline", "KFF Health News", "The Marshall Project", "Inside Climate News", "The Conversation", "Reason"};
    private static final String[] URLS = {
        "https://news.google.com/rss/search?q=site%3Aapnews.com%2Fus-news+when%3A3d&hl=en-US&gl=US&ceid=US%3Aen",
        "https://feeds.npr.org/1003/rss.xml",
        "https://www.pbs.org/newshour/feeds/rss/headlines",
        "https://thehill.com/feed/",
        "https://www.propublica.org/feeds/propublica/main",
        "",
        "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
        "https://abcnews.com/abcnews/usheadlines",
        "https://www.cbsnews.com/latest/rss/us",
        "https://feeds.nbcnews.com/nbcnews/public/news",
        "",
        "https://feeds.foxnews.com/foxnews/national",
        "",
        "https://www.theguardian.com/us-news/rss",
        "",
        "https://rss.nytimes.com/services/xml/rss/nyt/US.xml",
        "https://www.politico.com/rss/politicopicks.xml",
        "",
        "https://rss.csmonitor.com/feeds/usa",
        "https://stateline.org/feed/",
        "https://kffhealthnews.org/feed/",
        "https://www.themarshallproject.org/rss/recent.rss",
        "https://insideclimatenews.org/feed/",
        "https://theconversation.com/us/articles.atom",
        "https://reason.com/feed/"
    };
    private static final String[] NOTES = {
        "US news from AP via Google News",
        "Public radio reporting and national affairs",
        "Public-service journalism and national reporting",
        "Washington, Congress and federal policy",
        "Investigative reporting and accountability",
        "Wire reporting on US affairs and the economy",
        "US and Canadian news from the BBC",
        "US national reporting",
        "National reporting and current events",
        "National reporting and breaking news",
        "US news and current affairs",
        "US news and current affairs",
        "National reporting and public-interest news",
        "US affairs and public policy",
        "National reporting and federal government",
        "National reporting",
        "Politics and public policy",
        "Concise reporting on national affairs",
        "Contextual reporting on US affairs",
        "State government and policy across the US",
        "Health care and policy",
        "Nonprofit criminal justice reporting",
        "Nonprofit energy and climate reporting",
        "Research-based reporting and analysis",
        "Libertarian-oriented reporting and commentary"
    };
    private final Context context;
    private final LinearLayout body;
    private final boolean dark;
    private final int ink, muted;
    private final ReaderStyle style;
    private volatile boolean loading;
    private static final String[] DOMAINS = {"apnews.com/us-news", "npr.org", "pbs.org/newshour", "thehill.com", "propublica.org",
        "reuters.com/world/us", "bbc.com/news", "abcnews.com/US", "cbsnews.com/us", "nbcnews.com", "cnn.com/us", "foxnews.com/us", "usatoday.com", "theguardian.com/us-news", "washingtonpost.com", "nytimes.com", "politico.com", "axios.com", "csmonitor.com/USA", "stateline.org", "kffhealthnews.org", "themarshallproject.org", "insideclimatenews.org", "theconversation.com/us", "reason.com"};
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
        long updated = prefs().getLong("updated", 0);
        body.addView(text(updated == 0 ? "No edition yet" :
            "Edition: " + new SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US).format(new Date(updated))
            + (!sameDay(updated,System.currentTimeMillis()) ? " · Cached / older edition" : ""), 12, false));
        long checked=prefs().getLong("checked",0);
        String outcome=prefs().getString("outcome","");
        if(loading) body.addView(style.text("Updating national edition…",13,false,muted));
        else if(!outcome.isEmpty()) {
            String when=checked==0?"":" · Checked "+new SimpleDateFormat("EEE, MMM d · h:mm a",Locale.US).format(new Date(checked));
            TextView note=style.text(outcome+when,13,false,muted);
            note.setPadding(0,0,0,style.dp(12));body.addView(note);
        }
        try {
            JSONArray entries = new JSONArray(prefs().getString("stories", "[]"));
            boolean lead=true;
            for(int i=0;i<entries.length();i++){
                JSONObject item=entries.getJSONObject(i);
                final String url=item.optString("url");
                if(SavedNews.moved(context,url))continue;
                String source=item.optString("source");
                long published=item.optLong("time",0);
                if(published>0)source+=" · "+new SimpleDateFormat("MMM d, h:mm a",Locale.US).format(new Date(published));
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
            if(lead&&!loading)body.addView(style.text("No stories to display in this edition. Use Settings to check sources or manually update.",16,false,muted));
        }catch(Exception ignored){}
        if (!loading) update(false);
    }
    private void open(String url) {
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){}
    }
    void showSettings(){
        MainActivity activity=(MainActivity)context;
        SettingsPage page=new SettingsPage(context,style);
        page.section("News sources","Select the publications you want in National. Sources with no direct feed use Google News; other feeds can fall back to it. New sources are optional.");
        for(int i=0;i<NAMES.length;i++){
            final int index=i;
            String delivery=URLS[i].isEmpty()||i==0?" · Via Google News":" · Publisher feed";
            page.choice(NAMES[i],NOTES[i]+delivery,prefs().getBoolean("source_"+i,i<5),
                checked->prefs().edit().putBoolean("source_"+index,checked).apply());
        }
        page.section("Edition management","Automatic editions are checked once per day. Update manually when needed.");
        page.action(loading?"Updating…":"Update edition",()->{activity.closeSettings();update(true);});
        page.action("Source diagnostics",()->{
            String report=prefs().getString("status","No refresh completed yet.");
            SettingsPage diagnostics=new SettingsPage(context,style);
            diagnostics.section("Source diagnostics",report);
            diagnostics.action("Back to settings",this::showSettings);
            activity.openSettings(diagnostics,this::render);
        });
        activity.openSettings(page,this::render);
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
            catch(Exception e) { recordFailure(context,e); }
            finally {
                DailyRefresh.end(DailyRefresh.NATIONAL);
                ((android.app.Activity)context).runOnUiThread(()->{loading=false;render();});
            }
        },"national-daily-edition").start();
    }

    static void refreshScheduled(Context c) {
        if(!DailyRefresh.begin(c,DailyRefresh.NATIONAL,false,lastEdition(c)))return;
        try { fetchEdition(c); }
        catch(Exception e) { recordFailure(c,e); }
        finally { DailyRefresh.end(DailyRefresh.NATIONAL); }
    }

    private static void fetchEdition(Context context) {
            final long now=System.currentTimeMillis();
            final long previousEdition=lastEdition(context);
            final boolean historyInitialized=prefs(context).getBoolean("history_initialized",false);
            final boolean sameEdition=historyInitialized&&sameDay(previousEdition,now);
            JSONArray previous=readArray(prefs(context).getString("stories","[]"));
            Set<String> history=new HashSet<>(prefs(context).getStringSet("published_keys",Collections.emptySet()));
            // Seed the history from the installed version, without deleting its cache.
            for(int i=0;i<previous.length();i++){
                JSONObject item=previous.optJSONObject(i);
                if(item!=null)remember(history,item);
            }
            // Commit migration before network work, including on a failed refresh.
            prefs(context).edit().putStringSet("published_keys",new HashSet<>(history)).apply();
            java.util.concurrent.atomic.AtomicInteger successful=new java.util.concurrent.atomic.AtomicInteger();
            ArrayList<JSONObject> found=new ArrayList<>();
            ArrayList<String> diagnostics=new ArrayList<>();
            java.util.concurrent.ExecutorService feeds=Executors.newFixedThreadPool(5);
            ArrayList<java.util.concurrent.Future<String>> jobs=new ArrayList<>();
            for(int i=0;i<URLS.length;i++) {
                if(!prefs(context).getBoolean("source_"+i,i<5))continue;
                final int index=i;
                jobs.add(feeds.submit(()->{
                    String source=NAMES[index];
                    String failure="";
                    List<Story> stories=Collections.emptyList();
                    boolean fallback=index==0||URLS[index].isEmpty();
                    try {
                        if(!URLS[index].isEmpty())stories=FeedReader.read(new SourceConfig(source,URLS[index],"","direct"));
                    } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); }
                    if((stories.isEmpty() || stories.stream().noneMatch(st->st.publishedEpoch>0&&now-st.publishedEpoch<=48L*3600000L&&st.publishedEpoch<=now+3600000L)) && index!=0) {
                        try {
                            String query=java.net.URLEncoder.encode("site:"+DOMAINS[index]+" (US OR United States OR American) when:2d","UTF-8");
                            String url="https://news.google.com/rss/search?q="+query+"&hl=en-US&gl=US&ceid=US%3Aen";
                            stories=FeedReader.read(new SourceConfig(source,url,"","direct"));
                            fallback=true;
                        } catch(Exception e) { failure=e.getClass().getSimpleName()+": "+e.getMessage(); }
                    }
                    if(!stories.isEmpty())successful.incrementAndGet();
                    int accepted=0;
                    for(Story story:stories) {
                        if(story.url==null || !story.url.startsWith("https://")) continue;
                        if(story.publishedEpoch<=0 || now-story.publishedEpoch>48L*3600000L || story.publishedEpoch>now+3600000L) continue;
                        try {
                            JSONObject o=new JSONObject();
                            o.put("title",story.title); o.put("url",story.url); o.put("summary",story.summary);
                            o.put("source",source+(fallback?" (via Google News)":""));
                            o.put("time",story.publishedEpoch);
                            synchronized(found){found.add(o);}
                            if(++accepted>=35)break;
                        } catch(Exception ignored){}
                    }
                    return source+": "+accepted+" recent candidates"+(accepted==0&&!failure.isEmpty()?" ("+failure+")":fallback?" (Google News)":"");
                }));
            }
            feeds.shutdown();
            for(java.util.concurrent.Future<String> job:jobs) {
                try { diagnostics.add(job.get()); }
                catch(Exception e) { diagnostics.add("Source failed: "+e.getMessage()); }
            }
            found.sort((a,b)->Long.compare(b.optLong("time"),a.optLong("time")));
            JSONArray result=new JSONArray();
            ArrayList<Set<String>> tokens=new ArrayList<>();
            int repeats=0,grouped=0;
            Set<String> editionKeys=new HashSet<>();
            for(JSONObject item:found) {
                if(wasPublished(history,item)||SavedNews.moved(context,item.optString("url"))){repeats++;continue;}
                String normalized=normalizedTitle(item);
                if(normalized.isEmpty())continue;
                Set<String> words=new HashSet<>(Arrays.asList(normalized.split(" ")));
                words.removeAll(Arrays.asList("a","an","the","in","on","of","to","for","and","is","at","with","by","as","from"));
                boolean duplicate=wasPublished(editionKeys,item);
                for(int j=0;j<tokens.size()&&!duplicate;j++) {
                    Set<String> prior=tokens.get(j);
                    Set<String> overlap=new HashSet<>(words);overlap.retainAll(prior);
                    if(overlap.size()>=4 && overlap.size()>=Math.min(words.size(),prior.size())*0.65) {
                        JSONObject original=result.optJSONObject(j);
                        try {
                            String other=item.optString("source"),also=original.optString("also");
                            if(!other.equals(original.optString("source"))&&!Arrays.asList(also.split(", ")).contains(other))
                                original.put("also",also.isEmpty()?other:also+", "+other);
                        }catch(Exception ignored){}
                        duplicate=true;
                    }
                }
                if(duplicate){grouped++;remember(editionKeys,item);continue;}
                if(result.length()<25){result.put(item);tokens.add(words);remember(editionKeys,item);}
            }
            android.content.SharedPreferences.Editor edit=prefs(context).edit().putLong("checked",now);
            String outcome;
            if(jobs.isEmpty()){
                edit.putString("stories","[]").putLong("updated",now).putBoolean("history_initialized",true).putStringSet("published_keys",history);
                outcome="No national sources selected";
            }else if(successful.get()==0){
                outcome="Feeds unavailable · showing the last available edition";
                if(previousEdition==0)outcome="Feeds unavailable · no edition available";
            }else{
                history.addAll(editionKeys);
                edit.putBoolean("history_initialized",true).putStringSet("published_keys",history);
                if(sameEdition){
                    if(result.length()>0){
                        for(int i=0;i<result.length();i++)previous.put(result.optJSONObject(i));
                        edit.putString("stories",previous.toString()).putLong("updated",now);
                        outcome=result.length()+" new stories added to today's edition";
                    }else outcome="No new stories · today's edition unchanged";
                }else{
                    edit.putString("stories",result.toString()).putLong("updated",now);
                    outcome=result.length()==0?"No new stories for this edition":result.length()+" new stories in today's edition";
                }
            }
            edit.putString("outcome",outcome)
                .putString("status",outcome+"\n"+repeats+" previously published or saved; "+grouped+" duplicates grouped.\n"+String.join("\n",diagnostics)).apply();

    }
    private static void recordFailure(Context c,Exception e){
        prefs(c).edit().putLong("checked",System.currentTimeMillis())
            .putString("outcome","Refresh failed · edition unchanged")
            .putString("status","Refresh failed: "+e.getMessage()).apply();
    }
    private static boolean sameDay(long a,long b){
        if(a<=0||b<=0)return false;
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        return java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate().equals(
            java.time.Instant.ofEpochMilli(b).atZone(zone).toLocalDate());
    }
    private static JSONArray readArray(String raw){
        try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}
    }
    private static String normalizedTitle(JSONObject item){
        String title=item.optString("title").trim();
        // Google News usually appends the publisher to its headline.
        if(item.optString("source").contains("(via Google News)")){
            for(String name:NAMES){
                String suffix=" - "+name;
                if(title.toLowerCase(Locale.US).endsWith(suffix.toLowerCase(Locale.US))){
                    title=title.substring(0,title.length()-suffix.length());break;
                }
            }
            if(title.endsWith(" - AP News"))title=title.substring(0,title.length()-10);
        }
        return title.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static String urlKey(JSONObject item){
        String url=item.optString("url").trim();
        if(url.isEmpty())return "";
        // Reuse the same canonical URL identity as the reading shelf.
        return SavedNews.key(url);
    }
    private static boolean wasPublished(Set<String> keys,JSONObject item){
        String u=urlKey(item),t=normalizedTitle(item);
        return (!u.isEmpty()&&keys.contains("u:"+u))||(!t.isEmpty()&&keys.contains("t:"+t));
    }
    private static void remember(Set<String> keys,JSONObject item){
        String u=urlKey(item),t=normalizedTitle(item);
        if(!u.isEmpty())keys.add("u:"+u);
        if(!t.isEmpty())keys.add("t:"+t);
    }

}
