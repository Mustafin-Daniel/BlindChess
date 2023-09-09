package com.example.caecuschess.book;

import com.example.caecuschess.book.CaecusBook.BookEntry;

import java.util.ArrayList;

class NoBook implements IOpeningBook {
    private boolean enabled = false;

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setOptions(BookOptions options) {
        enabled = options.filename.equals("nobook:");
    }

    @Override
    public ArrayList<BookEntry> getBookEntries(BookPosInput posInput) {
        return null;
    }
}
