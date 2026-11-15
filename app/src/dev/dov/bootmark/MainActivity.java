package dev.dov.bootmark;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.HashMap;

public final class MainActivity extends Activity implements Engine.Listener {
    Engine engine;
    LinearLayout shell,content;
    ScrollView scroll;
    boolean light;
    int bg,surface,outline,ink,muted,accent,onAccent;
    int topInset,bottomInset;
    final HashMap<String,Bitmap> images=new HashMap<>();

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        engine=((BootmarkApp)getApplication()).engine();
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        render();
        if(Engine.supported()&&!engine.busy)engine.connect();
    }
    @Override protected void onStart(){super.onStart();engine.listen(this);render();}
    @Override protected void onStop(){engine.unlisten(this);super.onStop();}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);render();}
    @Override public void changed(){
        if(isFinishing()||isDestroyed())return;
        if(!engine.notice.isEmpty()){
            Toast.makeText(this,engine.notice,Toast.LENGTH_SHORT).show();
            engine.notice="";
        }
        render();
    }
    int dp(float value){return Ui.dp(this,value);}
    void palette(){
        light=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        bg=Color.parseColor(light?"#F6F7FB":"#000000");
        surface=Color.parseColor(light?"#FFFFFF":"#14161B");
        outline=Color.parseColor(light?"#E1E5EE":"#292C34");
        ink=Color.parseColor(light?"#192032":"#F3F5FC");
        muted=Color.parseColor(light?"#646E82":"#A2AABD");
        accent=getColor(light?android.R.color.system_accent1_600:android.R.color.system_accent1_200);
        onAccent=getColor(light?android.R.color.system_accent1_0:android.R.color.system_accent1_800);
    }
    void styleSystemBars(){
        // PhoneWindow creates its decor during setContentView.
        WindowInsetsController ctl=getWindow().getInsetsController();
        if(ctl!=null)ctl.setSystemBarsAppearance(light?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }
    TextView text(String value,float size,int color,boolean medium){return Ui.text(this,value,size,color,medium);}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    void pad(View view,int horizontal,int top,int bottom){view.setPadding(dp(horizontal),dp(top),dp(horizontal),dp(bottom));}
    void render(){
        int oldY=scroll==null?0:scroll.getScrollY();palette();
        shell=column();shell.setBackgroundColor(bg);shell.setPadding(0,topInset,0,bottomInset);
        shell.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            topInset=i.top;bottomInset=i.bottom;shell.setPadding(i.left,i.top,i.right,i.bottom);return insets;
        });
        LinearLayout toolbar=row();pad(toolbar,24,14,14);
        FrameLayout mark=new FrameLayout(this);mark.setBackground(Ui.shape(surface,14,this));
        Ui.Glyph glyph=new Ui.Glyph(this,"app",accent);
        mark.addView(glyph,new FrameLayout.LayoutParams(dp(27),dp(27),Gravity.CENTER));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(43),dp(43));mp.setMarginEnd(dp(12));toolbar.addView(mark,mp);
        toolbar.addView(text("Bootmark",23,ink,true));add(shell,toolbar);
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=column();pad(content,24,12,24);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));home();
        setContentView(shell);styleSystemBars();shell.requestApplyInsets();scroll.post(()->scroll.scrollTo(0,oldY));
    }
    void home(){
        LinearLayout selector=row();selector.setBackground(Ui.outline(surface,outline,17,this));pad(selector,4,4,4);
        themeButton(selector,"Galaxy",Engine.KERNEL);themeButton(selector,"Custom",Engine.CUSTOM);add(content,selector);
        String selected=engine.selected();
        if(Engine.CUSTOM.equals(selected)){
            space(content,12);
            Button pick=Ui.button(this,engine.customReady()?"Change image or GIF":"Choose image or GIF",accent,surface,outline);
            pick.setEnabled(!engine.busy);pick.setOnClickListener(v->importPicker());add(content,pick);space(content,8);
            TextView help=text("1440 × 3120 · PNG, JPEG, WebP or GIF\nGIF: up to 6 seconds · 20 MB maximum",12,muted,false);add(content,help);
        }
        space(content,16);
        String cacheKey=Engine.CUSTOM.equals(selected)?selected+engine.prefs.getString("custom_sha",""):selected;
        if(Engine.CUSTOM.equals(selected)&&!images.containsKey(cacheKey))images.clear();
        Bitmap bitmap=images.get(cacheKey);
        if(bitmap==null){bitmap=Ui.bitmap(this,selected,false);if(bitmap!=null)images.put(cacheKey,bitmap);}
        FrameLayout hero=new FrameLayout(this);hero.setBackground(Ui.outline(surface,outline,27,this));
        Ui.Phone phone=new Ui.Phone(this,bitmap,Engine.themeName(selected));
        int height=Math.max(260,Math.min(380,Math.round(getResources().getConfiguration().screenHeightDp*.43f)));
        hero.addView(phone,new FrameLayout.LayoutParams(-1,dp(height)));
        phone.setOnClickListener(v->{if(Engine.CUSTOM.equals(selected)&&!engine.customReady())importPicker();else startActivity(new Intent(this,PreviewActivity.class).putExtra("theme",selected));});
        TextView preview=text("Preview",12,muted,false);preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);pp.setMargins(0,0,dp(18),dp(16));hero.addView(preview,pp);
        add(content,hero);space(content,18);
        String primary=Engine.CUSTOM.equals(selected)&&!engine.customReady()?"Choose image or GIF":"Apply";
        Button apply=Ui.button(this,primary,onAccent,accent,accent);
        apply.setEnabled(!engine.busy&&Engine.supported()&&!"foreign".equals(engine.active));
        if(!apply.isEnabled())apply.setAlpha(.45f);
        apply.setOnClickListener(v->{if(Engine.CUSTOM.equals(engine.selected())&&!engine.customReady())importPicker();else engine.apply();});add(content,apply);
        space(content,8);
        Button restore=Ui.button(this,"Restore original",muted,bg,Color.TRANSPARENT);
        restore.setEnabled(engine.root&&!engine.busy&&!"stock".equals(engine.active)&&!"foreign".equals(engine.active)&&!"unknown".equals(engine.active));
        restore.setAlpha(restore.isEnabled()?1:.45f);
        restore.setOnClickListener(v->dialog("Restore original?","Remove the custom boot screen?","Restore",engine::restore));add(content,restore);
        if(engine.busy){
            space(content,12);LinearLayout progress=row();progress.setGravity(Gravity.CENTER);
            ProgressBar spinner=new ProgressBar(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(20),dp(20));sp.setMarginEnd(dp(12));progress.addView(spinner,sp);
            progress.addView(text(engine.operation,13,muted,false));add(content,progress);
        }else if(!Engine.supported()){
            space(content,12);add(content,text("Requires SM-S926U on S926USQU5DZDR.",13,muted,false));
        }else if(!engine.message.isEmpty()){
            space(content,12);TextView error=text(engine.message,13,muted,false);error.setTextIsSelectable(true);add(content,error);
            if(!engine.lastOutput.isEmpty()){
                space(content,8);Button details=Ui.button(this,"Details",muted,bg,outline);details.setOnClickListener(v->details());add(content,details);
            }
        }
    }
    void themeButton(LinearLayout parent,String title,String key){
        boolean selected=key.equals(engine.selected());
        Button button=Ui.button(this,title,selected?onAccent:muted,selected?accent:Color.TRANSPARENT,Color.TRANSPARENT);
        button.setTextSize(14);button.setMinimumHeight(dp(46));button.setMinHeight(dp(46));button.setPadding(dp(8),dp(7),dp(8),dp(7));
        button.setSelected(selected);button.setEnabled(!engine.busy);button.setOnClickListener(v->engine.select(key));
        parent.addView(button,new LinearLayout.LayoutParams(0,dp(46),1));
    }
    AlertDialog.Builder builder(){return new AlertDialog.Builder(new ContextThemeWrapper(this,
        light?android.R.style.Theme_Material_Light_Dialog_Alert:android.R.style.Theme_Material_Dialog_Alert));}
    void styleDialog(AlertDialog dialog){
        dialog.setOnShowListener(x->{
            if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(Ui.shape(surface,26,this));
            for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE}){
                Button button=dialog.getButton(id);if(button!=null){button.setTextColor(accent);button.setAllCaps(false);}
            }
        });
    }
    void dialog(String title,String body,String positive,Runnable action){
        AlertDialog dialog=builder().setTitle(title).setMessage(body).setNegativeButton("Cancel",null).setPositiveButton(positive,(x,w)->action.run()).create();
        styleDialog(dialog);dialog.show();
    }
    void details(){
        TextView log=text(engine.lastOutput,12,ink,false);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);pad(log,24,12,12);
        ScrollView wrap=new ScrollView(this);wrap.addView(log);
        AlertDialog dialog=builder().setTitle("Details").setView(wrap).setPositiveButton("Close",null).create();styleDialog(dialog);dialog.show();
    }
    void importPicker(){
        if(engine.busy)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,92);}catch(ActivityNotFoundException e){Toast.makeText(this,"No file picker available.",Toast.LENGTH_LONG).show();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==92&&result==RESULT_OK&&data!=null&&data.getData()!=null)engine.importArtwork(data.getData());
    }
}
