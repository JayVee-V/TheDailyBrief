package org.morningbrief.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Once-daily comic editions, persisted locally. Only public publisher feeds are read. */
final class ComicEdition {
    static final class Source {
        final String id,name,description,feed,home,match;
        final boolean initial,inline;
        Source(String id,String name,String description,String feed,String home,String match,boolean initial,boolean inline){
            this.id=id;this.name=name;this.description=description;this.feed=feed;this.home=home;this.match=match;this.initial=initial;this.inline=inline;
        }
    }
    static final Source[] SOURCES={
        new Source("penny","Penny Arcade","Video games and geek culture.","https://www.penny-arcade.com/feed","https://www.penny-arcade.com/comic","/comic/",true,true),
        new Source("cad","Ctrl+Alt+Del","Gaming humor and continuing stories.","https://cad-comic.com/feed/","https://cad-comic.com/comic/","/comic/",true,true),
        new Source("sam","Sam & Fuzzy","Sam Logan's character-driven comedy and adventure.","https://www.samandfuzzy.com/feed","https://www.samandfuzzy.com/","",true,true),
        new Source("awkward","Awkward Zombie","Video-game jokes and familiar gaming franchises.","https://www.awkwardzombie.com/awkward-zombie/rss","https://www.awkwardzombie.com/","",false,true),
        new Source("xkcd","xkcd","Science, technology and dry humor.","https://xkcd.com/rss.xml","https://xkcd.com/","",false,true),
        new Source("smbc","Saturday Morning Breakfast Cereal","Science, philosophy and absurdity.","https://www.smbc-comics.com/comic/rss","https://www.smbc-comics.com/","",false,true),
        new Source("pdl","Poorly Drawn Lines","Absurd conversations and surreal humor.","https://poorlydrawnlines.com/feed/","https://poorlydrawnlines.com/","/comic/",false,true),
        new Source("dinosaur","Dinosaur Comics","Talking dinosaurs and philosophical comedy.","https://qwantz.com/rssfeed.php","https://qwantz.com/","",false,true),
        new Source("nerf","Nerf NOW!!","Video games and online culture.","https://feeds.feedburner.com/nerfnow/full","https://www.nerfnow.com/","/comic/",false,true),
        new Source("qc","Questionable Content","Relationships, work and robots.","https://www.questionablecontent.net/QCRSS.xml","https://www.questionablecontent.net/","",false,true),
        new Source("doa","Dumbing of Age","College life and interpersonal comedy.","https://www.dumbingofage.com/feed/","https://www.dumbingofage.com/","/comic/",false,true),
        new Source("sheldon","Sheldon","Family, animals and pop culture.","https://www.sheldoncomics.com/feed/atom/","https://www.sheldoncomics.com/","",false,true),
        new Source("buni","Buni","Wordless dark and silly animal humor.","https://www.bunicomic.com/feed/","https://www.bunicomic.com/","",false,true),
        new Source("buttersafe","Buttersafe","Offbeat short comics.","https://www.buttersafe.com/feed/","https://www.buttersafe.com/","",false,true),
        new Source("sandra","Sandra and Woo","A girl, a raccoon and their adventures.","https://www.sandraandwoo.com/feed/atom/","https://www.sandraandwoo.com/","",false,true),
        new Source("jen","Jen Sorensen","Editorial and political cartoons.","https://jensorensen.ghost.io/rss/","https://jensorensen.ghost.io/","",false,false),
        new Source("gunner","Gunnerkrigg Court","Science-fantasy adventure and character humor.","https://gunnerkrigg.com/rss.xml","https://gunnerkrigg.com/","",false,true)
    };
    private ComicEdition(){}
    private static SharedPreferences prefs(Context c){return SettingsStore.prefs(c);}
    static boolean enabled(Context c,Source s){return prefs(c).getBoolean("comic_enabled_"+s.id,s.initial);}
    static JSONArray items(Context c,Source s){return array(prefs(c).getString("comic_items_"+s.id,"[]"));}
    static String status(Context c,Source s){return prefs(c).getString("comic_status_"+s.id,"");}
    static String edition(Context c,Source s){return prefs(c).getString("comic_edition_"+s.id,"");}
    private static JSONArray array(String raw){try{return new JSONArray(raw);}catch(Exception e){return new JSONArray();}}
    static boolean needsRefresh(Context c){
        String day=LocalDate.now().toString();
        for(Source s:SOURCES)if(enabled(c,s)&&!day.equals(prefs(c).getString("daily_attempt_comic_"+s.id,"")))return true;
        return false;
    }
    static void refresh(Context c,boolean manual){
        ExecutorService pool=Executors.newFixedThreadPool(3);
        List<Future<?>> jobs=new ArrayList<>();
        for(Source s:SOURCES)if(enabled(c,s))jobs.add(pool.submit(()->refreshOne(c,s,manual)));
        pool.shutdown();
        for(Future<?> f:jobs)try{f.get();}catch(Exception ignored){}
    }
    private static void refreshOne(Context c,Source s,boolean manual){
        String section="comic_"+s.id;
        if(!DailyRefresh.begin(c,section,manual,0))return;
        try{
            String today=LocalDate.now().toString();
            List<Story> feed;
            try{feed=FeedReader.read(new SourceConfig(s.name,s.feed,s.home,"direct"));}
            catch(Exception first){
                if(!"penny".equals(s.id))throw first;
                feed=FeedReader.read(new SourceConfig(s.name,"https://www.penny-arcade.com/rss.xml",s.home,"direct"));
            }
            if(feed.isEmpty())throw new Exception("Feed returned no entries");
            JSONArray old=items(c,s);
            Set<String> seen=new LinkedHashSet<>(prefs(c).getStringSet("comic_seen_"+s.id,Collections.emptySet()));
            List<Story> candidates=new ArrayList<>();
            Set<String> unique=new HashSet<>();
            for(Story st:feed){
                if(st.url==null)continue;
                if(st.url.startsWith("http://"))st.url="https://"+st.url.substring(7);
                if(!st.url.startsWith("https://"))continue;
                String path=android.net.Uri.parse(st.url).getPath();
                if(path==null||(!s.match.isEmpty()&&!path.contains(s.match)))continue;
                if("sam".equals(s.id)&&!path.matches("/[0-9]+/?"))continue;
                String key=comicKey(st.url);
                if(key.isEmpty()||seen.contains(key)||!unique.add(key))continue;
                candidates.add(st);
            }
            // Most feeds are newest-first; prefer publication dates when supplied.
            candidates.sort((a,b)->Long.compare(b.publishedEpoch,a.publishedEpoch));
            if(seen.isEmpty()&&candidates.size()>1)candidates=new ArrayList<>(candidates.subList(0,1));
            else if(candidates.size()>5)candidates=new ArrayList<>(candidates.subList(0,5));
            Collections.reverse(candidates);
            JSONArray result=today.equals(edition(c,s))?old:new JSONArray();
            // Repair a previously collected link-only strip when manually requested.
            if(manual&&s.inline)for(int i=0;i<result.length();i++){
                JSONObject o=result.optJSONObject(i);
                if(o!=null&&imageFile(c,o.optString("file"))==null){
                    Story prior=new Story();prior.url=o.optString("url");prior.title=o.optString("title");
                    o.put("file",cacheImage(c,s,prior));
                }
            }
            for(Story st:candidates){
                JSONObject o=new JSONObject();
                o.put("title",st.title);o.put("url",st.url);o.put("time",st.publishedEpoch);
                String image=s.inline?cacheImage(c,s,st):"";
                o.put("file",image);result.put(o);seen.add(comicKey(st.url));
            }
            // Mark the initial feed backlog as already seen, not as new tomorrow.
            if(!candidates.isEmpty()&&prefs(c).getStringSet("comic_seen_"+s.id,Collections.emptySet()).isEmpty())
                for(Story st:feed)if(st.url!=null&&st.url.startsWith("https://"))seen.add(comicKey(st.url));
            // Keep the previous edition intact until successful collection; remove outdated image files afterwards.
            boolean committed=prefs(c).edit().putString("comic_items_"+s.id,result.toString())
                .putString("comic_edition_"+s.id,today).putStringSet("comic_seen_"+s.id,seen)
                .putString("comic_status_"+s.id,result.length()==0?"No new strip today.":"").commit();
            if(committed)cleanup(c,s,result);
        }catch(Exception e){
            prefs(c).edit().putString("comic_status_"+s.id,"Feed unavailable; open the original site.").apply();
        }finally{DailyRefresh.end(section);}
    }
    private static String comicKey(String url){
        android.net.Uri u=android.net.Uri.parse(url);
        // Some comic archives use query parameters as the strip identifier.
        return SavedNews.key(url)+(u.getEncodedQuery()==null?"":"?"+u.getEncodedQuery());
    }

