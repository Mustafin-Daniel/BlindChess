package com.example.caecuschess.GameEngine;

public class Piece {
    public static final int EMPTY = 0;

    public static final int WKING = 1;
    public static final int WQUEEN = 2;
    public static final int WROOK = 3;
    public static final int WBISHOP = 4;
    public static final int WKNIGHT = 5;
    public static final int WPAWN = 6;

    public static final int BKING = 7;
    public static final int BQUEEN = 8;
    public static final int BROOK = 9;
    public static final int BBISHOP = 10;
    public static final int BKNIGHT = 11;
    public static final int BPAWN = 12;

    /** Number of different pieces (including empty squares) */
    public static final int NumPieces = 13;

    /** BE WARNED THAT EMPTY RETURNS AN UNSPECIFIED VALUE */
    public static boolean isWhite(int pieceType){
        return pieceType<BKING;
    }
    public static int makeWhite(int pieceType){
        if(pieceType>=BKING){return pieceType-(BKING-WKING);}
        return pieceType;
    }
    public static int makeBlack(int pieceType){
        if(pieceType==EMPTY) return pieceType;
        if(pieceType<BKING){return pieceType+(BKING-WKING);}
        return pieceType;
    }

    public static int swapColor(int pieceType){
        if(pieceType==EMPTY) return pieceType;
        if(pieceType<BKING){return pieceType+(BKING-WKING);}
        return pieceType-(BKING-WKING);
    }

}
