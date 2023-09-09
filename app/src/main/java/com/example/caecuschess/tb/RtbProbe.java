package com.example.caecuschess.tb;

import com.example.caecuschess.EngineUtil;

import java.util.concurrent.ConcurrentLinkedQueue;

/** */
public class RtbProbe {
    static {
        System.loadLibrary("rtb");
    }

    private String currTbPath = "";
    private ConcurrentLinkedQueue<String> tbPathQueue = new ConcurrentLinkedQueue<>();

    RtbProbe() {
    }

    public final void setPath(String tbPath, boolean forceReload) {
        if (forceReload || !tbPathQueue.isEmpty() || !currTbPath.equals(tbPath)) {
            tbPathQueue.add(tbPath);
            Thread t = new Thread(() -> {
                // Sleep 0.4s to increase probability that engine
                // is initialized before TB.
                try { Thread.sleep(400); } catch (InterruptedException ignore) { }
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

    public final static int NOINFO = 1000;

    /**
     * Probe tablebases.
     * @param squares          Array of length 64, see Position class.
     * @param wtm              True if white to move.
     * @param epSq             En passant square, see Position class.
     * @param castleMask       Castle mask, see Position class.
     * @param halfMoveClock    half move clock, see Position class.
     * @param fullMoveCounter  Full move counter, see Position class.
     * @param result           Two element array. Set to [wdlScore, dtzScore].
     *                         The wdl score is one of:  0: Draw
     *                                                   1: win for side to move
     *                                                  -1: loss for side to move
     *                                              NOINFO: No info available
     *                         The dtz score is one of:  0: Draw
     *                                                 x>0: Win in x plies
     *                                                 x<0: Loss in -x plies
     *                                              NOINFO: No info available
     */
    public final native void probe(byte[] squares,
                                   boolean wtm,
                                   int epSq, int castleMask,
                                   int halfMoveClock,
                                   int fullMoveCounter,
                                   int[] result);

    private native static boolean init(String tbPath);
}
