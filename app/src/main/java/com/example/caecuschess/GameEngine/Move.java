package com.example.caecuschess.GameEngine;

public class Move {
    public int SQfrom;
    public int SQto;
    public int PromoteTo;

    public Move(int SQfrom, int SQto, int PromoteTo){
        this.PromoteTo =PromoteTo;
        this.SQfrom = SQfrom;
        this.SQto = SQto;
    }
    public Move(Move M){
        this.PromoteTo =M.PromoteTo;
        this.SQto = M.SQto;
        this.SQfrom =M.SQfrom;
    }

    /** Create move from compressed */
    public static Move TurnFromCompressed(int TTM){
        return new Move((TTM>>10)&63, (TTM>>4)&63, TTM&15);
    }

    @Override
    public boolean equals(Object o){
        if((o==null) || (o.getClass()!=this.getClass())) return false;
        Move other = (Move)o;
        if(SQfrom !=other.SQfrom) return false;
        if(SQto !=other.SQto) return false;
        if(PromoteTo != other.PromoteTo) return false;
        return true;
    }

    public int hashCode(){
        return TurnToCompressed();
    }

    public int TurnToCompressed(){ return (SQfrom *64 + SQto)*16+ PromoteTo; }

    //public final String toString(){ return TextIO.moveToUCIString(this);}
    //TODO Figure out if this works
}
