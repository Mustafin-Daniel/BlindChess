package com.example.caecuschess;

import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class EngineUtil {
    static {
        System.loadLibrary("nativeutil");
    }
    /** For synchronizing non thread safe native calls. */
    public static final Object nativeLock = new Object();
}
