package com.example.caecuschess.GameEngine;

import android.util.Pair;

import com.example.caecuschess.GameEngine.Game.GameState;
import com.example.caecuschess.PGNOptions;
import com.example.caecuschess.book.EcoDb;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;


public class GameTree {
    // Data from the seven tag roster (STR) part of the PGN standard
    private String event, site, date, round;
    public String white, black;
    // Result is the last tag pair in the STR, but it is computed on demand from the game tree.

    public Position startPos;

    // Non-standard tags
    static private final class TagPair {
        String tagName;
        String tagValue;
    }
    private List<TagPair> tagPairs;

    public Node rootNode;
    public Node currentNode;
    public Position currentPos;    // Cached value. Computable from "currentNode".

    private final PGNToken.PgnTokenReceiver gameStateListener;

    /** Creates an empty GameTree starting at the standard start position.
     * @param gameStateListener  Optional tree change listener.
     */
    public GameTree(PGNToken.PgnTokenReceiver gameStateListener) {
        this.gameStateListener = gameStateListener;
        try {
            setStartPos(TextInfo.readFEN(TextInfo.startPosFEN));
        } catch (ChessError ignore) {
        }
    }

    final void setPlayerNames(String white, String black) {
        this.white = white;
        this.black = black;
        updateListener();
    }

    /** Set start position. Drops the whole game tree. */
    final void setStartPos(Position pos) {
        event = "?";
        site = "?";
        {
            Calendar now = GregorianCalendar.getInstance();
            int year = now.get(Calendar.YEAR);
            int month = now.get(Calendar.MONTH) + 1;
            int day = now.get(Calendar.DAY_OF_MONTH);
            date = String.format(Locale.US, "%04d.%02d.%02d", year, month, day);
        }
        round = "?";
        white = "?";
        black = "?";
        startPos = pos;
        tagPairs = new ArrayList<>();
        rootNode = new Node();
        currentNode = rootNode;
        currentPos = new Position(startPos);
        updateListener();
    }

    private void updateListener() {
        if (gameStateListener != null)
            gameStateListener.clear();
    }

    /** PgnTokenReceiver implementation that generates plain text PGN data. */
    private static class PgnText implements PGNToken.PgnTokenReceiver {
        private StringBuilder sb = new StringBuilder(256);
        private String header = "";
        private int prevType = PGNToken.EOF;

        final String getPgnString() {
            StringBuilder ret = new StringBuilder(4096);
            ret.append(header);
            ret.append('\n');

            int currLineLength = 0;
            for (String s : sb.toString().split(" ")) {
                String word = s.trim();
                int wordLen = word.length();
                if (wordLen > 0) {
                    if (currLineLength == 0) {
                        ret.append(word);
                        currLineLength = wordLen;
                    } else if (currLineLength + 1 + wordLen >= 80) {
                        ret.append('\n');
                        ret.append(word);
                        currLineLength = wordLen;
                    } else {
                        ret.append(' ');
                        currLineLength++;
                        ret.append(word);
                        currLineLength += wordLen;
                    }
                }
            }
            ret.append("\n\n");
            return ret.toString();
        }

        @Override
        public void processToken(Node node, int type, String token) {
            if (    (prevType == PGNToken.RIGHT_BRACKET) &&
                    (type != PGNToken.LEFT_BRACKET))  {
                header = sb.toString();
                sb = new StringBuilder(4096);
            }
            switch (type) {
                case PGNToken.STRING: {
                    sb.append(" \"");
                    int len = token.length();
                    for (int i = 0; i < len; i++) {
                        char c = token.charAt(i);
                        if ((c == '\\') || (c == '"')) {
                            sb.append('\\');
                        }
                        sb.append(c);
                    }
                    sb.append("\"");
                    break;
                }
                case PGNToken.INTEGER:
                    if (    (prevType != PGNToken.LEFT_PAREN) &&
                            (prevType != PGNToken.RIGHT_BRACKET))
                        sb.append(' ');
                    sb.append(token);
                    break;
                case PGNToken.PERIOD:
                    sb.append('.');
                    break;
                case PGNToken.ASTERISK:
                    sb.append(" *");
                    break;
                case PGNToken.LEFT_BRACKET:
                    sb.append('[');
                    break;
                case PGNToken.RIGHT_BRACKET:
                    sb.append("]\n");
                    break;
                case PGNToken.LEFT_PAREN:
                    sb.append(" (");
                    break;
                case PGNToken.RIGHT_PAREN:
                    sb.append(')');
                    break;
                case PGNToken.NAG:
                    sb.append(" $");
                    sb.append(token);
                    break;
                case PGNToken.SYMBOL:
                    if ((prevType != PGNToken.RIGHT_BRACKET) && (prevType != PGNToken.LEFT_BRACKET))
                        sb.append(' ');
                    sb.append(token);
                    break;
                case PGNToken.COMMENT:
                    if (    (prevType != PGNToken.LEFT_PAREN) &&
                            (prevType != PGNToken.RIGHT_BRACKET))
                        sb.append(' ');
                    sb.append('{');
                    sb.append(token);
                    sb.append('}');
                    break;
                case PGNToken.EOF:
                    break;
            }
            prevType = type;
        }

        @Override
        public boolean isUpToDate() {
            return true;
        }
        @Override
        public void clear() {
        }
        @Override
        public void setCurrent(Node node) {
        }
    }

    /** Update moveStrLocal in all game nodes. */
    public final void translateMoves() {
        List<Integer> currPath = new ArrayList<>();
        while (currentNode != rootNode) {
            Node child = currentNode;
            goBack();
            int childNum = currentNode.children.indexOf(child);
            currPath.add(childNum);
        }
        translateMovesHelper();
        for (int i = currPath.size() - 1; i >= 0; i--)
            goForward(currPath.get(i), false);
    }

    private void translateMovesHelper() {
        ArrayList<Integer> currPath = new ArrayList<>();
        currPath.add(0);
        while (!currPath.isEmpty()) {
            int last = currPath.size() - 1;
            int currChild = currPath.get(last);
            if (currChild == 0) {
                ArrayList<Move> moves = MoveGeneration.instance.legalMoves(currentPos);
                currentNode.verifyChildren(currentPos, moves);
                int nc = currentNode.children.size();
                for (int i = 0; i < nc; i++) {
                    Node child = currentNode.children.get(i);
                    child.moveStrLocal = TextInfo.moveToString(currentPos, child.move, false, true, moves);
                }
            }
            int nc = currentNode.children.size();
            if (currChild < nc) {
                goForward(currChild, false);
                currPath.add(0);
            } else {
                currPath.remove(last);
                last--;
                if (last >= 0) {
                    currPath.set(last, currPath.get(last) + 1);
                    goBack();
                }
            }
        }
    }

