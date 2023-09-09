/*
    CaecusChess - An Android chess program.
    Copyright (C) 2016  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.example.caecuschess;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import com.example.caecuschess.GameEngine.Move;
import com.example.caecuschess.GameEngine.Piece;
import com.example.caecuschess.GameEngine.Position;
import com.example.caecuschess.GameEngine.TextInfo;

import java.util.Locale;

/** Handles text to speech translation. */
public class Speech {
    private TextToSpeech tts;
    private boolean initialized = false;
    private String toSpeak = null; // Pending text to speak after initialization
    private boolean toPlaySound = false; // Pending sound to play after initialization

    public enum Language {
        EN,   // English
        DE,   // German
        ES,   // Spanish
        NONE; // Not supported

        public static Language fromString(String langStr) {
            if ("en".equals(langStr))
                return EN;
            if ("de".equals(langStr))
                return DE;
            if ("es".equals(langStr))
                return ES;
            return NONE;
        }
    }
    private Language lang = Language.NONE;


    @SuppressWarnings("deprecation")
    private void say(String text, boolean playSound) {
        if (initialized) {
            if (lang != Language.NONE && text != null) {
                if (playSound && !tts.isSpeaking())
                    tts.playEarcon("[move]", TextToSpeech.QUEUE_ADD, null);
                tts.speak(text, TextToSpeech.QUEUE_ADD, null);
            }
            toSpeak = null;
        } else {
            toSpeak = text;
            toPlaySound = playSound;
        }
    }

    /** Immediately cancel all speech output. */
    public void flushQueue() {
        toSpeak = null;
        if (tts != null)
            tts.stop();
    }