    /** Only recognize comic-specific image paths, not arbitrary page logos or adverts. */
    private static String publicComicImage(Source source,String page){
        try{
            String html=Net.getString(page,1_600_000);
            java.util.regex.Matcher tags=java.util.regex.Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
            while(tags.find()){
                String tag=tags.group();
                java.util.regex.Matcher src=java.util.regex.Pattern.compile("(?i)\\b(?:src|data-src)\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
                while(src.find()){
                    String raw=src.group(2).replace("&amp;","&");
                    String lower=raw.toLowerCase(Locale.US);
                    boolean known=switch(source.id){
                        case "penny" -> lower.contains("art.penny-arcade.com/photos/")||lower.contains("cdn.penny-arcade.com")&&lower.contains("comic");
                        case "cad" -> lower.contains("/comics/")||lower.contains("/comic/")||lower.contains("/wp-content/uploads/")&&tag.toLowerCase(Locale.US).contains("comic");
                        case "sam" -> lower.contains("/comics/")||lower.contains("/comic/");
                        case "qc" -> lower.contains("/comics/");
                        case "xkcd" -> lower.contains("imgs.xkcd.com/comics/");
                        case "smbc" -> lower.contains("/comics/");
                        case "dinosaur" -> lower.contains("/comics/");
                        default -> tag.toLowerCase(Locale.US).matches("(?s).*\\b(?:id|class)\\s*=\\s*['\"][^'\"]*(?:comic-image|comicimage|comic-img|comicimg|comic-art)[^'\"]*['\"].*");
                    };
                    if(known)return new URL(new URL(page),raw).toString();
                }
            }
        }catch(Exception ignored){}
        return "";
    }

    private static File dir(Context c){File d=new File(c.getFilesDir(),"comic_images");d.mkdirs();return d;}
    private static String cacheImage(Context c,Source s,Story st){
        if(st.imageUrl==null||st.imageUrl.trim().isEmpty())st.imageUrl=publicComicImage(s,st.url);
        if(st.imageUrl==null||st.imageUrl.trim().isEmpty())return "";
        File target=null;
        try{
            URL url=new URL(new URL(st.url),st.imageUrl);
            if("http".equals(url.getProtocol()))url=new URL("https",url.getHost(),url.getFile());
            if(!"https".equals(url.getProtocol()))return "";
            byte[] bytes=Net.getBytes(url.toString(),8_000_000);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0)return "";
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
            while((long)(bounds.outWidth/opts.inSampleSize)*(bounds.outHeight/opts.inSampleSize)>1_600_000L)opts.inSampleSize*=2;
            Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
            if(image==null)return "";
            String name=s.id+"_"+Integer.toHexString(st.url.hashCode())+".png";
            target=new File(dir(c),name);
            try(FileOutputStream out=new FileOutputStream(target)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Image encode failed");}
            finally{image.recycle();}
            return name;
        }catch(Exception e){if(target!=null)target.delete();return "";}
    }
    static File imageFile(Context c,String name){
        if(name==null||!name.matches("[a-z0-9_]+[.]png"))return null;
        File f=new File(dir(c),name);return f.isFile()?f:null;
    }
    private static void cleanup(Context c,Source s,JSONArray keep){
        Set<String> names=new HashSet<>();
        for(int i=0;i<keep.length();i++){JSONObject o=keep.optJSONObject(i);if(o!=null)names.add(o.optString("file"));}
        File[] files=dir(c).listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith(s.id+"_")&&!names.contains(f.getName()))f.delete();
    }
}
