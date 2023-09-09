package com.example.caecuschess.tb;

import java.util.concurrent.ConcurrentLinkedQueue;

import com.example.caecuschess.EngineUtil;
/** Interface to native gtb probing code. */
class GtbProbe {
    static {
        System.loadLibrary("gtb");
    }

    private String currTbPath = "";
    private ConcurrentLinkedQueue<String> tbPathQueue = new ConcurrentLinkedQueue<>();

    GtbProbe() {
    }

    public final void setPath(String tbPath, boolean forceReload) {
        if (forceReload || !tbPathQueue.isEmpty() || !currTbPath.equals(tbPath)) {
            tbPathQueue.add(tbPath);
            Thread t = new Thread(() -> {
                // Sleep 0.5s to increase probability that engine
                // is initialized before TB.
                try { Thread.sleep(500); } catch (InterruptedException ignore) { }
                initIfNeeded();
            });
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    public final synchronized void initIfNeeded() {
        String path = tbPathQueue.poll();
        while (!tbPathQueue.isEmpty())
            path = tbPathQueue.poll();
        if (path != null) {
            currTbPath = path;
            synchronized (EngineUtil.nativeLock) {
                init(currTbPath);
            }
        }
    }

    final static int NOPIECE = 0;
    final static int PAWN    = 1;
    final static int KNIGHT  = 2;
    final static int BISHOP  = 3;
    final static int ROOK    = 4;
    final static int QUEEN   = 5;
    final static int KING    = 6;

    final static int NOSQUARE = 64;

    // Castle masks
    final static int H1_CASTLE = 8;
    final static int A1_CASTLE = 4;
    final static int H8_CASTLE = 2;
    final static int A8_CASTLE = 1;

    // tbinfo values
    final static int DRAW    = 0;
    final static int WMATE   = 1;
    final static int BMATE   = 2;
    final static int FORBID  = 3;
    final static int UNKNOWN = 7;

    /**
     * Probe tablebases.
     * @param wtm           True if white to move.
     * @param epSq          En passant square, or NOSQUARE.
     * @param castleMask    Castle mask.
     * @param whiteSquares  Array of squares occupied by white pieces, terminated with NOSQUARE.
     * @param blackSquares  Array of squares occupied by black pieces, terminated with NOSQUARE.
     * @param whitePieces   Array of white pieces, terminated with NOPIECE.
     * @param blackPieces   Array of black pieces, terminated with NOPIECE.
     * @param result        Two element array. Set to [tbinfo, plies].
     * @return              True if success.
     */
    public final native boolean probeHard(boolean wtm, int epSq,
                                          int castleMask,
                                          int[] whiteSquares,
                                          int[] blackSquares,
                                          byte[] whitePieces,
                                          byte[] blackPieces,
                                          int[] result);

    private native static boolean init(String tbPath);
}