    /** Shut down the speech engine. */
    public void shutdown() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
            lang = Language.NONE;
            initialized = false;
        }
    }

    /** Convert move "move" in position "pos" to a sentence and speak it. */
    public void say(Position pos, Move move, boolean playSound) {
        String s = moveToText(pos, move, lang);
//        System.out.printf("%.3f Speech.say(): %s\n", System.currentTimeMillis() * 1e-3, s);
        if (!s.isEmpty())
            say(s, playSound);
    }

    /** Convert move "move" in position "pos" to a sentence that can be spoken. */
    public static String moveToText(Position pos, Move move, Language lang) {
        if (move == null || move.SQfrom == move.SQto)
            return "";

        String moveStr = TextInfo.moveToString(pos, move, false, false);
        int piece = Piece.makeWhite(pos.getPiece(move.SQfrom));
        boolean capture = pos.getPiece(move.SQto) != Piece.EMPTY;
        boolean promotion = move.PromoteTo != Piece.EMPTY;
        boolean check = moveStr.endsWith("+");
        boolean checkMate = moveStr.endsWith("#");
        boolean castle = false;
        boolean enPassant = false;

        if (piece == Piece.WPAWN && !capture) {
            int fx = Position.getX(move.SQfrom);
            int tx = Position.getX(move.SQto);
            if (fx != tx) {
                capture = true; // En passant
                enPassant = true;
            }
        }

        StringBuilder sentence = new StringBuilder();

        if (piece == Piece.WKING) {
            int fx = Position.getX(move.SQfrom);
            int tx = Position.getX(move.SQto);
            if (fx == 4 && tx == 6) {
                addWord(sentence, castleToString(true, lang));
                castle = true;
            } else if (fx == 4 && (tx == 2)) {
                addWord(sentence, castleToString(false, lang));
                castle = true;
            }
        }

        if (!castle) {
            boolean pawnMove = piece == Piece.WPAWN;
            if (!pawnMove)
                addWord(sentence, pieceName(piece, lang));

            if (capture) {
                int i = moveStr.indexOf("x");
                String from = moveStr.substring(pawnMove ? 0 : 1, i);
                if (!from.isEmpty())
                    addWord(sentence, fromToString(from, lang));
                String to = moveStr.substring(i + 1, i + 3);
                addWord(sentence, captureToString(lang));
                addWord(sentence, toToString(to, lang));
                if (enPassant)
                    addWord(sentence, epToString(lang));
            } else {
                int nSkip = (promotion ? 1 : 0) + ((check | checkMate) ? 1 : 0);
                int i = moveStr.length() - nSkip;
                String from = moveStr.substring(pawnMove ? 0 : 1, i - 2);
                if (!from.isEmpty())
                    addWord(sentence, fromToString(from, lang));
                String to = moveStr.substring(i - 2, i);
                addWord(sentence, toToString(to, lang));
            }

            if (promotion)
                addWord(sentence, promToString(move.PromoteTo, lang));
        }

        if (checkMate) {
            addWord(sentence, checkMateToString(lang));
        } else if (check) {
            addWord(sentence, checkToString(lang));
        }

        return sentence.toString().trim();
    }

    /** Return the locale corresponding to a language string,
     *  or null if language not supported. */
    private static Locale getLocale(Language lang) {
        switch (lang) {
        case EN:
            return Locale.US;
        case DE:
            return Locale.GERMANY;
        case ES:
            return new Locale("es", "ES");
        case NONE:
            return null;
        }
        throw new IllegalArgumentException();
    }

    /** Add zero or more words to the string builder.
     *  If anything was added, an extra space is also added at the end. */
    private static void addWord(StringBuilder sb, String words) {
        if (!words.isEmpty())
            sb.append(words).append(' ');
    }

    /** Get the name of a non-pawn piece. Return empty string if no such piece. */
    private static String pieceName(int piece, Language lang) {
        piece = Piece.makeWhite(piece);
        switch (lang) {
        case EN:
            switch (piece) {
            case Piece.WKING:   return "King";
            case Piece.WQUEEN:  return "Queen";
            case Piece.WROOK:   return "Rook";
            case Piece.WBISHOP: return "Bishop";
            case Piece.WKNIGHT: return "Knight";
            default:            return "";
            }
        case DE:
            switch (piece) {
            case Piece.WKING:   return "König";
            case Piece.WQUEEN:  return "Dame";
            case Piece.WROOK:   return "Turm";
            case Piece.WBISHOP: return "Läufer";
            case Piece.WKNIGHT: return "Springer";
            default:            return "";
            }
        case ES:
            switch (piece) {
            case Piece.WKING:   return "Rey";
            case Piece.WQUEEN:  return "Dama";
            case Piece.WROOK:   return "Torre";
            case Piece.WBISHOP: return "Alfil";
            case Piece.WKNIGHT: return "Caballo";
            default:            return "";
            }
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String fromToString(String from, Language lang) {
        if ("b4".equals(from)) // Some TTS systems convert "b4" to "before"
            from = "b 4";
        switch (lang) {
        case EN:
            if ("a".equals(from))
                return "ae";
            return from;
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String toToString(String to, Language lang) {
        if ("b4".equals(to)) // Some TTS systems convert "b4" to "before"
            to = "b 4";
        switch (lang) {
        case EN:
            return to;
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String captureToString(Language lang) {
        switch (lang) {
        case EN:
            return "takes,";
        case DE:
            return "schlägt";
        case ES:
            return "captura";
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String castleToString(boolean kingSide, Language lang) {
        switch (lang) {
        case EN:
            return kingSide ? "Short castle" : "Long castle";
        case DE:
            return kingSide ? "Kleine Rochade" : "Große Rochade";
        case ES:
            return kingSide ? "Enroque corto" : "Enroque largo";
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String epToString(Language lang) {
        switch (lang) {
        case EN: case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String promToString(int piece, Language lang) {
        String pn = pieceName(piece, lang);
        switch (lang) {
        case EN:
            return pn;
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String checkToString(Language lang) {
        switch (lang) {
        case EN:
            return ", check!";
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }

    private static String checkMateToString(Language lang) {
        switch (lang) {
        case EN:
            return ", check mate!";
        case DE:
            return ", Schach matt!";
        case ES:
            return ", mate!";
        case NONE:
            return "";
        }
        throw new IllegalArgumentException();
    }
}
