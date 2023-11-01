package com.example.caecuschess.GameEngine;

import java.util.ArrayList;

public class MoveGeneration {
    public static MoveGeneration instance;
    static {
        instance = new MoveGeneration();
    }

    /** List of legal moves */
    public final ArrayList<Move> legalMoves(Position pos){
        ArrayList<Move> moveList = pseudoLegalMoves(pos);
        moveList = MoveGeneration.removeIllegal(pos, moveList);
        return moveList;
    }

    public ArrayList<Move> generateLegalMoves(Position pos){
        ArrayList<Move> moveList = generatePseudoLegalMoves(pos);
        moveList = MoveGeneration.removeIllegal(pos, moveList);
        return moveList;
    }

    public final ArrayList<Move> generatePseudoLegalMoves(Position pos) {
        ArrayList<Move> moveList = new ArrayList<>(60);
        final boolean wm = pos.whiteMove;
        for(int x=0; x<8; x++){
            for(int y=0; y<8; y++){
                int sq = Position.getSquare(x, y);
                int p = pos.getPiece(sq);
                if((p == Piece.EMPTY)||(Piece.isWhite(p)!=wm)){
                    continue;
                }
                if ((p == Piece.WROOK)||(p == Piece.BROOK)||(p==Piece.WQUEEN)||(p==Piece.BQUEEN)){
                    if(addDirection(moveList, pos, sq, 7-x, 1)) return moveList;
                    if(addDirection(moveList, pos, sq, 7-y, 8)) return moveList;
                    if(addDirection(moveList, pos, sq, x, -1)) return moveList;
                    if(addDirection(moveList, pos, sq, y, -8)) return moveList;
                }
                if ((p==Piece.WBISHOP)||(p==Piece.BBISHOP)||(p==Piece.WQUEEN)||(p==Piece.BQUEEN)){
                    if(addDirection(moveList, pos, sq, Math.min(7-x, 7-y), 9)) return moveList;
                    if(addDirection(moveList, pos, sq, Math.min(x, 7-y), 7)) return moveList;
                    if(addDirection(moveList, pos, sq, Math.min(x, y), -9)) return moveList;
                    if(addDirection(moveList, pos, sq, Math.min(7-x, y), -7)) return moveList;
                }
                if ((p==Piece.WKNIGHT)||(p==Piece.BKNIGHT)){
                    if(x<6 && y<7 && addDirection(moveList, pos, sq, 1, 10)) return moveList;
                    if(x<7 && y<6 && addDirection(moveList, pos, sq, 1, 17)) return moveList;
                    if(x>0 && y<6 && addDirection(moveList, pos, sq, 1, 15)) return moveList;
                    if(x>1 && y<7 && addDirection(moveList, pos, sq, 1, 6)) return moveList;
                    if(x>1 && y>0 && addDirection(moveList, pos, sq, 1, -10)) return moveList;
                    if(x>0 && y>1 && addDirection(moveList, pos, sq, 1, -17)) return moveList;
                    if(x<7 && y>1 && addDirection(moveList, pos, sq, 1, -15)) return moveList;
                    if(x<6 && y>0 && addDirection(moveList, pos, sq, 1, -6)) return moveList;
                }
                if ((p==Piece.WKING)||(p==Piece.BKING)){
                    if(x<7 && addDirection(moveList, pos, sq, 1, 1)) return moveList;
                    if(x<7 && y<7 && addDirection(moveList, pos, sq, 1, 9)) return moveList;
                    if(y<7 && addDirection(moveList, pos, sq, 1, 8)) return moveList;
                    if(x>0 && y<7 && addDirection(moveList, pos, sq, 1, 7)) return moveList;
                    if(x>0 && addDirection(moveList, pos, sq, 1, -1)) return moveList;
                    if(x>0 && y>0 && addDirection(moveList, pos, sq, 1, -9)) return moveList;
                    if(y>0 && addDirection(moveList, pos, sq, 1, -8)) return moveList;
                    if(x<7 && y>0 && addDirection(moveList, pos, sq, 1, -7)) return moveList;

                    int k0 = wm ? Position.getSquare(4,0) : Position.getSquare(4, 7);
                    if (Position.getSquare(x,y) == k0){
                        int longCastle = wm ? Position.WhiteLongCastleMask : Position.BlackLongCastleMask;
                        int shortCastle = wm ? Position.WhiteShortCastleMask : Position.BlackShortCastleMask;
                        int rook = wm ? Piece.WROOK : Piece.BROOK;
                        if(((pos.getCastleMask() & (1<<shortCastle))!=0) && (pos.getPiece(k0+1) == Piece.EMPTY) && (pos.getPiece(k0+2) == Piece.EMPTY) && (pos.getPiece(k0+3) == rook) && !sqAttacked(pos, k0) && !sqAttacked(pos, k0+1)){
                            moveList.add(getMoveObj(k0, k0+2, Piece.EMPTY));
                        }
                        if(((pos.getCastleMask() & (1<<longCastle))!=0) && (pos.getPiece(k0-1) == Piece.EMPTY) && (pos.getPiece(k0-2) == Piece.EMPTY) && (pos.getPiece(k0-3) == Piece.EMPTY) && (pos.getPiece(k0-4) == rook) && !sqAttacked(pos, k0) && !sqAttacked(pos, k0-1)){
                            moveList.add(getMoveObj(k0, k0-2, Piece.EMPTY));
                        }
                    }
                }
                if((p==Piece.WPAWN)||(p==Piece.BPAWN)){
                    int dir = wm ? 8 : -8;
                    if(pos.getPiece(sq+dir)==Piece.EMPTY){
                        addPawnMoves(moveList, sq, sq+dir);
                        if((y==(wm?1:6)) && (pos.getPiece(sq+2*dir)==Piece.EMPTY)){
                            addPawnMoves(moveList, sq, sq+dir*2);
                        }
                    }
                    if (x>0){
                        int toSq = sq+dir-1;
                        int cap = pos.getPiece(toSq);
                        if(cap!=Piece.EMPTY){
                            if(Piece.isWhite(cap)!=wm){
                                if(cap==(wm?Piece.BKING:Piece.WKING)){
                                    moveList.clear();
                                    moveList.add(getMoveObj(sq, toSq, Piece.EMPTY));
                                    return moveList;
                                } else {
                                    addPawnMoves(moveList, sq, toSq);
                                }
                            }
                        }else if(toSq == pos.getEpSquare()){
                            addPawnMoves(moveList, sq, toSq);
                        }
                    }
                    if(x<7){
                        int toSq = sq+dir+1;
                        int cap=pos.getPiece(toSq);
                        if(cap!=Piece.EMPTY){
                            if(Piece.isWhite(cap)!=wm){
                                if(cap==(wm?Piece.BKING:Piece.WKING)){
                                    moveList.clear();
                                    moveList.add(getMoveObj(sq, toSq, Piece.EMPTY));
                                    return moveList;
                                }else{
                                    addPawnMoves(moveList, sq, toSq);
                                }
                            }
                        }else if(toSq==pos.getEpSquare()){
                            addPawnMoves(moveList, sq, toSq);
                        }
                    }
                }
            }
        }
        return moveList;
    }


