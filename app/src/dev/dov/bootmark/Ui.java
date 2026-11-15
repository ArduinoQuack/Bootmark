package dev.dov.bootmark;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.InputStream;

final class Ui {
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(int color,float radius,Context c){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;
    }
    static GradientDrawable outline(int color,int stroke,float radius,Context c){
        GradientDrawable d=shape(color,radius,c);d.setStroke(dp(c,1),stroke);return d;
    }
    static RippleDrawable ripple(int color,int line,Context c,float radius){
        return new RippleDrawable(ColorStateList.valueOf(0x226E8FEB),outline(color,line,radius,c),shape(Color.WHITE,radius,c));
    }
    static TextView text(Context c,String value,float size,int color,boolean medium){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setFontFeatureSettings("kern");t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        t.setLineSpacing(dp(c,3),1);return t;
    }
    static Button button(Context c,String text,int color,int bg,int line){
        Button b=new Button(c);b.setText(text);b.setTextColor(color);b.setTextSize(16);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",0));b.setMinHeight(dp(c,54));b.setMinimumHeight(dp(c,54));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));
        b.setBackground(ripple(bg,line,c,18));b.setStateListAnimator(null);return b;
    }
    static final class Glyph extends View {
        final String kind;int color;final Paint p=new Paint(3);
        Glyph(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float s=Math.min(getWidth(),getHeight())/24f;
            canvas.save();canvas.translate((getWidth()-s*24)/2,(getHeight()-s*24)/2);canvas.scale(s,s);
            p.setColor(color);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            if("app".equals(kind)){
                canvas.drawRoundRect(4,2,20,22,3,3,p);canvas.drawLine(9,9,9,15,p);
                canvas.drawLine(9,12,14,8,p);canvas.drawLine(9,12,15,16,p);canvas.drawLine(9,19,15,19,p);
            }else if("settings".equals(kind)){
                canvas.drawCircle(12,12,6.5f,p);canvas.drawCircle(12,12,2.3f,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9,p);}
            }else if("history".equals(kind)){
                canvas.drawArc(4,4,20,20,-70,295,false,p);canvas.drawLine(4,4,4,9,p);canvas.drawLine(4,9,9,9,p);
                canvas.drawLine(12,8,12,12,p);canvas.drawLine(12,12,15,14,p);
            }else if("check".equals(kind)){
                canvas.drawCircle(12,12,8,p);canvas.drawLine(8,12,11,15,p);canvas.drawLine(11,15,16,9,p);
            }else if("refresh".equals(kind)){
                canvas.drawArc(4,4,20,20,25,290,false,p);canvas.drawLine(20,4,20,9,p);canvas.drawLine(20,9,15,9,p);
            }else if("back".equals(kind)){
                canvas.drawLine(18,12,5,12,p);canvas.drawLine(5,12,11,6,p);canvas.drawLine(5,12,11,18,p);
            }else if("close".equals(kind)){
                canvas.drawLine(6,6,18,18,p);canvas.drawLine(6,18,18,6,p);
            }else if("export".equals(kind)){
                canvas.drawLine(12,3,12,15,p);canvas.drawLine(8,7,12,3,p);canvas.drawLine(12,3,16,7,p);
                canvas.drawLine(5,13,5,20,p);canvas.drawLine(5,20,19,20,p);canvas.drawLine(19,20,19,13,p);
            }else {
                canvas.drawCircle(12,12,8,p);canvas.drawLine(12,8,12,12,p);canvas.drawPoint(12,16,p);
            }
            canvas.restore();
        }
    }
    static Bitmap bitmap(Context c,String theme,boolean full){
        if(Engine.CUSTOM.equals(theme)){
            Engine engine=((BootmarkApp)c.getApplicationContext()).engine();java.io.File dir=engine.customDir();
            if(dir==null)return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=full?1:2;
            return BitmapFactory.decodeFile(new java.io.File(dir,"poster.png").getAbsolutePath(),options);
        }
        String asset="kernelsu.png";
        try(InputStream input=c.getAssets().open(asset)){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=full?1:2;
            return BitmapFactory.decodeStream(input,null,o);
        }catch(Exception e){return null;}
    }
    static final class Phone extends View {
        final Bitmap bitmap;final Paint p=new Paint(3);final RectF frame=new RectF(),image=new RectF();
        Phone(Context c,Bitmap bitmap,String label){super(c);this.bitmap=bitmap;setContentDescription(label+". Tap for full-screen preview.");setFocusable(true);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float h=getHeight()-dp(getContext(),22),w=h*1440/3120f;
            float x=(getWidth()-w)/2,y=dp(getContext(),11);
            frame.set(x,y,x+w,y+h);p.setStyle(Paint.Style.FILL);p.setColor(0xff303744);
            c.drawRoundRect(frame,dp(getContext(),24),dp(getContext(),24),p);
            image.set(x+dp(getContext(),3),y+dp(getContext(),3),x+w-dp(getContext(),3),y+h-dp(getContext(),3));
            p.setColor(Color.BLACK);c.drawRoundRect(image,dp(getContext(),22),dp(getContext(),22),p);
            if(bitmap!=null){Path clip=new Path();clip.addRoundRect(image,dp(getContext(),22),dp(getContext(),22),Path.Direction.CW);c.save();c.clipPath(clip);c.drawBitmap(bitmap,null,image,p);c.restore();}
            p.setColor(0xff24262c);c.drawCircle(x+w/2,y+dp(getContext(),10),dp(getContext(),2.4f),p);
        }
    }
}
