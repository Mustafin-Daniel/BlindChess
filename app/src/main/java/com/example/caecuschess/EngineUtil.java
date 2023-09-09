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

    /** Return true if file "engine" is a network engine. */
    public static boolean isNetEngine(String engine) {
        boolean netEngine = false;
        try (InputStream inStream = new FileInputStream(engine);
             InputStreamReader inFile = new InputStreamReader(inStream)) {
            char[] buf = new char[4];
            if ((inFile.read(buf) == 4) && "NETE".equals(new String(buf)))
                netEngine = true;
        } catch (IOException ignore) {
        }
        return netEngine;
    }

    public static final String openExchangeDir = "oex";

    /** Return true if file "engine" is an open exchange engine. */
    public static boolean isOpenExchangeEngine(String engine) {
        File parent = new File(engine).getParentFile();
        if (parent == null)
            return false;
        String parentDir = parent.getName();
        return openExchangeDir.equals(parentDir);
    }

    /** Remove characters from s that are not safe to use in a filename. */
    private static String sanitizeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (((ch >= 'A') && (ch <= 'Z')) ||
                ((ch >= 'a') && (ch <= 'z')) ||
                ((ch >= '0') && (ch <= '9')))
                sb.append(ch);
            else
                sb.append('_');
        }
        return sb.toString();
    }

    /** For synchronizing non thread safe native calls. */
    public static final Object nativeLock = new Object();
}
