package com.pery.clib;

public class NativeLib {

    // Used to load the 'clib' library on application startup.
    static {
        System.loadLibrary("clib");
    }

    /**
     * A native method that is implemented by the 'clib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}