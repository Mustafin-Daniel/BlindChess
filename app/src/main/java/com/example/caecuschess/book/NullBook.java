package com.example.caecuschess.book;

import com.example.caecuschess.book.CaecusBook.BookEntry;

import java.util.ArrayList;

class NullBook implements IOpeningBook {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void setOptions(BookOptions options) {
    }

    @Override
    public ArrayList<BookEntry> getBookEntries(BookPosInput posInput) {
        return null;
    }
}
