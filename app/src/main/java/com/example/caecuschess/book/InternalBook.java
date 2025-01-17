package com.example.caecuschess.book;

import android.annotation.SuppressLint;

import com.example.caecuschess.GameEngine.ChessError;
import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Piece;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;
import com.example.caecuschess.GameEngine.UndoInfo;
import com.example.caecuschess.book.CaecusBook.BookEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressLint("UseSparseArrays")
final class InternalBook implements IOpeningBook {
    private static HashMap<Long, ArrayList<BookEntry>> bookMap;
    private static int numBookMoves = -1;
    private boolean enabled = false;

    InternalBook() {
        Thread t = new Thread(this::initInternalBook);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public ArrayList<BookEntry> getBookEntries(BookPosInput posInput) {
        Position pos = posInput.getCurrPos();
        initInternalBook();
        ArrayList<BookEntry> ents = bookMap.get(pos.createZobristHash());
        if (ents == null)
            return null;
        ArrayList<BookEntry> ret = new ArrayList<>();
        for (BookEntry be : ents) {
            BookEntry be2 = new BookEntry(be.move);
            be2.weight = (float)(Math.sqrt(be.weight) * 100 + 1);
            ret.add(be2);
        }
        return ret;
    }

    @Override
    public void setOptions(BookOptions options) {
        enabled = options.filename.equals("internal:");
    }

    private synchronized void initInternalBook() {
        if (numBookMoves >= 0)
            return;
//        long t0 = System.currentTimeMillis();
        bookMap = new HashMap<>();
        numBookMoves = 0;
        try {
            InputStream inStream = getClass().getResourceAsStream("/book.bin");
            if (inStream == null)
                throw new IOException();
            List<Byte> buf = new ArrayList<>(8192);
            byte[] tmpBuf = new byte[1024];
            while (true) {
                int len = inStream.read(tmpBuf);
                if (len <= 0) break;
                for (int i = 0; i < len; i++)
                    buf.add(tmpBuf[i]);
            }
            inStream.close();
            Position startPos = TextInfo.readFEN(TextInfo.startPosFEN);
            Position pos = new Position(startPos);
            UndoInfo ui = new UndoInfo();
            int len = buf.size();
            for (int i = 0; i < len; i += 2) {
                int b0 = buf.get(i); if (b0 < 0) b0 += 256;
                int b1 = buf.get(i+1); if (b1 < 0) b1 += 256;
                int move = (b0 << 8) + b1;
                if (move == 0) {
                    pos = new Position(startPos);
                } else {
                    boolean bad = ((move >> 15) & 1) != 0;
                    int prom = (move >> 12) & 7;
                    Move m = new Move(move & 63, (move >> 6) & 63,
                                      promToPiece(prom, pos.whiteMove));
                    if (!bad)
                        addToBook(pos, m);
                    pos.makeMove(m, ui);
                }
            }
        } catch (ChessError ex) {
            throw new RuntimeException();
        } catch (IOException ex) {
            throw new RuntimeException("Can't read internal opening book");
        }
    }


    /** Add a move to a position in the opening book. */
    private void addToBook(Position pos, Move moveToAdd) {
        ArrayList<BookEntry> ent = bookMap.get(pos.createZobristHash());
        if (ent == null) {
            ent = new ArrayList<>();
            bookMap.put(pos.createZobristHash(), ent);
        }
        for (int i = 0; i < ent.size(); i++) {
            BookEntry be = ent.get(i);
            if (be.move.equals(moveToAdd)) {
                be.weight++;
                return;
            }
        }
        BookEntry be = new BookEntry(moveToAdd);
        ent.add(be);
        numBookMoves++;
    }

    private static int promToPiece(int prom, boolean whiteMove) {
        switch (prom) {
        case 1: return whiteMove ? Piece.WQUEEN : Piece.BQUEEN;
        case 2: return whiteMove ? Piece.WROOK  : Piece.BROOK;
        case 3: return whiteMove ? Piece.WBISHOP : Piece.BBISHOP;
        case 4: return whiteMove ? Piece.WKNIGHT : Piece.BKNIGHT;
        default: return Piece.EMPTY;
        }
    }
}
