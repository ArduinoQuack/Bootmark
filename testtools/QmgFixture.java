package dev.dov.bootmark;
import java.io.*;
public final class QmgFixture {
 public static void main(String[] args)throws Exception{
  int w=Integer.parseInt(args[1]),h=Integer.parseInt(args[2]);short[] prev=new short[w*h],cur=new short[w*h];
  File dir=new File(args[0]);dir.mkdirs();
  try(QmgWriter q=new QmgWriter(new File(dir,"generated.qmg"),w,h,3,83)){
   for(int frame=0;frame<3;frame++){
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
     int v=frame==0?((x/19+y/23)%7==0?0x07e0:0):prev[y*w+x]&65535;
     if(frame==1&&(x<8||y>=h-8||x>=w-8))v=((x*29+y*31)&65535);
     if(frame==2&&x>=w/2&&x<w/2+8&&y<h/2)v=0xf800;
     cur[y*w+x]=(short)v;
    }
    q.frame(cur,frame==0?null:prev);
    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(new File(dir,"frame-"+frame+".raw")))){for(short p:cur){out.write(p&255);out.write((p>>>8)&255);}}
    short[] t=cur;cur=prev;prev=t;
   }
  }
 }
}
