package com.example.caecuschess;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.example.caecuschess.GameEngine.ChessError;
import com.example.caecuschess.GameEngine.DroidChessController;
import com.example.caecuschess.GameEngine.GameTree.Node;
import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.MoveGeneration;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;
import com.example.caecuschess.GameEngine.UndoInfo;
import com.example.caecuschess.activities.CPUWarning;
import com.example.caecuschess.activities.EditBoard;
import com.example.caecuschess.activities.EditPGNLoad;
import com.example.caecuschess.activities.EditPGNSave;
import com.example.caecuschess.activities.LoadFEN;
import com.example.caecuschess.activities.LoadScid;
import com.example.caecuschess.activities.Preferences;
import com.example.caecuschess.activities.util.PGNFile;
import com.example.caecuschess.activities.util.PGNFile.GameInfo;
import com.example.caecuschess.book.BookOptions;
import com.example.caecuschess.view.MoveListView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import tourguide.TourGuide;

@SuppressLint("ClickableViewAccessibility")
public class CaecusChess extends Activity
                       implements GUIInterface,
                                  ActivityCompat.OnRequestPermissionsResultCallback {
    private ChessBoardPlay cb;
    DroidChessController ctrl = null;
    GameMode gameMode;
    private String playerName;
    private boolean boardFlipped;
    private boolean autoSwapSides;
    private boolean playerNameFlip;
    private boolean discardVariations;

    private TextView status;
    private ScrollView moveListScroll;
    private MoveListView moveList;
    private TextView thinking;
    private View buttons;
    private ImageButton modeButton, undoButton, redoButton, blindButton, flipButton;
    private TextView whiteTitleText, blackTitleText;
    private View secondTitleLine;
    private TextView whiteFigText, blackFigText;
    private Dialog moveListMenuDlg;

    private DrawerLayout drawerLayout;
    private ListView leftDrawer;
    private ListView rightDrawer;

    private SharedPreferences settings;
    private ObjectCache cache;

    boolean dragMoveEnabled;
    float scrollSensitivity;
    boolean invertScrollDirection;
    boolean scrollGames;
    private boolean autoScrollMoveList;

    private boolean leftHanded;
    private boolean animateMoves;
    private boolean autoScrollTitle;

    private int autoMoveDelay; // Delay in auto forward/backward mode
    enum AutoMode {
        OFF, FORWARD, BACKWARD
    }
    private AutoMode autoMode = AutoMode.OFF;

    private int ECO_HINTS_OFF = 0;
    private int ECO_HINTS_AUTO = 1;
    private int ECO_HINTS_ALWAYS = 2;

    /** State of requested permissions. */
    private enum PermissionState {
        UNKNOWN,
        REQUESTED,
        GRANTED,
        DENIED
    }
    /** State of WRITE_EXTERNAL_STORAGE permission. */
    private PermissionState storagePermission = PermissionState.UNKNOWN;

    private static String bookDir = "CaecusChess/book";
    private static String pgnDir = "CaecusChess/pgn";
    private static String fenDir = "CaecusChess/epd";
    private static String gtbDefaultDir = "CaecusChess/gtb";
    private static String rtbDefaultDir = "CaecusChess/rtb";
    private BookOptions bookOptions = new BookOptions();
    private PGNOptions pgnOptions = new PGNOptions();

    private PgnScreenText gameTextListener;

    private Typeface figNotation;
    private Typeface defaultThinkingListTypeFace;

    private TourGuide tourGuide;

    /** Defines all configurable button actions. */
    ActionFactory actionFactory = new ActionFactory() {
        private HashMap<String, UIAction> actions;

        private void addAction(UIAction a) {
            actions.put(a.getId(), a);
        }

        {
            actions = new HashMap<>();
            addAction(new UIAction() {
                public String getId() { return "flipboard"; }
                public int getName() { return R.string.flip_board; }
                public int getIcon() { return R.raw.flip; }
                public boolean enabled() { return true; }
                public void run() {
                    boardFlipped = !cb.flipped;
                    setBooleanPref("boardFlipped", boardFlipped);
                    cb.setFlipped(boardFlipped);
                }
            });/* //TODO Book action prob remove this too
            addAction(new UIAction() {
                public String getId() { return "bookHints"; }
                public int getName() { return R.string.toggle_book_hints; }
                public int getIcon() { return R.raw.book; }
                public boolean enabled() { return true; }
                public void run() {
                    mShowBookHints = toggleBooleanPref("bookHints");
                    updateThinkingInfo();
                }
            });*/
            addAction(new UIAction() {
                public String getId() { return "viewVariations"; }
                public int getName() { return R.string.toggle_pgn_variations; }
                public int getIcon() { return R.raw.variation; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.variations = toggleBooleanPref("viewVariations");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "viewHeaders"; }
                public int getName() { return R.string.toggle_pgn_headers; }
                public int getIcon() { return R.raw.header; }
                public boolean enabled() { return true; }
                public void run() {
                    pgnOptions.view.headers = toggleBooleanPref("viewHeaders");
                    gameTextListener.clear();
                    ctrl.prefsChanged(false);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "largeButtons"; }
                public int getName() { return R.string.toggle_large_buttons; }
                public int getIcon() { return R.raw.magnify; }
                public boolean enabled() { return true; }
                public void run() {
                    toggleBooleanPref("largeButtons");
                    updateButtons();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "blindMode"; }
                public int getName() { return R.string.blind_mode; }
                public int getIcon() { return R.raw.blind; }
                public boolean enabled() { return true; }
                public void run() {
                    boolean blindMode = !cb.blindMode;
                    setBooleanPref("blindMode", blindMode);
                    cb.setBlindMode(blindMode);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadLastFile"; }
                public int getName() { return R.string.load_last_file; }
                public int getIcon() { return R.raw.open_last_file; }
                public boolean enabled() { return currFileType() != FT_NONE && storageAvailable(); }
                public void run() {
                    loadLastFile();
                }
            });
            addAction(new UIAction() {
                public String getId() { return "loadGame"; }
                public int getName() { return R.string.load_game; }
                public int getIcon() { return R.raw.open_file; }
                public boolean enabled() { return storageAvailable(); }
                public void run() {
                    selectFile(R.string.select_pgn_file, R.string.pgn_load, "currentPGNFile", pgnDir,
                               SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                }
            });
            addAction(new UIAction() {
                public String getId() { return "prevGame"; }
                public int getName() { return R.string.load_prev_game; }
                public int getIcon() { return R.raw.variation; }
                public boolean enabled() {
                    return (currFileType() != FT_NONE) && !gameMode.clocksActive();
                }
                public void run() {
                    final int currFT = currFileType();
                    final String currPathName = currPathName();
                    Intent i;
                    if (currFT == FT_PGN) {
                        i = new Intent(CaecusChess.this, EditPGNLoad.class);
                        i.setAction("com.example.caecuschess.loadFilePrevGame");
                        i.putExtra("com.example.caecuschess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_SCID) {
                        i = new Intent(CaecusChess.this, LoadScid.class);
                        i.setAction("com.example.caecuschess.loadScidPrevGame");
                        i.putExtra("com.example.caecuschess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_FEN) {
                        i = new Intent(CaecusChess.this, LoadFEN.class);
                        i.setAction("com.example.caecuschess.loadPrevFen");
                        i.putExtra("com.example.caecuschess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_FEN);
                    }
                }
            });
            addAction(new UIAction() {
                public String getId() { return "nextGame"; }
                public int getName() { return R.string.load_next_game; }
                public int getIcon() { return R.raw.variation; }
                public boolean enabled() {
                    return (currFileType() != FT_NONE) && !gameMode.clocksActive();
                }
                public void run() {
                    final int currFT = currFileType();
                    final String currPathName = currPathName();
                    Intent i;
                    if (currFT == FT_PGN) {
                        i = new Intent(CaecusChess.this, EditPGNLoad.class);
                        i.setAction("com.example.caecuschess.loadFileNextGame");
                        i.putExtra("com.example.caecuschess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_SCID) {
                        i = new Intent(CaecusChess.this, LoadScid.class);
                        i.setAction("com.example.caecuschess.loadScidNextGame");
                        i.putExtra("com.example.caecushcess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_PGN);
                    } else if (currFT == FT_FEN) {
                        i = new Intent(CaecusChess.this, LoadFEN.class);
                        i.setAction("com.example.caecuschess.loadNextFen");
                        i.putExtra("com.example.caecuschess.pathname", currPathName);
                        startActivityForResult(i, RESULT_LOAD_FEN);
                    }
                }
            });
        }

        @Override
        public UIAction getAction(String actionId) {
            return actions.get(actionId);
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        String intentPgnOrFen = null;
        String intentFilename = null;
        if (savedInstanceState == null) {
            Pair<String,String> pair = getPgnOrFenIntent();
            intentPgnOrFen = pair.first;
            intentFilename = pair.second;
        }

        createDirectories();

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        cache = new ObjectCache();

        setWakeLock(false);

        figNotation = Typeface.createFromAsset(getAssets(), "fonts/CaecusChessChessNotationDark.otf");
        setPieceNames(PGNOptions.PT_LOCAL);
        initMenu();
        //initUI();


    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CaecusChessApp.setLanguage(newBase, false));
    }

    // Unicode code points for chess pieces
    private static final String figurinePieceNames = PieceFontInfo.NOTATION_PAWN   + " " +
                                                     PieceFontInfo.NOTATION_KNIGHT + " " +
                                                     PieceFontInfo.NOTATION_BISHOP + " " +
                                                     PieceFontInfo.NOTATION_ROOK   + " " +
                                                     PieceFontInfo.NOTATION_QUEEN  + " " +
                                                     PieceFontInfo.NOTATION_KING;

    private void setPieceNames(int pieceType) {
        if (pieceType == PGNOptions.PT_FIGURINE) {
            TextInfo.setPieceNames(figurinePieceNames);
        } else {
            TextInfo.setPieceNames(getString(R.string.piece_names));
        }
    }

    /** Create directory structure on SD card. */
    private void createDirectories() {
        if (storagePermission == PermissionState.UNKNOWN) {
            String extStorage = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, extStorage) == 
                    PackageManager.PERMISSION_GRANTED) {
                storagePermission = PermissionState.GRANTED;
            } else {
                storagePermission = PermissionState.REQUESTED;
                ActivityCompat.requestPermissions(this, new String[]{extStorage}, 0);
            }
        }
        if (storagePermission != PermissionState.GRANTED)
            return;

        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;
        new File(extDir + sep + bookDir).mkdirs();
        new File(extDir + sep + pgnDir).mkdirs();
        new File(extDir + sep + fenDir).mkdirs();
        new File(extDir + sep + gtbDefaultDir).mkdirs();
        new File(extDir + sep + rtbDefaultDir).mkdirs();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (storagePermission == PermissionState.REQUESTED) {
            if ((results.length > 0) && (results[0] == PackageManager.PERMISSION_GRANTED))
                storagePermission = PermissionState.GRANTED;
            else
                storagePermission = PermissionState.DENIED;
        }
        createDirectories();
    }

    /** Return true if the WRITE_EXTERNAL_STORAGE permission has been granted. */
    private boolean storageAvailable() {
        return storagePermission == PermissionState.GRANTED;
    }

    /**
     * Return PGN/FEN data or filename from the Intent. Both can not be non-null.
     * @return Pair of PGN/FEN data and filename.
     */
    private Pair<String,String> getPgnOrFenIntent() {
        String pgnOrFen = null;
        String filename = null;
        try {
            Intent intent = getIntent();
            Uri data = intent.getData();
            if (data == null) {
                Bundle b = intent.getExtras();
                if (b != null) {
                    Object strm = b.get(Intent.EXTRA_STREAM);
                    if (strm instanceof Uri) {
                        data = (Uri)strm;
                        if ("file".equals(data.getScheme())) {
                            filename = data.getEncodedPath();
                            if (filename != null)
                                filename = Uri.decode(filename);
                        }
                    }
                }
            }
            if (data == null) {
                if ((Intent.ACTION_SEND.equals(intent.getAction()) ||
                     Intent.ACTION_VIEW.equals(intent.getAction())) &&
                    ("application/x-chess-pgn".equals(intent.getType()) ||
                     "application/x-chess-fen".equals(intent.getType())))
                    pgnOrFen = intent.getStringExtra(Intent.EXTRA_TEXT);
            } else {
                String scheme = data.getScheme();
                if ("file".equals(scheme)) {
                    filename = data.getEncodedPath();
                    if (filename != null)
                        filename = Uri.decode(filename);
                }
                if ((filename == null) &&
                    ("content".equals(scheme) || "file".equals(scheme))) {
                    ContentResolver resolver = getContentResolver();
                    String sep = File.separator;
                    String fn = Environment.getExternalStorageDirectory() + sep +
                                pgnDir + sep + ".sharedfile.pgn";
                    try (InputStream in = resolver.openInputStream(data)) {
                        if (in == null)
                            throw new IOException("No input stream");
                        FileUtil.writeFile(in, fn);
                    }
                    PGNFile pgnFile = new PGNFile(fn);
                    long fileLen = FileUtil.getFileLength(fn);
                    boolean moreThanOneGame = false;
                    try {
                        ArrayList<GameInfo> gi = pgnFile.getGameInfo(2);
                        moreThanOneGame = gi.size() > 1;
                    } catch (IOException ignore) {
                    }
                    if (fileLen > 1024 * 1024 || moreThanOneGame) {
                        filename = fn;
                    } else {
                        try (FileInputStream in = new FileInputStream(fn)) {
                            pgnOrFen = FileUtil.readFromStream(in);
                        }
                    }
                }
            }
        } catch (IOException e) {
            CaecusChessApp.toast(R.string.failed_to_read_pgn_data, Toast.LENGTH_SHORT);
        } catch (SecurityException|IllegalArgumentException e) {
            CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
        return new Pair<>(pgnOrFen,filename);
    }

    private byte[] strToByteArr(String str) {
        if (str == null)
            return null;
        int nBytes = str.length() / 2;
        byte[] ret = new byte[nBytes];
        for (int i = 0; i < nBytes; i++) {
            int c1 = str.charAt(i * 2) - 'A';
            int c2 = str.charAt(i * 2 + 1) - 'A';
            ret[i] = (byte)(c1 * 16 + c2);
        }
        return ret;
    }

    private String byteArrToString(byte[] data) {
        if (data == null)
            return null;
        StringBuilder ret = new StringBuilder(32768);
        for (int b : data) {
            if (b < 0) b += 256;
            char c1 = (char)('A' + (b / 16));
            char c2 = (char)('A' + (b & 15));
            ret.append(c1);
            ret.append(c2);
        }
        return ret.toString();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        reInitUI();
    }

    /** Re-initialize UI when layout should change because of rotation or handedness change. */
    private void reInitUI() {
        ChessBoardPlay oldCB = cb;
        String statusStr = status.getText().toString();
        initUI();
        readPrefs(true);
        cb.setPosition(oldCB.pos);
        cb.setFlipped(oldCB.flipped);
        cb.setDrawSquareLabels(oldCB.drawSquareLabels);
        cb.oneTouchMoves = oldCB.oneTouchMoves;
        cb.toggleSelection = oldCB.toggleSelection;
        cb.highlightLastMove = oldCB.highlightLastMove;
        cb.setBlindMode(oldCB.blindMode);
        setSelection(oldCB.selectedSquare);
        cb.userSelectedSquare = oldCB.userSelectedSquare;
        setStatusString(statusStr);
        moveList.setOnLinkClickListener(gameTextListener);
        moveListUpdated();
        ctrl.updateMaterialDiffList();
        if (tourGuide != null) {
            tourGuide.cleanUp();
            tourGuide = null;
        }
    }

    /** Return true if the current orientation is landscape. */
    private boolean landScapeView() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
    
    /** Return true if left-handed layout should be used. */
    private boolean leftHandedView() {
        return settings.getBoolean("leftHanded", false) && landScapeView();
    }

    /** Re-read preferences settings. */
    private void handlePrefsChange() {
        if (leftHanded != leftHandedView())
            reInitUI();
        else
            readPrefs(true);
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);

    }

    private void initMenu(){
        setContentView(R.layout.main_menu);
        Button btnColorTest = findViewById(R.id.btnColorTest);
        btnColorTest.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.color_test);
                RadioGroup modeRadioGroup = findViewById(R.id.radiocolortestmode);
                RadioGroup colorRadioGroup = findViewById(R.id.colorRadioGroup);
                RadioGroup timeRadioGroup = findViewById(R.id.timerRadioGroup);
                TextView square = findViewById(R.id.tv_square_color);
                Button submitOptBtn = findViewById(R.id.btn_submit);
                colorRadioGroup.setVisibility(View.GONE);
                Button whiteBtn = findViewById(R.id.whiteRadioButton);
                Button blackBtn = findViewById(R.id.blackRadioButton);
                final int[] counter = {0};

                submitOptBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if ((modeRadioGroup.getCheckedRadioButtonId()==R.id.radio_bullet && timeRadioGroup.getCheckedRadioButtonId()!=-1) || modeRadioGroup.getCheckedRadioButtonId()==R.id.radio_training) {
                            timeRadioGroup.setVisibility(View.GONE);
                            colorRadioGroup.setVisibility(View.VISIBLE);
                            modeRadioGroup.setVisibility(View.GONE);

                            square.setText(generateRandomSq());
                            RadioButton timeBtn=findViewById(timeRadioGroup.getCheckedRadioButtonId());
                            if(modeRadioGroup.getCheckedRadioButtonId()==R.id.radio_bullet) {
                                int timer=startTimer((String) timeBtn.getText());

                                CountDownTimer time = new CountDownTimer(timer, 1000) {
                                    @Override
                                    public void onTick(long millisUntilFinished) {

                                    }

                                    @Override
                                    public void onFinish() {
                                        Toast.makeText(CaecusChess.this, "Timer is up. Score: "+counter[0], Toast.LENGTH_SHORT).show();
                                        colorRadioGroup.setVisibility(View.GONE);
                                        timeRadioGroup.setVisibility(View.VISIBLE);
                                        timeRadioGroup.clearCheck();
                                        modeRadioGroup.setVisibility(View.VISIBLE);
                                        modeRadioGroup.clearCheck();
                                        colorRadioGroup.setVisibility(View.GONE);
                                        submitOptBtn.setVisibility(View.VISIBLE);
                                        square.setText("Square");
                                        counter[0]=0;
                                    }
                                };

                                time.start();
                            }
                            submitOptBtn.setVisibility(View.GONE);
                        }
                    }

                });

                whiteBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        boolean isCorr=checkSq((String) square.getText(),true);
                        if(isCorr){
                            square.setText(generateRandomSq());
                            counter[0]++;
                        }else{
                            square.setText(generateRandomSq());
                            Toast.makeText(CaecusChess.this, "oops", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                blackBtn.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        boolean isCorr=checkSq((String) square.getText(),false);
                        if(isCorr){
                            square.setText(generateRandomSq());
                            counter[0]++;
                        }else{
                            square.setText(generateRandomSq());
                            Toast.makeText(CaecusChess.this, "oops", Toast.LENGTH_SHORT).show();
                        }
                    }
                });


            }
        });

        Button btnSequenceMaster = findViewById(R.id.btnSequenceMaster);
        btnSequenceMaster.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.sequence_master);
                SeekBar slider = findViewById(R.id.smseekbar);
                RadioGroup smTimeGroup = findViewById(R.id.smTimeGroup);
                final int[] numMove={5};
                slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        Toast.makeText(CaecusChess.this, getString(R.string.memorizeText)+": "+String.valueOf(progress+1), Toast.LENGTH_SHORT).show();
                        numMove[0] = progress+1;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });
                Button smBtnSubmit = findViewById(R.id.smsubmit);
                TextView smmoves = findViewById(R.id.smmoves);
                final Position[] endpos = new Position[1];

                smBtnSubmit.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        RadioButton timeBtn = findViewById(smTimeGroup.getCheckedRadioButtonId());
                        if (timeBtn!=null){
                            smBtnSubmit.setVisibility(View.GONE);
                            smmoves.setVisibility(View.VISIBLE);
                            int timer=startTimer((String) timeBtn.getText());

                            ArrayList<Move> mList = new ArrayList<>();
                            String moveListText = "";
                            try {
                                Position pos=TextInfo.readFEN(TextInfo.startPosFEN);
                                for (int i=1; i<=numMove[0]; i++){
                                    ArrayList<Move> moves = MoveGeneration.instance.generateLegalMoves(pos);
                                    Random random = new Random();
                                    Move randomMove = moves.get(random.nextInt(moves.size()));
                                    mList.add(randomMove);
                                    UndoInfo ui = new UndoInfo();
                                    moveListText+=String.valueOf(i)+". ";
                                    moveListText+=(TextInfo.moveToString(pos, randomMove, false, false));
                                    moveListText+=" ";
                                    pos.makeMove(randomMove, ui);

                                    moves = MoveGeneration.instance.generateLegalMoves(pos);
                                    randomMove = moves.get(random.nextInt(moves.size()));
                                    mList.add(randomMove);
                                    ui = new UndoInfo();
                                    moveListText+=(TextInfo.moveToString(pos, randomMove, false, false));
                                    moveListText+=" ";
                                    pos.makeMove(randomMove, ui);
                                }
                                endpos[0]=pos;
                            } catch (ChessError e) {
                                Toast.makeText(CaecusChess.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }

                            smmoves.setText(moveListText);

                            CountDownTimer time = new CountDownTimer(timer, 1000) {
                                @Override
                                public void onTick(long millisUntilFinished) {
                                }

                                @Override
                                public void onFinish() {
                                    initUI();
                                    gameTextListener = new PgnScreenText(CaecusChess.this, pgnOptions);
                                    moveList.setOnLinkClickListener(gameTextListener);
                                    ctrl = new DroidChessController(CaecusChess.this, gameTextListener, pgnOptions);
                                    ctrl.type=1;
                                    ctrl.corrMoveList=mList;
                                    egtbForceReload = true;
                                    readPrefs(false);
                                    ctrl.newGame(gameMode);
                                    setAutoMode(AutoMode.OFF);
                                    ctrl.setGuiPaused(true);
                                    ctrl.setGuiPaused(false);
                                    ctrl.startGame();
                                    setBoardFlip(true);

                                    CountDownTimer tim = new CountDownTimer(Long.MAX_VALUE, 1000) {
                                        @Override
                                        public void onTick(long millisUntilFinished) {
                                            if (Arrays.equals(cb.pos.getSquares(), endpos[0].getSquares())) {
                                                setContentView(R.layout.sequence_master);
                                                smBtnSubmit.setVisibility(View.VISIBLE);
                                                smmoves.setVisibility(View.GONE);
                                                Toast.makeText(CaecusChess.this, "Good job", Toast.LENGTH_SHORT).show();
                                                try {
                                                    endpos[0]=TextInfo.readFEN(TextInfo.startPosFEN);
                                                } catch (ChessError e) {
                                                }
                                            }
                                        }

                                        @Override
                                        public void onFinish() {
                                        }
                                    };
                                    tim.start();
                                }
                            };

                            time.start();
                        }
                    }
                });
            }
        });

        Button btnBlindChess = findViewById(R.id.btnBlindChess);
        btnBlindChess.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                initUI();
                gameTextListener = new PgnScreenText(CaecusChess.this, pgnOptions);
                moveList.setOnLinkClickListener(gameTextListener);
                ctrl = new DroidChessController(CaecusChess.this, gameTextListener, pgnOptions);
                egtbForceReload = true;
                readPrefs(false);
                ctrl.newGame(gameMode);
                setAutoMode(AutoMode.OFF);
                ctrl.setGuiPaused(true);
                ctrl.setGuiPaused(false);
                ctrl.startGame();
                setBoardFlip(true);
                cb.setBlindMode(true);
            }
        });
    }

    @Override
    public void onBackPressed() {
        initMenu();
    }



    private void initUI() {
        leftHanded = leftHandedView();
        setContentView(leftHanded ? R.layout.main_left_handed : R.layout.main);
        overrideViewAttribs();

        // title lines need to be regenerated every time due to layout changes (rotations)
        View firstTitleLine = findViewById(R.id.first_title_line);
        secondTitleLine = findViewById(R.id.second_title_line);
        whiteTitleText = findViewById(R.id.white_clock);
        whiteTitleText.setSelected(true);
        blackTitleText = findViewById(R.id.black_clock);
        blackTitleText.setSelected(true);
        whiteFigText = findViewById(R.id.white_pieces);
        whiteFigText.setTypeface(figNotation);
        whiteFigText.setSelected(true);
        whiteFigText.setTextColor(whiteTitleText.getTextColors());
        blackFigText = findViewById(R.id.black_pieces);
        blackFigText.setTypeface(figNotation);
        blackFigText.setSelected(true);
        blackFigText.setTextColor(blackTitleText.getTextColors());

        status = findViewById(R.id.status);
        moveListScroll = findViewById(R.id.scrollView);
        moveList = findViewById(R.id.moveList);
        thinking = findViewById(R.id.thinking);
        defaultThinkingListTypeFace = thinking.getTypeface();
        status.setFocusable(false);
        moveListScroll.setFocusable(false);
        moveList.setFocusable(false);
        thinking.setFocusable(false);

        initDrawers();

        class ClickListener implements OnClickListener, OnTouchListener {
            private float touchX = -1;
            @Override
            public void onClick(View v) {
                boolean left = touchX <= v.getWidth() / 2.0;
                drawerLayout.openDrawer(left ? Gravity.LEFT : Gravity.RIGHT);
                touchX = -1;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                touchX = event.getX();
                return false;
            }
        }
        ClickListener listener = new ClickListener();
        firstTitleLine.setOnClickListener(listener);
        firstTitleLine.setOnTouchListener(listener);
        secondTitleLine.setOnClickListener(listener);
        secondTitleLine.setOnTouchListener(listener);

        cb = findViewById(R.id.chessboard);
        cb.setFocusable(true);
        cb.requestFocus();
        cb.setClickable(true);
        cb.setPgnOptions(pgnOptions);

        ChessBoardPlayListener cbpListener = new ChessBoardPlayListener(this, cb);
        cb.setOnTouchListener(cbpListener);

        moveList.setOnLongClickListener(v -> {
            reShowDialog(MOVELIST_MENU_DIALOG);
            return true;
        });

        buttons = findViewById(R.id.buttons);

        flipButton = findViewById(R.id.flipbutton);
        flipButton.setOnClickListener(v -> {
            boardFlipped = !cb.flipped;
            setBooleanPref("boardFlipped", boardFlipped);
            cb.setFlipped(boardFlipped);
        });
        modeButton = findViewById(R.id.modeButton);
        modeButton.setOnClickListener(v -> cb.setDrawSquareLabels(!cb.drawSquareLabels));
        modeButton.setOnLongClickListener(v -> {
            drawerLayout.openDrawer(Gravity.LEFT);
            return true;
        });
        undoButton = findViewById(R.id.undoButton);
        undoButton.setOnClickListener(v -> {
            setAutoMode(AutoMode.OFF);
            ctrl.undoMove();
        });
        undoButton.setOnLongClickListener(v -> {
            reShowDialog(GO_BACK_MENU_DIALOG);
            return true;
        });
        redoButton = findViewById(R.id.redoButton);
        redoButton.setOnClickListener(v -> {
            setAutoMode(AutoMode.OFF);
            ctrl.redoMove();
        });
        redoButton.setOnLongClickListener(v -> {
            reShowDialog(GO_FORWARD_MENU_DIALOG);
            return true;
        });

        blindButton = findViewById(R.id.blindButton);
        blindButton.setOnClickListener(v -> {
            boolean bm=cb.blindMode;
            cb.setBlindMode(!bm);
        });
    }

    private static final int serializeVersion = 4;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (ctrl != null) {
            byte[] data = ctrl.toByteArray();
            byte[] token = data == null ? null : cache.storeBytes(data);
            outState.putByteArray("gameStateT", token);
            outState.putInt("gameStateVersion", serializeVersion);
        }
    }

    @Override
    protected void onResume() {
        if (ctrl != null)
            ctrl.setGuiPaused(false);
        notificationActive = true;
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (ctrl != null) {
            setAutoMode(AutoMode.OFF);
            ctrl.setGuiPaused(true);
            byte[] data = ctrl.toByteArray();
            Editor editor = settings.edit();
            String dataStr = byteArrToString(data);
            editor.putString("gameState", dataStr);
            editor.putInt("gameStateVersion", serializeVersion);
            editor.apply();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        setAutoMode(AutoMode.OFF);
        setNotification(false);
        super.onDestroy();
    }

    private int getIntSetting(String settingName, int defaultValue) {
        String tmp = settings.getString(settingName, String.format(Locale.US, "%d", defaultValue));
        return Integer.parseInt(tmp);
    }

    private void readPrefs(boolean restartIfLangChange) {
        int modeNr = getIntSetting("gameMode", 1);
        gameMode = new GameMode(modeNr);
        String oldPlayerName = playerName;
        playerName = settings.getString("playerName", "Player");
        boardFlipped = settings.getBoolean("boardFlipped", false);
        autoSwapSides = settings.getBoolean("autoSwapSides", false);
        playerNameFlip = settings.getBoolean("playerNameFlip", true);
        setBoardFlip(!playerName.equals(oldPlayerName));
        boolean drawSquareLabels = settings.getBoolean("drawSquareLabels", false);
        cb.setDrawSquareLabels(drawSquareLabels);
        cb.oneTouchMoves = settings.getBoolean("oneTouchMoves", false);
        cb.toggleSelection = getIntSetting("squareSelectType", 0) == 1;
        cb.highlightLastMove = settings.getBoolean("highlightLastMove", true);
        cb.setUltraBlindMode(settings.getBoolean("ultrablindMode", false));


        autoMoveDelay = getIntSetting("autoDelay", 5000);

        dragMoveEnabled = settings.getBoolean("dragMoveEnabled", true);
        scrollSensitivity = Float.parseFloat(settings.getString("scrollSensitivity", "2"));
        invertScrollDirection = settings.getBoolean("invertScrollDirection", false);
        scrollGames = settings.getBoolean("scrollGames", false);
        autoScrollMoveList = settings.getBoolean("autoScrollMoveList", true);
        discardVariations = settings.getBoolean("discardVariations", false);
        Util.setFullScreenMode(this, settings);
        boolean useWakeLock = settings.getBoolean("wakeLock", false);
        setWakeLock(useWakeLock);

        CaecusChessApp.setLanguage(this, restartIfLangChange);
        int fontSize = getIntSetting("fontSize", 12);
        int statusFontSize = fontSize;
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT)
            statusFontSize = Math.min(statusFontSize, 16);
        status.setTextSize(statusFontSize);
        animateMoves = settings.getBoolean("animateMoves", true);
        autoScrollTitle = settings.getBoolean("autoScrollTitle", true);
        setTitleScrolling();

        updateButtons();

        bookOptions.filename = settings.getString("bookFile", "");
        bookOptions.maxLength = getIntSetting("bookMaxLength", 1000000);
        bookOptions.preferMainLines = settings.getBoolean("bookPreferMainLines", false);
        bookOptions.tournamentMode = settings.getBoolean("bookTournamentMode", false);
        bookOptions.random = (settings.getInt("bookRandom", 500) - 500) * (3.0 / 500);
        setBookOptions();

        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;

        String gtbPath = settings.getString("gtbPath", "").trim();
        if (gtbPath.length() == 0)
            gtbPath = extDir.getAbsolutePath() + sep + gtbDefaultDir;
        String rtbPath = settings.getString("rtbPath", "").trim();
        if (rtbPath.length() == 0)
            rtbPath = extDir.getAbsolutePath() + sep + rtbDefaultDir;


        pgnOptions.view.variations  = settings.getBoolean("viewVariations",     true);
        pgnOptions.view.comments    = settings.getBoolean("viewComments",       true);
        pgnOptions.view.nag         = settings.getBoolean("viewNAG",            true);
        pgnOptions.view.headers     = settings.getBoolean("viewHeaders",        false);
        final int oldViewPieceType = pgnOptions.view.pieceType;
        pgnOptions.view.pieceType   = getIntSetting("viewPieceType", PGNOptions.PT_LOCAL);
        pgnOptions.imp.variations   = settings.getBoolean("importVariations",   true);
        pgnOptions.imp.comments     = settings.getBoolean("importComments",     true);
        pgnOptions.imp.nag          = settings.getBoolean("importNAG",          true);
        pgnOptions.exp.variations   = settings.getBoolean("exportVariations",   true);
        pgnOptions.exp.comments     = settings.getBoolean("exportComments",     true);
        pgnOptions.exp.nag          = settings.getBoolean("exportNAG",          true);
        pgnOptions.exp.playerAction = settings.getBoolean("exportPlayerAction", false);
        pgnOptions.exp.clockInfo    = settings.getBoolean("exportTime",         false);

        ColorTheme.instance().readColors(settings);
        PieceSet.instance().readPrefs(settings);
        cb.setColors();
        overrideViewAttribs();

        gameTextListener.clear();
        setPieceNames(pgnOptions.view.pieceType);
        ctrl.prefsChanged(oldViewPieceType != pgnOptions.view.pieceType);
        // update the typeset in case of a change anyway, cause it could occur
        // as well in rotation
        setFigurineNotation(pgnOptions.view.pieceType == PGNOptions.PT_FIGURINE, fontSize);

        boolean showMaterialDiff = settings.getBoolean("materialDiff", false);
        secondTitleLine.setVisibility(showMaterialDiff ? View.VISIBLE : View.GONE);
    }

    private void overrideViewAttribs() {
        Util.overrideViewAttribs(findViewById(R.id.main));
    }

    /**
     * Change the Pieces into figurine or regular (i.e. letters) display
     */
    private void setFigurineNotation(boolean displayAsFigures, int fontSize) {
        if (displayAsFigures) {
            // increase the font cause it has different kerning and looks small
            float increaseFontSize = fontSize * 1.1f;
            moveList.setTypeface(figNotation, increaseFontSize);
            thinking.setTypeface(figNotation);
            thinking.setTextSize(increaseFontSize);
        } else {
            moveList.setTypeface(null, fontSize);
            thinking.setTypeface(defaultThinkingListTypeFace);
            thinking.setTextSize(fontSize);
        }
    }

    private String generateRandomSq(){
        Random random = new Random();
        char file = (char) (random.nextInt(8) + 'A');
        int rank = random.nextInt(8) + 1;
        String square = String.valueOf(file) + rank;
        return square;
    }

    private boolean checkSq(String sq, boolean isWhite){
        char file = sq.charAt(0);
        int rank = Character.getNumericValue(sq.charAt(1));

        boolean isBlackSquare = (file % 2 == 0 && rank % 2 == 0) || (file % 2 != 0 && rank % 2 != 0);

        boolean isCorrectColor = (isBlackSquare && !isWhite) || (!isBlackSquare && isWhite);
        return isCorrectColor;
    }

    private int startTimer(String Timer){
        switch (Timer){
            case "5 sec":
                return 5000;
            case "10 sec":
                return 10000;
            case "15 sec":
                return 15000;
            case "30 sec":
                return 30000;
            case "1 min":
                return 60000;
            default:
                return -1;
        }

    }

    /** Enable/disable title bar scrolling. */
    private void setTitleScrolling() {
        TextUtils.TruncateAt where = autoScrollTitle ? TextUtils.TruncateAt.MARQUEE
                                                     : TextUtils.TruncateAt.END;
        whiteTitleText.setEllipsize(where);
        blackTitleText.setEllipsize(where);
        whiteFigText.setEllipsize(where);
        blackFigText.setEllipsize(where);
    }

    private void updateButtons() {
        boolean largeButtons = settings.getBoolean("largeButtons", false);
        Resources r = getResources();
        int bWidth  = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, r.getDisplayMetrics()));
        int bHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, r.getDisplayMetrics()));
        if (largeButtons) {
            bWidth  = bWidth  * 3 / 2;
            bHeight = bHeight * 3 / 2;
        }
        SVG svg = null;
        try {
            svg = SVG.getFromResource(getResources(), R.raw.touch);
        } catch (SVGParseException ignore) {
        }
        setButtonData(modeButton, bWidth, bHeight, R.raw.mode, svg);
        setButtonData(undoButton, bWidth, bHeight, R.raw.left, svg);
        setButtonData(redoButton, bWidth, bHeight, R.raw.right, svg);
        setButtonData(blindButton, bWidth, bHeight, R.raw.blind, svg);
        setButtonData(flipButton, bWidth, bHeight, R.raw.flip, svg);
    }

    @SuppressWarnings("deprecation")
    private void setButtonData(ImageButton button, int bWidth, int bHeight,
                                     int svgResId, SVG touched) {
        SVG svg = null;
        try {
            svg = SVG.getFromResource(getResources(), svgResId);
        } catch (SVGParseException ignore ) {
        }
        button.setBackgroundDrawable(new SVGPictureDrawable(svg));

        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, new SVGPictureDrawable(touched));
        button.setImageDrawable(sld);

        LayoutParams lp = button.getLayoutParams();
        lp.height = bHeight;
        lp.width = bWidth;
        button.setLayoutParams(lp);
        button.setPadding(0,0,0,0);
        button.setScaleType(ScaleType.FIT_XY);
    }

    @SuppressLint("Wakelock")
    private synchronized void setWakeLock(boolean enableLock) {
        if (enableLock)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** TODO Remove this too
     * Update center field in second header line. */
    /*public final void updateTimeControlTitle() {
        int[] tmpInfo = ctrl.getTimeLimit();
        StringBuilder sb = new StringBuilder();
        int tc = tmpInfo[0];
        int mps = tmpInfo[1];
        int inc = tmpInfo[2];
        if (mps > 0) {
            sb.append(mps);
            sb.append("/");
        }
        sb.append(timeToString(tc));
        if ((inc > 0) || (mps <= 0)) {
            sb.append("+");
            sb.append(tmpInfo[2] / 1000);
        }
        summaryTitleText.setText(sb.toString());
    }
*/

    @Override
    public void updateMaterialDifferenceTitle(Util.MaterialDiff diff) {
        whiteFigText.setText(diff.white);
        blackFigText.setText(diff.black);
    }

    private void setBookOptions() {
        BookOptions options = new BookOptions(bookOptions);
        if (options.filename.isEmpty())
            options.filename = "internal:";
        if (!options.filename.endsWith(":")) {
            String sep = File.separator;
            if (!options.filename.startsWith(sep)) {
                File extDir = Environment.getExternalStorageDirectory();
                options.filename = extDir.getAbsolutePath() + sep + bookDir + sep + options.filename;
            }
        }
        ctrl.setBookOptions(options);
    }

    private boolean egtbForceReload = false;

    private class DrawerItem {
        DrawerItemId id;
        private int resId; // Item string resource id

        DrawerItem(DrawerItemId id, int resId) {
            this.id = id;
            this.resId = resId;
        }

        @Override
        public String toString() {
            return getString(resId);
        }
    }

    private enum DrawerItemId {
        NEW_GAME,
        SET_STRENGTH,
        EDIT_BOARD,
        SETTINGS,
        FILE_MENU,
        RESIGN,
        FORCE_MOVE,
        DRAW,
        SELECT_BOOK,
        MANAGE_ENGINES,
        SET_COLOR_THEME,
        ABOUT,
    }

    /** Initialize the drawer part of the user interface. */
    private void initDrawers() {
        drawerLayout = findViewById(R.id.drawer_layout);
        leftDrawer = findViewById(R.id.left_drawer);
        rightDrawer = findViewById(R.id.right_drawer);

        final DrawerItem[] leftItems = new DrawerItem[] {
            new DrawerItem(DrawerItemId.NEW_GAME, R.string.option_new_game),
            new DrawerItem(DrawerItemId.EDIT_BOARD, R.string.option_edit_board),
            new DrawerItem(DrawerItemId.FILE_MENU, R.string.option_file),
            new DrawerItem(DrawerItemId.SET_COLOR_THEME, R.string.option_color_theme),
            new DrawerItem(DrawerItemId.SETTINGS, R.string.option_settings),
            new DrawerItem(DrawerItemId.ABOUT, R.string.option_about),
        };
        leftDrawer.setAdapter(new ArrayAdapter<>(this,
                                                 R.layout.drawer_list_item,
                                                 leftItems));
        leftDrawer.setOnItemClickListener((parent, view, position, id) -> {
            DrawerItem di = leftItems[position];
            handleDrawerSelection(di.id);
        });

        final DrawerItem[] rightItems = new DrawerItem[] {
            new DrawerItem(DrawerItemId.RESIGN, R.string.option_resign_game),
            new DrawerItem(DrawerItemId.DRAW, R.string.option_draw),
        };
        rightDrawer.setAdapter(new ArrayAdapter<>(this,
                                                  R.layout.drawer_list_item,
                                                  rightItems));
        rightDrawer.setOnItemClickListener((parent, view, position, id) -> {
            DrawerItem di = rightItems[position];
            handleDrawerSelection(di.id);
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        drawerLayout.openDrawer(Gravity.LEFT);
        return false;
    }

    /** React to a selection in the left/right drawers. */
    private void handleDrawerSelection(DrawerItemId id) {
        drawerLayout.closeDrawer(Gravity.LEFT);
        drawerLayout.closeDrawer(Gravity.RIGHT);
        leftDrawer.clearChoices();
        rightDrawer.clearChoices();

        setAutoMode(AutoMode.OFF);

        switch (id) {
        case NEW_GAME:
            showDialog(NEW_GAME_DIALOG);
            break;
        case SET_STRENGTH:
            reShowDialog(SET_STRENGTH_DIALOG);
            break;
        case EDIT_BOARD:
            startEditBoard(ctrl.getFEN());
            break;
        case SETTINGS: {
            Intent i = new Intent(CaecusChess.this, Preferences.class);
            startActivityForResult(i, RESULT_SETTINGS);
            break;
        }
        case FILE_MENU:
            if (storageAvailable())
                reShowDialog(FILE_MENU_DIALOG);
            break;
        case RESIGN:
            if (ctrl.humansTurn())
                ctrl.resignGame();
            break;
        case DRAW:
            if (ctrl.claimDrawIfPossible()){}
            else
                CaecusChessApp.toast(R.string.offer_draw, Toast.LENGTH_SHORT);
            break;
        case SELECT_BOOK:
            if (storageAvailable())
                reShowDialog(SELECT_BOOK_DIALOG);
            break;
        case MANAGE_ENGINES:
            if (storageAvailable())
                reShowDialog(MANAGE_ENGINES_DIALOG);
            else
                reShowDialog(SELECT_ENGINE_DIALOG_NOMANAGE);
            break;
        case SET_COLOR_THEME:
            showDialog(SET_COLOR_THEME_DIALOG);
            break;
        case ABOUT:
            showDialog(ABOUT_DIALOG);
            break;
        }
    }

    static private final int RESULT_EDITBOARD   =  0;
    static private final int RESULT_SETTINGS    =  1;
    static private final int RESULT_LOAD_PGN    =  2;
    static private final int RESULT_LOAD_FEN    =  3;
    static private final int RESULT_SAVE_PGN    =  4;
    static private final int RESULT_SELECT_SCID =  5;
    static private final int RESULT_OI_PGN_SAVE =  6;
    static private final int RESULT_OI_PGN_LOAD =  7;
    static private final int RESULT_OI_FEN_LOAD =  8;
    static private final int RESULT_GET_FEN     =  9;
    static private final int RESULT_EDITOPTIONS = 10;

    private void startEditBoard(String fen) {
        Intent i = new Intent(CaecusChess.this, EditBoard.class);
        i.setAction(fen);
        startActivityForResult(i, RESULT_EDITBOARD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case RESULT_SETTINGS:
            handlePrefsChange();
            break;
        case RESULT_EDITBOARD:
            if (resultCode == RESULT_OK) {
                try {
                    String fen = data.getAction();
                    ctrl.setFENOrPGN(fen, true);
                    setBoardFlip(false);
                } catch (ChessError ignore) {
                }
            }
            break;
        case RESULT_LOAD_PGN:
            if (resultCode == RESULT_OK) {
                try {
                    String pgnToken = data.getAction();
                    String pgn = cache.retrieveString(pgnToken);
                    int modeNr = ctrl.getGameMode().getModeNr();
                    if ((modeNr != GameMode.ANALYSIS) && (modeNr != GameMode.EDIT_GAME))
                        newGameMode(GameMode.EDIT_GAME);
                    ctrl.setFENOrPGN(pgn, false);
                    setBoardFlip(true);
                } catch (ChessError e) {
                    CaecusChessApp.toast(getParseErrString(e), Toast.LENGTH_SHORT);
                }
            }
            break;
        case RESULT_SAVE_PGN:
            if (resultCode == RESULT_OK) {
                long hash = data.getLongExtra("com.example.caecuschess.treeHash", -1);
                ctrl.setLastSaveHash(hash);
            }
            break;
        case RESULT_SELECT_SCID:
            if (resultCode == RESULT_OK) {
                String pathName = data.getAction();
                if (pathName != null) {
                    Editor editor = settings.edit();
                    editor.putString("currentScidFile", pathName);
                    editor.putInt("currFT", FT_SCID);
                    editor.apply();
                    Intent i = new Intent(CaecusChess.this, LoadScid.class);
                    i.setAction("com.example.caecuschess.loadScid");
                    i.putExtra("com.example.caecuschess.pathname", pathName);
                    startActivityForResult(i, RESULT_LOAD_PGN);
                }
            }
            break;
        case RESULT_OI_PGN_LOAD:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null)
                    loadPGNFromFile(pathName);
            }
            break;
        case RESULT_OI_PGN_SAVE:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null) {
                    if ((pathName.length() > 0) && !pathName.contains("."))
                        pathName += ".pgn";
                    savePGNToFile(pathName);
                }
            }
            break;
        case RESULT_OI_FEN_LOAD:
            if (resultCode == RESULT_OK) {
                String pathName = FileUtil.getFilePathFromUri(data.getData());
                if (pathName != null)
                    loadFENFromFile(pathName);
            }
            break;
        case RESULT_GET_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getStringExtra(Intent.EXTRA_TEXT);
                if (fen == null) {
                    String pathName = FileUtil.getFilePathFromUri(data.getData());
                    loadFENFromFile(pathName);
                }
                setFenHelper(fen, true);
            }
            break;
        case RESULT_LOAD_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getAction();
                setFenHelper(fen, false);
            }
            break;
        //TODO Remove if unneccessary
        /*case RESULT_EDITOPTIONS:
            if (resultCode == RESULT_OK) {
                @SuppressWarnings("unchecked")
                Map<String,String> uciOpts =
                    (Map<String,String>)data.getSerializableExtra("com.example.caecuschess.ucioptions");
            }
            break;*/
        }
    }

    /** Set new game mode. */
    private void newGameMode(int gameModeType) {
        Editor editor = settings.edit();
        String gameModeStr = String.format(Locale.US, "%d", gameModeType);
        editor.putString("gameMode", gameModeStr);
        editor.apply();
        gameMode = new GameMode(gameModeType);
        maybeAutoModeOff(gameMode);
        ctrl.setGameMode(gameMode);
    }

    private String getParseErrString(ChessError e) {
        if (e.resourceID == -1)
            return e.getMessage();
        else
            return getString(e.resourceID);
    }

    private int nameMatchScore(String name, String match) {
        if (name == null)
            return 0;
        String lName = name.toLowerCase(Locale.US);
        String lMatch = match.toLowerCase(Locale.US);
        if (name.equals(match))
            return 6;
        if (lName.equals(lMatch))
            return 5;
        if (name.startsWith(match))
            return 4;
        if (lName.startsWith(lMatch))
            return 3;
        if (name.contains(match))
            return 2;
        if (lName.contains(lMatch))
            return 1;
        return 0;
    }

    private void setBoardFlip() {
        setBoardFlip(false);
    }

    /** Set a boolean preference setting. */
    private void setBooleanPref(String name, boolean value) {
        Editor editor = settings.edit();
        editor.putBoolean(name, value);
        editor.apply();
    }

    /** Toggle a boolean preference setting. Return new value. */
    private boolean toggleBooleanPref(String name) {
        boolean value = !settings.getBoolean(name, false);
        setBooleanPref(name, value);
        return value;
    }

    private void setBoardFlip(boolean matchPlayerNames) {
        boolean flipped = boardFlipped;
        if (playerNameFlip && matchPlayerNames && (ctrl != null)) {
            final TreeMap<String,String> headers = new TreeMap<>();
            ctrl.getHeaders(headers);
            int whiteMatch = nameMatchScore(headers.get("White"), playerName);
            int blackMatch = nameMatchScore(headers.get("Black"), playerName);
            if (( flipped && (whiteMatch > blackMatch)) ||
                (!flipped && (whiteMatch < blackMatch))) {
                flipped = !flipped;
                boardFlipped = flipped;
                setBooleanPref("boardFlipped", flipped);
            }
        }
        if (autoSwapSides) {
            if (gameMode.analysisMode()) {
                flipped = !cb.pos.whiteMove;
            } else if (gameMode.playerWhite() && gameMode.playerBlack()) {
                flipped = !cb.pos.whiteMove;
            } else if (gameMode.playerWhite()) {
                flipped = false;
            } else if (gameMode.playerBlack()) {
                flipped = true;
            } else { // two computers
                flipped = !cb.pos.whiteMove;
            }
        }
        cb.setFlipped(flipped);
    }

    @Override
    public void setSelection(int sq) {
        cb.setSelection(cb.highlightLastMove ? sq : -1);
        cb.userSelectedSquare = false;
    }

    @Override
    public void setStatus(GameStatus s) {
        String str;
        switch (s.state) {
        case ALIVE:
            str = Integer.valueOf(s.moveNr).toString();
            if (s.white)
                str += ". " + getString(R.string.whites_move);
            else
                str += "... " + getString(R.string.blacks_move);
            break;
        case WHITE_MATE:
            str = getString(R.string.white_mate);
            break;
        case BLACK_MATE:
            str = getString(R.string.black_mate);
            break;
        case WHITE_STALEMATE:
        case BLACK_STALEMATE:
            str = getString(R.string.stalemate);
            break;
        case DRAW_REP: {
            str = getString(R.string.draw_rep);
            if (s.drawInfo.length() > 0)
                str = str + " [" + s.drawInfo + "]";
            break;
        }
        case DRAW_50: {
            str = getString(R.string.draw_50);
            if (s.drawInfo.length() > 0)
                str = str + " [" + s.drawInfo + "]";
            break;
        }
        case DRAW_NO_MATE:
            str = getString(R.string.draw_no_mate);
            break;
        case DRAW_AGREE:
            str = getString(R.string.draw_agree);
            break;
        case RESIGN_WHITE:
            str = getString(R.string.resign_white);
            break;
        case RESIGN_BLACK:
            str = getString(R.string.resign_black);
            break;
        default:
            throw new RuntimeException();
        }
        setStatusString(str);
    }

    private void setStatusString(String str) {
        status.setText(str);
    }

    @Override
    public void moveListUpdated() {
        moveList.setText(gameTextListener.getText());
        int currPos = gameTextListener.getCurrPos();
        int line = moveList.getLineForOffset(currPos);
        if (line >= 0 && autoScrollMoveList) {
            int y = moveList.getLineStartY(line - 1);
            moveListScroll.scrollTo(0, y);
        }
    }

    @Override
    public String playerName() {
        return playerName;
    }

    @Override
    public boolean discardVariations() {
        return discardVariations;
    }

    /** Report a move made that is a candidate for GUI animation. */
    public void setAnimMove(Position sourcePos, Move move, boolean forward) {
        if (animateMoves && (move != null))
            cb.setAnimMove(sourcePos, move, forward);
    }

    @Override
    public void setPosition(Position pos, String variantInfo, ArrayList<Move> variantMoves) {
        variantStr = variantInfo;
        this.variantMoves = variantMoves;
        cb.setPosition(pos);
        setBoardFlip();
    }

    private String bookInfoStr = "";
    private String ecoInfoStr = "";
    private int distToEcoTree = 0;
    private String variantStr = "";
    private ArrayList<ArrayList<Move>> pvMoves = new ArrayList<>();
    private ArrayList<Move> bookMoves = null;
    private ArrayList<Move> variantMoves = null;

    /** Truncate line to max "maxLen" characters. Truncates at
     *  space character if possible. */
    private String truncateLine(String line, int maxLen) {
        if (line.length() <= maxLen || maxLen <= 0)
            return line;
        int idx = line.lastIndexOf(' ', maxLen-1);
        if (idx > 0)
            return line.substring(0, idx);
        return line.substring(0, maxLen);
    }

    static private final int PROMOTE_DIALOG = 0;
    static         final int BOARD_MENU_DIALOG = 1;
    static private final int ABOUT_DIALOG = 2;
    static private final int SELECT_BOOK_DIALOG = 4;
    static private final int SELECT_ENGINE_DIALOG_NOMANAGE = 6;
    static private final int SELECT_PGN_FILE_DIALOG = 7;
    static private final int SELECT_PGN_FILE_SAVE_DIALOG = 8;
    static private final int SET_COLOR_THEME_DIALOG = 9;
    static private final int GAME_MODE_DIALOG = 10;
    static private final int SELECT_PGN_SAVE_NEWFILE_DIALOG = 11;
    static private final int MOVELIST_MENU_DIALOG = 12;
    static private final int THINKING_MENU_DIALOG = 13;
    static private final int GO_BACK_MENU_DIALOG = 14;
    static private final int GO_FORWARD_MENU_DIALOG = 15;
    static private final int FILE_MENU_DIALOG = 16;
    static private final int NEW_GAME_DIALOG = 17;
    static private final int MANAGE_ENGINES_DIALOG = 21;
    static private final int CLIPBOARD_DIALOG = 26;
    static private final int SELECT_FEN_FILE_DIALOG = 27;
    static private final int SET_STRENGTH_DIALOG = 28;

    /** Remove and show a dialog. */
    void reShowDialog(int id) {
        removeDialog(id);
        showDialog(id);
    }

    //TODO onCreateDialog
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case NEW_GAME_DIALOG:                return newGameDialog();
        case PROMOTE_DIALOG:                 return promoteDialog();
        case BOARD_MENU_DIALOG:              return boardMenuDialog();
        case FILE_MENU_DIALOG:               return fileMenuDialog();
        // case ABOUT_DIALOG:                   return aboutDialog();
        case SELECT_PGN_FILE_DIALOG:         return selectPgnFileDialog();
        case SELECT_PGN_FILE_SAVE_DIALOG:    return selectPgnFileSaveDialog();
        case SELECT_PGN_SAVE_NEWFILE_DIALOG: return selectPgnSaveNewFileDialog();
        case SET_COLOR_THEME_DIALOG:         return setColorThemeDialog();
        case GAME_MODE_DIALOG:               return gameModeDialog();
        case MOVELIST_MENU_DIALOG:           return moveListMenuDialog();
        case GO_BACK_MENU_DIALOG:            return goBackMenuDialog();
        case GO_FORWARD_MENU_DIALOG:         return goForwardMenuDialog();
        case CLIPBOARD_DIALOG:               return clipBoardDialog();
        case SELECT_FEN_FILE_DIALOG:         return selectFenFileDialog();
        }
        return null;
    }

    private Dialog newGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.option_new_game);
        builder.setMessage(R.string.start_new_game);
        builder.setNeutralButton(R.string.yes, (dialog, which) -> startNewGame());
        return builder.create();
    }

    private void startNewGame() {
        ctrl.newGame(gameMode);
        ctrl.startGame();
        setBoardFlip(true);
    }

    private Dialog promoteDialog() {
        final String[] items = {
            getString(R.string.queen), getString(R.string.rook),
            getString(R.string.bishop), getString(R.string.knight)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.promote_pawn_to);
        builder.setItems(items, (dialog, item) -> ctrl.reportPromotePiece(item));
        return builder.create();
    }

    private Dialog clipBoardDialog() {
        final int COPY_GAME      = 0;
        final int COPY_POSITION  = 1;
        final int PASTE          = 2;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.copy_game));     actions.add(COPY_GAME);
        lst.add(getString(R.string.copy_position)); actions.add(COPY_POSITION);
        lst.add(getString(R.string.paste));         actions.add(PASTE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tools_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case COPY_GAME: {
                String pgn = ctrl.getPGN();
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData("CaecusChess game",
                        new String[]{ "application/x-chess-pgn", ClipDescription.MIMETYPE_TEXT_PLAIN },
                        new ClipData.Item(pgn)));
                break;
            }
            case COPY_POSITION: {
                String fen = ctrl.getFEN() + "\n";
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData(fen,
                        new String[]{ "application/x-chess-fen", ClipDescription.MIMETYPE_TEXT_PLAIN },
                        new ClipData.Item(fen)));
                break;
            }
            case PASTE: {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null) {
                    StringBuilder fenPgn = new StringBuilder();
                    for (int i = 0; i < clip.getItemCount(); i++)
                        fenPgn.append(clip.getItemAt(i).coerceToText(getApplicationContext()));
                    try {
                        String fenPgnData = fenPgn.toString();
                        ArrayList<GameInfo> gi = PGNFile.getGameInfo(fenPgnData, 2);
                        if (gi.size() > 1) {
                            String sep = File.separator;
                            String fn = Environment.getExternalStorageDirectory() + sep +
                                        pgnDir + sep + ".sharedfile.pgn";
                            try (FileOutputStream writer = new FileOutputStream(fn)) {
                                writer.write(fenPgnData.getBytes());
                                writer.close();
                                loadPGNFromFile(fn);
                            } catch (IOException ex) {
                                ctrl.setFENOrPGN(fenPgnData, true);
                            }
                        } else {
                            ctrl.setFENOrPGN(fenPgnData, true);
                        }
                        setBoardFlip(true);
                    } catch (ChessError e) {
                        CaecusChessApp.toast(getParseErrString(e), Toast.LENGTH_SHORT);
                    }
                }
                break;
            }
            }
        });
        return builder.create();
    }

    private Dialog boardMenuDialog() {
        final int CLIPBOARD        = 0;
        final int FILEMENU         = 1;
        final int SHARE_GAME       = 2;
        final int SHARE_TEXT       = 3;
        final int SHARE_IMAG       = 4;
        final int GET_FEN          = 5;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.clipboard));     actions.add(CLIPBOARD);
        if (storageAvailable()) {
            lst.add(getString(R.string.option_file));   actions.add(FILEMENU);
        }
        lst.add(getString(R.string.share_game));         actions.add(SHARE_GAME);
        lst.add(getString(R.string.share_text));         actions.add(SHARE_TEXT);
        lst.add(getString(R.string.share_image));        actions.add(SHARE_IMAG);
        if (hasFenProvider(getPackageManager())) {
            lst.add(getString(R.string.get_fen)); actions.add(GET_FEN);
        }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tools_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
                case CLIPBOARD:
                    showDialog(CLIPBOARD_DIALOG);
                    break;
                case FILEMENU:
                    reShowDialog(FILE_MENU_DIALOG);
                    break;
                case SHARE_GAME:
                    shareGameOrText(true);
                    break;
                case SHARE_TEXT:
                    shareGameOrText(false);
                    break;
                case SHARE_IMAG:
                    shareImage();
                    break;
                case GET_FEN:
                    getFen();
                    break;
            }
        });
        return builder.create();
    }

    private void shareGameOrText(boolean game) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        i.setType(game ? "application/x-chess-pgn" : "text/plain");
        String pgn = ctrl.getPGN();
        if (pgn.length() < 32768) {
            i.putExtra(Intent.EXTRA_TEXT, pgn);
        } else {
            File dir = new File(getFilesDir(), "shared");
            dir.mkdirs();
            File file = new File(dir, game ? "game.pgn" : "game.txt");
            try (FileOutputStream fos = new FileOutputStream(file);
                 OutputStreamWriter ow = new OutputStreamWriter(fos, "UTF-8")) {
                ow.write(pgn);
            } catch (IOException e) {
                CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
                return;
            }
            String authority = "com.example.caecuschess.fileprovider";
            Uri uri = FileProvider.getUriForFile(this, authority, file);
            i.putExtra(Intent.EXTRA_STREAM, uri);
        }
        try {
            startActivity(Intent.createChooser(i, getString(game ? R.string.share_game :
                                                                   R.string.share_text)));
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private void shareImage() {
        View v = findViewById(R.id.chessboard);
        int w = v.getWidth();
        int h = v.getHeight();
        if (w <= 0 || h <= 0)
            return;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.draw(c);
        File imgDir = new File(getFilesDir(), "shared");
        imgDir.mkdirs();
        File file = new File(imgDir, "screenshot.png");
        try {
            try (OutputStream os = new FileOutputStream(file)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
        } catch (IOException e) {
            CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
            return;
        }

        String authority = "com.example.caecuschess.fileprovider";
        Uri uri = FileProvider.getUriForFile(this, authority, file);

        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setType("image/png");
        try {
            startActivity(Intent.createChooser(i, getString(R.string.share_image)));
        } catch (ActivityNotFoundException ignore) {
        }
    }

    private Dialog fileMenuDialog() {
        final int LOAD_LAST_FILE    = 0;
        final int LOAD_GAME         = 1;
        final int LOAD_POS          = 2;
        final int LOAD_SCID_GAME    = 3;
        final int SAVE_GAME         = 4;
        final int LOAD_DELETED_GAME = 5;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        if (currFileType() != FT_NONE) {
            lst.add(getString(R.string.load_last_file)); actions.add(LOAD_LAST_FILE);
        }
        lst.add(getString(R.string.load_game));     actions.add(LOAD_GAME);
        lst.add(getString(R.string.load_position)); actions.add(LOAD_POS);
        if (hasScidProvider()) {
            lst.add(getString(R.string.load_scid_game)); actions.add(LOAD_SCID_GAME);
        }
        if (storageAvailable() && (new File(getAutoSaveFile())).exists()) {
            lst.add(getString(R.string.load_del_game));  actions.add(LOAD_DELETED_GAME);
        }
        lst.add(getString(R.string.save_game));     actions.add(SAVE_GAME);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.load_save_menu);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case LOAD_LAST_FILE:
                loadLastFile();
                break;
            case LOAD_GAME:
                selectFile(R.string.select_pgn_file, R.string.pgn_load, "currentPGNFile", pgnDir,
                              SELECT_PGN_FILE_DIALOG, RESULT_OI_PGN_LOAD);
                break;
            case SAVE_GAME:
                selectFile(R.string.select_pgn_file_save, R.string.pgn_save, "currentPGNFile", pgnDir,
                              SELECT_PGN_FILE_SAVE_DIALOG, RESULT_OI_PGN_SAVE);
                break;
            case LOAD_POS:
                selectFile(R.string.select_fen_file, R.string.pgn_load, "currentFENFile", fenDir,
                              SELECT_FEN_FILE_DIALOG, RESULT_OI_FEN_LOAD);
                break;
            case LOAD_SCID_GAME:
                selectScidFile();
                break;
            case LOAD_DELETED_GAME:
                loadPGNFromFile(getAutoSaveFile(), false);
                break;
            }
        });
        return builder.create();
    }

    /** Open dialog to select a game/position from the last used file. */
    private void loadLastFile() {
        String path = currPathName();
        if (path.length() == 0)
            return;
        setAutoMode(AutoMode.OFF);
        switch (currFileType()) {
        case FT_PGN:
            loadPGNFromFile(path);
            break;
        case FT_SCID: {
            Intent data = new Intent(path);
            onActivityResult(RESULT_SELECT_SCID, RESULT_OK, data);
            break;
        }
        case FT_FEN:
            loadFENFromFile(path);
            break;
        }
    }

    /*private Dialog selectBookDialog() {
        String[] fileNames = FileUtil.findFilesInDirectory(bookDir, filename -> {
            int dotIdx = filename.lastIndexOf(".");
            if (dotIdx < 0)
                return false;
            String ext = filename.substring(dotIdx+1);
            return ("ctg".equals(ext) || "bin".equals(ext) || "abk".equals(ext));
        });
        final int numFiles = fileNames.length;
        final String[] items = new String[numFiles + 3];
        for (int i = 0; i < numFiles; i++)
            items[i] = fileNames[i];
        items[numFiles] = getString(R.string.internal_book);
        items[numFiles + 1] = getString(R.string.eco_book);
        items[numFiles + 2] = getString(R.string.no_book);

        int defaultItem = numFiles;
        if ("eco:".equals(bookOptions.filename))
            defaultItem = numFiles + 1;
        else if ("nobook:".equals(bookOptions.filename))
            defaultItem = numFiles + 2;
        String oldName = bookOptions.filename;
        File extDir = Environment.getExternalStorageDirectory();
        String sep = File.separator;
        String defDir = extDir.getAbsolutePath() + sep + bookDir + sep;
        if (oldName.startsWith(defDir))
            oldName = oldName.substring(defDir.length());
        for (int i = 0; i < numFiles; i++) {
            if (oldName.equals(items[i])) {
                defaultItem = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_opening_book_file);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            Editor editor = settings.edit();
            final String bookFile;
            if (item == numFiles)
                bookFile = "internal:";
            else if (item == numFiles + 1)
                bookFile = "eco:";
            else if (item == numFiles + 2)
                bookFile = "nobook:";
            else
                bookFile = items[item];
            editor.putString("bookFile", bookFile);
            editor.apply();
            bookOptions.filename = bookFile;
            setBookOptions();
            dialog.dismiss();
        });
        return builder.create();
    }*/ //TODO select book dialog

    private interface Loader {
        void load(String pathName);
    }

    private Dialog selectPgnFileDialog() {
        return selectFileDialog(pgnDir, R.string.select_pgn_file, R.string.no_pgn_files,
                                "currentPGNFile", this::loadPGNFromFile);
    }

    private Dialog selectFenFileDialog() {
        return selectFileDialog(fenDir, R.string.select_fen_file, R.string.no_fen_files,
                                "currentFENFile", this::loadFENFromFile);
    }

    private Dialog selectFileDialog(final String defaultDir, int selectFileMsg, int noFilesMsg,
                                          String settingsName, final Loader loader) {
        setAutoMode(AutoMode.OFF);
        final String[] fileNames = FileUtil.findFilesInDirectory(defaultDir, null);
        final int numFiles = fileNames.length;
        if (numFiles == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.app_name).setMessage(noFilesMsg);
            return builder.create();
        }
        int defaultItem = 0;
        String currentFile = settings.getString(settingsName, "");
        currentFile = new File(currentFile).getName();
        for (int i = 0; i < numFiles; i++) {
            if (currentFile.equals(fileNames[i])) {
                defaultItem = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(selectFileMsg);
        builder.setSingleChoiceItems(fileNames, defaultItem, (dialog, item) -> {
            dialog.dismiss();
            String sep = File.separator;
            String fn = fileNames[item];
            String pathName = Environment.getExternalStorageDirectory() + sep + defaultDir + sep + fn;
            loader.load(pathName);
        });
        return builder.create();
    }

    private Dialog selectPgnFileSaveDialog() {
        setAutoMode(AutoMode.OFF);
        final String[] fileNames = FileUtil.findFilesInDirectory(pgnDir, null);
        final int numFiles = fileNames.length;
        int defaultItem = 0;
        String currentPGNFile = settings.getString("currentPGNFile", "");
        currentPGNFile = new File(currentPGNFile).getName();
        for (int i = 0; i < numFiles; i++) {
            if (currentPGNFile.equals(fileNames[i])) {
                defaultItem = i;
                break;
            }
        }
        final String[] items = new String[numFiles + 1];
        for (int i = 0; i < numFiles; i++)
            items[i] = fileNames[i];
        items[numFiles] = getString(R.string.new_file);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_pgn_file_save);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            String pgnFile;
            if (item >= numFiles) {
                dialog.dismiss();
                showDialog(SELECT_PGN_SAVE_NEWFILE_DIALOG);
            } else {
                dialog.dismiss();
                pgnFile = fileNames[item];
                String sep = File.separator;
                String pathName = Environment.getExternalStorageDirectory() + sep + pgnDir + sep + pgnFile;
                savePGNToFile(pathName);
            }
        });
        return builder.create();
    }

    private Dialog selectPgnSaveNewFileDialog() {
        setAutoMode(AutoMode.OFF);
        View content = View.inflate(this, R.layout.create_pgn_file, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(content);
        builder.setTitle(R.string.select_pgn_file_save);
        final EditText fileNameView = content.findViewById(R.id.create_pgn_filename);
        fileNameView.setText("");
        final Runnable savePGN = () -> {
            String pgnFile = fileNameView.getText().toString();
            if ((pgnFile.length() > 0) && !pgnFile.contains("."))
                pgnFile += ".pgn";
            String sep = File.separator;
            String pathName = Environment.getExternalStorageDirectory() + sep + pgnDir + sep + pgnFile;
            savePGNToFile(pathName);
        };
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> savePGN.run());
        builder.setNegativeButton(R.string.cancel, null);

        final Dialog dialog = builder.create();
        fileNameView.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                savePGN.run();
                dialog.cancel();
                return true;
            }
            return false;
        });
        return dialog;
    }

    private Dialog setColorThemeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_color_theme);
        String[] themeNames = new String[ColorTheme.themeNames.length];
        for (int i = 0; i < themeNames.length; i++)
            themeNames[i] = getString(ColorTheme.themeNames[i]);
        builder.setSingleChoiceItems(themeNames, -1, (dialog, item) -> {
            ColorTheme.instance().setTheme(settings, item);
            PieceSet.instance().readPrefs(settings);
            cb.setColors();
            gameTextListener.clear();
            ctrl.prefsChanged(false);
            dialog.dismiss();
            overrideViewAttribs();
        });
        return builder.create();
    }

    private Dialog gameModeDialog() {
        final String[] items = {
            getString(R.string.analysis_mode),
            getString(R.string.edit_replay_game),
            getString(R.string.play_white),
            getString(R.string.play_black),
            getString(R.string.two_players),
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.select_game_mode);
        builder.setItems(items, (dialog, item) -> {
            int gameModeType = -1;
            boolean matchPlayerNames = false;
            switch (item) {
            case 0: gameModeType = GameMode.ANALYSIS;      break;
            case 1: gameModeType = GameMode.EDIT_GAME;     break;
            case 2: gameModeType = GameMode.PLAYER_WHITE; matchPlayerNames = true; break;
            case 3: gameModeType = GameMode.PLAYER_BLACK; matchPlayerNames = true; break;
            case 4: gameModeType = GameMode.TWO_PLAYERS;   break;
            case 5: gameModeType = GameMode.TWO_COMPUTERS; break;
            default: break;
            }
            dialog.dismiss();
            if (gameModeType >= 0) {
                newGameMode(gameModeType);
                setBoardFlip(matchPlayerNames);
            }
        });
        return builder.create();
    }

    private Dialog moveListMenuDialog() {
        final int EDIT_HEADERS   = 0;
        final int EDIT_COMMENTS  = 1;
        final int ADD_ECO        = 2;
        final int REMOVE_SUBTREE = 3;
        final int MOVE_VAR_UP    = 4;
        final int MOVE_VAR_DOWN  = 5;
        final int ADD_NULL_MOVE  = 6;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.add_eco));           actions.add(ADD_ECO);
        lst.add(getString(R.string.truncate_gametree)); actions.add(REMOVE_SUBTREE);
        if (ctrl.canMoveVariationUp()) {
            lst.add(getString(R.string.move_var_up));   actions.add(MOVE_VAR_UP);
        }
        if (ctrl.canMoveVariationDown()) {
            lst.add(getString(R.string.move_var_down)); actions.add(MOVE_VAR_DOWN);
        }

        boolean allowNullMove =
            (gameMode.analysisMode() ||
             (gameMode.playerWhite() && gameMode.playerBlack() && !gameMode.clocksActive())) &&
             !ctrl.inCheck();
        if (allowNullMove) {
            lst.add(getString(R.string.add_null_move)); actions.add(ADD_NULL_MOVE);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_game);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case ADD_ECO:
                ctrl.addECO();
                break;
            case REMOVE_SUBTREE:
                ctrl.removeSubTree();
                break;
            case MOVE_VAR_UP:
                ctrl.moveVariation(-1);
                break;
            case MOVE_VAR_DOWN:
                ctrl.moveVariation(1);
                break;
            case ADD_NULL_MOVE:
                ctrl.makeHumanNullMove();
                break;
            }
            moveListMenuDlg = null;
        });
        AlertDialog alert = builder.create();
        moveListMenuDlg = alert;
        return alert;
    }

    private Dialog goBackMenuDialog() {
        final int GOTO_START_GAME = 0;
        final int GOTO_START_VAR  = 1;
        final int GOTO_PREV_VAR   = 2;
        final int LOAD_PREV_GAME  = 3;
        final int AUTO_BACKWARD   = 4;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_start_game));      actions.add(GOTO_START_GAME);
        lst.add(getString(R.string.goto_start_variation)); actions.add(GOTO_START_VAR);
        if (ctrl.currVariation() > 0) {
            lst.add(getString(R.string.goto_prev_variation)); actions.add(GOTO_PREV_VAR);
        }
        final UIAction prevGame = actionFactory.getAction("prevGame");
        if (prevGame.enabled()) {
            lst.add(getString(R.string.load_prev_game)); actions.add(LOAD_PREV_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_backward)); actions.add(AUTO_BACKWARD);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.go_back);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case GOTO_START_GAME: ctrl.gotoMove(0); break;
            case GOTO_START_VAR:  ctrl.gotoStartOfVariation(); break;
            case GOTO_PREV_VAR:   ctrl.changeVariation(-1); break;
            case LOAD_PREV_GAME:
                prevGame.run();
                break;
            case AUTO_BACKWARD:
                setAutoMode(AutoMode.BACKWARD);
                break;
            }
        });
        return builder.create();
    }

    private Dialog goForwardMenuDialog() {
        final int GOTO_END_VAR   = 0;
        final int GOTO_NEXT_VAR  = 1;
        final int LOAD_NEXT_GAME = 2;
        final int AUTO_FORWARD   = 3;

        setAutoMode(AutoMode.OFF);
        List<String> lst = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        lst.add(getString(R.string.goto_end_variation)); actions.add(GOTO_END_VAR);
        if (ctrl.currVariation() < ctrl.numVariations() - 1) {
            lst.add(getString(R.string.goto_next_variation)); actions.add(GOTO_NEXT_VAR);
        }
        final UIAction nextGame = actionFactory.getAction("nextGame");
        if (nextGame.enabled()) {
            lst.add(getString(R.string.load_next_game)); actions.add(LOAD_NEXT_GAME);
        }
        if (!gameMode.clocksActive()) {
            lst.add(getString(R.string.auto_forward)); actions.add(AUTO_FORWARD);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.go_forward);
        builder.setItems(lst.toArray(new String[0]), (dialog, item) -> {
            switch (actions.get(item)) {
            case GOTO_END_VAR:  ctrl.gotoMove(Integer.MAX_VALUE); break;
            case GOTO_NEXT_VAR: ctrl.changeVariation(1); break;
            case LOAD_NEXT_GAME:
                nextGame.run();
                break;
            case AUTO_FORWARD:
                setAutoMode(AutoMode.FORWARD);
                break;
            }
        });
        return builder.create();
    }

    private Dialog makeButtonDialog(ButtonActions buttonActions) {
        List<String> names = new ArrayList<>();
        final List<UIAction> actions = new ArrayList<>();

        HashSet<String> used = new HashSet<>();
        for (UIAction a : buttonActions.getMenuActions()) {
            if ((a != null) && a.enabled() && !used.contains(a.getId())) {
                names.add(getString(a.getName()));
                actions.add(a);
                used.add(a.getId());
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(buttonActions.getMenuTitle());
        builder.setItems(names.toArray(new String[0]), (dialog, item) -> {
            UIAction a = actions.get(item);
            a.run();
        });
        return builder.create();
    }


//TODO Remove, could be useful in the future tho
    /*private Dialog networkEngineDialog() {
        String[] fileNames = FileUtil.findFilesInDirectory(engineDir, filename -> {
            if (reservedEngineName(filename))
                return false;
            return EngineUtil.isNetEngine(filename);
        });
        final int numItems = fileNames.length + 1;
        final String[] items = new String[numItems];
        final String[] ids = new String[numItems];
        int idx = 0;
        String sep = File.separator;
        String base = Environment.getExternalStorageDirectory() + sep + engineDir + sep;
        for (String fileName : fileNames) {
            ids[idx] = base + fileName;
            items[idx] = fileName;
            idx++;
        }
        ids[idx] = ""; items[idx] = getString(R.string.new_engine); idx++;
        String currEngine = ctrl.getEngine();
        int defaultItem = 0;
        for (int i = 0; i < numItems; i++)
            if (ids[i].equals(currEngine)) {
                defaultItem = i;
                break;
            }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.configure_network_engine);
        builder.setSingleChoiceItems(items, defaultItem, (dialog, item) -> {
            if ((item < 0) || (item >= numItems))
                return;
            dialog.dismiss();
            if (item == numItems - 1) {
                showDialog(NEW_NETWORK_ENGINE_DIALOG);
            } else {
                networkEngineToConfig = ids[item];
                reShowDialog(NETWORK_ENGINE_CONFIG_DIALOG);
            }
        });
        builder.setOnCancelListener(dialog -> reShowDialog(MANAGE_ENGINES_DIALOG));
        return builder.create();
    }*/

    /** Open a load/save file dialog. Uses OI file manager if available. */
    private void selectFile(int titleMsg, int buttonMsg, String settingsName, String defaultDir,
                            int dialog, int result) {
        setAutoMode(AutoMode.OFF);
        String action = "org.openintents.action.PICK_FILE";
        Intent i = new Intent(action);
        String currentFile = settings.getString(settingsName, "");
        String sep = File.separator;
        if (!currentFile.contains(sep))
            currentFile = Environment.getExternalStorageDirectory() +
                          sep + defaultDir + sep + currentFile;
        i.setData(Uri.fromFile(new File(currentFile)));
        i.putExtra("org.openintents.extra.TITLE", getString(titleMsg));
        i.putExtra("org.openintents.extra.BUTTON_TEXT", getString(buttonMsg));
        try {
            startActivityForResult(i, result);
        } catch (ActivityNotFoundException e) {
            reShowDialog(dialog);
        }
    }

    private boolean hasScidProvider() {
        try {
            getPackageManager().getPackageInfo("org.scid.android", 0);
            return true;
        } catch (NameNotFoundException ex) {
            return false;
        }
    }

    private void selectScidFile() {
        setAutoMode(AutoMode.OFF);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("org.scid.android",
                                              "org.scid.android.SelectFileActivity"));
        intent.setAction(".si4");
        try {
            startActivityForResult(intent, RESULT_SELECT_SCID);
        } catch (ActivityNotFoundException e) {
            CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    public static boolean hasFenProvider(PackageManager manager) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); 
        i.setType("application/x-chess-fen");
        List<ResolveInfo> resolvers = manager.queryIntentActivities(i, 0);
        return (resolvers != null) && (resolvers.size() > 0);
    }

    private void getFen() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT); 
        i.setType("application/x-chess-fen");
        try {
            startActivityForResult(i, RESULT_GET_FEN);
        } catch (ActivityNotFoundException e) {
            CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    final static int FT_NONE = 0;
    final static int FT_PGN  = 1;
    final static int FT_SCID = 2;
    final static int FT_FEN  = 3;

    private int currFileType() {
        return settings.getInt("currFT", FT_NONE);
    }

    /** Return path name for the last used PGN or SCID file. */
    private String currPathName() {
        int ft = settings.getInt("currFT", FT_NONE);
        switch (ft) {
        case FT_PGN: {
            String ret = settings.getString("currentPGNFile", "");
            String sep = File.separator;
            if (!ret.contains(sep))
                ret = Environment.getExternalStorageDirectory() + sep + pgnDir + sep + ret;
            return ret;
        }
        case FT_SCID:
            return settings.getString("currentScidFile", "");
        case FT_FEN:
            return settings.getString("currentFENFile", "");
        default:
            return "";
        }
    }

    /** Save current game to a PGN file. */
    private void savePGNToFile(String pathName) {
        String pgn = ctrl.getPGN();
        String pgnToken = cache.storeString(pgn);
        Editor editor = settings.edit();
        editor.putString("currentPGNFile", pathName);
        editor.putInt("currFT", FT_PGN);
        editor.apply();
        Intent i = new Intent(CaecusChess.this, EditPGNSave.class);
        i.setAction("com.example.caecuschess.saveFile");
        i.putExtra("com.example.caecuschess.pathname", pathName);
        i.putExtra("com.example.caecuschess.pgn", pgnToken);
        setEditPGNBackup(i, pathName);
        startActivityForResult(i, RESULT_SAVE_PGN);
    }

    /** Set a Boolean value in the Intent to decide if backups should be made
     *  when games in a PGN file are overwritten or deleted. */
    private void setEditPGNBackup(Intent i, String pathName) {
        boolean backup = storageAvailable() && !pathName.equals(getAutoSaveFile());
        i.putExtra("com.example.caecuschess.backup", backup);
    }

    /** Get the full path to the auto-save file. */
    private static String getAutoSaveFile() {
        String sep = File.separator;
        return Environment.getExternalStorageDirectory() + sep + pgnDir + sep + ".autosave.pgn";
    }

    @Override
    public void autoSaveGameIfAllowed(String pgn) {
        if (storageAvailable())
            autoSaveGame(pgn);
    }

    /** Save a copy of the pgn data in the .autosave.pgn file. */
    public static void autoSaveGame(String pgn) {
        PGNFile pgnFile = new PGNFile(getAutoSaveFile());
        pgnFile.autoSave(pgn);
    }

    /** Load a PGN game from a file. */
    private void loadPGNFromFile(String pathName) {
        loadPGNFromFile(pathName, true);
    }

    /** Load a PGN game from a file. */
    private void loadPGNFromFile(String pathName, boolean updateCurrFile) {
        if (updateCurrFile) {
            Editor editor = settings.edit();
            editor.putString("currentPGNFile", pathName);
            editor.putInt("currFT", FT_PGN);
            editor.apply();
        }
        Intent i = new Intent(CaecusChess.this, EditPGNLoad.class);
        i.setAction("com.example.caecuschess.loadFile");
        i.putExtra("com.example.caecuschess.pathname", pathName);
        i.putExtra("com.example.caecuschess.updateDefFilePos", updateCurrFile);
        setEditPGNBackup(i, pathName);
        startActivityForResult(i, RESULT_LOAD_PGN);
    }

    /** Load a FEN position from a file. */
    private void loadFENFromFile(String pathName) {
        if (pathName == null)
            return;
        Editor editor = settings.edit();
        editor.putString("currentFENFile", pathName);
        editor.putInt("currFT", FT_FEN);
        editor.apply();
        Intent i = new Intent(CaecusChess.this, LoadFEN.class);
        i.setAction("com.example.caecuschess.loadFen");
        i.putExtra("com.example.caecuschess.pathname", pathName);
        startActivityForResult(i, RESULT_LOAD_FEN);
    }

    private void setFenHelper(String fen, boolean setModified) {
        if (fen == null)
            return;
        try {
            ctrl.setFENOrPGN(fen, setModified);
        } catch (ChessError e) {
            // If FEN corresponds to illegal chess position, go into edit board mode.
            try {
                TextInfo.readFEN(fen);
            } catch (ChessError e2) {
                if (e2.pos != null)
                    startEditBoard(TextInfo.intoFEN(e2.pos));
            }
        }
    }

    @Override
    public void requestPromotePiece() {
        showDialog(PROMOTE_DIALOG);
    }

    @Override
    public void reportInvalidMove(Move m) {
        String msg = String.format(Locale.US, "%s %s-%s",
                                   getString(R.string.invalid_move),
                                   TextInfo.squareToString(m.SQfrom), TextInfo.squareToString(m.SQto));
        CaecusChessApp.toast(msg, Toast.LENGTH_SHORT);
    }

    @Override
    public void runOnUIThread(Runnable runnable) {
        runOnUiThread(runnable);
    }

    private boolean notificationActive = false;
    private NotificationChannel notificationChannel = null;

    /** Set/clear the "heavy CPU usage" notification. */
    private void setNotification(boolean show) {
        if (notificationActive == show)
            return;
        notificationActive = show;

        final int cpuUsage = 1;
        Context context = getApplicationContext();
        NotificationManagerCompat notificationManagerCompat =
                NotificationManagerCompat.from(context);

        if (show) {
            final int sdkVer = Build.VERSION.SDK_INT;
            String channelId = "general";
            if (notificationChannel == null && sdkVer >= 26) {
                notificationChannel = new NotificationChannel(channelId, "General",
                                                              NotificationManager.IMPORTANCE_HIGH);
                NotificationManager notificationManager =
                        (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.createNotificationChannel(notificationChannel);
            }

            int icon = R.mipmap.ic_launcher;
            String tickerText = getString(R.string.heavy_cpu_usage);
            String contentTitle = getString(R.string.background_processing);
            String contentText = getString(R.string.lot_cpu_power);
            Intent notificationIntent = new Intent(this, CPUWarning.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            Notification notification = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(icon)
                    .setTicker(tickerText)
                    .setOngoing(true)
                    .setContentTitle(contentTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .build();
            notificationManagerCompat.notify(cpuUsage, notification);
        } else {
            notificationManagerCompat.cancel(cpuUsage);
        }
    }

    private String timeToString(int time) {
        int secs = (int)Math.floor((time + 999) / 1000.0);
        boolean neg = false;
        if (secs < 0) {
            neg = true;
            secs = -secs;
        }
        int mins = secs / 60;
        secs -= mins * 60;
        StringBuilder ret = new StringBuilder();
        if (neg) ret.append('-');
        ret.append(mins);
        ret.append(':');
        if (secs < 10) ret.append('0');
        ret.append(secs);
        return ret.toString();
    }

    private Handler handlerTimer = new Handler();

    private Handler autoModeTimer = new Handler();
    private Runnable amRunnable = () -> {
        switch (autoMode) {
        case BACKWARD:
            ctrl.undoMove();
            setAutoMode(autoMode);
            break;
        case FORWARD:
            ctrl.redoMove();
            setAutoMode(autoMode);
            break;
        case OFF:
            break;
        }
    };

    /** Set automatic move forward/backward mode. */
    void setAutoMode(AutoMode am) {
        autoMode = am;
        switch (am) {
        case BACKWARD:
        case FORWARD:
            if (autoMoveDelay > 0)
                autoModeTimer.postDelayed(amRunnable, autoMoveDelay);
            break;
        case OFF:
            autoModeTimer.removeCallbacks(amRunnable);
            break;
        }
    }

    /** Disable automatic move mode if clocks are active. */
    void maybeAutoModeOff(GameMode gm) {
        if (gm.clocksActive())
            setAutoMode(AutoMode.OFF);
    }

    /** Go to given node in game tree. */
    public void goNode(Node node) {
        if (ctrl == null)
            return;

        // On android 4.1 this onClick method is called
        // even when you long click the move list. The test
        // below works around the problem.
        Dialog mlmd = moveListMenuDlg;
        if ((mlmd == null) || !mlmd.isShowing()) {
            setAutoMode(AutoMode.OFF);
            ctrl.goNode(node);
        }
    }
}
