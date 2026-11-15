package dev.dov.bootmark;
import java.io.*;
import java.util.*;
public final class GifFixture {
 public static void main(String[] args)throws Exception{
  File input=new File(args[0]),out=new File(args[1]);out.mkdirs();GifFrames gif=new GifFrames(input);int time=0;int[] first=null;
  for(int f=0;f<gif.frames.size();f++){
   int[] pixels=gif.at(time);if(f==0)first=pixels.clone();
   byte[] rgb=new byte[pixels.length*3];for(int i=0;i<pixels.length;i++){rgb[i*3]=(byte)(pixels[i]>>16);rgb[i*3+1]=(byte)(pixels[i]>>8);rgb[i*3+2]=(byte)pixels[i];}
   try(OutputStream s=new FileOutputStream(new File(out,"frame-"+f+".rgb"))){s.write(rgb);}
   time+=gif.frames.get(f).delay;
  }
  if(!Arrays.equals(first,gif.at(0)))throw new AssertionError("Loop reset changed pixels");
  System.out.println(gif.width+"x"+gif.height+" frames="+gif.frames.size()+" duration="+gif.duration);
 }
}
