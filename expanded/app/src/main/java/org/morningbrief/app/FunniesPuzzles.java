package org.morningbrief.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The daily back page, with individually selectable comic and puzzle sources. */
final class FunniesPuzzles {
    private static final class Game {
        final String id,title,provider,description,url;
        final boolean initial;
        Game(String i,String t,String p,String d,String u,boolean on){id=i;title=t;provider=p;description=d;url=u;initial=on;}
    }
    private static final Game[] GAMES={
        new Game("crossword","Mini Crossword","Play Mini Crossword","A bite-sized daily crossword.","https://www.playminicrossword.com/",true),
        new Game("sudoku","Sudoku","Daily Logic Puzzles","Three daily difficulty levels.","https://dailylogicpuzzles.com/sudoku/",true),
        new Game("nonogram","Nonogram","Daily Logic Puzzles","Reveal a picture using row and column clues.","https://dailylogicpuzzles.com/nonogram/",true),
        new Game("minesweeper","Minesweeper","Daily Logic Puzzles","Deduce the locations of hidden mines.","https://dailylogicpuzzles.com/minesweeper/",true),
        new Game("waffle","Waffle","Waffle Studio","Swap letters to complete a grid of words.","https://wafflegame.net/daily",false),
        new Game("worldle","Worldle","Teuteuf Games","Identify a country by its silhouette.","https://worldle.teuteuf.fr/",false),
        new Game("chess","Daily Chess Puzzle","Lichess","Find the best moves in a tactical position.","https://lichess.org/training/daily",false)
    };
    private final MainActivity activity;
    private final ReaderStyle style;
    private final LinearLayout screen;
    private boolean busy;
    private ScrollView pageScroll;
    FunniesPuzzles(MainActivity activity,boolean dark){
        this.activity=activity;style=new ReaderStyle(activity,dark);
        screen=new LinearLayout(activity);screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(style.paper);
        draw();
    }
    View view(){return screen;}
    private SharedPreferences prefs(){return SettingsStore.prefs(activity);}
    private boolean enabled(Game g){
        boolean def=g.initial;
        if("crossword".equals(g.id))def=prefs().getBoolean("funnies_show_crossword",true);
        if("sudoku".equals(g.id)||"nonogram".equals(g.id)||"minesweeper".equals(g.id))def=prefs().getBoolean("funnies_show_logic",true);
        return prefs().getBoolean("puzzle_enabled_"+g.id,def);
    }
    private int dp(int n){return style.dp(n);}
    private void section(LinearLayout column,String label){
        TextView t=style.text(label,12,false,style.ink);
        t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        t.setLetterSpacing(.10f);t.setPadding(0,dp(14),0,dp(8));column.addView(t);
        View line=new View(activity);line.setBackgroundColor(style.rule);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.bottomMargin=dp(12);column.addView(line,lp);
    }
    void render(){draw();if(ComicEdition.needsRefresh(activity))collect(false);}
    private void collect(boolean manual){
        if(busy)return;busy=true;draw();
        new Thread(()->{
            try{ComicEdition.refresh(activity.getApplicationContext(),manual);}
            finally{activity.runOnUiThread(()->{busy=false;draw();});}
        },"daily-comics").start();
    }
    private void draw(){
        int oldY=pageScroll==null?0:pageScroll.getScrollY();
        screen.removeAllViews();
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);pageScroll=scroll;
        LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(16),dp(16),dp(28));
        TextView date=style.text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy",Locale.US)).toUpperCase(Locale.US),12,false,style.muted);
        date.setGravity(Gravity.CENTER);date.setLetterSpacing(.08f);date.setPadding(0,dp(5),0,dp(12));column.addView(date);
        String today=LocalDate.now().toString();
        boolean comics=false;
        for(ComicEdition.Source source:ComicEdition.SOURCES)
            if(ComicEdition.enabled(activity,source)&&today.equals(ComicEdition.edition(activity,source))&&ComicEdition.items(activity,source).length()>0)comics=true;
        boolean memes=prefs().getBoolean("funnies_show_memes",true);
        if(comics||memes){
            section(column,"FUNNIES & COMICS");
            for(ComicEdition.Source source:ComicEdition.SOURCES){
                if(!ComicEdition.enabled(activity,source)||!today.equals(ComicEdition.edition(activity,source)))continue;
                JSONArray items=ComicEdition.items(activity,source);
                for(int i=0;i<items.length();i++){
                    JSONObject o=items.optJSONObject(i);if(o!=null)column.addView(comicCard(source,o,false));
                }
            }
            if(memes)column.addView(style.card("Imgur · deafkitten","Daily meme collection","Optional collection of internet humor.",false,"Open",()->open("https://imgur.com/user/deafkitten/posts"),null,null));
        }
        boolean games=false;for(Game g:GAMES)if(enabled(g))games=true;
        if(games){
            section(column,"DAILY PUZZLES");
            for(Game g:GAMES)if(enabled(g))column.addView(style.card(g.provider,g.title,g.description,false,"Play",()->open(g.url),null,null));
        }
        if(!comics&&!games&&!memes){
            TextView empty=style.text(busy?"Collecting today’s comics…":"Nothing to display in today’s edition.",16,false,style.muted);column.addView(empty);
        }
        scroll.addView(column);screen.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        if(oldY>0)scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private View comicCard(ComicEdition.Source s,JSONObject o,boolean cached){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(style.surface(style.panel,14,false));
        TextView source=style.text(s.name.toUpperCase(Locale.US)+(cached?" · CACHED":""),11,false,style.muted);
        source.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);source.setLetterSpacing(.08f);card.addView(source);
        String url=o.optString("url",s.home);
        TextView title=style.text(o.optString("title",s.name),20,true,style.ink);title.setPadding(0,dp(6),0,dp(8));card.addView(title);
        File file=ComicEdition.imageFile(activity,o.optString("file"));
        if(file!=null){
            android.graphics.Bitmap bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
            if(bitmap!=null){
                ImageView image=new ImageView(activity);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(bitmap);image.setContentDescription("Comic by "+s.name);
                image.setOnClickListener(v->readComic(s.name,url,file));card.addView(image,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        TextView read=style.action("Read comic",()->readComic(s.name,url,file));
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(44));action.topMargin=dp(12);card.addView(read,action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);card.setLayoutParams(p);return card;
    }
    void showSettings(){
        SettingsPage page=new SettingsPage(activity,style);
        page.section("Comic strips","Choose your comic subscriptions. Only strips collected for today's edition appear in Leisure.");
        for(ComicEdition.Source source:ComicEdition.SOURCES)
            page.choice(source.name,source.description,ComicEdition.enabled(activity,source),
                on->prefs().edit().putBoolean("comic_enabled_"+source.id,on).apply());
        page.section("Memes","");
        page.choice("deafkitten","Optional daily Imgur meme collection.",prefs().getBoolean("funnies_show_memes",true),
            on->prefs().edit().putBoolean("funnies_show_memes",on).apply());
        page.section("Daily puzzles","Select each game individually.");
        for(Game g:GAMES)page.choice(g.title,g.description+" · "+g.provider,enabled(g),
            on->prefs().edit().putBoolean("puzzle_enabled_"+g.id,on).apply());
        page.section("Edition management","");
        page.action(busy?"Collecting comics…":"Collect comics now",()->{collect(true);activity.closeSettings();});
        page.action("Source diagnostics",()->{
            SettingsPage details=new SettingsPage(activity,style);
            details.section("Comic source diagnostics","Today's edition status for each selected comic.");
            for(ComicEdition.Source source:ComicEdition.SOURCES)if(ComicEdition.enabled(activity,source)){
                String status=ComicEdition.status(activity,source);
                String date=ComicEdition.edition(activity,source);
                details.section(source.name,(status.isEmpty()?ComicEdition.items(activity,source).length()+" strips collected":status)+(date.isEmpty()?"":" · Edition "+date));
            }
            details.action("Back to settings",this::showSettings);
            activity.openSettings(details,this::render);
        });
        activity.openSettings(page,this::render);
    }
    /** Modal reader leaves the back page and its scroll position intact. */
    private void readComic(String name,String url,File image){
        android.app.Dialog dialog=new android.app.Dialog(activity,android.R.style.Theme_Material_NoActionBar);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(style.paper);
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(12),dp(8));
        bar.addView(style.action("‹ Back to Leisure",dialog::dismiss),new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams linkParams=new LinearLayout.LayoutParams(-2,dp(48));linkParams.leftMargin=dp(8);
        bar.addView(style.action("Website",()->open(url)),linkParams);layout.addView(bar);
        TextView title=style.text(name,18,true,style.ink);title.setPadding(dp(16),0,dp(16),dp(8));layout.addView(title);
        android.webkit.WebView reader=new android.webkit.WebView(activity);
        reader.setBackgroundColor(style.paper);
        reader.getSettings().setAllowFileAccess(false);
        reader.getSettings().setAllowContentAccess(false);
        reader.getSettings().setBuiltInZoomControls(true);
        reader.getSettings().setDisplayZoomControls(false);
        reader.getSettings().setSupportZoom(true);
        reader.getSettings().setUseWideViewPort(true);
        reader.getSettings().setLoadWithOverviewMode(true);
        reader.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        reader.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest req){
                String scheme=req.getUrl().getScheme();return !"https".equals(scheme)&&!"http".equals(scheme);
            }
        });
        layout.addView(reader,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(layout);
        dialog.setOnDismissListener(d->{reader.stopLoading();reader.destroy();});
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setLayout(-1,-1);
            dialog.getWindow().setStatusBarColor(style.paper);
            dialog.getWindow().setNavigationBarColor(style.paper);
        }
        if(image!=null){
            try{
                String data=android.util.Base64.encodeToString(java.nio.file.Files.readAllBytes(image.toPath()),android.util.Base64.NO_WRAP);
                String bg=String.format(Locale.US,"#%06X",style.paper&0xffffff);
                reader.loadDataWithBaseURL("https://morningbrief.local/","<html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0;background:"+bg+"'><img style='display:block;width:100%;height:auto' src='data:image/png;base64,"+data+"'></body></html>","text/html","UTF-8",null);
                return;
            }catch(Exception ignored){}
        }
        reader.getSettings().setJavaScriptEnabled(true);
        reader.getSettings().setDomStorageEnabled(true);
        reader.loadUrl(url);
    }
    private void open(String url){
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(activity,"Unable to open link",Toast.LENGTH_SHORT).show();}
    }
}