    /** List of moves that don't account for checks */
    public final ArrayList<Move> pseudoLegalMoves(Position pos) {
            ArrayList<Move> moveList = new ArrayList<>(60);
            final boolean wm = pos.whiteMove;
            for(int x=0; x<8; x++){
                for(int y=0; y<8; y++){
                    int sq = Position.getSquare(x, y);
                    int p = pos.getPiece(sq);
                    if((p == Piece.EMPTY)||(Piece.isWhite(p)!=wm)){
                        continue;
                    }
                    if ((p == Piece.WROOK)||(p == Piece.BROOK)||(p==Piece.WQUEEN)||(p==Piece.BQUEEN)){
                        if(addDirection(moveList, pos, sq, 7-x, 1)) return moveList;
                        if(addDirection(moveList, pos, sq, 7-y, 8)) return moveList;
                        if(addDirection(moveList, pos, sq, x, -1)) return moveList;
                        if(addDirection(moveList, pos, sq, y, -8)) return moveList;
                    }
                    if ((p==Piece.WBISHOP)||(p==Piece.BBISHOP)||(p==Piece.WQUEEN)||(p==Piece.BQUEEN)){
                        if(addDirection(moveList, pos, sq, Math.min(7-x, 7-y), 9)) return moveList;
                        if(addDirection(moveList, pos, sq, Math.min(x, 7-y), 7)) return moveList;
                        if(addDirection(moveList, pos, sq, Math.min(x, y), -9)) return moveList;
                        if(addDirection(moveList, pos, sq, Math.min(7-x, y), -7)) return moveList;
                    }
                    if ((p==Piece.WKNIGHT)||(p==Piece.BKNIGHT)){
                        if(x<6 && y<7 && addDirection(moveList, pos, sq, 1, 10)) return moveList;
                        if(x<7 && y<6 && addDirection(moveList, pos, sq, 1, 17)) return moveList;
                        if(x>0 && y<6 && addDirection(moveList, pos, sq, 1, 15)) return moveList;
                        if(x>1 && y<7 && addDirection(moveList, pos, sq, 1, 6)) return moveList;
                        if(x>1 && y>0 && addDirection(moveList, pos, sq, 1, -10)) return moveList;
                        if(x>0 && y>1 && addDirection(moveList, pos, sq, 1, -17)) return moveList;
                        if(x<7 && y>1 && addDirection(moveList, pos, sq, 1, -15)) return moveList;
                        if(x<6 && y>0 && addDirection(moveList, pos, sq, 1, -6)) return moveList;
                    }
                    if ((p==Piece.WKING)||(p==Piece.BKING)){
                        if(x<7 && addDirection(moveList, pos, sq, 1, 1)) return moveList;
                        if(x<7 && y<7 && addDirection(moveList, pos, sq, 1, 9)) return moveList;
                        if(y<7 && addDirection(moveList, pos, sq, 1, 8)) return moveList;
                        if(x>0 && y<7 && addDirection(moveList, pos, sq, 1, 7)) return moveList;
                        if(x>0 && addDirection(moveList, pos, sq, 1, -1)) return moveList;
                        if(x>0 && y>0 && addDirection(moveList, pos, sq, 1, -9)) return moveList;
                        if(y>0 && addDirection(moveList, pos, sq, 1, -8)) return moveList;
                        if(x<7 && y>0 && addDirection(moveList, pos, sq, 1, -7)) return moveList;

                        int k0 = wm ? Position.getSquare(4,0) : Position.getSquare(4, 7);
                        if (Position.getSquare(x,y) == k0){
                            int longCastle = wm ? Position.WhiteLongCastleMask : Position.BlackLongCastleMask;
                            int shortCastle = wm ? Position.WhiteShortCastleMask : Position.BlackShortCastleMask;
                            int rook = wm ? Piece.WROOK : Piece.BROOK;
                            if(((pos.getCastleMask() & (1<<shortCastle))!=0) && (pos.getPiece(k0+1) == Piece.EMPTY) && (pos.getPiece(k0+2) == Piece.EMPTY) && (pos.getPiece(k0+3) == rook) && !sqAttacked(pos, k0) && !sqAttacked(pos, k0+1)){
                                moveList.add(getMoveObj(k0, k0+2, Piece.EMPTY));
                            }
                            if(((pos.getCastleMask() & (1<<longCastle))!=0) && (pos.getPiece(k0-1) == Piece.EMPTY) && (pos.getPiece(k0-2) == Piece.EMPTY) && (pos.getPiece(k0-3) == Piece.EMPTY) && (pos.getPiece(k0-4) == rook) && !sqAttacked(pos, k0) && !sqAttacked(pos, k0-1)){
                                moveList.add(getMoveObj(k0, k0-2, Piece.EMPTY));
                            }
                        }
                    }
                    if((p==Piece.WPAWN)||(p==Piece.BPAWN)){
                        int dir = wm ? 8 : -8;
                        if(pos.getPiece(sq+dir)==Piece.EMPTY){
                            addPawnMoves(moveList, sq, sq+dir);
                            if((y==(wm?1:6)) && (pos.getPiece(sq+2*dir)==Piece.EMPTY)){
                                addPawnMoves(moveList, sq, sq+dir*2);
                            }
                        }
                        if (x>0){
                            int toSq = sq+dir-1;
                            int cap = pos.getPiece(toSq);
                            if(cap!=Piece.EMPTY){
                                if(Piece.isWhite(cap)!=wm){
                                    if(cap==(wm?Piece.BKING:Piece.WKING)){
                                        moveList.clear();
                                        moveList.add(getMoveObj(sq, toSq, Piece.EMPTY));
                                        return moveList;
                                    } else {
                                        addPawnMoves(moveList, sq, toSq);
                                    }
                                }
                            }else if(toSq == pos.getEpSquare()){
                                addPawnMoves(moveList, sq, toSq);
                            }
                        }
                        if(x<7){
                            int toSq = sq+dir+1;
                            int cap=pos.getPiece(toSq);
                            if(cap!=Piece.EMPTY){
                                if(Piece.isWhite(cap)!=wm){
                                    if(cap==(wm?Piece.BKING:Piece.WKING)){
                                        moveList.clear();
                                        moveList.add(getMoveObj(sq, toSq, Piece.EMPTY));
                                        return moveList;
                                    }else{
                                        addPawnMoves(moveList, sq, toSq);
                                    }
                                }
                            }else if(toSq==pos.getEpSquare()){
                                addPawnMoves(moveList, sq, toSq);
                            }
                        }
                    }
                }
            }
            return moveList;
        }

