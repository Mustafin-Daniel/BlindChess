package com.example.caecuschess.activities;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import com.example.caecuschess.CaecusChessApp;
import com.example.caecuschess.ColorTheme;
import com.example.caecuschess.GameEngine.ChessError;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;
import com.example.caecuschess.R;
import com.example.caecuschess.Util;
import com.example.caecuschess.activities.util.FENFile;
import com.example.caecuschess.activities.util.FENFile.FenInfo;
import com.example.caecuschess.activities.util.FENFile.FenInfoResult;
import com.example.caecuschess.databinding.LoadFenBinding;

import java.io.File;
import java.util.ArrayList;

public class LoadFEN extends ListActivity {
    private static ArrayList<FenInfo> fensInFile = new ArrayList<>();
    private static boolean cacheValid = false;
    private FENFile fenFile;
    private FenInfo selectedFi = null;
    private ArrayAdapter<FenInfo> aa = null;

    private SharedPreferences settings;
    private int defaultItem = 0;
    private String lastFileName = "";
    private long lastModTime = -1;

    private Thread workThread = null;

    LoadFenBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = PreferenceManager.getDefaultSharedPreferences(this);
        Util.setFullScreenMode(this, settings);

        if (savedInstanceState != null) {
            defaultItem = savedInstanceState.getInt("defaultItem");
            lastFileName = savedInstanceState.getString("lastFenFileName");
            if (lastFileName == null) lastFileName = "";
            lastModTime = savedInstanceState.getLong("lastFenModTime");
        } else {
            defaultItem = settings.getInt("defaultItem", 0);
            lastFileName = settings.getString("lastFenFileName", "");
            lastModTime = settings.getLong("lastFenModTime", 0);
        }

        Intent i = getIntent();
        String action = i.getAction();
        String fileName = i.getStringExtra("com.example.caecuschess.pathname");
        if ("com.example.caecuschess.loadFen".equals(action)) {
            fenFile = new FENFile(fileName);
            final LoadFEN lfen = this;
            workThread = new Thread(() -> {
                if (!readFile())
                    return;
                runOnUiThread(lfen::showList);
            });
            workThread.start();
        } else if ("com.example.caecuschess.loadNextFen".equals(action) ||
                   "com.example.caecuschess.loadPrevFen".equals(action)) {
            fenFile = new FENFile(fileName);
            boolean next = action.equals("com.example.caecuschess.loadNextFen");
            final int loadItem = defaultItem + (next ? 1 : -1);
            if (loadItem < 0) {
                CaecusChessApp.toast(R.string.no_prev_fen, Toast.LENGTH_SHORT);
                setResult(RESULT_CANCELED);
                finish();
            } else {
                workThread = new Thread(() -> {
                    if (!readFile())
                        return;
                    runOnUiThread(() -> {
                        if (loadItem >= fensInFile.size()) {
                            CaecusChessApp.toast(R.string.no_next_fen, Toast.LENGTH_SHORT);
                            setResult(RESULT_CANCELED);
                            finish();
                        } else {
                            defaultItem = loadItem;
                            sendBackResult(fensInFile.get(loadItem), true);
                        }
                    });
                });
                workThread.start();
            }
        } else { // Unsupported action
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CaecusChessApp.setLanguage(newBase, false));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("defaultItem", defaultItem);
        outState.putString("lastFenFileName", lastFileName);
        outState.putLong("lastFenModTime", lastModTime);
    }

    @Override
    protected void onPause() {
        Editor editor = settings.edit();
        editor.putInt("defaultItem", defaultItem);
        editor.putString("lastFenFileName", lastFileName);
        editor.putLong("lastFenModTime", lastModTime);
        editor.apply();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (workThread != null) {
            workThread.interrupt();
            try {
                workThread.join();
            } catch (InterruptedException e) {
            }
            workThread = null;
        }
        super.onDestroy();
    }

    private void showList() {
        setContentView(R.layout.load_fen);
        binding = DataBindingUtil.setContentView(this, R.layout.load_fen);
        binding.loadfenOk.setEnabled(false);
        binding.loadfenOk.setOnClickListener(v -> {
            if (selectedFi != null)
                sendBackResult(selectedFi, false);
        });
        binding.loadfenCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        Util.overrideViewAttribs(findViewById(android.R.id.content));
        aa = new ArrayAdapter<FenInfo>(this, R.layout.select_game_list_item, fensInFile) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    int fg = ColorTheme.instance().getColor(ColorTheme.FONT_FOREGROUND);
                    ((TextView) view).setTextColor(fg);
                }
                return view;
            }
        };
        setListAdapter(aa);
        final ListView lv = getListView();
        lv.setSelectionFromTop(defaultItem, 0);
        lv.setFastScrollEnabled(true);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            selectedFi = aa.getItem(pos);
            if (selectedFi == null)
                return;
            defaultItem = pos;
            Position chessPos;
            try {
                chessPos = TextInfo.readFEN(selectedFi.fen);
            } catch (ChessError e2) {
                chessPos = e2.pos;
            }
            if (chessPos != null) {
                binding.loadfenChessboard.setPosition(chessPos);
                binding.loadfenOk.setEnabled(true);
            }
        });
        lv.setOnItemLongClickListener((parent, view, pos, id) -> {
            selectedFi = aa.getItem(pos);
            if (selectedFi == null)
                return false;
            defaultItem = pos;
            Position chessPos;
            try {
                chessPos = TextInfo.readFEN(selectedFi.fen);
            } catch (ChessError e2) {
                chessPos = e2.pos;
            }
            if (chessPos != null)
                sendBackResult(selectedFi, false);
            return true;
        });
        lv.requestFocus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding != null && binding.loadfenChessboard != null) {
            Position pos = binding.loadfenChessboard.pos;
            showList();
            binding.loadfenChessboard.setPosition(pos);
            binding.loadfenOk.setEnabled(selectedFi != null);
        }
    }

    private boolean readFile() {
        String fileName = fenFile.getName();
        if (!fileName.equals(lastFileName))
            defaultItem = 0;
        long modTime = new File(fileName).lastModified();
        if (cacheValid && (modTime == lastModTime) && fileName.equals(lastFileName))
            return true;
        fenFile = new FENFile(fileName);
        Pair<FenInfoResult, ArrayList<FenInfo>> p = fenFile.getFenInfo();
        if (p.first != FenInfoResult.OK) {
            fensInFile = new ArrayList<>();
            if (p.first == FenInfoResult.OUT_OF_MEMORY) {
                runOnUiThread(() -> CaecusChessApp.toast(R.string.file_too_large, Toast.LENGTH_SHORT));
            }
            setResult(RESULT_CANCELED);
            finish();
            return false;
        }
        fensInFile = p.second;
        cacheValid = true;
        lastModTime = modTime;
        lastFileName = fileName;
        return true;
    }

    private void sendBackResult(FenInfo fi, boolean toast) {
        String fen = fi.fen;
        if (fen != null) {
            if (toast)
                CaecusChessApp.toast(String.valueOf(fi.gameNo) + ": " + fen, Toast.LENGTH_SHORT);
            setResult(RESULT_OK, (new Intent()).setAction(fen));
            finish();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
