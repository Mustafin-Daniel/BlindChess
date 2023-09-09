package chess;

/** A token in a PGN data stream. Used by the PGN parser. */
public class PgnToken {
    // These are tokens according to the PGN spec
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

    // These are not tokens according to the PGN spec, but the parser
    // extracts these anyway for convenience.
    public static final int COMMENT = 10;
    public static final int EOF = 11;

    // Actual token data
    int type;
    String token;

    PgnToken(int type, String token) {
        this.type = type;
        this.token = token;
    }
}
