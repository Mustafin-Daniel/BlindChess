package com.example.caecuschess.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import com.example.caecuschess.CaecusChessApp;
import com.example.caecuschess.R;

public class CPUWarning extends Activity {
    public static class Fragment extends DialogFragment {
        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            Activity a = getActivity();
            if (a != null)
                a.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DialogFragment df = new Fragment();
        df.show(getFragmentManager(), "");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CaecusChessApp.setLanguage(newBase, false));
    }
}
