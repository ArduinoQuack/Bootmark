package dev.dov.bootmark;

final class RootLauncher {
    static String quote(String s) { return "'"+s.replace("'","'\\''")+"'"; }
    static String[] command(String script,String mode) {
        if(!"repair".equals(mode)&&!"restore".equals(mode)&&!"status".equals(mode))
            throw new IllegalArgumentException("Unknown root operation.");
        // KernelSU's virtual su is an exec hook. Java's PATH lookup can fail
        // before that hook runs; let Android's shell exec the absolute path.
        String work="/system/bin/sh "+quote(script)+" "+mode;
        return new String[]{"/system/bin/sh","-c","exec /system/bin/su -c "+quote(work)};
    }
}
