package com.lianyun.chatlxd;

import com.perry.audiorecorder.ARApplication;
import com.perry.audiorecorder.Injector;

public class APP extends ARApplication {
    public static InjectorLxd injectorLxd;

    public static InjectorLxd getInjectorLxd() {
        return injectorLxd;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        injectorLxd = new InjectorLxd(getApplicationContext());
        injectorLxd.setSuper(injector);
    }

}
