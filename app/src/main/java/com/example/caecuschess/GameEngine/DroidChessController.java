package com.example.caecuschess.GameEngine;

import android.util.Pair;

import com.example.caecuschess.GUIInterface;
import com.example.caecuschess.GameEngine.Game.CommentInfo;
import com.example.caecuschess.GameEngine.Game.GameState;
import com.example.caecuschess.GameEngine.GameTree.Node;
import com.example.caecuschess.GameMode;
import com.example.caecuschess.PGNOptions;
import com.example.caecuschess.Util;
import com.example.caecuschess.book.BookOptions;
import com.example.caecuschess.book.EcoDb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DroidChessController {
    private PGNToken.PgnTokenReceiver gameTextListener;
    private BookOptions bookOptions = new BookOptions();
    private Game game = null;
    private GUIInterface gui;
    private GameMode gameMode;
    private PGNOptions pgnOptions;

    private boolean guiPaused = false;

    /** Partial move that needs promotion choice to be completed. */
    private Move promoteMove;

    /** Constructor. */
    public DroidChessController(GUIInterface gui, PGNToken.PgnTokenReceiver gameTextListener, PGNOptions options) {
        this.gui = gui;
        this.gameTextListener = gameTextListener;
        gameMode = new GameMode(GameMode.TWO_PLAYERS);
        pgnOptions = options;
    }

    /** Start a new game. */
    public final synchronized void newGame(GameMode gameMode) {
        this.gameMode = gameMode;
        Game oldGame = game;
        game = new Game(gameTextListener);
        setPlayerNames(game);
        updateGameMode();
        game.resetModified(pgnOptions);
        autoSaveOldGame(oldGame, game.treeHashSignature);
    }

    /** Save old game if has been modified since start/load of game and is
     *  not equal to the new game. */
    private void autoSaveOldGame(Game oldGame, long newGameHash) {
        if (oldGame == null)
            return;
        String pgn = oldGame.tree.toPGN(pgnOptions);
        long oldGameOrigHash = oldGame.treeHashSignature;
        long oldGameCurrHash = Util.stringHash(pgn);
        if (oldGameCurrHash != oldGameOrigHash && oldGameCurrHash != newGameHash)
            gui.autoSaveGameIfAllowed(pgn);
    }

    /** Start playing a new game. Should be called after newGame(). */
    public final synchronized void startGame() {
        setSelection();
        updateGUI();
        updateGameMode();
    }

    /** The chess clocks are stopped when the GUI is paused. */
    public final synchronized void setGuiPaused(boolean paused) {
        guiPaused = paused;
        updateGameMode();
    }

    /** Set game mode. */
    public final synchronized void setGameMode(GameMode newMode) {
        if (!gameMode.equals(newMode)) {
            gameMode = newMode;
            if (!gameMode.playerWhite() || !gameMode.playerBlack())
                setPlayerNames(game);
            updateGameMode();
        }
    }

    public final GameMode getGameMode() {
        return gameMode;
    }


    /** Set engine book options. */
    public final synchronized void setBookOptions(BookOptions options) {
        if (!bookOptions.equals(options)) {
            bookOptions = options;
        }
    }


    /** Notify controller that preferences has changed. */
    public final synchronized void prefsChanged(boolean translateMoves) {
        if (game == null)
            translateMoves = false;
        if (translateMoves)
            game.tree.translateMoves();
        updateMoveList();
        if (translateMoves)
            updateGUI();
    }

    private void updateGameMode() {
        if (game != null) {
            Game.AddMoveBehavior amb;
            if (gui.discardVariations())
                amb = Game.AddMoveBehavior.REPLACE;
            else if (gameMode.clocksActive())
                amb = Game.AddMoveBehavior.ADD_FIRST;
            else
                amb = Game.AddMoveBehavior.ADD_LAST;
            game.setAddFirst(amb);
        }
    }

    /** De-serialize from byte array. */
    public final synchronized void fromByteArray(byte[] data, int version) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            game.readFromStream(dis, version);
            game.tree.translateMoves();
        } catch (IOException|ChessError ignore) {
        }
    }

    /** Serialize to byte array. */
    public final synchronized byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
             DataOutputStream dos = new DataOutputStream(baos)) {
            game.writeToStream(dos);
            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Return FEN string corresponding to a current position. */
    public final synchronized String getFEN() {
        return TextInfo.intoFEN(game.tree.currentPos);
    }

    /** Convert current game to PGN format. */
    public final synchronized String getPGN() {
        return game.tree.toPGN(pgnOptions);
    }

    /** Parse a string as FEN or PGN data. */
    public final synchronized void setFENOrPGN(String fenPgn, boolean setModified) throws ChessError {
        if (!fenPgn.isEmpty() && fenPgn.charAt(0) == '\ufeff')
            fenPgn = fenPgn.substring(1); // Remove BOM
        Game newGame = new Game(gameTextListener);
        try {
            Position pos = TextInfo.readFEN(fenPgn);
            newGame.setPos(pos);
            setPlayerNames(newGame);
        } catch (ChessError e) {
            // Try read as PGN instead
            if (!newGame.readPGN(fenPgn, pgnOptions))
                throw e;
            newGame.tree.translateMoves();
        }
        Game oldGame = game;
        game = newGame;
        gameTextListener.clear();
        updateGameMode();
        gui.setSelection(-1);
        updateGUI();
        game.resetModified(pgnOptions);
        autoSaveOldGame(oldGame, game.treeHashSignature);
        if (setModified)
            game.treeHashSignature = oldGame.treeHashSignature;
    }

    public final synchronized void setLastSaveHash(long hash) {
        game.treeHashSignature = hash;
    }

    /** True if human's turn to make a move. (True in analysis mode.) */
    public final synchronized boolean humansTurn() {
        if (game == null)
            return false;
        return gameMode.humansTurn(game.currPos().whiteMove);
    }

    /** Make a move for a human player. */
    public final synchronized void makeHumanMove(Move m, boolean animate) {
        if (!humansTurn())
            return;
        Position oldPos = new Position(game.currPos());
        if (game.pendingDrawOffer) {
            ArrayList<Move> moves = new MoveGeneration().legalMoves(oldPos);
            for (Move m2 : moves) {
                if (m2.equals(m)) {
                    if (findValidDrawClaim(TextInfo.moveToUCIString(m))) {
                        updateGUI();
                        gui.setSelection(-1);
                        return;
                    }
                    break;
                }
            }
        }
        if (doMove(m)) {
            if (animate)
                setAnimMove(oldPos, m, true);
            updateGUI();
        } else {
            gui.setSelection(-1);
        }
    }

    /** Report promotion choice for incomplete move.
     * @param choice 0=queen, 1=rook, 2=bishop, 3=knight. */
    public final synchronized void reportPromotePiece(int choice) {
        if (promoteMove == null)
            return;
        final boolean white = game.currPos().whiteMove;
        int promoteTo;
        switch (choice) {
            case 1:
                promoteTo = white ? Piece.WROOK : Piece.BROOK;
                break;
            case 2:
                promoteTo = white ? Piece.WBISHOP : Piece.BBISHOP;
                break;
            case 3:
                promoteTo = white ? Piece.WKNIGHT : Piece.BKNIGHT;
                break;
            default:
                promoteTo = white ? Piece.WQUEEN : Piece.BQUEEN;
                break;
        }
        promoteMove.PromoteTo = promoteTo;
        Move m = promoteMove;
        promoteMove = null;
        makeHumanMove(m, true);
    }

    /** Add a null-move to the game tree. */
    public final synchronized void makeHumanNullMove() {
        if (humansTurn()) {
            int varNo = game.tree.addMove("--", "", 0, "", "");
            game.tree.goForward(varNo);
            gui.setSelection(-1);
        }
    }

    /** Help human to claim a draw by trying to find and execute a valid draw claim. */
    public final synchronized boolean claimDrawIfPossible() {
        if (!findValidDrawClaim(""))
            return false;
        updateGUI();
        return true;
    }

    /** Resign game for current player. */
    public final synchronized void resignGame() {
        if (game.getGameState() == GameState.ALIVE) {
            game.processString("resign");
            updateGUI();
        }
    }

    /** Return true if the player to move in the current position is in check. */
    public final synchronized boolean inCheck() {
        return MoveGeneration.inCheck(game.tree.currentPos);
    }

    /** Undo last move. Does not truncate game tree. */
    public final synchronized void undoMove() {
        if (game.getLastMove() != null) {
            boolean didUndo = undoMoveNoUpdate();
            setSelection();
            if (didUndo)
                setAnimMove(game.currPos(), game.getNextMove(), false);
            updateGUI();
        }
    }

    /** Redo last move. Follows default variation. */
    public final synchronized void redoMove() {
        if (game.canRedoMove()) {
            redoMoveNoUpdate();
            setSelection();
            setAnimMove(game.prevPos(), game.getLastMove(), true);
            updateGUI();
        }
    }

    /** Go back/forward to a given move number.
     * Follows default variations when going forward. */
    public final synchronized void gotoMove(int moveNr) {
        boolean needUpdate = false;
        while (game.currPos().MoveCounter > moveNr) { // Go backward
            int before = game.currPos().MoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            undoMoveNoUpdate();
            int after = game.currPos().MoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            if (after >= before)
                break;
            needUpdate = true;
        }
        while (game.currPos().MoveCounter < moveNr) { // Go forward
            int before = game.currPos().MoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            redoMoveNoUpdate();
            int after = game.currPos().MoveCounter * 2 + (game.currPos().whiteMove ? 0 : 1);
            if (after <= before)
                break;
            needUpdate = true;
        }
        if (needUpdate) {
            setSelection();
            updateGUI();
        }
    }

    /** Go to start of the current variation. */
    public final synchronized void gotoStartOfVariation() {
        boolean needUpdate = false;
        while (true) {
            if (!undoMoveNoUpdate())
                break;
            needUpdate = true;
            if (game.numVariations() > 1)
                break;
        }
        if (needUpdate) {
            setSelection();
            updateGUI();
        }
    }

    /** Go to given node in game tree. */
    public final synchronized void goNode(Node node) {
        if (node == null)
            return;
        if (!game.goNode(node))
            return;
        if (!humansTurn()) {
            if (game.getLastMove() != null) {
                game.undoMove();
                if (!humansTurn())
                    game.redoMove();
            }
        }
        setSelection();
        updateGUI();
    }

    /** Get number of variations in current game position. */
    public final synchronized int numVariations() {
        return game.numVariations();
    }

    /** Return true if the current variation can be moved closer to the main-line. */
    public final synchronized boolean canMoveVariationUp() {
        return game.canMoveVariation(-1);
    }

    /** Return true if the current variation can be moved farther away from the main-line. */
    public final synchronized boolean canMoveVariationDown() {
        return game.canMoveVariation(1);
    }

    /** Get current variation in current position. */
    public final synchronized int currVariation() {
        return game.currVariation();
    }

    /** Go to a new variation in the game tree. */
    public final synchronized void changeVariation(int delta) {
        if (game.numVariations() > 1) {
            game.changeVariation(delta);
            setSelection();
            updateGUI();
        }
    }

    /** Delete whole game sub-tree rooted at current position. */
    public final synchronized void removeSubTree() {
        game.removeSubTree();
        setSelection();
        updateGUI();
    }

    /** Move current variation up/down in the game tree. */
    public final synchronized void moveVariation(int delta) {
        if (((delta > 0) && canMoveVariationDown()) ||
            ((delta < 0) && canMoveVariationUp())) {
            game.moveVariation(delta);
            updateGUI();
        }
    }

    /** Add a variation to the game tree.
     * @param preComment Comment to add before first move.
     * @param pvMoves List of moves in variation.
     * @param updateDefault If true, make this the default variation. */
    public final synchronized void addVariation(String preComment, List<Move> pvMoves, boolean updateDefault) {
        for (int i = 0; i < pvMoves.size(); i++) {
            Move m = pvMoves.get(i);
            String moveStr = TextInfo.moveToUCIString(m);
            String pre = (i == 0) ? preComment : "";
            int varNo = game.tree.addMove(moveStr, "", 0, pre, "");
            game.tree.goForward(varNo, updateDefault);
        }
        for (int i = 0; i < pvMoves.size(); i++)
            game.tree.goBack();
        gameTextListener.clear();
        updateGUI();
    }

    /** Get PGN header tags and values. */
    public final synchronized void getHeaders(Map<String,String> headers) {
        if (game != null)
            game.tree.getHeaders(headers);
    }

    /** Set PGN header tags and values. */
    public final synchronized void setHeaders(Map<String,String> headers) {
        boolean resultChanged = game.tree.setHeaders(headers);
        gameTextListener.clear();
        if (resultChanged) {
            setSelection();
        }
        updateGUI();
    }

    /** Add ECO classification headers. */
    public final synchronized void addECO() {
        EcoDb.Result r = game.tree.getGameECO();
        Map<String,String> headers = new TreeMap<>();
        headers.put("ECO",       r.eco.isEmpty() ? null : r.eco);
        headers.put("Opening",   r.opn.isEmpty() ? null : r.opn);
        headers.put("Variation", r.var.isEmpty() ? null : r.var);
        game.tree.setHeaders(headers);
        gameTextListener.clear();
        updateGUI();
    }

    /** Get comments associated with current position. */
    public final synchronized CommentInfo getComments() {
        Pair<CommentInfo,Boolean> p = game.getComments();
        if (p.second) {
            gameTextListener.clear();
            updateGUI();
        }
        return p.first;
    }

    /** Set comments associated with current position. "commInfo" must be an object
     *  (possibly modified) previously returned from getComments(). */
    public final synchronized void setComments(CommentInfo commInfo) {
        game.setComments(commInfo);
        gameTextListener.clear();
        updateGUI();
    }

    /** Return true if localized piece names should be used. */
    private boolean localPt() {
        switch (pgnOptions.view.pieceType) {
        case PGNOptions.PT_ENGLISH:
            return false;
        case PGNOptions.PT_LOCAL:
        case PGNOptions.PT_FIGURINE:
        default:
            return true;
        }
    }

    private void appendWithPrefix(StringBuilder sb, long value) {
         if (value > 100000000000L) {
            value /= 1000000000;
            sb.append(value);
            sb.append('G');
         } else if (value > 100000000) {
             value /= 1000000;
             sb.append(value);
             sb.append('M');
         } else if (value > 100000) {
             value /= 1000;
             sb.append(value);
             sb.append('k');
            } else {
                sb.append(value);
            }
        }


    private void setPlayerNames(Game game) {
        if (game != null) {
            String player = gui.playerName();
            String white = player;
            String black = player;
            game.tree.setPlayerNames(white, black);
        }
    }

    private synchronized void updatePlayerNames(String engineName) {
        if (game != null) {
            String white = gameMode.playerWhite() ? game.tree.white : engineName;
            String black = gameMode.playerBlack() ? game.tree.black : engineName;
            game.tree.setPlayerNames(white, black);
            updateMoveList();
        }
    }

    private boolean undoMoveNoUpdate() {
        if (game.getLastMove() == null)
            return false;
        game.undoMove();
        if (!humansTurn()) {
            if (game.getLastMove() != null) {
                game.undoMove();
                if (!humansTurn()) {
                    game.redoMove();
                }
            } else {
                // Don't undo first white move if playing black vs computer,
                // because that would cause computer to immediately make
                // a new move.
                if (gameMode.playerWhite() || gameMode.playerBlack()) {
                    game.redoMove();
                    return false;
                }
            }
        }
        return true;
    }

    private void redoMoveNoUpdate() {
        if (game.canRedoMove()) {
            game.redoMove();
            if (!humansTurn() && game.canRedoMove()) {
                game.redoMove();
                if (!humansTurn())
                    game.undoMove();
            }
        }
    }

    /**
     * Move a piece from one square to another.
     * @return True if the move was legal, false otherwise.
     */
    private boolean doMove(Move move) {
        Position pos = game.currPos();
        ArrayList<Move> moves = new MoveGeneration().legalMoves(pos);
        int promoteTo = move.PromoteTo;
        for (Move m : moves) {
            if ((m.SQfrom == move.SQfrom) && (m.SQto == move.SQto)) {
                if ((m.PromoteTo != Piece.EMPTY) && (promoteTo == Piece.EMPTY)) {
                    promoteMove = m;
                    gui.requestPromotePiece();
                    return false;
                }
                if (m.PromoteTo == promoteTo) {
                    String strMove = TextInfo.moveToString(pos, m, false, false, moves);
                    Pair<Boolean,Move> res = game.processString(strMove);
                    return true;
                }
            }
        }
        gui.reportInvalidMove(move);
        return false;
    }

    private void updateGUI() {
        GUIInterface.GameStatus s = new GUIInterface.GameStatus();
        s.state = game.getGameState();
        if (s.state == GameState.ALIVE) {
            s.moveNr = game.currPos().MoveCounter;
            s.white = game.currPos().whiteMove;
        } else {
            if ((s.state == GameState.DRAW_REP) || (s.state == GameState.DRAW_50))
                s.drawInfo = game.getDrawInfo(localPt());
        }
        gui.setStatus(s);
        updateMoveList();

        StringBuilder sb = new StringBuilder();
        if (game.tree.currentNode != game.tree.rootNode) {
            game.tree.goBack();
            Position pos = game.currPos();
            List<Move> prevVarList = game.tree.variations();
            for (int i = 0; i < prevVarList.size(); i++) {
                if (i > 0) sb.append(' ');
                if (i == game.tree.currentNode.defaultChild)
                    sb.append(Util.boldStart);
                sb.append(TextInfo.moveToString(pos, prevVarList.get(i), false, localPt()));
                if (i == game.tree.currentNode.defaultChild)
                    sb.append(Util.boldStop);
            }
            game.tree.goForward(-1);
        }
        gui.setPosition(game.currPos(), sb.toString(), game.tree.variations());
        updateMaterialDiffList();
    }

    public final void updateMaterialDiffList() {
        gui.updateMaterialDifferenceTitle(Util.getMaterialDiff(game.currPos()));
    }


    private void updateMoveList() {
        if (game == null)
            return;
        if (!gameTextListener.isUpToDate()) {
            PGNOptions tmpOptions = new PGNOptions();
            tmpOptions.exp.variations     = pgnOptions.view.variations;
            tmpOptions.exp.comments       = pgnOptions.view.comments;
            tmpOptions.exp.nag            = pgnOptions.view.nag;
            tmpOptions.exp.playerAction   = false;
            tmpOptions.exp.clockInfo      = false;
            tmpOptions.exp.moveNrAfterNag = false;
            tmpOptions.exp.pieceType      = pgnOptions.view.pieceType;
            gameTextListener.clear();
            game.tree.pgnTreeWalker(tmpOptions, gameTextListener);
        }
        gameTextListener.setCurrent(game.tree.currentNode);
        gui.moveListUpdated();
    }

    /** Mark last played move in the GUI. */
    private void setSelection() {
        Move m = game.getLastMove();
        int sq = ((m != null) && (m.SQfrom != m.SQto)) ? m.SQto : -1;
        gui.setSelection(sq);
    }

    private void setAnimMove(Position sourcePos, Move move, boolean forward) {
        gui.setAnimMove(sourcePos, move, forward);
    }

    private boolean findValidDrawClaim(String ms) {
        if (!ms.isEmpty())
            ms = " " + ms;
        if (game.getGameState() != GameState.ALIVE) return true;
        game.tryClaimDraw("draw accept");
        if (game.getGameState() != GameState.ALIVE) return true;
        game.tryClaimDraw("draw rep" + ms);
        if (game.getGameState() != GameState.ALIVE) return true;
        game.tryClaimDraw("draw 50" + ms);
        if (game.getGameState() != GameState.ALIVE) return true;
        return false;
    }
}
