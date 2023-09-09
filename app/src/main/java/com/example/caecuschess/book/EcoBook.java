package com.example.caecuschess.book;

import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.book.CaecusBook.BookEntry;

import java.util.ArrayList;

/** Opening book containing all moves that define the ECO opening classifications. */
public class EcoBook implements IOpeningBook {
    private boolean enabled = false;

    /** Constructor. */
    EcoBook() {
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setOptions(BookOptions options) {
        enabled = options.filename.equals("eco:");
    }

    @Override
    public ArrayList<BookEntry> getBookEntries(BookPosInput posInput) {
        Position pos = posInput.getCurrPos();
        ArrayList<Move> moves = EcoDb.getInstance().getMoves(pos);
        ArrayList<BookEntry> entries = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            BookEntry be = new BookEntry(moves.get(i));
            be.weight = 10000 - i;
            entries.add(be);
        }
        return entries;
    }
}
