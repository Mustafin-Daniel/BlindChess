package com.example.caecuschess;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.Locale;

public class CaecusChessApp extends Application {
    private static Context appContext;
    private static Toast toast;

    public CaecusChessApp() {
        super();
        appContext = this;
    }

    /** Get the application context. */
    public static Context getContext() {
        return appContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(setLanguage(base, false));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setLanguage(appContext, false);
    }

    public static Context setLanguage(Context context, boolean restartIfLangChange) {
        Context ret = context;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = settings.getString("language", "default");
        Locale newLocale;
        if ("default".equals(lang)) {
            newLocale = Resources.getSystem().getConfiguration().locale;
        } else if (lang.contains("_")) {
            String[] parts = lang.split("_");
            newLocale = new Locale(parts[0], parts[1]);
        } else {
            newLocale = new Locale(lang);
        }
        String currLang = context.getResources().getConfiguration().locale.getLanguage();
        if (!newLocale.getLanguage().equals(currLang)) {
            Locale.setDefault(newLocale);
            Resources res = context.getResources();
            Configuration config = new Configuration(res.getConfiguration());
            if (Build.VERSION.SDK_INT >= 17) {
                config.setLocale(newLocale);
                ret = context.createConfigurationContext(config);
            } else {
                config.locale = newLocale;
                res.updateConfiguration(config, res.getDisplayMetrics());
            }
            if (restartIfLangChange) {
                Intent i = new Intent(context, CaecusChess.class);
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK |
                                                 Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
        return ret;
    }

    /** Show a toast after canceling current toast. */
    public static void toast(int resId, int duration) {
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
        toast = Toast.makeText(appContext, resId, duration);
        toast.show();
    }

    /** Show a toast after canceling current toast. */
    public static void toast(CharSequence text, int duration) {
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
        toast = Toast.makeText(appContext, text, duration);
        toast.show();
    }
}