    public static ArrayList<Move> removeIllegal(Position pos, ArrayList<Move> moveList){
        ArrayList<Move> ret = new ArrayList<>();
        UndoInfo ui = new UndoInfo();
        int mlSize = moveList.size();
        for(int mi = 0; mi<mlSize; mi++){
            Move m  = moveList.get(mi);
            pos.makeMove(m, ui);
            pos.setWhiteMove(!pos.whiteMove);

            if(!inCheck(pos)) ret.add(m);

            pos.setWhiteMove(!pos.whiteMove);
            pos.UndoMove(m, ui);
        }
        return ret;
    }

    public static boolean inCheck(Position pos){
        int kingSq = pos.getKingSq(pos.whiteMove);
        if(kingSq<0){
            return false; //It doesn't exist
        }
        return sqAttacked(pos, kingSq);
    }

    private static boolean sqAttacked(Position pos, int sq){
        int x = Position.getX(sq);
        int y = Position.getY(sq);
        boolean wm = pos.whiteMove;

        final int opQueen = wm ? Piece.BQUEEN : Piece.WQUEEN;
        final int opRook = wm ? Piece.BROOK : Piece.WROOK;
        final int opBishop = wm ? Piece.BBISHOP : Piece.WBISHOP;
        final int opKnight = wm ? Piece.BKNIGHT : Piece.WKNIGHT;

        int p;
        //Check vertical, diagonal and knight attacks
        if(y>0){
            p = checkDirection(pos, sq, y, -8);
            if((p==opQueen)||(p==opRook)) return true;

            p=checkDirection(pos, sq, Math.min(x, y), -9);
            if((p==opBishop)||(p==opQueen)) return true;

            p=checkDirection(pos, sq, Math.min(7-x, y), -7);
            if((p==opBishop)||(p==opQueen)) return true;

            if(x>1){
                p=checkDirection(pos, sq, 1, -10);
                if(p==opKnight) return true;
            }
            if(x>0 && y>1){
                p=checkDirection(pos, sq, 1, -17);
                if(p==opKnight) return true;
            }
            if(x<7 && y>1){
                p=checkDirection(pos, sq, 1, -15);
                if(p==opKnight) return true;
            }
            if(x<6){
                p=checkDirection(pos, sq, 1, -6);
                if(p==opKnight) return true;
            }

            if(!wm){
                if(x<7 && y>1){
                    p=checkDirection(pos, sq, 1, -7);
                    if(p==Piece.WPAWN) return true;
                }
                if(x>0 && y>1){
                    p=checkDirection(pos, sq, 1, -9);
                    if(p==Piece.WPAWN) return true;
                }
            }
        }
        if(y<7){
            p=checkDirection(pos, sq, 7-y, 8);
            if((p==opQueen)||(p==opRook)) return true;

            p=checkDirection(pos, sq, Math.min(7-x, 7-y), 9);
            if((p==opQueen)||(p==opBishop)) return true;

            p=checkDirection(pos, sq, Math.min(x, 7-y), 7);
            if((p==opQueen)||(p==opBishop)) return true;

            if(x<6){
                p=checkDirection(pos, sq, 1, 10);
                if(p==opKnight) return true;
            }
            if(x<7 && y<6){
                p=checkDirection(pos, sq, 1, 17);
                if(p==opKnight) return true;
            }
            if(x>0 && y<6){
                p=checkDirection(pos, sq, 1, 15);
                if(p==opKnight) return true;
            }
            if(x<1){
                p=checkDirection(pos, sq, 1, 6);
                if(p==opKnight) return true;
            }

            if(wm){
                if(x<7 && y<6){
                    p=checkDirection(pos, sq, 1, 9);
                    if(p==Piece.BPAWN) return true;
                }
                if(x>0 && y<6){
                    p=checkDirection(pos, sq, 1, 7);
                    if(p==Piece.BPAWN) return true;
                }
            }
        }
        //Check horizontal attacks
        p=checkDirection(pos, sq, 7-x, 1);
        if((p==opQueen)||(p==opRook)) return true;
        p=checkDirection(pos, sq, x, -1);
        if((p==opQueen)||(p==opRook)) return true;

        int opKingSq = pos.getKingSq(!wm);
        if(opKingSq>=0){
            int opX = Position.getX(opKingSq);
            int opY = Position.getY(opKingSq);
            if((Math.abs(x-opX)<=1)&&(Math.abs(y-opY)<=1)){
                return true;
            }
        }
        return false;
    }

