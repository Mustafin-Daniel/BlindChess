package com.example.caecuschess.book;

import android.util.Pair;

import com.example.caecuschess.GameEngine.Game;
import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.book.CaecusBook.BookEntry;

import java.util.ArrayList;

public interface IOpeningBook {
    /** Return true if book is currently enabled. */
    boolean enabled();

    /** Set book options, including filename. */
    void setOptions(BookOptions options);

    /** Information required to query an opening book. */
    class BookPosInput {
        private final Position currPos;

        private Game game;
        private Position prevPos;
        private ArrayList<Move> moves;

        public BookPosInput(Position currPos, Position prevPos, ArrayList<Move> moves) {
            this.currPos = currPos;
            this.prevPos = prevPos;
            this.moves = moves;
        }

        public BookPosInput(Game game) {
            currPos = game.currPos();
            this.game = game;
        }

        public Position getCurrPos() {
            return currPos;
        }
        public Position getPrevPos() {
            lazyInit();
            return prevPos;
        }
        public ArrayList<Move> getMoves() {
            lazyInit();
            return moves;
        }

        private void lazyInit() {
            if (prevPos == null) {
                Pair<Position, ArrayList<Move>> ph = game.getUCIHistory();
                prevPos = ph.first;
                moves = ph.second;
            }
        }
    }
    /** Get all book entries for a position. */
    ArrayList<BookEntry> getBookEntries(BookPosInput posInput);
}
