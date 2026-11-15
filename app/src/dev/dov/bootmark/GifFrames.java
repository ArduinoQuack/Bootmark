package dev.dov.bootmark;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Bounded GIF87a/89a reader: LZW, interlace, palettes, transparency, disposal. */
final class GifFrames {
    static final class Frame {int x,y,w,h,delay=100,disposal,transparent=-1;boolean interlaced;int[] palette;int minCode;byte[] data;}
    final List<Frame> frames=new ArrayList<>();
    int width,height,duration,background,shown=-1;
    int[] pixels,restore;
    byte[] source;int position;
    GifFrames(File file)throws IOException{
        source=Files.readAllBytes(file.toPath());if(source.length>20*1024*1024)throw new IOException("Choose a GIF smaller than 20 MB.");
        if(source.length<13||source[0]!='G'||source[1]!='I'||source[2]!='F')throw bad();
        position=6;width=u16();height=u16();
        if(width<1||height<1||width>4096||height>4096||(long)width*height>5000000)throw new IOException("GIF dimensions must be at most 5 megapixels, with each side under 4097 pixels.");
        int packed=u8(),bg=u8();u8();int[] global=(packed&128)!=0?palette(2<<(packed&7)):null;
        background=global!=null&&bg<global.length?global[bg]:0;
        int disposal=0,transparent=-1,delay=100;
        while(position<source.length){
            int kind=u8();if(kind==0x3b)break;
            if(kind==0x21){
                int extension=u8();
                if(extension==0xf9){
                    if(u8()!=4)throw bad();int flags=u8();int cs=u16();int index=u8();if(u8()!=0)throw bad();
                    disposal=(flags>>2)&7;if(disposal>3)disposal=1;transparent=(flags&1)!=0?index:-1;
                    delay=cs==0?100:Math.max(20,cs*10);
                }else blocks(false);
            }else if(kind==0x2c){
                Frame f=new Frame();f.x=u16();f.y=u16();f.w=u16();f.h=u16();
                if(f.w<1||f.h<1||f.x+f.w>width||f.y+f.h>height)throw bad();
                int flags=u8();f.interlaced=(flags&64)!=0;
                f.palette=(flags&128)!=0?palette(2<<(flags&7)):global;if(f.palette==null)throw bad();
                f.minCode=u8();if(f.minCode<2||f.minCode>8)throw bad();f.data=blocks(true);
                f.disposal=disposal;f.transparent=transparent;f.delay=delay;
                frames.add(f);duration+=delay;
                if(frames.size()>300||duration>6000)throw new IOException("Use a GIF up to 6 seconds long. Trim this GIF and import it again.");
                disposal=0;transparent=-1;delay=100;
            }else throw bad();
        }
        if(frames.isEmpty())throw bad();source=null;
        pixels=new int[width*height];reset();
    }
    int[] at(int milliseconds)throws IOException{
        int time=Math.max(0,Math.min(duration-1,milliseconds)),end=0,target=0;
        for(int i=0;i<frames.size();i++){end+=frames.get(i).delay;if(time<end){target=i;break;}}
        if(target<shown)reset();
        while(shown<target){
            if(shown>=0){Frame old=frames.get(shown);
                if(old.disposal==2){int color=old.transparent>=0?0:background;for(int y=0;y<old.h;y++)Arrays.fill(pixels,(old.y+y)*width+old.x,(old.y+y)*width+old.x+old.w,color);}
                else if(old.disposal==3&&restore!=null)System.arraycopy(restore,0,pixels,0,pixels.length);
            }
            Frame f=frames.get(++shown);
            if(f.disposal==3){if(restore==null)restore=new int[pixels.length];System.arraycopy(pixels,0,restore,0,pixels.length);}
            byte[] indices=decode(f);int row=0;
            if(f.interlaced){int[] starts={0,4,2,1},steps={8,8,4,2};for(int pass=0;pass<4;pass++)for(int y=starts[pass];y<f.h;y+=steps[pass])drawRow(f,indices,row++,y);}
            else for(int y=0;y<f.h;y++)drawRow(f,indices,y,y);
        }
        return pixels;
    }
    void reset(){shown=-1;Arrays.fill(pixels,frames.get(0).transparent>=0?0:background);}
    void drawRow(Frame f,byte[] data,int srcRow,int dstRow)throws IOException{
        for(int x=0;x<f.w;x++){int color=data[srcRow*f.w+x]&255;if(color==f.transparent)continue;if(color>=f.palette.length)throw bad();pixels[(f.y+dstRow)*width+f.x+x]=f.palette[color];}
    }
    static byte[] decode(Frame f)throws IOException{
        int clear=1<<f.minCode,end=clear+1,available=clear+2,size=f.minCode+1,old=-1,first=0,bit=0,out=0;
        byte[] output=new byte[f.w*f.h],suffix=new byte[4096],stack=new byte[4097];short[] prefix=new short[4096];
        for(int i=0;i<clear;i++)suffix[i]=(byte)i;
        while(out<output.length){
            if(bit+size>f.data.length*8)throw bad();int code=0;
            for(int i=0;i<size;i++,bit++)code|=((f.data[bit>>>3]>>>(bit&7))&1)<<i;
            if(code==clear){available=clear+2;size=f.minCode+1;old=-1;continue;}
            if(code==end)break;
            if(old<0){if(code>=clear)throw bad();output[out++]=(byte)code;first=code;old=code;continue;}
            int input=code,top=0;
            if(code==available){stack[top++]=(byte)first;code=old;}else if(code>available)throw bad();
            while(code>=clear){if(code>=available||top>=4096)throw bad();stack[top++]=suffix[code];code=prefix[code]&65535;}
            first=suffix[code]&255;stack[top++]=(byte)first;
            while(top>0){if(out>=output.length)throw bad();output[out++]=stack[--top];}
            if(available<4096){prefix[available]=(short)old;suffix[available++]=(byte)first;if(available==(1<<size)&&size<12)size++;}
            old=input;
        }
        if(out!=output.length)throw bad();return output;
    }
    int u8()throws IOException{if(position>=source.length)throw bad();return source[position++]&255;}
    int u16()throws IOException{return u8()|(u8()<<8);}
    int[] palette(int n)throws IOException{int[] p=new int[n];for(int i=0;i<n;i++)p[i]=0xff000000|(u8()<<16)|(u8()<<8)|u8();return p;}
    byte[] blocks(boolean keep)throws IOException{
        ByteArrayOutputStream out=keep?new ByteArrayOutputStream():null;int n;
        while((n=u8())!=0){if(position+n>source.length)throw bad();if(keep)out.write(source,position,n);position+=n;}
        return keep?out.toByteArray():null;
    }
    static IOException bad(){return new IOException("This GIF is incomplete or uses an unsupported layout. Export it as a standard GIF and try again.");}
}
