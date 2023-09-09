package com.example.caecuschess;

/** Settings controlling PGN import/export */
public class PGNOptions {
    /** Pieces displayed as English letters. */
    public static final int PT_ENGLISH  = 0;
    /** Pieces displayed as local language letters. */
    public static final int PT_LOCAL    = 1;
    /** Piece displayed in figurine notation, by using UniCode characters
      * and a special font. */
    public static final int PT_FIGURINE = 2;

    public static class Viewer {
        public boolean variations;
        public boolean comments;
        public boolean nag;
        public boolean headers;
        public int pieceType;
    }
    public static class Import {
        public boolean variations;
        public boolean comments;
        public boolean nag;
    }
    public static class Export {
        public boolean variations;
        public boolean comments;
        public boolean nag;
        public boolean playerAction;
        public boolean clockInfo;
        public boolean pgnPromotions;
        public boolean moveNrAfterNag;
        public int pieceType;
    }

    public Viewer view;
    public Import imp;
    public Export exp;

    public PGNOptions() {
        view = new Viewer();
        imp = new Import();
        exp = new Export();
        exp.moveNrAfterNag = true;
        exp.pieceType = PT_ENGLISH;
    }
}
