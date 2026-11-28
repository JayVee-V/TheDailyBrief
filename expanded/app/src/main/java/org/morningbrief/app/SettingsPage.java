package org.morningbrief.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A common scrollable configuration page, hosted under the permanent masthead. */
final class SettingsPage {
    final ReaderStyle style;
    final ScrollView view;
    final LinearLayout body;
    SettingsPage(Context context, ReaderStyle style) {
        this.style=style;
        view=new ScrollView(context);view.setFillViewport(true);view.setBackgroundColor(style.paper);
        body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(style.dp(16),style.dp(8),style.dp(16),style.dp(28));
        view.addView(body);
    }
    void section(String name,String description) {
        TextView title=style.text(name,21,true,style.ink);
        title.setPadding(0,style.dp(14),0,style.dp(6));body.addView(title);
        if(description!=null&&!description.isEmpty()){
            TextView info=style.text(description,14,false,style.muted);
            info.setPadding(0,0,0,style.dp(10));body.addView(info);
        }
    }
    void action(String label,Runnable run){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,style.dp(48));
        p.topMargin=style.dp(8);body.addView(style.action(label,run),p);
    }
    void choice(String name,String description,boolean checked,java.util.function.Consumer<Boolean> changed){
        Context c=style.context;
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(style.dp(12),style.dp(9),style.dp(4),style.dp(9));
        row.setBackground(style.surface(style.panel,10,false));
        LinearLayout copy=new LinearLayout(c);copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(style.text(name,17,true,style.ink));
        TextView detail=style.text(description,13,false,style.muted);detail.setPadding(0,style.dp(4),0,0);copy.addView(detail);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        CheckBox check=new CheckBox(c);check.setContentDescription(name);check.setChecked(checked);
        check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{style.ink,style.muted}));
        check.setOnCheckedChangeListener((b,on)->changed.accept(on));
        row.addView(check,new LinearLayout.LayoutParams(style.dp(48),style.dp(48)));
        copy.setOnClickListener(v->check.setChecked(!check.isChecked()));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=style.dp(6);
        body.addView(row,p);
    }
}
