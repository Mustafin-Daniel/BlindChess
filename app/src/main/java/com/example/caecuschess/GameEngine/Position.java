package com.example.caecuschess.GameEngine;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Position {
    private int[] squares;
    /** See who moves*/
    public boolean whiteMove;

    public static final int WhiteLongCastleMask = 0;
    public static final int WhiteShortCastleMask = 1;
    public static final int BlackLongCastleMask = 2;
    public static final int BlackShortCastleMask = 3;

    /** Stores information about which castling moves are still possible*/
    private int castleMask;

    /** En passant */
    private int epSquare;

    /** Fifty-move rule */
    public int DrawTimer;

    public int MoveCounter;

    private long hashKey;
    private int wKingSq, bKingSq;


    @Override
    public int hashCode() {return (int)hashKey;}

    public final long createZobristHash(){
        return hashKey;
    }

    /** Empty position */
    public Position(){
        squares=new int[64];
        for(int i=0; i<64; i++) squares[i]=Piece.EMPTY;
        whiteMove = true;
        castleMask = 0;
        epSquare = -1;
        DrawTimer = 0;
        MoveCounter = 1;
        hashKey = createZobristHash();
        wKingSq=bKingSq=-1;
    }

    public Position(Position pos){
        squares = new int[64];
        System.arraycopy(pos.squares, 0, squares, 0, 64);
        whiteMove = pos.whiteMove;
        castleMask = pos.castleMask;
        epSquare = pos.epSquare;
        DrawTimer = pos.DrawTimer;
        MoveCounter = pos.MoveCounter;
        hashKey = pos.hashKey;
        wKingSq = pos.wKingSq;;
        bKingSq = pos.bKingSq;
    }

    @Override
    public boolean equals(Object o){
        if((o==null) || o.getClass() != this.getClass()) return false;
        Position pos = (Position)o;
        if(!isDrawRuleEqual(pos)) return false;
        if(DrawTimer!=pos.DrawTimer) return false;
        if(MoveCounter!= pos.MoveCounter) return false;
        if(hashKey!=pos.hashKey) return false;
        return true;
    }

    public int[] getSquares() {
        return squares;
    }

    /** Check if two positions are equal by the perspective of threefold repetition */
    final public boolean isDrawRuleEqual(Position pos){
        for(int i=0; i<64; i++){
            if(squares[i]!=pos.squares[i]) return false;
        }
        if(whiteMove!=pos.whiteMove) return false;
        if(castleMask!=pos.castleMask) return false;
        if(epSquare!=pos.epSquare) return false;
        return true;
    }

    public void setWhiteMove(boolean whiteMove) {
        if (whiteMove != this.whiteMove) {
            hashKey ^= whiteHashKey;
            this.whiteMove = whiteMove;
        }
    }

    /** Returns index */
    public static int getSquare(int x, int y){ return y*8+x;}

    /** Turn square index into x position */
    public static int getX(int square){return square & 7;}

    /** Turn square index into y position */
    public static int getY(int square){return square >> 3;}

    public static boolean isDarkSquare(int x, int y){return (x&1)==(y&1);}

    public final int getPiece(int square){ return squares[square]; }
    public final void setPiece(int square, int piece){
        int oldPiece = squares[square];
        hashKey ^= psHashKeys[oldPiece][square];
        hashKey ^= psHashKeys[piece][square];

        squares[square]=piece;

        if(piece==Piece.WKING){wKingSq=square;}
        else if(piece==Piece.BKING){bKingSq=square;}
    }

    /** Can white long castle */
    public final boolean whiteLongCastle(){return (castleMask & (1 << WhiteLongCastleMask)) != 0;}
    /** Can white short castle */
    public final boolean whiteShortCastle(){return (castleMask & (1 << WhiteShortCastleMask))!=0;}
    /** Can black long castle */
    public final boolean blackLongCastle(){return (castleMask & (1 << BlackLongCastleMask)) != 0;}
    /** Can black short castle */
    public final boolean blackShortCastle(){return (castleMask & (1 << BlackShortCastleMask)) != 0;}

    public final int getCastleMask() {return castleMask;}
    public final void setCastleMask(int castleMask){
        hashKey ^= castleHashKeys[this.castleMask];
        hashKey ^= castleHashKeys[castleMask];
        this.castleMask=castleMask;
    }

    /** Gets the en passant square*/
    public final int getEpSquare(){return epSquare;}
    public final void setEpSquare(int epSquare) {
        if (this.epSquare != epSquare) {
            hashKey ^= epHashKeys[(this.epSquare >= 0) ? getX(this.epSquare) + 1 : 0];
            hashKey ^= epHashKeys[(epSquare >= 0) ? getX(epSquare) + 1 : 0];
            this.epSquare = epSquare;
        }
    }

    /**
     * Gets the king square for a certain color
     * @param whiteMove Takes the input of whether or not it's white's move
     */
    public final int getKingSq(boolean whiteMove){
        if(whiteMove){return wKingSq;}
        return bKingSq;
    }

    /** Count number of a specific piece */
    public final int numPiece(int pieceType){
        int num = 0;
        for(int sq=0;sq<64;sq++){
            if(squares[sq]==pieceType) num++;
        }
        return num;
    }

    /** Count of all pieces */
    public final int numPiecesTotal(){
        int num=0;
        for (int sq=0; sq<64; sq++){
            if(squares[sq]!=Piece.EMPTY){
                num++;
            }
        }
        return num;
    }

    public final void makeMove(Move move, UndoInfo ui){
        ui.capturedPiece=squares[move.SQto];
        ui.castleMask=castleMask;
        ui.epSquare=epSquare;
        ui.DrawTimer=DrawTimer;

        boolean wm = whiteMove;

        int from = squares[move.SQfrom];
        int to = squares[move.SQto];

        boolean nullMove = (move.SQfrom==0)&&(move.SQto==0);

        if(nullMove||(to!=Piece.EMPTY)||(from==(wm ? Piece.WPAWN : Piece.BPAWN))){DrawTimer=0;}
        else{DrawTimer++;}

        if(!wm){MoveCounter++;}

        int king = wm ? Piece.WKING : Piece.BKING;
        int k0 = move.SQfrom;
        if(from==king) {
            if (move.SQto == k0 + 2) { // Short castle
                setPiece(k0 + 1, squares[k0 + 3]);
                setPiece(k0 + 3, Piece.EMPTY);
            } else if (move.SQto == k0 - 2) {
                setPiece(k0 - 1, squares[k0 - 4]);
                setPiece(k0 - 4, Piece.EMPTY);
            }
            //Clears the bits for the castle mask
            if (wm) {
                setCastleMask(castleMask & ~(1 << Position.WhiteLongCastleMask));
                setCastleMask(castleMask & ~(1 << Position.WhiteShortCastleMask));
            } else {
                setCastleMask(castleMask & ~(1 << Position.BlackLongCastleMask));
                setCastleMask(castleMask & ~(1 << Position.BlackShortCastleMask));
            }
        }
        if(!nullMove){
            int rook = wm ? Piece.WROOK : Piece.BROOK;
            if(from==rook){
                removeCastleRights(move.SQfrom);
            }
            //Captured rook
            int capRook = wm?Piece.BROOK:Piece.WROOK;
            if(capRook==to){
                removeCastleRights(move.SQto);
            }
        }

        //En passant
        int prevSquare = epSquare;
        setEpSquare(-1);
        if(from==Piece.WPAWN){
            if(move.SQto -move.SQfrom==2*8){
                int x = Position.getX(move.SQto);
                if(((x>0)&&(squares[move.SQto -1]==Piece.BPAWN))||(((x<7)&&(squares[move.SQto +1]==Piece.BPAWN)))){
                    setEpSquare(move.SQfrom +8);
                }
            } else if(move.SQto ==prevSquare){
                setPiece(move.SQto - 8, Piece.EMPTY);
            }
        } else if(from==Piece.BPAWN){
            if(move.SQto -move.SQfrom ==-2*8){
                int x = Position.getX(move.SQto);
                if(((x>0)&&(squares[move.SQto -1]==Piece.WPAWN)) || ((x<7)&&(squares[move.SQto +1]==Piece.WPAWN))){
                    setEpSquare(move.SQfrom -8);
                }
            } else if(move.SQto ==prevSquare){
                setPiece(move.SQto +8, Piece.EMPTY);
            }
        }


        setPiece(move.SQfrom, Piece.EMPTY);

        if(move.PromoteTo !=Piece.EMPTY) setPiece(move.SQto, move.PromoteTo);
        else setPiece(move.SQto, from);

        setWhiteMove(!wm);
    }

    public final void UndoMove(Move move, UndoInfo ui){
        setWhiteMove(!whiteMove);
        int p = squares[move.SQto];
        setPiece(move.SQfrom, p);
        setPiece(move.SQto, ui.capturedPiece);
        setCastleMask(ui.castleMask);
        setEpSquare(ui.epSquare);
        DrawTimer = ui.DrawTimer;
        boolean wm = whiteMove;
        if(move.PromoteTo !=Piece.EMPTY){
            p = wm ? Piece.WPAWN : Piece.BPAWN;
            setPiece(move.SQfrom, p);
        }
        if(!wm) MoveCounter++;


        //Castling
        int king = wm ? Piece.WKING : Piece.BKING;
        if(p==king){
            //Short castle
            if(move.SQto ==move.SQfrom +2){
                setPiece(move.SQfrom +3, squares[move.SQfrom +1]);
                setPiece(move.SQfrom +1, Piece.EMPTY);
            } //Long castle
             else if(move.SQto == move.SQfrom - 2){
                 setPiece(move.SQfrom - 4, squares[move.SQfrom - 1]);
                 setPiece(move.SQfrom - 1, Piece.EMPTY);
            }
        }


        //En passant
        if(move.SQto ==epSquare){
            if(p==Piece.WPAWN) setPiece(move.SQto - 8, Piece.BPAWN);
            else if(p == Piece.BPAWN) setPiece(move.SQto + 8, Piece.WPAWN);
        }

    }

    private void removeCastleRights(int square){
        if(square==Position.getSquare(0,0)) setCastleMask(castleMask&~(1<<Position.WhiteLongCastleMask));
        else if(square==Position.getSquare(7,0)) setCastleMask(castleMask&~(1<<Position.WhiteShortCastleMask));
        else if(square==Position.getSquare(0,7)) setCastleMask(castleMask&~(1<<Position.BlackLongCastleMask));
        else if(square==Position.getSquare(7,7)) setCastleMask(castleMask&~(1<<Position.BlackShortCastleMask));
    }

    //Hashkeys:
    private static long[][] psHashKeys;
    private static long whiteHashKey;
    private static long []castleHashKeys;
    private static long []epHashKeys;

    static {
        psHashKeys=new long[Piece.NumPieces][64];
        castleHashKeys=new long[16];
        epHashKeys = new long[9];
        int rnd=0;
        for(int p=0; p<Piece.NumPieces; p++){
            for(int sq=0; sq<64; sq++){
                psHashKeys[p][sq]=getRandomHashVal(rnd++);
            }
        }

        whiteHashKey = getRandomHashVal(rnd++);
        for(int c=0; c < castleHashKeys.length; c++) castleHashKeys[c]=getRandomHashVal(rnd++);
        for(int e=0; e < epHashKeys.length; e++) epHashKeys[e]=getRandomHashVal(rnd++);
    }

    /*
    final long computeZobristHah(){
        long hash=0;
        for(int sq=0; sq<64;sq++){
            int p = squares[sq];
            hash ^= psHashKeys[p][sq];
        }
        if(whiteMove) hash^=whiteHashKey;
        hash ^= castleHashKeys[castleMask];
        hash ^= epHashKeys[(epSquare>=0)?getX(epSquare)+1:0];
        return hash;
    } */ //TODO: Delete if unnecessary

    private static long getRandomHashVal(int rnd){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] input = new byte[4];
            for (int i = 0; i < 4; i++)
                input[i] = (byte)((rnd >> (i * 8)) & 0xff);
            byte[] digest = md.digest(input);
            long ret = 0;
            for (int i = 0; i < 8; i++) {
                ret ^= ((long)digest[i]) << (i * 8);
            }
            return ret;
        } catch (NoSuchAlgorithmException ex){
            throw new UnsupportedOperationException("SHA-1 is unavailable");
        }

    }

    public final String toString() {
        return TextInfo.asciiBoard(this);
    }
}
