package com.example.caecuschess;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.Toast;

import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.MoveGeneration;
import com.example.caecuschess.GameEngine.Piece;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.view.ChessBoard;

import java.util.ArrayList;

/** Chess board widget suitable for play mode. */
public class ChessBoardPlay extends ChessBoard {
    private PGNOptions pgnOptions = null;
    boolean oneTouchMoves;

    public ChessBoardPlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        oneTouchMoves = false;
    }

    public void setPgnOptions(PGNOptions pgnOptions) {
        this.pgnOptions = pgnOptions;
    }



    @Override
    protected XYCoord sqToPix(int x, int y) {
        int xPix = x0 + sqSize * (flipped ? 7 - x : x);
        int yPix = y0 + sqSize * (flipped ? y : 7 - y);
        return new XYCoord(xPix, yPix);
    }

    @Override
    protected XYCoord pixToSq(int xCrd, int yCrd) {
        int x = (int)Math.floor((xCrd - x0) / (double)sqSize); if (flipped) x = 7 - x;
        int y = (int)Math.floor((yCrd - y0) / (double)sqSize); if (!flipped) y = 7 - y;
        return new XYCoord(x, y);
    }

    @Override
    protected int getWidth(int sqSize) { return sqSize * 8; }
    @Override
    protected int getHeight(int sqSize) { return sqSize * 8; }
    @Override
    protected int getSqSizeW(int width) { return (width) / 8; }
    @Override
    protected int getSqSizeH(int height) { return (height) / 8; }
    @Override
    protected int getMaxHeightPercentage() { return 75; }
    @Override
    protected int getMaxWidthPercentage() { return 65; }

    @Override
    protected void computeOrigin(int width, int height) {
        x0 = (width - sqSize * 8) / 2;
        Configuration config = getResources().getConfiguration();
        boolean landScape = (config.orientation == Configuration.ORIENTATION_LANDSCAPE);
        y0 = landScape ? 0 : (height - sqSize * 8) / 2;
    }
    @Override
    protected int getXFromSq(int sq) { return Position.getX(sq); }
    @Override
    protected int getYFromSq(int sq) { return Position.getY(sq); }

    @Override
    protected int getSquare(int x, int y) { return Position.getSquare(x, y); }

    @Override
    protected void drawExtraSquares(Canvas canvas) {
    }

    public Move mousePressed(int sq) {
        if (sq < 0)
            return null;
        if ((selectedSquare != -1) && !userSelectedSquare)
            setSelection(-1); // Remove selection of opponents last moving piece

        if (!oneTouchMoves) {
            int p = pos.getPiece(sq);
            if (selectedSquare != -1) {
                if (sq == selectedSquare) {
                    if (toggleSelection)
                        setSelection(-1);
                    return null;
                }
                if (!myColor(p)) {
                    Move m = new Move(selectedSquare, sq, Piece.EMPTY);
                    setSelection(highlightLastMove ? sq : -1);
                    userSelectedSquare = false;
                    return m;
                } else
                    setSelection(sq);
            } else {
                if (myColor(p))
                    setSelection(sq);
            }
        } else {
            int prevSq = userSelectedSquare ? selectedSquare : -1;
            if (prevSq == sq) {
                if (toggleSelection)
                    setSelection(-1);
                return null;
            }
            ArrayList<Move> moves = new MoveGeneration().legalMoves(pos);
            Move matchingMove = null;
            if (prevSq >= 0)
                matchingMove = matchingMove(prevSq, sq, moves).first;
            boolean anyMatch = false;
            if  (matchingMove == null) {
                Pair<Move, Boolean> match = matchingMove(-1, sq, moves);
                matchingMove = match.first;
                anyMatch = match.second;
            }
            if (matchingMove != null) {
                setSelection(highlightLastMove ? matchingMove.SQto : -1);
                userSelectedSquare = false;
                return matchingMove;
            }
            if (!anyMatch) {
                int p = pos.getPiece(sq);
                if (myColor(p)) {
                    String msg = getContext().getString(R.string.piece_can_not_be_moved);
                    int pieceType = (pgnOptions == null) ? PGNOptions.PT_LOCAL
                                                         : pgnOptions.view.pieceType;
                    msg += ": " + PieceFontInfo.pieceAndSquareToString(pieceType, p, sq);
                    CaecusChessApp.toast(msg, Toast.LENGTH_SHORT);
                }
            }
            setSelection(anyMatch ? sq : -1);
        }
        return null;
    }

    /**
     * Determine if there is a unique legal move corresponding to one or two selected squares.
     * @param sq1   First square, or -1.
     * @param sq2   Second square.
     * @param moves List of legal moves.
     * @return      Matching move if unique.
     *              Boolean indicating if there was at least one match.
     */
    private Pair<Move, Boolean> matchingMove(int sq1, int sq2, ArrayList<Move> moves) {
        Move matchingMove = null;
        boolean anyMatch = false;
        for (Move m : moves) {
            boolean match;
            if (sq1 == -1)
                match = (m.SQfrom == sq2) || (m.SQto == sq2);
            else
                match = (m.SQfrom == sq1) && (m.SQto == sq2) ||
                        (m.SQfrom == sq2) && (m.SQto == sq1);
            if (match) {
                if (matchingMove == null) {
                    matchingMove = m;
                    anyMatch = true;
                } else {
                    if ((matchingMove.SQfrom == m.SQfrom) &&
                        (matchingMove.SQto == m.SQto)) {
                        matchingMove.PromoteTo = Piece.EMPTY;
                    } else {
                        matchingMove = null;
                        break;
                    }
                }
            }
        }
        return new Pair<>(matchingMove, anyMatch);
    }
}