    /** Export game tree in PGN format. */
    public final String toPGN(PGNOptions options) {
        PgnText pgnText = new PgnText();
        options.exp.pgnPromotions = true;
        options.exp.pieceType = PGNOptions.PT_ENGLISH;
        pgnTreeWalker(options, pgnText);
        return pgnText.getPgnString();
    }

    /** Get ECO classification corresponding to the end of mainline. */
    public final EcoDb.Result getGameECO() {
        ArrayList<Integer> currPath = currentNode.getPathFromRoot();
        while (currentNode != rootNode)
            goBack();
        while (variations().size() > 0)
            goForward(0, false);
        EcoDb.Result ecoData = EcoDb.getInstance().getEco(this);
        while (currentNode != rootNode)
            goBack();
        for (int i : currPath)
            goForward(i, false);
        return ecoData;
    }

    /** Walks the game tree in PGN export order. */
    public final void pgnTreeWalker(PGNOptions options, PGNToken.PgnTokenReceiver out) {
        String pgnResultString = getPGNResultStringMainLine();

        // Write seven tag roster
        addTagPair(out, "Event",  event);
        addTagPair(out, "Site",   site);
        addTagPair(out, "Date",   date);
        addTagPair(out, "Round",  round);
        addTagPair(out, "White",  white);
        addTagPair(out, "Black",  black);
        addTagPair(out, "Result", pgnResultString);

        // Write special tag pairs
        String fen = TextInfo.intoFEN(startPos);
        if (!fen.equals(TextInfo.startPosFEN)) {
            addTagPair(out, "FEN", fen);
            addTagPair(out, "SetUp", "1");
        }
        // Write other non-standard tag pairs
        for (int i = 0; i < tagPairs.size(); i++)
            addTagPair(out, tagPairs.get(i).tagName, tagPairs.get(i).tagValue);

        // Write moveText section
        MoveNumber mn = new MoveNumber(startPos.MoveCounter, startPos.whiteMove);
        Node.addPgnData(out, rootNode, mn.prev(), options);
        out.processToken(null, PGNToken.SYMBOL, pgnResultString);
        out.processToken(null, PGNToken.EOF, null);
    }

    private void addTagPair(PGNToken.PgnTokenReceiver out, String tagName, String tagValue) {
        out.processToken(null, PGNToken.LEFT_BRACKET, null);
        out.processToken(null, PGNToken.SYMBOL, tagName);
        out.processToken(null, PGNToken.STRING, tagValue);
        out.processToken(null, PGNToken.RIGHT_BRACKET, null);
    }

    final static class PgnScanner {
        String data;
        int idx;
        List<PGNToken> savedTokens;

        PgnScanner(String pgn) {
            savedTokens = new ArrayList<>();
            // Skip "escape" lines, ie lines starting with a '%' character
            StringBuilder sb = new StringBuilder();
            int len = pgn.length();
            boolean col0 = true;
            for (int i = 0; i < len; i++) {
                char c = pgn.charAt(i);
                if (c == '%' && col0) {
                    while (i + 1 < len) {
                        char nextChar = pgn.charAt(i + 1);
                        if ((nextChar == '\n') || (nextChar == '\r'))
                            break;
                        i++;
                    }
                    col0 = true;
                } else {
                    sb.append(c);
                    col0 = ((c == '\n') || (c == '\r'));
                }
            }
            sb.append('\n'); // Terminating whitespace simplifies the tokenizer
            data = sb.toString();
            idx = 0;
        }

        final void putBack(PGNToken tok) {
            savedTokens.add(tok);
        }

