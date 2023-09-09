package chess;

/** Exception class to represent parse errors in FEN or algebraic notation. */
public class ChessError extends Exception {
    private static final long serialVersionUID = -6051856171275301175L;

    public Position pos;
    public int resourceId = -1;

    public ChessError(String msg) {
        super(msg);
        pos = null;
    }
    public ChessError(String msg, Position pos) {
        super(msg);
        this.pos = pos;
    }

    public ChessError(int resourceId) {
        super("");
        pos = null;
        this.resourceId = resourceId;
    }

    public ChessError(int resourceId, Position pos) {
        super("");
        this.pos = pos;
        this.resourceId = resourceId;
    }
}
