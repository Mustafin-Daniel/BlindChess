package com.example.caecuschess.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.MotionEventCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.caecuschess.CaecusChess;
import com.example.caecuschess.CaecusChessApp;
import com.example.caecuschess.FileUtil;
import com.example.caecuschess.GameEngine.ChessError;
import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Piece;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;
import com.example.caecuschess.R;
import com.example.caecuschess.Util;
import com.example.caecuschess.Util.MaterialDiff;
import com.example.caecuschess.activities.util.ChessBoardEdit;
import com.example.caecuschess.view.ChessBoard.SquareDecoration;
import com.example.caecuschess.tb.Probe;
import com.example.caecuschess.tb.ProbeResult;

import java.util.ArrayList;
import java.util.Locale;

@SuppressLint("ClickableViewAccessibility")
public class EditBoard extends Activity {
    private ChessBoardEdit cb;
    private TextView status;

    private boolean egtbHints;
    private boolean autoScrollTitle;
    private boolean boardFlipped;
    private TextView whiteFigText;
    private TextView blackFigText;
    private Typeface figNotation;

    private DrawerLayout drawerLayout;
    private ListView leftDrawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        figNotation = Typeface.createFromAsset(getAssets(), "fonts/CaecusChessChessNotationDark.otf");

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        egtbHints = settings.getBoolean("tbHintsEdit", false);
        autoScrollTitle = settings.getBoolean("autoScrollTitle", true);
        boardFlipped = settings.getBoolean("boardFlipped", false);

        initUI();

        Util.setFullScreenMode(this, settings);

