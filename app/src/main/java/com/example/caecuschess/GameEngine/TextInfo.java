package com.example.caecuschess.GameEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextInfo {
    static public final String startPosFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static String[] pieceNames = null;
    public static void setPieceNames(String pieceNames){
        String[] pn = pieceNames.split(" ");
        if(pn.length==6) TextInfo.pieceNames=pn;
    }

    /** Returns position from FEN */
    public static Position readFEN(String fen) throws ChessError {
        fen=fen.trim();
        Position pos = new Position();
        String[] words = fen.split(" ");
        if(words.length<2){
            throw new ChessError("Too few spaces");
        }
        for(int i=0; i< words.length; i++){
            words[i] = words[i].trim();
        }

        int row = 7;
        int col = 0;
        for(int i=0; i < words[0].length(); i++) {
            char c = words[0].charAt(i);
            switch (c) {
                case '1':
                    col += 1;
                    break;
                case '2':
                    col += 2;
                    break;
                case '3':
                    col += 3;
                    break;
                case '4':
                    col += 4;
                    break;
                case '5':
                    col += 5;
                    break;
                case '6':
                    col += 6;
                    break;
                case '7':
                    col += 7;
                    break;
                case '8':
                    col += 8;
                    break;
                case '/':
                    row--;
                    col = 0;
                    break;
                case 'P':
                    safeSetPiece(pos, col, row, Piece.WPAWN);
                    col++;
                    break;
                case 'N':
                    safeSetPiece(pos, col, row, Piece.WKNIGHT);
                    col++;
                    break;
                case 'B':
                    safeSetPiece(pos, col, row, Piece.WBISHOP);
                    col++;
                    break;
                case 'R':
                    safeSetPiece(pos, col, row, Piece.WROOK);
                    col++;
                    break;
                case 'Q':
                    safeSetPiece(pos, col, row, Piece.WQUEEN);
                    col++;
                    break;
                case 'K':
                    safeSetPiece(pos, col, row, Piece.WKING);
                    col++;
                    break;
                case 'p':
                    safeSetPiece(pos, col, row, Piece.BPAWN);
                    col++;
                    break;
                case 'n':
                    safeSetPiece(pos, col, row, Piece.BKNIGHT);
                    col++;
                    break;
                case 'b':
                    safeSetPiece(pos, col, row, Piece.BBISHOP);
                    col++;
                    break;
                case 'r':
                    safeSetPiece(pos, col, row, Piece.BROOK);
                    col++;
                    break;
                case 'q':
                    safeSetPiece(pos, col, row, Piece.BQUEEN);
                    col++;
                    break;
                case 'k':
                    safeSetPiece(pos, col, row, Piece.BKING);
                    col++;
                    break;
                default:
                    throw new ChessError("Invalid piece", pos);
            }
        }

            if(words[1].length()>0){
                boolean wm;
                switch (words[1].charAt(0)){
                    case 'w':
                        wm=true;
                        break;
                    case 'b':
                        wm=false;
                        break;
                    default: throw new ChessError("Invalid side", pos);
                }
                pos.setWhiteMove(wm);
            } else{
                throw new ChessError("Invalid side", pos);
            }

            int cm = 0; // CastleMask
            if(words.length>2){
                for(int i=0; i < words[2].length(); i++){
                    char c = words[2].charAt(i);
                    switch (c) {
                        case 'K':
                            cm |= (1<<Position.BlackShortCastleMask);
                            break;
                        case 'Q':
                            cm |= (1<<Position.BlackLongCastleMask); //'|= is bitwise or'
                            break;
                        case 'k':
                            cm |= (1<<Position.WhiteShortCastleMask);
                            break;
                        case 'q':
                            cm |= (1<<Position.WhiteLongCastleMask);
                            break;
                        case '-': break;
                        default: throw new ChessError("Invalid castling", pos);
                    }
                }
            }
            pos.setCastleMask(cm);
            removeWrongCastleFlags(pos);

            if(words.length > 3) {
                String epStr = words[3];
                if(!epStr.equals("-")){
                    if(epStr.length()<2) throw new ChessError("Invalid enPassant", pos);
                    int epSq = getSquare(epStr);
                    if(epSq!=-1){
                        if(pos.whiteMove){
                            if((Position.getY(epSq)!=5)||(pos.getPiece(epSq)!=Piece.EMPTY)||(pos.getPiece(epSq-8)!=Piece.BPAWN)){
                                epSq=-1;
                            }
                        }
                        else {
                            if((Position.getY(epSq)!=2)||(pos.getPiece(epSq)!=Piece.EMPTY)||(pos.getPiece(epSq+8)!=Piece.WPAWN)){
                                epSq = -1;
                            }
                        }
                        pos.setEpSquare(epSq);
                    }
                }
            }

            //Update draw timer and move counter
            try{
                if(words.length>4){
                    pos.DrawTimer=Integer.parseInt(words[4]);
                }
                if(words.length>5){
                    pos.MoveCounter=Integer.parseInt(words[5]);
                }
            } catch (NumberFormatException nfe){}

            int[] numPieces = new int[Piece.NumPieces];
            for(int i=0; i<Piece.NumPieces; i++){
                numPieces[i]=0;
            }

            for(int x=0; x<8; x++){
                for(int y=0; y<8; y++){
                    numPieces[pos.getPiece(Position.getSquare(x, y))]++;
                }
            }

            if(numPieces[Piece.WKING] != 1){
                throw new ChessError("White must have only one king", pos);
            }
            if(numPieces[Piece.BKING] != 1){
                throw new ChessError("Black must have only one king", pos);
            }

            //TODO add this if necessary
        /*

            int maxWPawns = 8;
            maxWPawns -= Math.max(0, numPieces[Piece.WKNIGHT]-2);
            maxWPawns -= Math.max(0, numPieces[Piece.WBISHOP]-2);
            maxWPawns -= Math.max(0, numPieces[Piece.WROOK]-2);
            maxWPawns -= Math.max(0, numPieces[Piece.WQUEEN]-1);
            if(numPieces[Piece.WPAWN]>maxWPawns){
                throw new ChessError("Too many white pieces", pos);
            }


            int maxBPawns = 8;
            maxBPawns -= Math.max(0, numPieces[Piece.BKNIGHT]-2);
            maxBPawns -= Math.max(0, numPieces[Piece.BBISHOP]-2);
            maxBPawns -= Math.max(0, numPieces[Piece.BROOK]-2);
            maxBPawns -= Math.max(0, numPieces[Piece.BQUEEN]-1);
            if(numPieces[Piece.BPAWN]>maxBPawns){
                throw new ChessError("Too many black pieces", pos);
            }
        */
            Position pos2 = new Position(pos);
            pos2.setWhiteMove(!pos.whiteMove);
            if(MoveGeneration.inCheck(pos2)){
                throw new ChessError("King capture can happen", pos);
            }

            legalEPSquare(pos);
            return pos;
        }

    public static void removeWrongCastleFlags(Position pos) {
        int castleMask = pos.getCastleMask();
        int validCastle = 0;
        if(pos.getPiece(4)==Piece.WKING){
            if(pos.getPiece(0)==Piece.WROOK) validCastle |= (1<<Position.WhiteLongCastleMask);
            if(pos.getPiece(7)==Piece.WROOK) validCastle |= (1<<Position.WhiteShortCastleMask);
        }
        if(pos.getPiece(60)==Piece.BKING){
            if(pos.getPiece(56)==Piece.BROOK) validCastle |= (1<<Position.BlackLongCastleMask);
            if(pos.getPiece(63)==Piece.BROOK) validCastle |= (1<<Position.BlackShortCastleMask);
        }
        castleMask &= validCastle;
        pos.setCastleMask(castleMask);
    }

    public static void legalEPSquare(Position pos){
        int epSq = pos.getEpSquare();
        if(epSq>=0){
            ArrayList<Move> moves = MoveGeneration.instance.legalMoves(pos);
            boolean epValid = false;
            for (Move m : moves){
                if(m.SQto ==epSq){
                    if(pos.getPiece(m.SQfrom)==(pos.whiteMove?Piece.WPAWN:Piece.BPAWN)){
                        epValid = true;
                        break;
                    }
                }
            }
            if(!epValid) pos.setEpSquare(-1);
        }
    }

    private static void safeSetPiece(Position pos, int col, int row, int p) throws ChessError {
        if(row<0) throw new ChessError("Too many rows"); //TODO IS this correct
        if(col>7) throw new ChessError("Too many columns");
        if((p==Piece.WPAWN)||(p==Piece.BPAWN)){
            if((row==0)||(row==7)) throw new ChessError("Pawn on non possible rank");
        }
        pos.setPiece(Position.getSquare(col, row), p);
    }

    public static String intoFEN(Position pos){
        StringBuilder ret = new StringBuilder();
        for(int r=7; r>=0; r--){
            int numEmpty=0;
            for(int c=0; c<8; c++){
                int p = pos.getPiece(Position.getSquare(c,r));
                if(p==Piece.EMPTY){
                    numEmpty++;
                }else{
                    if(numEmpty>0){
                        ret.append(numEmpty);
                        numEmpty=0;
                    }
                    switch (p){
                        case Piece.WKING: ret.append('K'); break;
                        case Piece.WQUEEN: ret.append('Q'); break;
                        case Piece.WROOK: ret.append('R'); break;
                        case Piece.WBISHOP: ret.append('B'); break;
                        case Piece.WKNIGHT: ret.append('N'); break;
                        case Piece.WPAWN: ret.append('P'); break;

                        case Piece.BKING: ret.append('k'); break;
                        case Piece.BQUEEN: ret.append('q'); break;
                        case Piece.BROOK: ret.append('r'); break;
                        case Piece.BBISHOP: ret.append('b'); break;
                        case Piece.BKNIGHT: ret.append('n'); break;
                        case Piece.BPAWN: ret.append('p'); break;
                        default:throw new RuntimeException();
                    }
                }
            }
            if(numEmpty>0){
                ret.append(numEmpty);
            }if(r>0){
                ret.append('/');
            }
        }
        ret.append(pos.whiteMove?" w ":" b ");

        boolean cancastle=false;
        if(pos.blackLongCastle()){
            ret.append('K');
            cancastle=true;
        }
        if(pos.whiteLongCastle()){
            ret.append('Q');
            cancastle=true;
        }
        if(pos.blackShortCastle()){
            ret.append('k');
            cancastle=true;
        }
        if(pos.whiteShortCastle()){
            ret.append('q');
            cancastle=true;
        }
        if(!cancastle){
            ret.append('-');
        }

        ret.append(' ');
        if (pos.getEpSquare()>=0){
            int x=Position.getX(pos.getEpSquare());
            int y=Position.getY(pos.getEpSquare());
            ret.append((char)(x+'a'));
            ret.append((char)(y+'1'));
        }else{
            ret.append('-');
        }

        ret.append(' ');
        ret.append(pos.DrawTimer);
        ret.append(' ');
        ret.append(pos.MoveCounter);

        return ret.toString();
    }


    public static Move stringToMove(Position pos, String strMove) {
        return stringToMove(pos, strMove, null);
    }
    public static Move stringToMove(Position pos, String strMove,
                                    ArrayList<Move> moves) {
        if (strMove.equals("--"))
            return new Move(0, 0, 0);

        strMove = strMove.replaceAll("=", "");
        strMove = strMove.replaceAll("\\+", "");
        strMove = strMove.replaceAll("#", "");
        boolean wtm = pos.whiteMove;

        MoveInfo info = new MoveInfo();
        boolean capture = false;
        if (strMove.equals("O-O") || strMove.equals("0-0") || strMove.equals("o-o")) {
            info.piece = wtm ? Piece.WKING : Piece.BKING;
            info.fromX = 4;
            info.toX = 6;
            info.fromY = info.toY = wtm ? 0 : 7;
            info.promPiece = Piece.EMPTY;
        } else if (strMove.equals("O-O-O") || strMove.equals("0-0-0") || strMove.equals("o-o-o")) {
            info.piece = wtm ? Piece.WKING : Piece.BKING;
            info.fromX = 4;
            info.toX = 2;
            info.fromY = info.toY = wtm ? 0 : 7;
            info.promPiece = Piece.EMPTY;
        } else {
            boolean atToSq = false;
            for (int i = 0; i < strMove.length(); i++) {
                char c = strMove.charAt(i);
                if (i == 0) {
                    int piece = charToPiece(wtm, c);
                    if (piece >= 0) {
                        info.piece = piece;
                        continue;
                    }
                }
                int tmpX = c - 'a';
                if ((tmpX >= 0) && (tmpX < 8)) {
                    if (atToSq || (info.fromX >= 0))
                        info.toX = tmpX;
                    else
                        info.fromX = tmpX;
                }
                int tmpY = c - '1';
                if ((tmpY >= 0) && (tmpY < 8)) {
                    if (atToSq || (info.fromY >= 0))
                        info.toY = tmpY;
                    else
                        info.fromY = tmpY;
                }
                if ((c == 'x') || (c == '-')) {
                    atToSq = true;
                    if (c == 'x')
                        capture = true;
                }
                if (i == strMove.length() - 1) {
                    int promPiece = charToPiece(wtm, c);
                    if (promPiece >= 0) {
                        info.promPiece = promPiece;
                    }
                }
            }
            if ((info.fromX >= 0) && (info.toX < 0)) {
                info.toX = info.fromX;
                info.fromX = -1;
            }
            if ((info.fromY >= 0) && (info.toY < 0)) {
                info.toY = info.fromY;
                info.fromY = -1;
            }
            if (info.piece < 0) {
                boolean haveAll = (info.fromX >= 0) && (info.fromY >= 0) &&
                        (info.toX >= 0) && (info.toY >= 0);
                if (!haveAll)
                    info.piece = wtm ? Piece.WPAWN : Piece.BPAWN;
            }
            if (info.promPiece < 0)
                info.promPiece = Piece.EMPTY;
        }

        if (moves == null)
            moves = MoveGeneration.instance.legalMoves(pos);

        ArrayList<Move> matches = new ArrayList<>(2);
        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            int p = pos.getPiece(m.SQfrom);
            boolean match = true;
            if ((info.piece >= 0) && (info.piece != p))
                match = false;
            if ((info.fromX >= 0) && (info.fromX != Position.getX(m.SQfrom)))
                match = false;
            if ((info.fromY >= 0) && (info.fromY != Position.getY(m.SQfrom)))
                match = false;
            if ((info.toX >= 0) && (info.toX != Position.getX(m.SQto)))
                match = false;
            if ((info.toY >= 0) && (info.toY != Position.getY(m.SQto)))
                match = false;
            if ((info.promPiece >= 0) && (info.promPiece != m.PromoteTo))
                match = false;
            if (match) {
                matches.add(m);
            }
        }
        int nMatches = matches.size();
        if (nMatches == 0)
            return null;
        else if (nMatches == 1)
            return matches.get(0);
        if (!capture)
            return null;
        Move move = null;
        for (int i = 0; i < matches.size(); i++) {
            Move m = matches.get(i);
            int capt = pos.getPiece(m.SQto);
            if (capt != Piece.EMPTY) {
                if (move == null)
                    move = m;
                else
                    return null;
            }
        }
        return move;
    }

    /** Convert a chess move to a readable form
     * @param  longForm Use long notation (from-to)
     * @param localized Use localized piece names */
    public static String moveToString(Position pos, Move move, boolean longForm, boolean localized){
        return moveToString(pos, move, longForm, localized, null);
    }
    /** Convert a chess move to a readable form
     * @param  longForm Use long notation (from-to)
     * @param localized Use localized piece names */
    public static String moveToString(Position pos, Move move, boolean longForm, boolean localized, List<Move> moves){
        if((move==null) || move.equals(new Move(0,0,0))) return "--";
        StringBuilder ret = new StringBuilder();
        int wKingStPos = Position.getSquare(4, 0);
        int bKingStPos = Position.getSquare(4, 7);
        if(move.SQfrom == wKingStPos && pos.getPiece(wKingStPos)==Piece.WKING){
            // Check white castle
            if(move.SQto ==Position.getSquare(6, 0)){
                ret.append("O-O");
            } else if(move.SQto ==Position.getSquare(2,0)){
                ret.append("O-O-O");
            }
        } else if(move.SQfrom == bKingStPos && pos.getPiece(bKingStPos)==Piece.BKING){
            // Check black castle
            if(move.SQto ==Position.getSquare(6, 7)){
                ret.append("O-O");
            } else if(move.SQto == Position.getSquare(2, 7)){
                ret.append("O-O-O");
            }
        }

        if(ret.length()==0) {
            if (pieceNames == null) localized = false;
            int p = pos.getPiece(move.SQfrom);
            if (localized) {
                ret.append(pieceToLocalChar(p, false));
            } else {
                ret.append(pieceToChar(p, false));
            }
            int x1 = Position.getX(move.SQfrom);
            int y1 = Position.getY(move.SQfrom);
            int x2 = Position.getX(move.SQto);
            int y2 = Position.getY(move.SQto);
            if (longForm) {
                ret.append((char) (x1 + 'a'));
                ret.append((char) (y1 + '1'));
                ret.append(isCapture(pos, move) ? 'x' : '-');
            } else {
                if (p == (pos.whiteMove ? Piece.WPAWN : Piece.BPAWN)) {
                    if (isCapture(pos, move)) {
                        ret.append((char) (x1 + 'a'));
                    }
                } else {
                    int numSameTarget = 0;
                    int numSameFile = 0;
                    int numSameRow = 0;
                    if (moves == null) {
                        moves = MoveGeneration.instance.legalMoves(pos);
                    }
                    int mSize = moves.size();
                    for (int mi = 0; mi < mSize; mi++) {
                        Move m = moves.get(mi);
                        if ((pos.getPiece(m.SQfrom) == p) && (m.SQto == move.SQto)) {
                            numSameFile++;
                            if (Position.getX(m.SQfrom) == x1) numSameFile++;
                            if (Position.getY(m.SQfrom) == y1) numSameRow++;
                        }
                    }
                    if (numSameTarget < 2) {
                        //No file/row info needed
                    } else if (numSameFile < 2) {
                        ret.append((char) (x1 + 'a')); // Only file info needed
                    } else if (numSameRow < 2) {
                        ret.append((char) (y1 + '1')); // Only row info needed
                    } else {
                        ret.append((char) (x1 + 'a'));
                        ret.append((char) (y1 + '1'));
                    }
                }
                if (isCapture(pos, move)) {
                    ret.append('x');
                }
            }
            ret.append((char) (x2 + 'a'));
            ret.append((char) (y2 + '1'));
            if (move.PromoteTo != Piece.EMPTY){
                if(localized){
                    ret.append(pieceToLocalChar(move.PromoteTo, false));
                } else{
                    ret.append(pieceToChar(move.PromoteTo, false));
                }
            }
        }
        UndoInfo ui = new UndoInfo();
        pos.makeMove(move, ui);
        boolean givesCheck = MoveGeneration.inCheck(pos);
        if(givesCheck){
            ArrayList<Move> nextMoves = MoveGeneration.instance.legalMoves(pos);
            if(nextMoves.size() == 0){
                ret.append('#');
            } else{
                ret.append('+');
            }
        }
        pos.UndoMove(move, ui);

        return ret.toString();
    }

    private static boolean isCapture(Position pos, Move move){
        if(pos.getPiece(move.SQto)==Piece.EMPTY){
            int p = pos.getPiece(move.SQfrom);
            if((p==(pos.whiteMove ? Piece.WPAWN:Piece.BPAWN))&&(move.SQto ==pos.getEpSquare())) {
                return true;
            } else{
                return false;
            }
        }else{
            return true;
        }
    }

    /** Check if the move is valid in current position */
    public static boolean isValid(Position pos, Move move){
        if(move==null) return false;
        ArrayList<Move> moves = new MoveGeneration().legalMoves(pos);
        for(int i=0; i<moves.size(); i++){
            if(move.equals(moves.get(i))) return true;
        }
        return false;
    }

    private final static class MoveInfo{
        int piece;
        int fromX, fromY, toX, toY;
        int promPiece;
        MoveInfo() { piece=fromX=fromY=toY=toX=promPiece=-1; }
    }

    /** Move object to UCI format */
    public static String moveToUCIString(Move m){
        String ret = squareToString(m.SQfrom);
        ret += squareToString(m.SQto);
        switch (m.PromoteTo){
            case Piece.WQUEEN: case Piece.BQUEEN: ret+="q"; break;
            case Piece.WROOK: case Piece.BROOK: ret+="r"; break;
            case Piece.WBISHOP: case Piece.BBISHOP: ret+="b"; break;
            case Piece.WKNIGHT: case Piece.BKNIGHT: ret+="n"; break;
            default: break;
        }
        return ret;
    }

    /** String in UCI format to a Move object */
    public  static Move UCIStringToMove(String move){
        Move m = null;
        if((move.length()<4) || (move.length()>5)) return m;
        int fromSq = TextInfo.getSquare(move.substring(0, 2));
        int toSq = TextInfo.getSquare(move.substring(2, 4));
        if ((fromSq<0) || (toSq<0)){
            return m;
        }
        char prom = ' ';
        boolean white = true;
        if (move.length()==5){
            prom = move.charAt(4);
            if(Position.getY(toSq) == 7){
                white = true;
            } else if(Position.getY(toSq) == 0){
                white = false;
            } else{
                return m;
            }
        }
        int promoteTo;
        switch (prom){
            case ' ': promoteTo = Piece.EMPTY; break;
            case 'q': promoteTo = white ? Piece.WQUEEN : Piece.BQUEEN; break;
            case 'r': promoteTo = white ? Piece.WROOK : Piece.BROOK; break;
            case 'b': promoteTo = white ? Piece.WBISHOP : Piece.BBISHOP; break;
            case 'n': promoteTo = white ? Piece.WKNIGHT : Piece.BKNIGHT; break;
            default: return m;
        }
        m = new Move(fromSq, toSq, promoteTo);
        return m;
    }

    public static int getSquare(String s){
        int x = s.charAt(0) - 'a';
        int y = s.charAt(1) - '1';
        if((x<0) || (x>7) || (y<0) || (y>7)) return -1;
        return Position.getSquare(x, y);
    }

    public static String squareToString(int sq){
        StringBuilder ret = new StringBuilder();
        int x = Position.getX(sq);
        int y = Position.getY(sq);
        ret.append((char)(x+'a'));
        ret.append((char)(y+'1'));
        return ret.toString();
    }

    public static String asciiBoard(Position pos){
        StringBuilder ret = new StringBuilder(400);
        String nl = String.format(Locale.US, "%n");
        ret.append("    +----+----+----+----+----+----+----+----+");
        ret.append(nl);
        for (int y=7; y>=0; y--){
            ret.append("    |");
            for (int x=0; x<8; x++){
                ret.append(' ');
                int p = pos.getPiece(Position.getSquare(x, y));
                if (p==Piece.EMPTY){
                    boolean dark = Position.isDarkSquare(x, y);
                    ret.append(dark ? ".. |" : "   |");
                } else{
                    ret.append(Piece.isWhite(p) ? ' ' : '*');
                    String pieceName = pieceToChar(p, false);
                    if (pieceName.length()==0) pieceName="P";
                    ret.append(pieceName);
                    ret.append(" |");
                }
            }
            ret.append(nl);
            ret.append("    +----+----+----+----+----+----+----+----+");
            ret.append(nl);
        }
        return ret.toString();
    }

    /** Change int to a char (doesn't account for color) */
    public static String pieceToChar(int p, boolean namedPawn){
        switch (p){
            case Piece.WKING: case Piece.BKING: return "K";
            case Piece.WQUEEN: case Piece.BQUEEN: return "Q";
            case Piece.WROOK: case Piece.BROOK: return "R";
            case Piece.WBISHOP: case Piece.BBISHOP: return "B";
            case Piece.WKNIGHT: case Piece.BKNIGHT: return "N";
            case Piece.WPAWN: case Piece.BPAWN: if(namedPawn) return "P";
        }
        return "";
    }

    public static String pieceToLocalChar(int p, boolean namedPawn){
        switch (p){
            case Piece.WKING: case Piece.BKING: return pieceNames[5];
            case Piece.WQUEEN: case Piece.BQUEEN: return pieceNames[4];
            case Piece.WROOK: case Piece.BROOK: return pieceNames[3];
            case Piece.WBISHOP: case Piece.BBISHOP: return pieceNames[2];
            case Piece.WKNIGHT: case Piece.BKNIGHT: return pieceNames[1];
            case Piece.WPAWN: case Piece.BPAWN: if(namedPawn) return pieceNames[0];
        }
        return "";
    }

    /** Changes char to an int that represents the int (accounts for white and black)*/
    public static int charToPiece(boolean wm, char c){
        switch (c){
            case 'Q': case 'q': return wm ? Piece.WQUEEN : Piece.BQUEEN;
            case 'R': case 'r': return wm ? Piece.WROOK : Piece.BROOK;
            case 'B':           return wm ? Piece.WBISHOP : Piece.BBISHOP;
            case 'N': case 'n': return wm ? Piece.WKNIGHT : Piece.BKNIGHT;
            case 'K': case 'k': return wm ? Piece.WKING : Piece.BKING;
            case 'P': case 'p': return wm ? Piece.WPAWN : Piece.BPAWN;
        }
        return -1;
    }

    /** Promotion equals sign */
    public static String PromotionStr(String str){
        int id = str.length() - 1;
        while(id>0){
            char c = str.charAt(id);
            if((c!='#') && (c!='+'))  break;
            id--;
        }
        if((id>0) && (charToPiece(true, str.charAt(id)) != 1)){
            id--;
        }
        return str.substring(0, id+1) + '=' + str.substring(id + 1, str.length());
    }
}
