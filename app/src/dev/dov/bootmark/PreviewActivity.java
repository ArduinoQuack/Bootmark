package dev.dov.bootmark;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

public final class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().getDecorView().post(()->{
            WindowInsetsController ctl=getWindow().getInsetsController();
            if(ctl!=null){ctl.hide(WindowInsets.Type.systemBars());ctl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        });
        String requested=getIntent().getStringExtra("theme");
        String theme=Engine.CUSTOM.equals(requested)?Engine.CUSTOM:Engine.KERNEL;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        ImageView image=new ImageView(this);image.setImageBitmap(Ui.bitmap(this,theme,true));image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(Engine.themeName(theme)+" boot-screen preview");
        root.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Engine engine=((BootmarkApp)getApplication()).engine();
        if(Engine.CUSTOM.equals(theme)&&engine.customReady()&&engine.prefs.getBoolean("custom_gif",false)){
            try{root.addView(new GifView(this,new GifFrames(new java.io.File(engine.customDir(),"source"))),new FrameLayout.LayoutParams(-1,-1));}
            catch(java.io.IOException|OutOfMemoryError error){Toast.makeText(this,"Animated preview unavailable; showing the first frame.",Toast.LENGTH_LONG).show();}
        }
        Button close=Ui.button(this,"Close preview",Color.WHITE,0xd922252c,0xff3b3f49);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,Ui.dp(this,48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        p.bottomMargin=Ui.dp(this,34);root.addView(close,p);close.setOnClickListener(v->finish());
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            p.bottomMargin=Math.max(Ui.dp(this,28),safe.bottom+Ui.dp(this,12));close.setLayoutParams(p);return insets;
        });
        setContentView(root);
    }
    static final class GifView extends View {
        final GifFrames animation;final android.graphics.Bitmap bitmap;
        final long started=android.os.SystemClock.uptimeMillis();
        boolean failed;
        GifView(android.content.Context c,GifFrames frames){super(c);animation=frames;
            bitmap=android.graphics.Bitmap.createBitmap(frames.width,frames.height,android.graphics.Bitmap.Config.ARGB_8888);
            setContentDescription("Animated custom boot-screen preview");}
        @Override protected void onDraw(android.graphics.Canvas c){
            c.drawColor(Color.BLACK);
            try{if(!failed)bitmap.setPixels(animation.at((int)((android.os.SystemClock.uptimeMillis()-started)%animation.duration)),0,animation.width,0,0,animation.width,animation.height);}
            catch(java.io.IOException error){failed=true;}
            CustomArtwork.drawBitmap(c,bitmap,getWidth(),getHeight());
            if(!failed&&isAttachedToWindow())postInvalidateDelayed(83);
        }
    }
}
