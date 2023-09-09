package com.example.caecuschess.activities.util;

import android.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class FENFile {
    private final File fileName;

    public FENFile(String fileName) {
        this.fileName = new File(fileName);
    }

    public final String getName() {
        return fileName.getAbsolutePath();
    }

    public static final class FenInfo {
        public int gameNo;
        public String fen;

        FenInfo(int gameNo, String fen) {
            this.gameNo = gameNo;
            this.fen = fen;
        }

        public String toString() {
            StringBuilder info = new StringBuilder(128);
            info.append(gameNo);
            info.append(". ");
            info.append(fen);
            return info.toString();
        }
    }

    public enum FenInfoResult {
        OK,
        OUT_OF_MEMORY;
    }

    /** Read all FEN strings (one per line) in a file. */
    public final Pair<FenInfoResult,ArrayList<FenInfo>> getFenInfo() {
        ArrayList<FenInfo> fensInFile = new ArrayList<>();
        try (BufferedRandomAccessFileReader f =
                 new BufferedRandomAccessFileReader(fileName.getAbsolutePath())) {
            int fenNo = 1;
            while (true) {
                String line = f.readLine();
                if (line == null)
                    break; // EOF
                if ((line.length() == 0) || (line.charAt(0) == '#'))
                    continue;
                FenInfo fi = new FenInfo(fenNo++, line.trim());
                fensInFile.add(fi);
            }
        } catch (IOException ignore) {
        } catch (OutOfMemoryError e) {
            fensInFile.clear();
            fensInFile = null;
            return new Pair<>(FenInfoResult.OUT_OF_MEMORY, null);
        }
        return new Pair<>(FenInfoResult.OK, fensInFile);
    }
}
