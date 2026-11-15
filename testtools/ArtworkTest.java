package dev.dov.bootmark;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=BootmarkApp.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ArtworkTest {
 static File root=new File("testtools");
 @Test public void rootLaunchUsesShellAndAbsoluteSu(){
  String[] args=RootLauncher.command("/data/user/0/app/files/it's safe.sh","status");
  assertEquals("/system/bin/sh",args[0]);assertEquals("-c",args[1]);
  assertTrue(args[2].startsWith("exec /system/bin/su -c "));
  try{RootLauncher.command("/file","repair;reboot");fail("Invalid mode accepted");}catch(IllegalArgumentException expected){}
 }
 @Test public void pngImportsAtNativeSizeWithBlackMargins()throws Exception{
  Context c=RuntimeEnvironment.getApplication();File dir=new File(c.getFilesDir(),"import-test-png");CustomArtwork.remove(dir);
  CustomArtwork a=new CustomArtwork(dir);a.prepare(c,Uri.fromFile(new File(root,"import-red.png")),(n,total)->{});
  Bitmap p=BitmapFactory.decodeFile(new File(dir,"poster.png").getAbsolutePath());
  assertEquals(1440,p.getWidth());assertEquals(3120,p.getHeight());
  assertEquals(Color.BLACK,p.getPixel(0,0));assertEquals(Color.RED,p.getPixel(720,1560));
  assertFalse(a.gif);assertEquals(64,a.hash.length());
  File save=new File(root,"prepared-static.sh");Files.copy(new File(dir,"installer.sh").toPath(),save.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
 }
 @Test public void gifImportsMultipleFrames()throws Exception{
  Context c=RuntimeEnvironment.getApplication();File dir=new File(c.getFilesDir(),"import-test-gif");CustomArtwork.remove(dir);
  CustomArtwork a=new CustomArtwork(dir);int[] frames={0};
  a.prepare(c,Uri.fromFile(new File(root,"import-motion.gif")),(n,total)->frames[0]=total);
  assertTrue(a.gif);assertTrue("Movie duration and animation must decode",a.label.contains("GIF"));
  assertTrue(frames[0]>=2);assertTrue(frames[0]<=72);
  Files.copy(new File(dir,"installer.sh").toPath(),new File(root,"prepared-gif.sh").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
 }
 @Test public void corruptImageFailsWithoutInstaller()throws Exception{
  Context c=RuntimeEnvironment.getApplication();File dir=new File(c.getFilesDir(),"import-test-bad");CustomArtwork.remove(dir);
  File bad=new File(root,"not-image.txt");Files.write(bad.toPath(),"not an actual image file".getBytes());
  try{new CustomArtwork(dir).prepare(c,Uri.fromFile(bad),(n,total)->{});fail("Corrupt image accepted");}catch(IOException expected){}
  assertFalse(new File(dir,"installer.sh").exists());
 }
}