        Intent i = getIntent();
        Position pos;
        try {
            pos = TextInfo.readFEN(i.getAction());
        } catch (ChessError e) {
            pos = e.pos;
        }
        if (pos != null)
            cb.setPosition(pos);
        checkValidAndUpdateMaterialDiff();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CaecusChessApp.setLanguage(newBase, false));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ChessBoardEdit oldCB = cb;
        String statusStr = status.getText().toString();
        initUI();
        cb.setPosition(oldCB.pos);
        setSelection(oldCB.selectedSquare);
        cb.userSelectedSquare = oldCB.userSelectedSquare;
        status.setText(statusStr);
        checkValidAndUpdateMaterialDiff();
    }

    private void initUI() {
        setContentView(R.layout.editboard);
        Util.overrideViewAttribs(findViewById(R.id.main));

        View firstTitleLine = findViewById(R.id.first_title_line);
        View secondTitleLine = findViewById(R.id.second_title_line);
        cb = findViewById(R.id.eb_chessboard);
        cb.setFlipped(boardFlipped);
        status = findViewById(R.id.eb_status);
        Button okButton = findViewById(R.id.eb_ok);
        Button cancelButton = findViewById(R.id.eb_cancel);

        TextView whiteTitleText = findViewById(R.id.white_clock);
        whiteTitleText.setVisibility(View.GONE);
        TextView blackTitleText = findViewById(R.id.black_clock);
        blackTitleText.setVisibility(View.GONE);
        TextView engineTitleText = findViewById(R.id.title_text);
        engineTitleText.setVisibility(View.GONE);
        whiteFigText = findViewById(R.id.white_pieces);
        whiteFigText.setTypeface(figNotation);
        whiteFigText.setSelected(true);
        whiteFigText.setTextColor(whiteTitleText.getTextColors());
        blackFigText = findViewById(R.id.black_pieces);
        blackFigText.setTypeface(figNotation);
        blackFigText.setSelected(true);
        blackFigText.setTextColor(blackTitleText.getTextColors());
        TextView summaryTitleText = findViewById(R.id.title_text_summary);
        summaryTitleText.setText(R.string.edit_board);

        TextUtils.TruncateAt where = autoScrollTitle ? TextUtils.TruncateAt.MARQUEE
                                                     : TextUtils.TruncateAt.END;
        engineTitleText.setEllipsize(where);
        whiteFigText.setEllipsize(where);
        blackFigText.setEllipsize(where);

        initDrawers();

        OnClickListener listener = v -> drawerLayout.openDrawer(Gravity.LEFT);
        firstTitleLine.setOnClickListener(listener);
        secondTitleLine.setOnClickListener(listener);

        okButton.setOnClickListener(v -> sendBackResult());
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        status.setFocusable(false);
        cb.setFocusable(true);
        cb.requestFocus();
        cb.setClickable(true);
        cb.setOnTouchListener(new OnTouchListener() {
            private boolean pending = false;
            private int sq0 = -1;
            private Handler handler = new Handler();
            private Runnable runnable = new Runnable() {
                public void run() {
                    pending = false;
                    handler.removeCallbacks(runnable);
                    drawerLayout.openDrawer(Gravity.LEFT);
                }
            };
            public boolean onTouch(View v, MotionEvent event) {
                int action = MotionEventCompat.getActionMasked(event);
                switch (action) {
                case MotionEvent.ACTION_DOWN:
                    handler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout());
                    sq0 = cb.eventToSquare(event);
                    pending = true;
                    break;
                case MotionEvent.ACTION_UP:
                    if (pending) {
                        pending = false;
                        handler.removeCallbacks(runnable);
                        int sq = cb.eventToSquare(event);
                        if (sq == sq0) {
                            Move m = cb.mousePressed(sq);
                            if (m != null)
                                doMove(m);
                            setEgtbHints(cb.getSelectedSquare());
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    pending = false;
                    handler.removeCallbacks(runnable);
                    break;
                }
                return true;
            }
        });
    }

    /** Initialize the drawer part of the user interface. */
    private void initDrawers() {
        drawerLayout = findViewById(R.id.drawer_layout);
        leftDrawer = findViewById(R.id.left_drawer);

        class DrawerItem {
            int id;
            private int itemId; // Item string resource id

            private DrawerItem(int id, int itemId) {
                this.id = id;
                this.itemId = itemId;
            }

            @Override
            public String toString() {
                return getString(itemId);
            }
        }

        final int SIDE_TO_MOVE    = 0;
        final int CLEAR_BOARD     = 1;
        final int INITIAL_POS     = 2;
        final int CASTLING_FLAGS  = 3;
        final int EN_PASSANT_FILE = 4;
        final int MOVE_COUNTERS   = 5;
        final int COPY_POSITION   = 6;
        final int PASTE_POSITION  = 7;
        final int GET_FEN         = 8;

        final ArrayList<DrawerItem> leftItems = new ArrayList<>();
        leftItems.add(new DrawerItem(SIDE_TO_MOVE, R.string.side_to_move));
        leftItems.add(new DrawerItem(CLEAR_BOARD, R.string.clear_board));
        leftItems.add(new DrawerItem(INITIAL_POS, R.string.initial_position));
        leftItems.add(new DrawerItem(CASTLING_FLAGS, R.string.castling_flags));
        leftItems.add(new DrawerItem(EN_PASSANT_FILE, R.string.en_passant_file));
        leftItems.add(new DrawerItem(MOVE_COUNTERS, R.string.move_counters));
        leftItems.add(new DrawerItem(COPY_POSITION, R.string.copy_position));
        leftItems.add(new DrawerItem(PASTE_POSITION, R.string.paste_position));
        if (CaecusChess.hasFenProvider(getPackageManager()))
            leftItems.add(new DrawerItem(GET_FEN, R.string.get_fen));

        leftDrawer.setAdapter(new ArrayAdapter<>(this,
                                                 R.layout.drawer_list_item,
                                                 leftItems.toArray(new DrawerItem[0])));
        leftDrawer.setOnItemClickListener((parent, view, position, id) -> {
            drawerLayout.closeDrawer(Gravity.LEFT);
            leftDrawer.clearChoices();
            DrawerItem di = leftItems.get(position);
            switch (di.id) {
            case SIDE_TO_MOVE:
                showDialog(SIDE_DIALOG);
                setSelection(-1);
                checkValidAndUpdateMaterialDiff();
                break;
            case CLEAR_BOARD: {
                Position pos = new Position();
                cb.setPosition(pos);
                setSelection(-1);
                checkValidAndUpdateMaterialDiff();
                break;
            }
            case INITIAL_POS: {
                try {
                    Position pos = TextInfo.readFEN(TextInfo.startPosFEN);
                    cb.setPosition(pos);
                    setSelection(-1);
                    checkValidAndUpdateMaterialDiff();
                } catch (ChessError ignore) {
                }
                break;
            }
            case CASTLING_FLAGS:
                reShowDialog(CASTLE_DIALOG);
                setSelection(-1);
                checkValidAndUpdateMaterialDiff();
                break;
            case EN_PASSANT_FILE:
                reShowDialog(EP_DIALOG);
                setSelection(-1);
                checkValidAndUpdateMaterialDiff();
                break;
            case MOVE_COUNTERS:
                reShowDialog(MOVCNT_DIALOG);
                setSelection(-1);
                checkValidAndUpdateMaterialDiff();
                break;
            case COPY_POSITION: {
                setPosFields();
                String fen = TextInfo.intoFEN(cb.pos) + "\n";
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(new ClipData(fen,
                        new String[]{ "application/x-chess-fen", ClipDescription.MIMETYPE_TEXT_PLAIN },
                        new ClipData.Item(fen)));
                setSelection(-1);
                break;
            }
            case PASTE_POSITION: {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null) {
                    if (clip.getItemCount() > 0) {
                        String fen = clip.getItemAt(0).coerceToText(getApplicationContext()).toString();
                        setFEN(fen);
                    }
                }
                break;
            }
            case GET_FEN:
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("application/x-chess-fen");
                try {
                    startActivityForResult(i, RESULT_GET_FEN);
                } catch (ActivityNotFoundException e) {
                    CaecusChessApp.toast(e.getMessage(), Toast.LENGTH_LONG);
                }
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        drawerLayout.openDrawer(Gravity.LEFT);
        return false;
    }

    private void setSelection(int sq) {
        cb.setSelection(sq);
        setEgtbHints(sq);
    }

    private void setEgtbHints(int sq) {
        if (!egtbHints || (sq < 0)) {
            cb.setSquareDecorations(null);
            return;
        }

        Probe gtbProbe = Probe.getInstance();
        ArrayList<Pair<Integer,ProbeResult>> x = gtbProbe.relocatePieceProbe(cb.pos, sq);
        if (x == null) {
            cb.setSquareDecorations(null);
            return;
        }

        ArrayList<SquareDecoration> sd = new ArrayList<>();
        for (Pair<Integer,ProbeResult> p : x)
            sd.add(new SquareDecoration(p.first, p.second));
        cb.setSquareDecorations(sd);
    }

    private void doMove(Move m) {
        if (m.SQto < 0) {
            if ((m.SQfrom < 0) || (cb.pos.getPiece(m.SQfrom) == Piece.EMPTY)) {
                setSelection(m.SQto);
                return;
            }
        }
        Position pos = new Position(cb.pos);
        int piece;
        if (m.SQfrom >= 0) {
            piece = pos.getPiece(m.SQfrom);
        } else {
            piece = -(m.SQfrom + 2);
        }
        if (m.SQto >= 0) {
            int oPiece = Piece.swapColor(piece);
            if ((m.SQfrom < 0) && (pos.getPiece(m.SQto) == oPiece))
                pos.setPiece(m.SQto, Piece.EMPTY);
            else if ((m.SQfrom < 0) && (pos.getPiece(m.SQto) == piece))
                pos.setPiece(m.SQto, oPiece);
            else
                pos.setPiece(m.SQto, piece);
        }
        if (m.SQfrom >= 0)
            pos.setPiece(m.SQfrom, Piece.EMPTY);
        cb.setPosition(pos);
        if (m.SQfrom >= 0)
            setSelection(-1);
        else
            setSelection(m.SQfrom);
        checkValidAndUpdateMaterialDiff();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            sendBackResult();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void sendBackResult() {
        if (checkValidAndUpdateMaterialDiff()) {
            setPosFields();
            String fen = TextInfo.intoFEN(cb.pos);
            setResult(RESULT_OK, (new Intent()).setAction(fen));
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void setPosFields() {
        setEPFile(getEPFile()); // To handle sideToMove change
        if (isValid())
            TextInfo.legalEPSquare(cb.pos);
        TextInfo.removeWrongCastleFlags(cb.pos);
    }

    private int getEPFile() {
        int epSquare = cb.pos.getEpSquare();
        if (epSquare < 0) return 8;
        return Position.getX(epSquare);
    }

    private void setEPFile(int epFile) {
        int epSquare = -1;
        if ((epFile >= 0) && (epFile < 8)) {
            int epRank = cb.pos.whiteMove ? 5 : 2;
            epSquare = Position.getSquare(epFile, epRank);
        }
        cb.pos.setEpSquare(epSquare);
    }

    /** Test if a position is valid and update material diff display. */
    private boolean checkValidAndUpdateMaterialDiff() {
        try {
            MaterialDiff md = Util.getMaterialDiff(cb.pos);
            whiteFigText.setText(md.white);
            blackFigText.setText(md.black);

            String fen = TextInfo.intoFEN(cb.pos);
            TextInfo.readFEN(fen);
            status.setText("");
            return true;
        } catch (ChessError e) {
            status.setText(getParseErrString(e));
        }
        return false;
    }

    /** Return true if the position is valid. */
    private boolean isValid() {
        try {
            TextInfo.readFEN(TextInfo.intoFEN(cb.pos));
            return true;
        } catch (ChessError e) {
            return false;
        }
    }

    private String getParseErrString(ChessError e) {
        if (e.resourceID == -1)
            return e.getMessage();
        else
            return getString(e.resourceID);
    }

    static final int SIDE_DIALOG = 1;
    static final int CASTLE_DIALOG = 2;
    static final int EP_DIALOG = 3;
    static final int MOVCNT_DIALOG = 4;

    /** Remove and show a dialog. */
    private void reShowDialog(int id) {
        removeDialog(id);
        showDialog(id);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case SIDE_DIALOG: {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.select_side_to_move_first);
            final int selectedItem = (cb.pos.whiteMove) ? 0 : 1;
            builder.setSingleChoiceItems(new String[]{getString(R.string.white), getString(R.string.black)}, selectedItem, (dialog, id1) -> {
                if (id1 == 0) { // white to move
                    cb.pos.setWhiteMove(true);
                    checkValidAndUpdateMaterialDiff();
                    dialog.cancel();
                } else {
                    cb.pos.setWhiteMove(false);
                    checkValidAndUpdateMaterialDiff();
                    dialog.cancel();
                }
            });
            return builder.create();
        }
        case CASTLE_DIALOG: {
            final CharSequence[] items = {
                getString(R.string.white_king_castle), getString(R.string.white_queen_castle),
                getString(R.string.black_king_castle), getString(R.string.black_queen_castle)
            };
            boolean[] checkedItems = {
                    cb.pos.whiteShortCastle(), cb.pos.whiteLongCastle(),
                    cb.pos.blackShortCastle(), cb.pos.blackLongCastle()
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.castling_flags);
            builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                Position pos = new Position(cb.pos);
                boolean a1Castle = pos.whiteLongCastle();
                boolean h1Castle = pos.whiteShortCastle();
                boolean a8Castle = pos.blackLongCastle();
                boolean h8Castle = pos.blackShortCastle();
                switch (which) {
                case 0: h1Castle = isChecked; break;
                case 1: a1Castle = isChecked; break;
                case 2: h8Castle = isChecked; break;
                case 3: a8Castle = isChecked; break;
                }
                int castleMask = 0;
                if (a1Castle) castleMask |= 1 << Position.WhiteLongCastleMask;
                if (h1Castle) castleMask |= 1 << Position.WhiteShortCastleMask;
                if (a8Castle) castleMask |= 1 << Position.BlackLongCastleMask;
                if (h8Castle) castleMask |= 1 << Position.BlackShortCastleMask;
                pos.setCastleMask(castleMask);
                cb.setPosition(pos);
                checkValidAndUpdateMaterialDiff();
            });
            return builder.create();
        }
        case EP_DIALOG: {
            final CharSequence[] items = {
                    "A", "B", "C", "D", "E", "F", "G", "H", getString(R.string.none)
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.select_en_passant_file);
            builder.setSingleChoiceItems(items, getEPFile(), (dialog, item) -> {
                setEPFile(item);
                dialog.cancel();
            });
            return builder.create();
        }
        case MOVCNT_DIALOG: {
            View content = View.inflate(this, R.layout.edit_move_counters, null);
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setView(content);
            builder.setTitle(R.string.edit_move_counters);
            final EditText halfMoveClock = content.findViewById(R.id.ed_cnt_halfmove);
            final EditText fullMoveCounter = content.findViewById(R.id.ed_cnt_fullmove);
            halfMoveClock.setText(String.format(Locale.US, "%d", cb.pos.DrawTimer));
            fullMoveCounter.setText(String.format(Locale.US, "%d", cb.pos.MoveCounter));
            final Runnable setCounters = () -> {
                try {
                    int halfClock = Integer.parseInt(halfMoveClock.getText().toString());
                    int fullCount = Integer.parseInt(fullMoveCounter.getText().toString());
                    cb.pos.DrawTimer = halfClock;
                    cb.pos.MoveCounter = fullCount;
                } catch (NumberFormatException nfe) {
                    CaecusChessApp.toast(R.string.invalid_number_format, Toast.LENGTH_SHORT);
                }
            };
            builder.setPositiveButton("Ok", (dialog, which) -> setCounters.run());
            builder.setNegativeButton("Cancel", null);

            final Dialog dialog = builder.create();

            fullMoveCounter.setOnKeyListener((v, keyCode, event) -> {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    setCounters.run();
                    dialog.cancel();
                    return true;
                }
                return false;
            });
            return dialog;
        }
        }
        return null;
    }

    private void setFEN(String fen) {
        if (fen == null)
            return;
        try {
            Position pos = TextInfo.readFEN(fen);
            cb.setPosition(pos);
        } catch (ChessError e) {
            if (e.pos != null)
                cb.setPosition(e.pos);
            CaecusChessApp.toast(getParseErrString(e), Toast.LENGTH_SHORT);
        }
        setSelection(-1);
        checkValidAndUpdateMaterialDiff();
    }

    static private final int RESULT_GET_FEN  = 0;
    static private final int RESULT_LOAD_FEN = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case RESULT_GET_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getStringExtra(Intent.EXTRA_TEXT);
                if (fen == null) {
                    String pathName = FileUtil.getFilePathFromUri(data.getData());
                    Intent i = new Intent(EditBoard.this, LoadFEN.class);
                    i.setAction("com.example.caecuschess.loadFen");
                    i.putExtra("com.example.caecuschess.pathname", pathName);
                    startActivityForResult(i, RESULT_LOAD_FEN);
                }
                setFEN(fen);
            }
            break;
        case RESULT_LOAD_FEN:
            if (resultCode == RESULT_OK) {
                String fen = data.getAction();
                setFEN(fen);
            }
            break;
        }
    }
}
