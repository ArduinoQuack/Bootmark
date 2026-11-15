package dev.dov.bootmark;

import android.app.Application;

public final class BootmarkApp extends Application {
    private Engine engine;
    public synchronized Engine engine() {
        if (engine == null) engine = new Engine(this);
        return engine;
    }
}
