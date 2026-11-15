package dev.dov.bootmark;

import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import android.util.Base64;
import android.util.Base64OutputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.GZIPOutputStream;

final class CustomArtwork {
    static final int WIDTH=1440,HEIGHT=3120,FPS=12,MAX_DURATION=6000;
    interface Progress { void frame(int current,int count); }
    final File dir;
    String hash,label;
    boolean gif;
    CustomArtwork(File directory){dir=directory;}
    void prepare(Context c,Uri uri,Progress progress)throws Exception{
        if(!dir.mkdirs())throw new IOException("Could not prepare the import directory.");
        File source=new File(dir,"source");
        try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(source)){
            if(in==null)throw new IOException("The selected file could not be opened.");
            byte[] block=new byte[32768];int n;long size=0;
            while((n=in.read(block))!=-1){size+=n;if(size>20L*1024*1024)throw new IOException("Choose a file smaller than 20 MB.");out.write(block,0,n);}
            out.getFD().sync();
        }
        byte[] prefix=new byte[10];try(FileInputStream in=new FileInputStream(source)){if(in.read(prefix)<10)throw new IOException("That file is not a supported image.");}
        gif=prefix[0]=='G'&&prefix[1]=='I'&&prefix[2]=='F'&&prefix[3]=='8'&&(prefix[4]=='7'||prefix[4]=='9')&&prefix[5]=='a';
        GifFrames animation=null;Bitmap image=null;int duration=0;
        if(gif){
            animation=new GifFrames(source);duration=animation.duration;
            image=Bitmap.createBitmap(animation.width,animation.height,Bitmap.Config.ARGB_8888);
        }else{
            image=ImageDecoder.decodeBitmap(ImageDecoder.createSource(source),(decoder,info,src)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();
                float scale=Math.min(1f,Math.min((float)WIDTH/w,(float)HEIGHT/h));
                decoder.setTargetSize(Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        int count=gif&&duration>0?Math.max(2,(int)Math.ceil(duration*FPS/1000.0)):24;
        int delay=gif&&duration>0?Math.max(1,Math.round((float)duration/count)):83;
        Bitmap canvasImage=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        int[] argb=new int[WIDTH*HEIGHT];short[] current=new short[argb.length],previous=new short[argb.length];
        try(QmgWriter writer=new QmgWriter(new File(dir,"artwork.qmg"),WIDTH,HEIGHT,count,delay)){
            for(int frame=0;frame<count;frame++){
                Canvas canvas=new Canvas(canvasImage);canvas.drawColor(Color.BLACK);
                if(animation!=null)image.setPixels(animation.at(Math.min(duration-1,frame*delay)),0,animation.width,0,0,animation.width,animation.height);
                drawBitmap(canvas,image,WIDTH,HEIGHT);
                if(frame==0)try(FileOutputStream out=new FileOutputStream(new File(dir,"poster.png"))){canvasImage.compress(Bitmap.CompressFormat.PNG,100,out);}
                canvasImage.getPixels(argb,0,WIDTH,0,0,WIDTH,HEIGHT);
                for(int i=0;i<argb.length;i++){int p=argb[i];current[i]=(short)(((p>>8)&0xf800)|((p>>5)&0x7e0)|((p>>3)&31));}
                writer.frame(current,frame==0?null:previous);short[] swap=previous;previous=current;current=swap;
                progress.frame(frame+1,count);
            }
        }finally{canvasImage.recycle();if(image!=null)image.recycle();}
        File qmg=new File(dir,"artwork.qmg"),gz=new File(dir,"artwork.qmg.gz");
        hash=sha(qmg);
        try(FileInputStream in=new FileInputStream(qmg);GZIPOutputStream out=new GZIPOutputStream(new FileOutputStream(gz))){copy(in,out);}
        label="1440 × 3120 · "+(gif&&duration>0?"GIF · 12 fps · "+String.format(java.util.Locale.US,"%.1f s",duration/1000f):"Image · centered on black");
        String template;
        try(InputStream in=c.getAssets().open("custom-engine.template")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();copy(in,bytes);template=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
        template=template.replace("__ASSET_SHA__",hash).replace("__GZIP_SHA__",sha(gz));
        String token="__ASSET_BASE64__";int split=template.indexOf(token);
        if(split<0)throw new IOException("Custom installer template is unavailable.");
        try(FileOutputStream out=new FileOutputStream(new File(dir,"installer.sh"))){
            out.write(template.substring(0,split).getBytes(StandardCharsets.UTF_8));
            Base64OutputStream encoded=new Base64OutputStream(out,Base64.DEFAULT|Base64.NO_CLOSE);
            try(FileInputStream in=new FileInputStream(gz)){copy(in,encoded);}encoded.close();
            out.write(template.substring(split+token.length()).getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
        gz.delete();qmg.delete();
    }
    static void drawBitmap(Canvas c,Bitmap b,int w,int h){
        float scale=Math.min((float)w/b.getWidth(),(float)h/b.getHeight());
        float dw=b.getWidth()*scale,dh=b.getHeight()*scale;
        c.drawBitmap(b,null,new RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),new Paint(3));
    }
    static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    static String sha(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static void remove(File f){if(f==null)return;if(f.isDirectory()){File[] files=f.listFiles();if(files!=null)for(File p:files)remove(p);}f.delete();}
}
