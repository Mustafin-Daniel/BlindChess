package com.example.caecuschess.GameEngine;

public class ChessError extends Exception{
    public Position pos;
    public int resourceID = -1;

    public ChessError(String msg){
        super(msg);
        pos = null;
    }

    public ChessError(String msg, Position pos){
        super(msg);
        this.pos=pos;
    }

    public ChessError(int resourceID){
        super("");
        pos=null;
        this.resourceID=resourceID;
    }

    public ChessError(int resourceID, Position pos){
        super("");
        this.pos=pos;
        this.resourceID=resourceID;
    }


}//TODO Make sure that everything works and is useful
