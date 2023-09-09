package com.example.caecuschess;

import com.example.caecuschess.GameEngine.Game;
import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Position;

import java.util.ArrayList;

/** Interface between the GUI and the ChessController. */
public interface GUIInterface {

    /** Update the displayed board position. */
    void setPosition(Position pos, String variantInfo, ArrayList<Move> variantMoves);

    /** Mark square sq as selected. Set to -1 to clear selection. */
    void setSelection(int sq);

    final class GameStatus {
        public Game.GameState state = Game.GameState.ALIVE;
        public int moveNr = 0;
        /** Move required to claim draw, or empty string. */
        public String drawInfo = "";
        public boolean white = false;
        public boolean ponder = false;
    }

    /** Set the status text. */
    void setStatus(GameStatus status);

    /** Update the list of moves. */
    void moveListUpdated();



    /** Ask what to promote a pawn to. Should call reportPromotePiece() when done. */
    void requestPromotePiece();

    /** Run code on the GUI thread. */
    void runOnUIThread(Runnable runnable);

    /** Report that user attempted to make an invalid move. */
    void reportInvalidMove(Move m);

    /** Update title with the material difference. */
    void updateMaterialDifferenceTitle(Util.MaterialDiff diff);

    /** Update title with time control information. */
    //TODO remove this timeControl void updateTimeControlTitle();

    /** Report a move made that is a candidate for GUI animation. */
    void setAnimMove(Position sourcePos, Move move, boolean forward);

    /** Get the default player name. */
    String playerName();

    /** Return true if only main-line moves are to be kept. */
    boolean discardVariations();

    /** Save the current game to the auto-save file, if storage permission has been granted. */
    void autoSaveGameIfAllowed(String pgn);
}
