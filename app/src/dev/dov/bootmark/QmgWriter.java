package dev.dov.bootmark;

import java.io.*;

/** Boundary-safe RGB565 encoder. Every reference stays inside the image. */
final class QmgWriter implements Closeable {
    final RandomAccessFile out;
    final int width,height,frames,delay;
    int written;
    static final long MAX_BYTES=64L*1024*1024;
    QmgWriter(File file,int w,int h,int count,int delayMs) throws IOException {
        if(w<=0||h<=0||w%16!=0||h%16!=0||w>4096||h>4096||count<2||count>72)
            throw new IOException("Unsupported animation geometry.");
        width=w;height=h;frames=count;delay=delayMs;
        out=new RandomAccessFile(file,"rw");out.setLength(0);
    }
    void frame(short[] pixels,short[] previous) throws IOException {
        if(pixels.length!=width*height||written>=frames)throw new IOException("Invalid frame.");
        byte[] a,b,raw;
        Bits bits=new Bits(),secondary=new Bits();ByteArrayOutputStream literals=new ByteArrayOutputStream();
        if(written==0){
            for(int y=0;y<height;y+=4)for(int x=0;x<width;x+=4){
                boolean edge=x>0;
                for(int j=0;j<4&&edge;j++)for(int i=0;i<4;i++)
                    if(pixels[(y+j)*width+x+i]!=pixels[(y+j)*width+x-1]){edge=false;break;}
                if(edge){bits.put(3,2);continue;}
                bits.put(0,2);int mask=0;
                for(int j=0;j<4;j++)for(int i=0;i<4;i++){
                    int p=(y+j)*width+x+i;
                    if(x+i>0&&pixels[p]==pixels[p-1])mask|=1<<(j*4+i);
                }
                le16(literals,mask);
                for(int j=0;j<4;j++)for(int i=0;i<4;i++)if((mask&(1<<(j*4+i)))==0){
                    secondary.put(7,3);le16(literals,pixels[(y+j)*width+x+i]&65535);
                }
            }
            a=bits.finish();b=secondary.finish();raw=aligned(literals);
        }else{
            if(previous==null||previous.length!=pixels.length)throw new IOException("Missing previous frame.");
            for(int y=0;y<height;y+=16)for(int x=0;x<width;x+=16){
                if(equal(pixels,previous,x,y,16)){bits.put(3,2);continue;}
                bits.put(5,3); // 1,0,1: subdivide with zero motion vector.
                for(int j=0;j<16;j+=4)for(int i=0;i<16;i+=4){
                    if(equal(pixels,previous,x+i,y+j,4)){bits.put(5,3);continue;}
                    bits.put(4,3); // compare each pixel to its in-bounds previous position.
                    for(int v=0;v<4;v++)for(int u=0;u<4;u++){
                        int p=(y+j+v)*width+x+i+u;
                        if(pixels[p]==previous[p])bits.put(1,1);
                        else{bits.put(7,4);le16(literals,pixels[p]&65535);}
                    }
                }
            }
            a=bits.finish();b=new byte[0];raw=aligned(literals);
            if(raw.length==0)raw=new byte[4];
        }
        int size=32+a.length+b.length+raw.length;
        if(out.length()+size>MAX_BYTES)throw new IOException("This animation is too detailed. Use a shorter GIF or simpler artwork (64 MB output limit).");
        out.write('Q');out.write('M');out.write(new byte[]{11,0,(byte)128,0});
        le16(out,width);le16(out,height);out.write(0);out.write(0);le32(out,size);
        le16(out,frames);le16(out,++written);le16(out,delay);out.write(1);out.write(0);
        le32(out,32+a.length);le32(out,written==1?32+a.length+b.length:0);
        out.write(a);out.write(b);out.write(raw);
    }
    boolean equal(short[] a,short[] b,int x,int y,int n){
        for(int j=0;j<n;j++)for(int i=0;i<n;i++)if(a[(y+j)*width+x+i]!=b[(y+j)*width+x+i])return false;
        return true;
    }
    static byte[] aligned(ByteArrayOutputStream b){while(b.size()%4!=0)b.write(0);return b.toByteArray();}
    static void le16(OutputStream s,int v)throws IOException{s.write(v&255);s.write((v>>>8)&255);}
    static void le16(RandomAccessFile s,int v)throws IOException{s.write(v&255);s.write((v>>>8)&255);}
    static void le32(RandomAccessFile s,int v)throws IOException{le16(s,v);le16(s,v>>>16);}
    public void close()throws IOException{out.close();}
    static final class Bits {
        final ByteArrayOutputStream data=new ByteArrayOutputStream();int value,used;
        void put(int v,int n){for(int i=n-1;i>=0;i--){value=(value<<1)|((v>>>i)&1);if(++used==8){data.write(value);used=0;value=0;}}}
        byte[] finish(){if(used!=0){data.write(value<<(8-used));used=0;value=0;}while(data.size()%4!=0)data.write(0);return data.toByteArray();}
    }
}