        final PGNToken nextToken() {
            if (savedTokens.size() > 0) {
                int len = savedTokens.size();
                PGNToken ret = savedTokens.get(len - 1);
                savedTokens.remove(len - 1);
                return ret;
            }

            PGNToken ret = new PGNToken(PGNToken.EOF, null);
            try {
                while (true) {
                    char c = data.charAt(idx++);
                    if (Character.isWhitespace(c) || c == '\u00a0') {
                        // Skip
                    } else if (c == '.') {
                        ret.type = PGNToken.PERIOD;
                        break;
                    } else if (c == '*') {
                        ret.type = PGNToken.ASTERISK;
                        break;
                    } else if (c == '[') {
                        ret.type = PGNToken.LEFT_BRACKET;
                        break;
                    } else if (c == ']') {
                        ret.type = PGNToken.RIGHT_BRACKET;
                        break;
                    } else if (c == '(') {
                        ret.type = PGNToken.LEFT_PAREN;
                        break;
                    } else if (c == ')') {
                        ret.type = PGNToken.RIGHT_PAREN;
                        break;
                    } else if (c == '{') {
                        ret.type = PGNToken.COMMENT;
                        StringBuilder sb = new StringBuilder();
                        while ((c = data.charAt(idx++)) != '}') {
                            sb.append(c);
                        }
                        ret.token = sb.toString();
                        break;
                    } else if (c == ';') {
                        ret.type = PGNToken.COMMENT;
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            c = data.charAt(idx++);
                            if ((c == '\n') || (c == '\r'))
                                break;
                            sb.append(c);
                        }
                        ret.token = sb.toString();
                        break;
                    } else if (c == '"') {
                        ret.type = PGNToken.STRING;
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            c = data.charAt(idx++);
                            if (c == '"') {
                                break;
                            } else if (c == '\\') {
                                c = data.charAt(idx++);
                            }
                            sb.append(c);
                        }
                        ret.token = sb.toString();
                        break;
                    } else if (c == '$') {
                        ret.type = PGNToken.NAG;
                        StringBuilder sb = new StringBuilder();
                        while (true) {
                            c = data.charAt(idx++);
                            if (!Character.isDigit(c)) {
                                idx--;
                                break;
                            }
                            sb.append(c);
                        }
                        ret.token = sb.toString();
                        break;
                    } else { // Start of symbol or integer
                        ret.type = PGNToken.SYMBOL;
                        StringBuilder sb = new StringBuilder();
                        sb.append(c);
                        boolean onlyDigits = Character.isDigit(c);
                        final String term = ".*[](){;\"$";
                        while (true) {
                            c = data.charAt(idx++);
                            if (Character.isWhitespace(c) || (term.indexOf(c) >= 0)) {
                                idx--;
                                break;
                            }
                            sb.append(c);
                            if (!Character.isDigit(c))
                                onlyDigits = false;
                        }
                        if (onlyDigits) {
                            ret.type = PGNToken.INTEGER;
                        }
                        ret.token = sb.toString();
                        break;
                    }
                }
            } catch (StringIndexOutOfBoundsException e) {
                ret.type = PGNToken.EOF;
            }
            return ret;
        }

        final PGNToken nextTokenDropComments() {
            while (true) {
                PGNToken tok = nextToken();
                if (tok.type != PGNToken.COMMENT)
                    return tok;
            }
        }
    }

    /** Import PGN data. */
    public final boolean readPGN(String pgn, PGNOptions options) throws ChessError {
        PgnScanner scanner = new PgnScanner(pgn);
        PGNToken tok = scanner.nextToken();

        // Parse tag section
        List<TagPair> tagPairs = new ArrayList<>();
        while (tok.type == PGNToken.LEFT_BRACKET) {
            TagPair tp = new TagPair();
            tok = scanner.nextTokenDropComments();
            if (tok.type != PGNToken.SYMBOL)
                break;
            tp.tagName = tok.token;
            tok = scanner.nextTokenDropComments();
            if (tok.type != PGNToken.STRING)
                break;
            tp.tagValue = tok.token;
            tok = scanner.nextTokenDropComments();
            if (tok.type != PGNToken.RIGHT_BRACKET) {
                // In a well-formed PGN, there is nothing between the string
                // and the right bracket, but broken headers with non-escaped
                // " characters sometimes occur. Try to do something useful
                // for such headers here.
                PGNToken prevTok = new PGNToken(PGNToken.STRING, "");
                while ((tok.type == PGNToken.STRING) || (tok.type == PGNToken.SYMBOL)) {
                    if (tok.type != prevTok.type)
                        tp.tagValue += '"';
                    if ((tok.type == PGNToken.SYMBOL) && (prevTok.type == PGNToken.SYMBOL))
                        tp.tagValue += ' ';
                    tp.tagValue += tok.token;
                    prevTok = tok;
                    tok = scanner.nextTokenDropComments();
                }
            }
            tagPairs.add(tp);
            tok = scanner.nextToken();
        }
        scanner.putBack(tok);

        // Parse move section
        Node gameRoot = new Node();
        Node.parsePgn(scanner, gameRoot, options);

        if (tagPairs.size() == 0) {
            gameRoot.verifyChildren(TextInfo.readFEN(TextInfo.startPosFEN));
            if (gameRoot.children.size() == 0)
                return false;
        }

        // Store parsed data in GameTree
        String fen = TextInfo.startPosFEN;
        int nTags = tagPairs.size();
        for (int i = 0; i < nTags; i++) {
            if (tagPairs.get(i).tagName.equals("FEN")) {
                fen = tagPairs.get(i).tagValue;
            }
        }
        setStartPos(TextInfo.readFEN(fen));

        String result = "";
        for (int i = 0; i < nTags; i++) {
            String name = tagPairs.get(i).tagName;
            String val = tagPairs.get(i).tagValue;
            if (name.equals("FEN") || name.equals("SetUp")) {
                // Already handled
            } else if (name.equals("Event")) {
                event = val;
            } else if (name.equals("Site")) {
                site = val;
            } else if (name.equals("Date")) {
                date = val;
            } else if (name.equals("Round")) {
                round = val;
            } else if (name.equals("White")) {
                white = val;
            } else if (name.equals("Black")) {
                black = val;
            } else if (name.equals("Result")) {
                result = val;
            }  else {
                this.tagPairs.add(tagPairs.get(i));
            }
        }

        rootNode = gameRoot;
        currentNode = rootNode;

        // If result indicated draw by agreement or a resigned game,
        // add that info to the game tree.
        {
            // Go to end of mainline
            while (variations().size() > 0)
                goForward(0);
            GameState state = getGameState();
            if (state == GameState.ALIVE)
                addResult(result);
            // Go back to the root
            while (currentNode != rootNode)
                goBack();
        }

        updateListener();
        return true;
    }

    /** Add game result to the tree. currentNode must be at the end of the main line. */
    private void addResult(String result) {
        if (result.equals("1-0")) {
            if (currentPos.whiteMove) {
                currentNode.playerAction = "resign";
            } else {
                addMove("--", "resign", 0, "", "");
            }
        } else if (result.equals("0-1")) {
            if (!currentPos.whiteMove) {
                currentNode.playerAction = "resign";
            } else {
                addMove("--", "resign", 0, "", "");
            }
        } else if (result.equals("1/2-1/2") || result.equals("1/2")) {
            currentNode.playerAction = "draw offer";
            addMove("--", "draw accept", 0, "", "");
        }
    }

    /** Serialize to output stream. */
    public final void writeToStream(DataOutputStream dos) throws IOException {
        dos.writeUTF(event);
        dos.writeUTF(site);
        dos.writeUTF(date);
        dos.writeUTF(round);
        dos.writeUTF(white);
        dos.writeUTF(black);
        dos.writeUTF(TextInfo.intoFEN(startPos));
        int nTags = tagPairs.size();
        dos.writeInt(nTags);
        for (int i = 0; i < nTags; i++) {
            dos.writeUTF(tagPairs.get(i).tagName);
            dos.writeUTF(tagPairs.get(i).tagValue);
        }
        Node.writeToStream(dos, rootNode);
        ArrayList<Integer> pathFromRoot = currentNode.getPathFromRoot();
        int pathLen = pathFromRoot.size();
        dos.writeInt(pathLen);
        for (int i = 0; i < pathLen; i++)
            dos.writeInt(pathFromRoot.get(i));
    }

    /** De-serialize from input stream. */
    public final void readFromStream(DataInputStream dis, int version) throws IOException, ChessError {
        event = dis.readUTF();
        site = dis.readUTF();
        date = dis.readUTF();
        round = dis.readUTF();
        white = dis.readUTF();
        black = dis.readUTF();
        startPos = TextInfo.readFEN(dis.readUTF());
        currentPos = new Position(startPos);
        int nTags = dis.readInt();
        tagPairs.clear();
        for (int i = 0; i < nTags; i++) {
            TagPair tp = new TagPair();
            tp.tagName = dis.readUTF();
            tp.tagValue = dis.readUTF();
            tagPairs.add(tp);
        }
        rootNode = new Node();
        Node.readFromStream(dis, rootNode);
        currentNode = rootNode;
        int pathLen = dis.readInt();
        for (int i = 0; i < pathLen; i++)
            goForward(dis.readInt());

        updateListener();
    }


    /** Go backward in game tree. */
    public final void goBack() {
        if (currentNode.parent != null) {
            currentPos.UndoMove(currentNode.move, currentNode.ui);
            currentNode = currentNode.parent;
        }
    }

    /** Go forward in game tree.
     * @param variation Which variation to follow. -1 to follow default variation.
     */
    public final void goForward(int variation) {
        goForward(variation, true);
    }
    public final void goForward(int variation, boolean updateDefault) {
        if (currentNode.verifyChildren(currentPos))
            updateListener();
        if (variation < 0)
            variation = currentNode.defaultChild;
        int numChildren = currentNode.children.size();
        if (variation >= numChildren)
            variation = 0;
        if (updateDefault)
            currentNode.defaultChild = variation;
        if (numChildren > 0) {
            currentNode = currentNode.children.get(variation);
            currentPos.makeMove(currentNode.move, currentNode.ui);
            TextInfo.legalEPSquare(currentPos);
        }
    }

    /** Go to given node in game tree.
     * @return True if current node changed, false otherwise. */
    public final boolean goNode(Node node) {
        if (node == currentNode)
            return false;
        ArrayList<Integer> path = node.getPathFromRoot();
        while (currentNode != rootNode)
            goBack();
        for (Integer c : path)
            goForward(c);
        return true;
    }

    /** List of possible continuation moves. */
    public final ArrayList<Move> variations() {
        if (currentNode.verifyChildren(currentPos))
            updateListener();
        ArrayList<Move> ret = new ArrayList<>();
        for (Node child : currentNode.children)
            ret.add(child.move);
        return ret;
    }

    /** Add a move last in the list of variations.
     * @return Move number in variations list. -1 if moveStr is not a valid move
     */
    public final int addMove(String moveStr, String playerAction, int nag, String preComment, String postComment) {
        if (currentNode.verifyChildren(currentPos))
            updateListener();
        int idx = currentNode.children.size();
        Node node = new Node(currentNode, moveStr, playerAction, Integer.MIN_VALUE, nag, preComment, postComment);
        Move move = TextInfo.UCIStringToMove(moveStr);
        ArrayList<Move> moves = null;
        if (move == null) {
            moves = MoveGeneration.instance.legalMoves(currentPos);
            move = TextInfo.stringToMove(currentPos, moveStr, moves);
        }
        if (move == null)
            return -1;
        if (moves == null)
            moves = MoveGeneration.instance.legalMoves(currentPos);
        node.moveStr      = TextInfo.moveToString(currentPos, move, false, false, moves);
        node.moveStrLocal = TextInfo.moveToString(currentPos, move, false, true, moves);
        node.move = move;
        node.ui = new UndoInfo();
        currentNode.children.add(node);
        updateListener();
        return idx;
    }

    /** Move a variation in the ordered list of variations. */
    public final void reorderVariation(int varNo, int newPos) {
        if (currentNode.verifyChildren(currentPos))
            updateListener();
        int nChild = currentNode.children.size();
        if ((varNo < 0) || (varNo >= nChild) || (newPos < 0) || (newPos >= nChild))
            return;
        Node var = currentNode.children.get(varNo);
        currentNode.children.remove(varNo);
        currentNode.children.add(newPos, var);

        int newDef = currentNode.defaultChild;
        if (varNo == newDef) {
            newDef = newPos;
        } else {
            if (varNo < newDef) newDef--;
            if (newPos <= newDef) newDef++;
        }
        currentNode.defaultChild = newDef;
        updateListener();
    }

    /** Delete a variation. */
    public final void deleteVariation(int varNo) {
        if (currentNode.verifyChildren(currentPos))
            updateListener();
        int nChild = currentNode.children.size();
        if ((varNo < 0) || (varNo >= nChild))
            return;
        currentNode.children.remove(varNo);
        if (varNo == currentNode.defaultChild) {
            currentNode.defaultChild = 0;
        } else if (varNo < currentNode.defaultChild) {
            currentNode.defaultChild--;
        }
        updateListener();
    }

    /** Get linear game history, using default variations at branch points. */
    public final Pair<List<Node>, Integer> getMoveList() {
        List<Node> ret = new ArrayList<>();
        Node node = currentNode;
        while (node != rootNode) {
            ret.add(node);
            node = node.parent;
        }
        Collections.reverse(ret);
        int numMovesPlayed = ret.size();
        node = currentNode;
        Position pos = new Position(currentPos);
        UndoInfo ui = new UndoInfo();
        boolean changed = false;
        while (true) {
            if (node.verifyChildren(pos))
                changed = true;
            if (node.defaultChild >= node.children.size())
                break;
            Node child = node.children.get(node.defaultChild);
            ret.add(child);
            pos.makeMove(child.move, ui);
            node = child;
        }
        if (changed)
            updateListener();
        return new Pair<>(ret, numMovesPlayed);
    }

    final void setRemainingTime(int remaining) {
        currentNode.remainingTime = remaining;
    }

    final int getRemainingTime(boolean whiteMove, int initialTime) {
        final int undef = Integer.MIN_VALUE;
        int remainingTime = undef;
        Node node = currentNode;
        boolean wtm = currentPos.whiteMove;
        while (true) {
            if (wtm != whiteMove) { // If wtm in current mode, black made last move
                remainingTime = node.remainingTime;
                if (remainingTime != undef)
                    break;
            }
            Node parent = node.parent;
            if (parent == null)
                break;
            wtm = !wtm;
            node = parent;
        }
        if (remainingTime == undef) {
            remainingTime = initialTime;
        }
        return remainingTime;
    }

    final GameState getGameState() {
        Position pos = currentPos;
        String action = currentNode.playerAction;
        if (action.equals("resign")) {
            // Player made null move to resign, causing whiteMove to toggle
            return pos.whiteMove ? GameState.RESIGN_BLACK : GameState.RESIGN_WHITE;
        }
        ArrayList<Move> moves = new MoveGeneration().legalMoves(pos);
        if (moves.size() == 0) {
            if (MoveGeneration.inCheck(pos)) {
                return pos.whiteMove ? GameState.BLACK_MATE : GameState.WHITE_MATE;
            } else {
                return pos.whiteMove ? GameState.WHITE_STALEMATE : GameState.BLACK_STALEMATE;
            }
        }
        if (insufficientMaterial(pos)) {
            return GameState.DRAW_NO_MATE;
        }

        if (action.startsWith("draw accept")) {
            return GameState.DRAW_AGREE;
        }
        if (action.startsWith("draw rep")) {
            return GameState.DRAW_REP;
        }
        if (action.startsWith("draw 50")) {
            return GameState.DRAW_50;
        }
        return GameState.ALIVE;
    }

    /** Get additional info affecting gameState. A player "draw" or "resign" command. */
    final String getGameStateInfo(boolean localized) {
        String ret = "";
        String action = currentNode.playerAction;
        if (action.startsWith("draw rep ")) {
            ret = action.substring(9).trim();
        }
        if (action.startsWith("draw 50 ")) {
            ret = action.substring(8).trim();
        }
        if (localized) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ret.length(); i++) {
                int p = Piece.EMPTY;
                switch (ret.charAt(i)) {
                    case 'Q': p = Piece.WQUEEN;  break;
                    case 'R': p = Piece.WROOK;   break;
                    case 'B': p = Piece.WBISHOP; break;
                    case 'N': p = Piece.WKNIGHT; break;
                    case 'K': p = Piece.WKING;   break;
                    case 'P': p = Piece.WPAWN;   break;
                }
                if (p == Piece.EMPTY)
                    sb.append(ret.charAt(i));
                else
                    sb.append(TextInfo.pieceToLocalChar(p, false));
            }
            ret = sb.toString();
        }
        return ret;
    }

    /** Get PGN result string corresponding to the current position. */
    private final String getPGNResultString() {
        String gameResult = "*";
        switch (getGameState()) {
            case ALIVE:
                break;
            case WHITE_MATE:
            case RESIGN_BLACK:
                gameResult = "1-0";
                break;
            case BLACK_MATE:
            case RESIGN_WHITE:
                gameResult = "0-1";
                break;
            case WHITE_STALEMATE:
            case BLACK_STALEMATE:
            case DRAW_REP:
            case DRAW_50:
            case DRAW_NO_MATE:
            case DRAW_AGREE:
                gameResult = "1/2-1/2";
                break;
        }
        return gameResult;
    }

    /** Evaluate PGN result string at the end of the main line. */
    public final String getPGNResultStringMainLine() {
        List<Integer> currPath = new ArrayList<>();
        while (currentNode != rootNode) {
            Node child = currentNode;
            goBack();
            int childNum = currentNode.children.indexOf(child);
            currPath.add(childNum);
        }
        while (variations().size() > 0)
            goForward(0, false);
        String res = getPGNResultString();
        while (currentNode != rootNode)
            goBack();
        for (int i = currPath.size() - 1; i >= 0; i--)
            goForward(currPath.get(i), false);
        return res;
    }

    private static boolean insufficientMaterial(Position pos) {
        if (pos.numPiece(Piece.WQUEEN) > 0) return false;
        if (pos.numPiece(Piece.WROOK)  > 0) return false;
        if (pos.numPiece(Piece.WPAWN)  > 0) return false;
        if (pos.numPiece(Piece.BQUEEN) > 0) return false;
        if (pos.numPiece(Piece.BROOK)  > 0) return false;
        if (pos.numPiece(Piece.BPAWN)  > 0) return false;
        int wb = pos.numPiece(Piece.WBISHOP);
        int wn = pos.numPiece(Piece.WKNIGHT);
        int bb = pos.numPiece(Piece.BBISHOP);
        int bn = pos.numPiece(Piece.BKNIGHT);
        if (wb + wn + bb + bn <= 1) {
            return true;    // King + bishop/knight vs king is draw
        }
        if (wn + bn == 0) {
            // Only bishops. If they are all on the same color, the position is a draw.
            boolean bSquare = false;
            boolean wSquare = false;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int p = pos.getPiece(Position.getSquare(x, y));
                    if ((p == Piece.BBISHOP) || (p == Piece.WBISHOP)) {
                        if (Position.isDarkSquare(x, y)) {
                            bSquare = true;
                        } else {
                            wSquare = true;
                        }
                    }
                }
            }
            if (!bSquare || !wSquare) {
                return true;
            }
        }
        return false;
    }


    /** Keep track of current move and side to move. Used for move number printing. */
    private static final class MoveNumber {
        final int moveNo;
        final boolean wtm; // White to move
        MoveNumber(int moveNo, boolean wtm) {
            this.moveNo = moveNo;
            this.wtm = wtm;
        }
        public final MoveNumber next() {
            if (wtm) return new MoveNumber(moveNo, false);
            else     return new MoveNumber(moveNo + 1, true);
        }
        public final MoveNumber prev() {
            if (wtm) return new MoveNumber(moveNo - 1, false);
            else     return new MoveNumber(moveNo, true);
        }
    }

    /**
     *  A node object represents a position in the game tree.
     *  The position is defined by the move that leads to the position from the parent position.
     *  The root node is special in that it doesn't have a move.
     */
    public static class Node {
        String moveStr;             // String representation of move leading to this node. Empty string in root node.
        String moveStrLocal;        // Localized version of moveStr
        public Move move;           // Computed on demand for better PGN parsing performance.
        // Subtrees of invalid moves will be dropped when detected.
        // Always valid for current node.
        private UndoInfo ui;        // Computed when move is computed
        String playerAction;        // Player action. Draw claim/offer/accept or resign.

        int remainingTime;          // Remaining time in ms for side that played moveStr, or INT_MIN if unknown.
        int nag;                    // Numeric annotation glyph
        String preComment;          // Comment before move
        String postComment;         // Comment after move

        private Node parent;        // Null if root node
        int defaultChild;
        private ArrayList<Node> children;

        public Node() {
            this.moveStr = "";
            this.moveStrLocal = "";
            this.move = null;
            this.ui = null;
            this.playerAction = "";
            this.remainingTime = Integer.MIN_VALUE;
            this.parent = null;
            this.children = new ArrayList<>();
            this.defaultChild = 0;
            this.nag = 0;
            this.preComment = "";
            this.postComment = "";
        }

        public Node(Node parent, String moveStr, String playerAction, int remainingTime, int nag,
                    String preComment, String postComment) {
            this.moveStr = moveStr;
            this.moveStrLocal = moveStr;
            this.move = null;
            this.ui = null;
            this.playerAction = playerAction;
            this.remainingTime = remainingTime;
            this.parent = parent;
            this.children = new ArrayList<>();
            this.defaultChild = 0;
            this.nag = nag;
            this.preComment = preComment;
            this.postComment = postComment;
        }

        public Node getParent() {
            return parent;
        }

        public boolean hasSibling() {
            return parent != null && parent.children.size() > 1;
        }

        public Node getFirstChild() {
            return children.isEmpty() ? null : children.get(0);
        }

        /** nodePos must represent the same position as this Node object. */
        private boolean verifyChildren(Position nodePos) {
            return verifyChildren(nodePos, null);
        }
        private boolean verifyChildren(Position nodePos, ArrayList<Move> moves) {
            boolean anyToRemove = false;
            for (Node child : children) {
                if (child.move == null) {
                    if (moves == null)
                        moves = MoveGeneration.instance.legalMoves(nodePos);
                    Move move = TextInfo.stringToMove(nodePos, child.moveStr, moves);
                    if (move != null) {
                        child.moveStr      = TextInfo.moveToString(nodePos, move, false, false, moves);
                        child.moveStrLocal = TextInfo.moveToString(nodePos, move, false, true, moves);
                        child.move = move;
                        child.ui = new UndoInfo();
                    } else {
                        anyToRemove = true;
                    }
                }
            }
            if (anyToRemove) {
                ArrayList<Node> validChildren = new ArrayList<>();
                for (Node child : children)
                    if (child.move != null)
                        validChildren.add(child);
                children = validChildren;
            }
            return anyToRemove;
        }

        final ArrayList<Integer> getPathFromRoot() {
            ArrayList<Integer> ret = new ArrayList<>(64);
            Node node = this;
            while (node.parent != null) {
                ret.add(node.getChildNo());
                node = node.parent;
            }
            Collections.reverse(ret);
            return ret;
        }

        /** Return this node's position in the parent node child list. */
        public final int getChildNo() {
            Node p = parent;
            for (int i = 0; i < p.children.size(); i++)
                if (p.children.get(i) == this)
                    return i;
            throw new RuntimeException();
        }

        static void writeToStream(DataOutputStream dos, Node node) throws IOException {
            while (true) {
                dos.writeUTF(node.moveStr);
                if (node.move != null) {
                    dos.writeByte(node.move.SQfrom);
                    dos.writeByte(node.move.SQto);
                    dos.writeByte(node.move.PromoteTo);
                } else {
                    dos.writeByte(-1);
                }
                dos.writeUTF(node.playerAction);
                dos.writeInt(node.remainingTime);
                dos.writeInt(node.nag);
                dos.writeUTF(node.preComment);
                dos.writeUTF(node.postComment);
                dos.writeInt(node.defaultChild);
                int nChildren = node.children.size();
                dos.writeInt(nChildren);
                if (nChildren == 0)
                    break;
                for (int i = 1; i < nChildren; i++) {
                    writeToStream(dos, node.children.get(i));
                }
                node = node.children.get(0);
            }
        }

        static void readFromStream(DataInputStream dis, Node node) throws IOException {
            while (true) {
                node.moveStr = dis.readUTF();
                node.moveStrLocal = node.moveStr;
                int from = dis.readByte();
                if (from >= 0) {
                    int to = dis.readByte();
                    int prom = dis.readByte();
                    node.move = new Move(from, to, prom);
                    node.ui = new UndoInfo();
                }
                node.playerAction = dis.readUTF();
                node.remainingTime = dis.readInt();
                node.nag = dis.readInt();
                node.preComment = dis.readUTF();
                node.postComment = dis.readUTF();
                node.defaultChild = dis.readInt();
                int nChildren = dis.readInt();
                if (nChildren == 0)
                    break;
                for (int i = 1; i < nChildren; i++) {
                    Node child = new Node();
                    child.parent = node;
                    readFromStream(dis, child);
                    node.children.add(child);
                }
                Node child = new Node();
                child.parent = node;
                node.children.add(0, child);
                node = child;
            }
        }

        /** Export whole tree rooted at "node" in PGN format. */
        public static void addPgnData(PGNToken.PgnTokenReceiver out, Node node,
                                      MoveNumber moveNum, PGNOptions options) {
            boolean needMoveNr = node.addPgnDataOneNode(out, moveNum, true, options);
            while (true) {
                int nChild = node.children.size();
                if (nChild == 0)
                    break;
                MoveNumber nextMN = moveNum.next();
                needMoveNr = node.children.get(0).addPgnDataOneNode(out, nextMN, needMoveNr, options);
                if (options.exp.variations) {
                    for (int i = 1; i < nChild; i++) {
                        out.processToken(node, PGNToken.LEFT_PAREN, null);
                        addPgnData(out, node.children.get(i), nextMN, options);
                        out.processToken(node, PGNToken.RIGHT_PAREN, null);
                        needMoveNr = true;
                    }
                }
                node = node.children.get(0);
                moveNum = moveNum.next();
            }
        }

        /** Export this node in PGN (or display text) format. */
        private boolean addPgnDataOneNode(PGNToken.PgnTokenReceiver out, MoveNumber mn,
                                          boolean needMoveNr, PGNOptions options) {
            if ((preComment.length() > 0) && options.exp.comments) {
                out.processToken(this, PGNToken.COMMENT, preComment);
                needMoveNr = true;
            }
            if (moveStr.length() > 0) {
                boolean nullSkip = moveStr.equals("--") && (playerAction.length() > 0) && !options.exp.playerAction;
                if (!nullSkip) {
                    if (mn.wtm) {
                        out.processToken(this, PGNToken.INTEGER, Integer.valueOf(mn.moveNo).toString());
                        out.processToken(this, PGNToken.PERIOD, null);
                    } else {
                        if (needMoveNr) {
                            out.processToken(this, PGNToken.INTEGER, Integer.valueOf(mn.moveNo).toString());
                            for (int i = 0; i < 3; i++)
                                out.processToken(this, PGNToken.PERIOD, null);
                        }
                    }
                    String str;
                    if (options.exp.pieceType == PGNOptions.PT_ENGLISH) {
                        str = moveStr;
                        if (options.exp.pgnPromotions && (move != null) && (move.PromoteTo != Piece.EMPTY))
                            str = TextInfo.PromotionStr(str);
                    } else {
                        str = moveStrLocal;
                    }
                    out.processToken(this, PGNToken.SYMBOL, str);
                    needMoveNr = false;
                }
            }
            if ((nag > 0) && options.exp.nag) {
                out.processToken(this, PGNToken.NAG, Integer.valueOf(nag).toString());
                if (options.exp.moveNrAfterNag)
                    needMoveNr = true;
            }
            if ((postComment.length() > 0) && options.exp.comments) {
                out.processToken(this, PGNToken.COMMENT, postComment);
                needMoveNr = true;
            }
            if ((playerAction.length() > 0) && options.exp.playerAction) {
                addExtendedInfo(out, "playeraction", playerAction);
                needMoveNr = true;
            }
            if ((remainingTime != Integer.MIN_VALUE) && options.exp.clockInfo) {
                addExtendedInfo(out, "clk", getTimeStr(remainingTime));
                needMoveNr = true;
            }
            return needMoveNr;
        }

        private void addExtendedInfo(PGNToken.PgnTokenReceiver out,
                                     String extCmd, String extData) {
            out.processToken(this, PGNToken.COMMENT, "[%" + extCmd + " " + extData + "]");
        }

        private static String getTimeStr(int remainingTime) {
            int secs = (int)Math.floor((remainingTime + 999) / 1000.0);
            boolean neg = false;
            if (secs < 0) {
                neg = true;
                secs = -secs;
            }
            int mins = secs / 60;
            secs -= mins * 60;
            int hours = mins / 60;
            mins -= hours * 60;
            StringBuilder ret = new StringBuilder();
            if (neg) ret.append('-');
            if (hours < 10) ret.append('0');
            ret.append(hours);
            ret.append(':');
            if (mins < 10) ret.append('0');
            ret.append(mins);
            ret.append(':');
            if (secs < 10) ret.append('0');
            ret.append(secs);
            return ret.toString();
        }

        private Node addChild(Node child) {
            child.parent = this;
            children.add(child);
            return child;
        }

        public static void parsePgn(PgnScanner scanner, Node node, PGNOptions options) {
            Node nodeToAdd = new Node();
            boolean moveAdded = false;
            while (true) {
                PGNToken tok = scanner.nextToken();
                switch (tok.type) {
                    case PGNToken.INTEGER:
                    case PGNToken.PERIOD:
                        break;
                    case PGNToken.LEFT_PAREN:
                        if (moveAdded) {
                            node = node.addChild(nodeToAdd);
                            nodeToAdd = new Node();
                            moveAdded = false;
                        }
                        if ((node.parent != null) && options.imp.variations) {
                            parsePgn(scanner, node.parent, options);
                        } else {
                            int nestLevel = 1;
                            while (nestLevel > 0) {
                                switch (scanner.nextToken().type) {
                                    case PGNToken.LEFT_PAREN: nestLevel++; break;
                                    case PGNToken.RIGHT_PAREN: nestLevel--; break;
                                    case PGNToken.EOF: return; // Broken PGN file. Just give up.
                                }
                            }
                        }
                        break;
                    case PGNToken.NAG:
                        if (moveAdded && options.imp.nag) { // NAG must be after move
                            try {
                                nodeToAdd.nag = Integer.parseInt(tok.token);
                            } catch (NumberFormatException e) {
                                nodeToAdd.nag = 0;
                            }
                        }
                        break;
                    case PGNToken.SYMBOL:
                        if (tok.token.equals("1-0") || tok.token.equals("0-1") || tok.token.equals("1/2-1/2")) {
                            if (moveAdded) node.addChild(nodeToAdd);
                            return;
                        }
                        char lastChar = tok.token.charAt(tok.token.length() - 1);
                        if (lastChar == '+')
                            tok.token = tok.token.substring(0, tok.token.length() - 1);
                        if ((lastChar == '!') || (lastChar == '?')) {
                            int movLen = tok.token.length() - 1;
                            while (movLen > 0) {
                                char c = tok.token.charAt(movLen - 1);
                                if ((c == '!') || (c == '?'))
                                    movLen--;
                                else
                                    break;
                            }
                            String ann = tok.token.substring(movLen);
                            tok.token = tok.token.substring(0, movLen);
                            int nag = 0;
                            if      (ann.equals("!"))  nag = 1;
                            else if (ann.equals("?"))  nag = 2;
                            else if (ann.equals("!!")) nag = 3;
                            else if (ann.equals("??")) nag = 4;
                            else if (ann.equals("!?")) nag = 5;
                            else if (ann.equals("?!")) nag = 6;
                            if (nag > 0)
                                scanner.putBack(new PGNToken(PGNToken.NAG, Integer.valueOf(nag).toString()));
                        }
                        if (tok.token.length() > 0) {
                            if (moveAdded) {
                                node = node.addChild(nodeToAdd);
                                nodeToAdd = new Node();
                                moveAdded = false;
                            }
                            nodeToAdd.moveStr = tok.token;
                            nodeToAdd.moveStrLocal = tok.token;
                            moveAdded = true;
                        }
                        break;
                    case PGNToken.COMMENT:
                        try {
                            while (true) {
                                Pair<String,String> ret = extractExtInfo(tok.token, "clk");
                                tok.token = ret.first;
                                String cmdPars = ret.second;
                                if (cmdPars == null)
                                    break;
                                nodeToAdd.remainingTime = parseTimeString(cmdPars);
                            }
                            while (true) {
                                Pair<String,String> ret = extractExtInfo(tok.token, "playeraction");
                                tok.token = ret.first;
                                String cmdPars = ret.second;
                                if (cmdPars == null)
                                    break;
                                nodeToAdd.playerAction = cmdPars;
                            }
                        } catch (IndexOutOfBoundsException ignore) {
                        }
                        if (options.imp.comments) {
                            if (moveAdded)
                                nodeToAdd.postComment += tok.token;
                            else
                                nodeToAdd.preComment += tok.token;
                        }
                        break;
                    case PGNToken.ASTERISK:
                    case PGNToken.LEFT_BRACKET:
                    case PGNToken.RIGHT_BRACKET:
                    case PGNToken.STRING:
                    case PGNToken.RIGHT_PAREN:
                    case PGNToken.EOF:
                        if (moveAdded) node.addChild(nodeToAdd);
                        return;
                }
            }
        }

        private static Pair<String, String> extractExtInfo(String comment, String cmd) {
            comment = comment.replaceAll("[\n\r\t]", " ");
            String remaining = comment;
            String param = null;
            String match = "[%" + cmd + " ";
            int start = comment.indexOf(match);
            if (start >= 0) {
                int end = comment.indexOf("]", start);
                if (end >= 0) {
                    remaining = comment.substring(0, start) + comment.substring(end + 1);
                    param = comment.substring(start + match.length(), end);
                }
            }
            return new Pair<>(remaining, param);
        }

        /** Convert hh:mm:ss to milliseconds */
        private static int parseTimeString(String str) {
            str = str.trim();
            int ret = 0;
            boolean neg = false;
            int i = 0;
            if (str.charAt(0) == '-') {
                neg = true;
                i++;
            }
            int num = 0;
            final int len = str.length();
            for ( ; i < len; i++) {
                char c = str.charAt(i);
                if ((c >= '0') && (c <= '9')) {
                    num = num * 10 + c - '0';
                } else if (c == ':') {
                    ret += num;
                    num = 0;
                    ret *= 60;
                }
            }
            ret += num;
            ret *= 1000;
            if (neg)
                ret = -ret;
            return ret;
        }

        public static String nagStr(int nag) {
            switch (nag) {
                case 1: return "!";
                case 2: return "?";
                case 3: return "!!";
                case 4: return "??";
                case 5: return "!?";
                case 6: return "?!";
                case 11: return " =";
                case 13: return " ∞";
                case 14: return " +/=";
                case 15: return " =/+";
                case 16: return " +/-";
                case 17: return " -/+";
                case 18: return " +-";
                case 19: return " -+";
                default: return "";
            }
        }

        public static int strToNag(String str) {
            if      (str.equals("!"))  return 1;
            else if (str.equals("?"))  return 2;
            else if (str.equals("!!")) return 3;
            else if (str.equals("??")) return 4;
            else if (str.equals("!?")) return 5;
            else if (str.equals("?!")) return 6;
            else if (str.equals("=")) return 11;
            else if (str.equals("∞")) return 13;
            else if (str.equals("+/=")) return 14;
            else if (str.equals("=/+")) return 15;
            else if (str.equals("+/-")) return 16;
            else if (str.equals("-/+")) return 17;
            else if (str.equals("+-")) return 18;
            else if (str.equals("-+")) return 19;
            else {
                try {
                    str = str.replace("$", "");
                    return Integer.parseInt(str);
                } catch (NumberFormatException nfe) {
                    return 0;
                }
            }
        }
    }

    /** Set PGN header tags and values. Setting a non-required
     *  tag to null causes it to be removed.
     *  @return True if game result changes, false otherwise. */
    boolean setHeaders(Map<String,String> headers) {
        boolean resultChanged = false;
        for (Entry<String, String> entry : headers.entrySet()) {
            String tag = entry.getKey();
            String val = entry.getValue();
            if (tag.equals("Event")) event = val;
            else if (tag.equals("Site")) site = val;
            else if (tag.equals("Date")) date = val;
            else if (tag.equals("Round")) round = val;
            else if (tag.equals("White")) white = val;
            else if (tag.equals("Black")) black = val;
            else if (tag.equals("Result")) {
                List<Integer> currPath = new ArrayList<>();
                while (currentNode != rootNode) {
                    Node child = currentNode;
                    goBack();
                    int childNum = currentNode.children.indexOf(child);
                    currPath.add(childNum);
                }
                while (variations().size() > 0)
                    goForward(0, false);
                if (!val.equals(getPGNResultString())) {
                    resultChanged = true;
                    GameState state = getGameState();
                    switch (state) {
                        case ALIVE:
                        case DRAW_50:
                        case DRAW_AGREE:
                        case DRAW_REP:
                        case RESIGN_BLACK:
                        case RESIGN_WHITE:
                            currentNode.playerAction = "";
                            if ("--".equals(currentNode.moveStr)) {
                                Node child = currentNode;
                                goBack();
                                int childNum = currentNode.children.indexOf(child);
                                deleteVariation(childNum);
                            }
                            addResult(val);
                            break;
                        default:
                            break;
                    }
                }
                while (currentNode != rootNode)
                    goBack();
                for (int i = currPath.size() - 1; i >= 0; i--)
                    goForward(currPath.get(i), false);
            } else {
                if (val != null) {
                    boolean found = false;
                    for (TagPair t : tagPairs) {
                        if (t.tagName.equals(tag)) {
                            t.tagValue = val;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        TagPair tp = new TagPair();
                        tp.tagName = tag;
                        tp.tagValue = val;
                        tagPairs.add(tp);
                    }
                } else {
                    for (int i = 0; i < tagPairs.size(); i++) {
                        if (tagPairs.get(i).tagName.equals(tag)) {
                            tagPairs.remove(i);
                            break;
                        }
                    }
                }
            }
        }
        return resultChanged;
    }

    /** Get PGN header tags and values. */
    public void getHeaders(Map<String,String> headers) {
        headers.put("Event", event);
        headers.put("Site",  site);
        headers.put("Date",  date);
        headers.put("Round", round);
        headers.put("White", white);
        headers.put("Black", black);
        headers.put("Result", getPGNResultStringMainLine());
        for (int i = 0; i < tagPairs.size(); i++) {
            TagPair tp = tagPairs.get(i);
            headers.put(tp.tagName, tp.tagValue);
        }
    }
}