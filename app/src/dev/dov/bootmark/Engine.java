package dev.dov.bootmark;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;

final class Engine {
    interface Listener { void changed(); }
    static final String FINGERPRINT = "samsung/e2qsqw/e2q:16/BP4A.251205.006/S926USQU5DZDR:user/release-keys";
    static final String KERNEL = "kernelsu";
    static final String FUSION = "fusion";
    static final String CUSTOM = "custom";
    final Context context;
    final SharedPreferences prefs;
    final Handler main = new Handler(Looper.getMainLooper());
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    volatile boolean busy;
    volatile boolean root;
    volatile String active = "unknown";
    volatile String activeHash = "";
    volatile String message = "";
    volatile String notice = "";
    volatile String operation = "";
    volatile String lastOutput = "";
    private WeakReference<Listener> listener = new WeakReference<>(null);

    Engine(Context c) { context=c.getApplicationContext();prefs=context.getSharedPreferences("bootmark",0); }
    static boolean supported() { return "SM-S926U".equals(Build.MODEL) && FINGERPRINT.equals(Build.FINGERPRINT); }
    static String themeName(String theme) {
        if (KERNEL.equals(theme)) return "Galaxy";
        if (FUSION.equals(theme)) return "Previous preset";
        if (CUSTOM.equals(theme)) return "Your image";
        if ("stock".equals(theme)) return "Original Samsung";
        if ("legacy".equals(theme)) return "Earlier custom screen";
        if ("foreign".equals(theme)) return "Other customization";
        return "Not checked";
    }
    void listen(Listener value) { listener = new WeakReference<>(value); }
    void unlisten(Listener value) { if(listener.get()==value)listener.clear(); }
    void notifyUi() { main.post(() -> { Listener value=listener.get();if(value!=null)value.changed(); }); }
    void connect() { submit("Checking root", "status", selected()); }
    String selected() { return CUSTOM.equals(prefs.getString("theme", KERNEL)) ? CUSTOM : KERNEL; }
    void select(String value) {
        if(!KERNEL.equals(value)&&!CUSTOM.equals(value))return;
        prefs.edit().putString("theme",value).apply();notifyUi();
    }
    void apply() { submit("Applying boot screen", "repair", selected()); }
    void restore() { submit("Restoring Samsung", "restore", selected()); }
    File customDir(){
        String name=prefs.getString("custom_dir","");
        if(!name.matches("import-[0-9a-f-]+"))return null;
        return new File(context.getFilesDir(),name);
    }
    boolean customReady(){File d=customDir();return d!=null&&new File(d,"installer.sh").isFile()&&new File(d,"poster.png").isFile();}
    boolean selectionApplied(){return selected().equals(active)&&(!CUSTOM.equals(selected())||activeHash.equals(prefs.getString("custom_sha","")));}
    void importArtwork(final android.net.Uri uri){
        synchronized(this){if(busy)return;busy=true;operation="Preparing your artwork";message=operation;notifyUi();}
        executor.execute(()->{
            File folder=new File(context.getFilesDir(),"import-"+java.util.UUID.randomUUID().toString());
            try{
                CustomArtwork artwork=new CustomArtwork(folder);
                artwork.prepare(context,uri,(frame,count)->{operation="Preparing frame "+frame+" of "+count;notifyUi();});
                File old=customDir();
                if(!prefs.edit().putString("custom_dir",folder.getName()).putString("custom_sha",artwork.hash)
                        .putString("custom_label",artwork.label).putBoolean("custom_gif",artwork.gif)
                        .putString("theme",CUSTOM).commit())throw new java.io.IOException("Could not save the imported artwork.");
                CustomArtwork.remove(old);
                message="";
            }catch(Exception|OutOfMemoryError e){
                CustomArtwork.remove(folder);
                message=e instanceof OutOfMemoryError?"This file needs too much memory. Use a smaller image or simpler GIF.":e.getMessage();
                if(message==null)message="This image could not be prepared. Try a PNG, JPEG, WebP, or GIF.";
                record("Import artwork","error",message,"");
            }finally{busy=false;operation="";notifyUi();}
        });
    }
    private synchronized void submit(final String title, final String command, final String theme) {
        if(busy)return;
        if(!supported()) { message="This build is for your SM-S926U on S926USQU5DZDR. Device checks prevent changes here.";notifyUi();return; }
        busy=true;operation=title;lastOutput="";message=title+"…";notifyUi();
        executor.execute(() -> {
            try {
                File script=CUSTOM.equals(theme)&&"repair".equals(command)?(customReady()?new File(customDir(),"installer.sh"):null):extract(theme);
                if(script==null)throw new Exception("Import an image or GIF first.");
                Result result=runRoot(script,command);
                lastOutput=result.output;
                if(result.timedOut)throw new Exception("Root request timed out. If a permission prompt is open, allow Bootmark in KernelSU and try again.");
                if(result.code!=0)throw new Exception(friendly(result.output));
                if(!result.output.contains("BOOTMARK_ROOT=1"))throw new Exception("Root was not confirmed. Allow Bootmark in KernelSU, then connect again.");
                root=true;
                String detected=value(result.output,"BOOTMARK_THEME");
                if(!detected.isEmpty())active=detected;
                activeHash=value(result.output,"BOOTMARK_ASSET");
                if("repair".equals(command)) {
                    if(!theme.equals(active))throw new Exception("The applied screen could not be verified. See Details.");
                    if(CUSTOM.equals(theme)&&!activeHash.equals(prefs.getString("custom_sha","")))throw new Exception("The installed artwork hash did not match your imported file.");
                    message="";notice="Applied for the next restart.";
                    prefs.edit().putString("last_applied",theme).putLong("applied_at",System.currentTimeMillis()).apply();
                } else if("restore".equals(command)) {
                    if(!"stock".equals(active))throw new Exception("Restore could not be verified. See Details.");
                    message="";notice="Original restored.";
                } else message="";
                prefs.edit().putBoolean("connected_once",true).apply();
                if(!"status".equals(command))record(title,"success",message,result.output);
            } catch(Exception e) {
                message=e.getMessage()==null?"The operation could not complete.":e.getMessage();
                if("status".equals(command)||!lastOutput.contains("BOOTMARK_ROOT=1"))root=false;
                record(title,"error",message,lastOutput);
            } finally { busy=false;operation="";notifyUi(); }
        });
    }
    private File extract(String theme) throws Exception {
        String name="kernelsu-engine.sh";
        File dir=new File(context.getFilesDir(),"engine");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new Exception("Cannot prepare the private installer directory.");
        File file=new File(dir,name);
        try(InputStream input=context.getAssets().open(name);FileOutputStream out=new FileOutputStream(file)) {
            byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();
        }
        file.setReadable(false,false);file.setWritable(false,false);
        file.setReadable(true,true);file.setWritable(true,true);
        return file;
    }
    private Result runRoot(File script,String command) throws Exception {
        // Both mode and script are fixed application choices; no external Intent
        // or user-entered shell text is accepted.
        if(!"repair".equals(command)&&!"restore".equals(command)&&!"status".equals(command))throw new Exception("Unknown operation.");
        Process process=new ProcessBuilder(RootLauncher.command(script.getAbsolutePath(),command))
                .redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(() -> {
            try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;while((line=input.readLine())!=null) {
                    synchronized(output){if(output.length()<65536)output.append(line).append('\n');}
                }
            }catch(Exception ignored){}
        },"Bootmark output");
        reader.setDaemon(true);reader.start();
        boolean finished=process.waitFor("status".equals(command)?25:60,TimeUnit.SECONDS);
        if(!finished) { process.destroy();if(!process.waitFor(1,TimeUnit.SECONDS))process.destroyForcibly(); }
        reader.join(1000);
        synchronized(output){return new Result(finished?process.exitValue():-1,output.toString(),!finished);}
    }
    static String value(String output,String key) {
        String prefix=key+"=";String result="";
        for(String line:output.split("\n"))if(line.startsWith(prefix))result=line.substring(prefix.length()).trim();
        return result;
    }
    static String friendly(String text) {
        for(String line:text.split("\n"))if(line.startsWith("STOP:"))return line.substring(5).trim();
        String lower=text.toLowerCase(java.util.Locale.ROOT);
        if(lower.contains("denied")||lower.contains("not allowed"))return "Root access was denied. Allow Bootmark in KernelSU, then connect again.";
        if(lower.contains("not found")||lower.contains("no such file"))return "Root is unavailable. Restore your root session, then connect again.";
        return "The operation could not be verified. See Details.";
    }
    synchronized JSONArray history() {
        try{return new JSONArray(prefs.getString("history","[]"));}catch(Exception e){return new JSONArray();}
    }
    private synchronized void record(String title,String state,String summary,String output) {
        try {
            JSONArray old=history(),all=new JSONArray();
            JSONObject row=new JSONObject().put("title",title).put("state",state).put("summary",summary)
                    .put("time",System.currentTimeMillis()).put("output",output.length()>18000?output.substring(0,18000):output);
            all.put(row);for(int i=0;i<Math.min(old.length(),39);i++)all.put(old.get(i));
            prefs.edit().putString("history",all.toString()).apply();
        }catch(Exception ignored){}
    }
    void clearHistory() { prefs.edit().remove("history").apply();notifyUi(); }
    String export() {
        return "Bootmark 1.2\nModel: "+Build.MODEL+"\nBuild: "+Build.DISPLAY+"\nSelected: "+themeName(selected())
                +"\nDetected: "+themeName(active)+"\n\n"+history().toString();
    }
    static String time(long t) { return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(t)); }
    static final class Result {
        final int code;final String output;final boolean timedOut;
        Result(int code,String output,boolean timedOut){this.code=code;this.output=output;this.timedOut=timedOut;}
    }
}
