package chess;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class FileUtil {
    /** Read a text file. Return string array with one string per line. */
    public static String[] readFile(String filename) throws IOException {
        ArrayList<String> ret = new ArrayList<>();
        try (InputStream inStream = new FileInputStream(filename);
             InputStreamReader inFile = new InputStreamReader(inStream, "UTF-8");
             BufferedReader inBuf = new BufferedReader(inFile)) {
            String line;
            while ((line = inBuf.readLine()) != null)
                ret.add(line);
        }
        return ret.toArray(new String[0]);
    }
}