    private boolean addDirection(ArrayList<Move> moveList, Position pos, int sq0, int maxSteps, int delta){
        int sq = sq0;
        boolean wm = pos.whiteMove;
        final int opKing = (wm ? Piece.BKING : Piece.WKING);
        while(maxSteps > 0){
            sq += delta;
            int p = pos.getPiece(sq);
            if(p==Piece.EMPTY){
                moveList.add(getMoveObj(sq0, sq, Piece.EMPTY));
            } else {
                if(Piece.isWhite(p)!=wm){
                    if(p == opKing){
                        moveList.clear();
                        moveList.add(getMoveObj(sq0, sq, Piece.EMPTY));
                        return true;
                    } else {
                        moveList.add(getMoveObj(sq0, sq, Piece.EMPTY));
                    }
                }
                break;
            }
            maxSteps--;
        }
        return false;
    }

    private void addPawnMoves(ArrayList<Move> moveList, int sq0, int sq1){
        //White promotion
        if(sq1 >= 56){
            moveList.add(getMoveObj(sq0, sq1, Piece.WQUEEN));
            moveList.add(getMoveObj(sq0, sq1, Piece.WKNIGHT));
            moveList.add(getMoveObj(sq0, sq1, Piece.WBISHOP));
            moveList.add(getMoveObj(sq0, sq1, Piece.WROOK));
        } //Black promotion
        else if(sq1 < 8){
            moveList.add(getMoveObj(sq0, sq1, Piece.BQUEEN));
            moveList.add(getMoveObj(sq0, sq1, Piece.BKNIGHT));
            moveList.add(getMoveObj(sq0, sq1, Piece.BBISHOP));
            moveList.add(getMoveObj(sq0, sq1, Piece.BROOK));
        } else{
            moveList.add(getMoveObj(sq0, sq1, Piece.EMPTY));
        }

    }

    private static int checkDirection(Position pos, int sq, int maxSteps, int delta){
        while (maxSteps>0){
            sq+=delta;
            int p = pos.getPiece(sq);
            if(p!=Piece.EMPTY){
                return p;
            }
            maxSteps--;
        }
        return Piece.EMPTY;
    }

    private static Move getMoveObj(int from, int to, int promoteTo) {
        return new Move(from, to, promoteTo);
    }
}
