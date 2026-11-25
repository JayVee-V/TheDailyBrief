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
        new Game("worldle","Worldle","Teuteuf Games","Identify a country by its silhouette.","https://worldle.teuteuf.fr/",false),
        new Game("chess","Daily Chess Puzzle","Lichess","Find the best moves in a tactical position.","https://lichess.org/training/daily",false)
    };
    private final MainActivity activity;
    private final ReaderStyle style;
    private final LinearLayout screen;
    private boolean busy;
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
        screen.removeAllViews();
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);
        LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(16),dp(16),dp(28));
        TextView heading=style.text("THE BACK PAGE",28,true,style.ink);heading.setGravity(Gravity.CENTER);column.addView(heading);
        TextView date=style.text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy",Locale.US)).toUpperCase(Locale.US),12,false,style.muted);
        date.setGravity(Gravity.CENTER);date.setLetterSpacing(.08f);date.setPadding(0,dp(5),0,dp(12));column.addView(date);
        boolean comics=false;
        for(ComicEdition.Source s:ComicEdition.SOURCES)if(ComicEdition.enabled(activity,s))comics=true;
        if(comics||prefs().getBoolean("funnies_show_memes",true)){
            section(column,"FUNNIES & COMICS");
            if(busy){TextView t=style.text("Collecting the daily comics…",13,false,style.muted);t.setPadding(0,0,0,dp(12));column.addView(t);}
            String today=LocalDate.now().toString();
            for(ComicEdition.Source s:ComicEdition.SOURCES){
                if(!ComicEdition.enabled(activity,s))continue;
                JSONArray items=ComicEdition.items(activity,s);
                String status=ComicEdition.status(activity,s);
                if(items.length()==0){
                    String detail=status.isEmpty()?(busy?"Checking for new strips…":"No strip collected yet."):status;
                    column.addView(style.card("Comic strip",s.name,detail,false,"Visit comic",()->open(s.home),null,null));
                }else{
                    boolean cached=!today.equals(ComicEdition.edition(activity,s));
                    for(int i=0;i<items.length();i++){
                        JSONObject o=items.optJSONObject(i);if(o!=null)column.addView(comicCard(s,o,cached));
                    }
                }
            }
            if(prefs().getBoolean("funnies_show_memes",true))
                column.addView(style.card("Imgur · deafkitten","Daily meme collection","Optional collection of internet humor.",false,"Open",()->open("https://imgur.com/user/deafkitten/posts"),null,null));
        }
        boolean games=false;for(Game g:GAMES)if(enabled(g))games=true;
        if(games){
            section(column,"DAILY PUZZLES");
            for(Game g:GAMES)if(enabled(g))column.addView(style.card(g.provider,g.title,g.description,false,"Play",()->open(g.url),null,null));
        }
        if(!comics&&!games&&!prefs().getBoolean("funnies_show_memes",true)){
            TextView empty=style.text("Choose comics and puzzles in Settings.",16,false,style.muted);column.addView(empty);
        }
        scroll.addView(column);screen.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
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
                image.setOnClickListener(v->open(url));card.addView(image,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        TextView read=style.action("Read on original site",()->open(url));
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(44));action.topMargin=dp(12);card.addView(read,action);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);card.setLayoutParams(p);return card;
    }
    void showSettings(){
        new AlertDialog.Builder(activity).setTitle("Leisure settings")
            .setItems(new String[]{"Choose comics and puzzles",busy?"Collecting comics…":"Collect comics now"},(d,i)->{
                if(i==0)choose();else collect(true);
            }).setNegativeButton("Cancel",null).show();
    }
    private void choose(){
        LinearLayout column=new LinearLayout(activity);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18),dp(16),dp(18),dp(14));column.setBackgroundColor(style.paper);
        column.addView(style.text("Your back page",23,true,style.ink));
        TextView intro=style.text("Choose each comic and each puzzle individually.",14,false,style.muted);
        intro.setPadding(0,dp(7),0,dp(6));column.addView(intro);
        ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        section(list,"COMIC STRIPS");
        for(ComicEdition.Source s:ComicEdition.SOURCES)option(list,s.name,s.description,"comic_enabled_"+s.id,ComicEdition.enabled(activity,s));
        section(list,"MEMES");
        option(list,"deafkitten","Optional daily Imgur meme collection.","funnies_show_memes",prefs().getBoolean("funnies_show_memes",true));
        section(list,"DAILY PUZZLES");
        for(Game g:GAMES)option(list,g.title,g.description+" · "+g.provider,"puzzle_enabled_"+g.id,enabled(g));
        scroll.addView(list);column.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(activity).setView(column).setPositiveButton("Done",(d,w)->render()).create();
        dialog.setOnShowListener(d->{
            if(dialog.getWindow()!=null){
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(style.paper));
                dialog.getWindow().setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(24),
                    Math.round(activity.getResources().getDisplayMetrics().heightPixels*.82f));
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(style.ink);
        });dialog.show();
    }
    private void option(LinearLayout list,String title,String desc,String key,boolean checked){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(8));
        LinearLayout copy=new LinearLayout(activity);copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(style.text(title,17,true,style.ink));TextView detail=style.text(desc,13,false,style.muted);detail.setPadding(0,dp(3),0,0);copy.addView(detail);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox check=new CheckBox(activity);check.setChecked(checked);check.setContentDescription(title);
        check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{style.ink,style.muted}));
        check.setOnCheckedChangeListener((b,on)->prefs().edit().putBoolean(key,on).apply());
        row.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));copy.setOnClickListener(v->check.setChecked(!check.isChecked()));list.addView(row);
    }
    private void open(String url){
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(activity,"Unable to open link",Toast.LENGTH_SHORT).show();}
    }
}
