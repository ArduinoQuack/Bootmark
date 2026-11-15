package dev.dov.bootmark;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.robolectric.Shadows;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=36, application=BootmarkApp.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LaunchTest {
 @Test public void coldLaunchCreatesDecorBeforeStylingBars() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  assertNotNull(activity.getWindow().peekDecorView());
  assertNotNull(find(activity.shell,"Bootmark"));
 }
 @Test public void removedControlsAndCopyStayAbsent() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  for(String label:new String[]{"Root connected","Applied: Galaxy","YOUR BOOT SCREEN","KernelSU","Samsung + Apple","Settings","Activity"})
   assertNull(label,find(activity.shell,label));
  assertNotNull(find(activity.shell,"Galaxy"));assertNotNull(find(activity.shell,"Custom"));
  assertNotNull(find(activity.shell,"Apply"));assertNotNull(find(activity.shell,"Restore original"));
 }
 @Test public void oldManualThemePreferenceDoesNotOverrideSystem() {
  RuntimeEnvironment.setQualifiers("w411dp-h891dp-night");
  Engine engine=((BootmarkApp)RuntimeEnvironment.getApplication()).engine();
  engine.prefs.edit().putString("appearance","light").commit();
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  assertFalse(activity.light);assertEquals(0xff000000,activity.bg);
  assertEquals(activity.getColor(android.R.color.system_accent1_200),activity.accent);
 }
 @Test public void themeChangesWhileActivityIsOpen() {
  RuntimeEnvironment.setQualifiers("w411dp-h891dp-notnight");
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  assertTrue(activity.light);
  RuntimeEnvironment.setQualifiers("w411dp-h891dp-night");
  activity.onConfigurationChanged(new Configuration(activity.getResources().getConfiguration()));
  assertFalse(activity.light);
  RuntimeEnvironment.setQualifiers("w411dp-h891dp-notnight");
  activity.onConfigurationChanged(new Configuration(activity.getResources().getConfiguration()));
  assertTrue(activity.light);
 }
 @Test public void removedPresetSelectionFallsBackWithoutApplying() {
  Engine engine=((BootmarkApp)RuntimeEnvironment.getApplication()).engine();
  engine.prefs.edit().putString("theme",Engine.FUSION).commit();
  engine.active=Engine.FUSION;
  assertEquals(Engine.KERNEL,engine.selected());
  assertFalse(engine.selectionApplied());
  engine.select(Engine.FUSION);
  assertEquals(Engine.KERNEL,engine.selected());assertFalse(engine.busy);
 }
 @Test public void customChoiceSurvivesRecreation() {
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
  MainActivity activity=controller.get();find(activity.shell,"Custom").performClick();
  Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(Engine.CUSTOM,activity.engine.selected());
  controller.recreate();assertEquals(Engine.CUSTOM,controller.get().engine.selected());
  assertNotNull(find(controller.get().shell,"Choose image or GIF"));
 }
 @Test public void galaxyPreviewOpensAndCloses() {
  Activity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  PreviewActivity preview=Robolectric.buildActivity(PreviewActivity.class,new Intent(activity,PreviewActivity.class).putExtra("theme",Engine.KERNEL)).setup().get();
  find((ViewGroup)preview.findViewById(android.R.id.content),"Close preview").performClick();assertTrue(preview.isFinishing());
 }
 @Test public void operationsDisableEditingWithoutStatusCard() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  activity.engine.busy=true;activity.engine.operation="Applying boot screen";activity.render();
  assertFalse(find(activity.shell,"Custom").isEnabled());assertFalse(find(activity.shell,"Apply").isEnabled());
  assertNotNull(find(activity.shell,"Applying boot screen"));
 }
 @Test public void largeTextStillUsesScrollableContent() {
  RuntimeEnvironment.setQualifiers("w320dp-h568dp-notnight");
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
  Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=1.6f;
  activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());activity.render();
  assertNotNull(activity.scroll);assertNotNull(find(activity.shell,"Restore original"));
 }
 static View find(ViewGroup parent,String label) {
  for(int i=0;i<parent.getChildCount();i++){
   View view=parent.getChildAt(i);
   if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
   if(label.equals(view.getContentDescription()))return view;
   if(view instanceof ViewGroup){View nested=find((ViewGroup)view,label);if(nested!=null)return nested;}
  }
  return null;
 }
}
