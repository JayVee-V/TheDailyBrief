package org.morningbrief.app;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** One persistent reading shelf for saved news, independent of edition refreshes. */
final class SavedNews {
    private static final String QUEUE="saved_news_v2", MOVED="moved_news_v2";
    private SavedNews() {}
    static String key(String url) {
        if(url==null)return "";
        Uri u=Uri.parse(url);
        return ((u.getHost()==null?"":u.getHost().toLowerCase(java.util.Locale.US))
            +(u.getEncodedPath()==null?"":u.getEncodedPath())).replaceAll("/+$","");
    }
    static String marker(String url) {
        return android.util.Base64.encodeToString(key(url).getBytes(java.nio.charset.StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
    }
    private static JSONArray array(Context c,String k) {
        try{return new JSONArray(SettingsStore.prefs(c).getString(k,"[]"));}catch(Exception e){return new JSONArray();}
    }
    static synchronized void migrate(Context c) {
        if(SettingsStore.prefs(c).getBoolean("saved_news_migrated",false))return;
        try {
            JSONArray old=new JSONArray(c.getSharedPreferences("national_saved",0).getString("items","[]"));
            for(int i=0;i<old.length();i++){
                JSONObject o=old.optJSONObject(i);
                if(o!=null) save(c,o.optString("source"),o.optString("title"),o.optString("summary"),o.optString("url"),o.optLong("time"),"National");
            }
            SettingsStore.prefs(c).edit().putBoolean("saved_news_migrated",true).apply();
        }catch(Exception ignored){}
    }
    static synchronized boolean save(Context c,String source,String title,String summary,String url,long date,String section) {
        if(url==null||(!url.startsWith("https://")&&!url.startsWith("http://"))||key(url).isEmpty()||title==null||title.isEmpty())return false;
        JSONArray a=array(c,QUEUE), m=array(c,MOVED);
        String id=key(url);
        for(int i=0;i<m.length();i++)if(id.equals(m.optString(i)))return true;
        try {
            JSONObject o=new JSONObject();
            o.put("source",source);o.put("title",title);o.put("summary",summary);o.put("url",url);
            o.put("date",date);o.put("section",section);
            a.put(o);m.put(id);
            return SettingsStore.prefs(c).edit().putString(QUEUE,a.toString()).putString(MOVED,m.toString()).commit();
        }catch(Exception e){return false;}
    }
    static synchronized boolean moved(Context c,String url) {
        JSONArray a=array(c,MOVED);String id=key(url);
        for(int i=0;i<a.length();i++)if(id.equals(a.optString(i)))return true;
        return false;
    }
    static synchronized List<JSONObject> items(Context c) {
        JSONArray a=array(c,QUEUE);List<JSONObject> list=new ArrayList<>();
        for(int i=a.length()-1;i>=0;i--){JSONObject o=a.optJSONObject(i);if(o!=null)list.add(o);}
        return list;
    }
    static synchronized void close(Context c,String url) {
        JSONArray a=array(c,QUEUE),next=new JSONArray();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!key(url).equals(key(o.optString("url"))))next.put(o);}
        SettingsStore.prefs(c).edit().putString(QUEUE,next.toString()).apply();
    }
    static synchronized String hideMoved(Context c,String html) {
        Set<String> markers=new HashSet<>();
        JSONArray a=array(c,MOVED);
        for(int i=0;i<a.length();i++)markers.add(android.util.Base64.encodeToString(a.optString(i).getBytes(java.nio.charset.StandardCharsets.UTF_8),
            android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING));
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<!--news:([A-Za-z0-9_-]+)-->(.*?)<!--/news-->",java.util.regex.Pattern.DOTALL).matcher(html);
        StringBuffer out=new StringBuffer();
        while(m.find())m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(markers.contains(m.group(1))?"":m.group(0)));
        m.appendTail(out);return out.toString();
    }
}
