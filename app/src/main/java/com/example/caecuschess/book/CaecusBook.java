package com.example.caecuschess.book;

import android.annotation.SuppressLint;
import android.util.Pair;

import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.MoveGeneration;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;
import com.example.caecuschess.Util;
import com.example.caecuschess.book.IOpeningBook.BookPosInput;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Implements an opening book. */
public final class CaecusBook {
    static final class BookEntry {
        Move move;
        float weight;
        BookEntry(Move move) {
            this.move = move;
            weight = 1;
        }
        @Override
        public String toString() {
            return TextInfo.moveToUCIString(move) + " (" + weight + ")";
        }
    }
    @SuppressLint("TrulyRandom")
    private Random rndGen = new SecureRandom();

    private IOpeningBook externalBook = new NullBook();
    private IOpeningBook ecoBook = new EcoBook();
    private IOpeningBook internalBook = new InternalBook();
    private IOpeningBook noBook = new NoBook();
    private BookOptions options = null;

    private static final CaecusBook INSTANCE = new CaecusBook();

    /** Get singleton instance. */
    public static CaecusBook getInstance() {
        return INSTANCE;
    }

    private CaecusBook() {
    }

    /** Set opening book options. */
    public final synchronized void setOptions(BookOptions options) {
        this.options = options;
        if (CtgBook.canHandle(options))
            externalBook = new CtgBook();
        else if (PolyglotBook.canHandle(options))
            externalBook = new PolyglotBook();
        else if (AbkBook.canHandle(options))
            externalBook = new AbkBook();
        else
            externalBook = new NullBook();
        externalBook.setOptions(options);
        ecoBook.setOptions(options);
        internalBook.setOptions(options);
        noBook.setOptions(options);
    }

    /** Return a random book move for a position, or null if out of book. */
    public final synchronized Move getBookMove(BookPosInput posInput) {
        Position pos = posInput.getCurrPos();
        if ((options != null) && (pos.MoveCounter > options.maxLength))
            return null;
        List<BookEntry> bookMoves = getBook().getBookEntries(posInput);
        if (bookMoves == null || bookMoves.isEmpty())
            return null;

        ArrayList<Move> legalMoves = new MoveGeneration().legalMoves(pos);
        double sum = 0;
        final int nMoves = bookMoves.size();
        for (int i = 0; i < nMoves; i++) {
            BookEntry be = bookMoves.get(i);
            if (!legalMoves.contains(be.move)) {
                // If an illegal move was found, it means there was a hash collision,
                // or a corrupt external book file.
                return null;
            }
            sum += scaleWeight(bookMoves.get(i).weight);
        }
        if (sum <= 0) {
            return null;
        }
        double rnd = rndGen.nextDouble() * sum;
        sum = 0;
        for (int i = 0; i < nMoves; i++) {
            sum += scaleWeight(bookMoves.get(i).weight);
            if (rnd < sum)
                return bookMoves.get(i).move;
        }
        return bookMoves.get(nMoves-1).move;
    }

    /** Return all book moves, both as a formatted string and as a list of moves. */
    public final synchronized Pair<String,ArrayList<Move>> getAllBookMoves(BookPosInput posInput,
                                                                           boolean localized) {
        Position pos = posInput.getCurrPos();
        StringBuilder ret = new StringBuilder();
        ArrayList<Move> bookMoveList = new ArrayList<>();
        ArrayList<BookEntry> bookMoves = getBook().getBookEntries(posInput);

        // Check legality
        if (bookMoves != null) {
            ArrayList<Move> legalMoves = new MoveGeneration().legalMoves(pos);
            for (int i = 0; i < bookMoves.size(); i++) {
                BookEntry be = bookMoves.get(i);
                if (!legalMoves.contains(be.move)) {
                    bookMoves = null;
                    break;
                }
            }
        }

        if (bookMoves != null) {
            Collections.sort(bookMoves, (arg0, arg1) -> {
                double wd = arg1.weight - arg0.weight;
                if (wd != 0)
                    return (wd > 0) ? 1 : -1;
                String str0 = TextInfo.moveToUCIString(arg0.move);
                String str1 = TextInfo.moveToUCIString(arg1.move);
                return str0.compareTo(str1);
            });
            double totalWeight = 0;
            for (BookEntry be : bookMoves)
                totalWeight += scaleWeight(be.weight);
            if (totalWeight <= 0) totalWeight = 1;
            boolean first = true;
            for (BookEntry be : bookMoves) {
                Move m = be.move;
                bookMoveList.add(m);
                String moveStr = TextInfo.moveToString(pos, m, false, localized);
                if (first)
                    first = false;
                else
                    ret.append(' ');
                ret.append(Util.boldStart);
                ret.append(moveStr);
                ret.append(Util.boldStop);
                ret.append(':');
                int percent = (int)Math.round(scaleWeight(be.weight) * 100 / totalWeight);
                ret.append(percent);
            }
        }
        return new Pair<>(ret.toString(), bookMoveList);
    }

    private double scaleWeight(double w) {
        if (w <= 0)
            return 0;
        if (options == null)
            return w;
        return Math.pow(w, Math.exp(-options.random));
    }

    private IOpeningBook getBook() {
        if (externalBook.enabled()) {
            return externalBook;
        } else if (ecoBook.enabled()) {
            return ecoBook;
        } else if (noBook.enabled()) {
            return noBook;
        } else {
            return internalBook;
        }
    }
}
