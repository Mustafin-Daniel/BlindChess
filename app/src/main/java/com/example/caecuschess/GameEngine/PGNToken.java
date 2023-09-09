package com.example.caecuschess.GameEngine;

public class PGNToken {
    public static final int STRING = 0;
    public static final int INTEGER = 1;
    public static final int PERIOD = 2;
    public static final int ASTERISK = 3;
    public static final int LEFT_BRACKET = 4;
    public static final int RIGHT_BRACKET = 5;
    public static final int LEFT_PAREN = 6;
    public static final int RIGHT_PAREN = 7;
    public static final int NAG = 8;
    public static final int SYMBOL = 9;
    public static final int COMMENT = 10;
    public static final int EOF = 11;

    int type;
    String token;

    PGNToken(int type, String token) {
        this.type = type;
        this.token = token;
    }

    /** PGN parser visitor interface. */
    public interface PgnTokenReceiver {
        /** If this method returns false, the object needs a full re-initialization, using clear() and processToken(). */
        boolean isUpToDate();

        /** Clear object state. */
        void clear();

        /** Update object state with one token from a PGN game. */
        void processToken(GameTree.Node node, int type, String token);

        /** Change current move number. */
        void setCurrent(GameTree.Node node);
    }
}
//TODO See if this is necessary
