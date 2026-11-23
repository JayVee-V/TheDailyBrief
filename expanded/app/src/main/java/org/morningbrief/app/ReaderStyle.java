package org.morningbrief.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native card palette, type and actions; Local uses the matching CSS. */
final class ReaderStyle {
    final Context context;
    final int paper,ink,muted,panel,rule;
    ReaderStyle(Context c,boolean dark){
        context=c;paper=dark?0xff10110f:0xfffafaf6;
        ink=dark?0xfff0efe8:0xff111111;muted=dark?0xffb8b8b0:0xff585852;
        panel=dark?0xff252725:0xffefefe9;rule=dark?0xff44453f:0xffd8d8cf;
    }
    int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    TextView text(String value,int size,boolean serif,int color){
        TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(serif)t.setTypeface(Typeface.SERIF,Typeface.BOLD);
        return t;
    }
    GradientDrawable surface(int fill,int radius,boolean border){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));
        if(border)d.setStroke(dp(1),rule);return d;
    }
    TextView action(String label,Runnable run){
        TextView v=text(label,14,false,ink);
        v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),0,dp(12),0);v.setBackground(surface(paper,8,true));
        v.setOnClickListener(w->run.run());return v;
    }
    View card(String source,String title,String summary,boolean lead,String first,Runnable read,String second,Runnable other){
        LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(surface(panel,14,false));
        TextView eyebrow=text((lead?"TOP STORY · ":"")+source.toUpperCase(java.util.Locale.US),11,false,muted);
        eyebrow.setTypeface(Typeface.DEFAULT,Typeface.BOLD);eyebrow.setLetterSpacing(.08f);card.addView(eyebrow);
        TextView headline=text(title,lead?27:20,true,ink);headline.setPadding(0,dp(6),0,dp(8));card.addView(headline);
        if(summary!=null&&!summary.trim().isEmpty()){
            String brief=summary.length()> (lead?420:210)?summary.substring(0,lead?417:207)+"…":summary;
            TextView copy=text(brief,14,false,muted);copy.setLineSpacing(dp(2),1);copy.setPadding(0,0,0,dp(12));card.addView(copy);
        }
        LinearLayout actions=new LinearLayout(context);actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(action(first,read),new LinearLayout.LayoutParams(0,dp(44),1));
        if(second!=null&&other!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.leftMargin=dp(8);actions.addView(action(second,other),p);}
        card.addView(actions);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);card.setLayoutParams(p);return card;
    }
}
