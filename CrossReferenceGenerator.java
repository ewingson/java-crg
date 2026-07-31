import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross Reference Generator
 *
 * Two-pass scan of a source text file:
 *   Phase 1: read all lines.
 *   Phase 2: find declarations introduced by KEYWORD (default "var") and
 *            extract the declared identifier + its declaration line.
 *   Phase 3: for every extracted identifier, scan the whole file again and
 *            record every line on which that exact identifier occurs
 *            (whole-word match only).
 *   Phase 4: print / optionally write the resulting cross-reference matrix.
 *
 * All character comparisons are plain ASCII (space = 32, 'A' = 65, ...),
 * matching the pseudocode. No regex is used in the core scan so the logic
 * stays close to the char-by-char pseudocode; ArrayList/Map are only used
 * as containers, not for the actual matching.
 *
 * Usage:
 *   java CrossReferenceGenerator <inputFile> [outputFile]
 */
public class CrossReferenceGenerator {

    // The keyword that marks a variable declaration. Change this constant
    // to index a different keyword ("let", "dim", "int", ...).
    private static final String KEYWORD = "var";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java CrossReferenceGenerator <inputFile> [outputFile]");
            return;
        }

        String inputFile = args[0];
        String outputFile = args.length > 1 ? args[1] : null;

        try {
            // ---------- Phase 1: read the file ----------
            List<String> zeile = readLines(inputFile);

            // ---------- Phase 2: find declarations ----------
            List<String> variablenname = new ArrayList<>();       // distinct names, in order found
            Map<String, Integer> declarationLine = new LinkedHashMap<>(); // name -> declaration line

            for (int i = 0; i < zeile.size(); i++) {
                extractDeclarations(zeile.get(i), i, variablenname, declarationLine);
            }

            if (variablenname.isEmpty()) {
                System.out.println("No declarations found using keyword \"" + KEYWORD + "\".");
                return;
            }

            // ---------- Phase 3: build the cross-reference matrix ----------
            // vorkommen: variable name -> ordered list of line numbers it occurs on
            Map<String, List<Integer>> vorkommen = new LinkedHashMap<>();
            for (String name : variablenname) {
                vorkommen.put(name, findOccurrences(zeile, name));
            }

            // ---------- Phase 4: output ----------
            String matrix = buildMatrixReport(variablenname, declarationLine, vorkommen);
            System.out.print(matrix);

            if (outputFile != null) {
                writeToFile(outputFile, matrix);
                System.out.println("\nMatrix written to " + outputFile);
            }

        } catch (IOException e) {
            System.out.println("Error reading/writing file: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Phase 1
    // ---------------------------------------------------------------

    private static List<String> readLines(String path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------
    // Phase 2 — declaration detection
    //
    // Mirrors the pseudocode's char-by-char core check:
    //   if (line[pos]=='v' && line[pos+1]=='a' && line[pos+2]=='r'
    //       && line[pos+3]==' ') { extract identifier at pos+4 }
    // ---------------------------------------------------------------

    private static void extractDeclarations(String line, int lineIndex,
                                              List<String> variablenname,
                                              Map<String, Integer> declarationLine) {
        int kwLen = KEYWORD.length();
        int limit = line.length() - kwLen - 1; // need at least one char + a boundary after keyword

        for (int pos = 0; pos <= limit; pos++) {
            if (matchesKeyword(line, pos, kwLen) && isWordBoundaryBefore(line, pos)) {
                char afterKeyword = line.charAt(pos + kwLen);
                if (afterKeyword == 32) { // ' ' (ascii space) — required separator
                    int nameStart = pos + kwLen + 1;
                    int nameEnd = nameStart;
                    while (nameEnd < line.length() && isIdentifierChar(line.charAt(nameEnd))) {
                        nameEnd++;
                    }
                    if (nameEnd > nameStart) {
                        String name = line.substring(nameStart, nameEnd);
                        if (!declarationLine.containsKey(name)) {
                            variablenname.add(name);
                            declarationLine.put(name, lineIndex);
                        }
                    }
                }
            }
        }
    }

    private static boolean matchesKeyword(String line, int pos, int kwLen) {
        for (int k = 0; k < kwLen; k++) {
            if (line.charAt(pos + k) != KEYWORD.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // Phase 3 — whole-word occurrence search for one variable name
    // ---------------------------------------------------------------

    private static List<Integer> findOccurrences(List<String> zeile, String name) {
        List<Integer> lines = new ArrayList<>();
        int nameLen = name.length();

        for (int lineIdx = 0; lineIdx < zeile.size(); lineIdx++) {
            String line = zeile.get(lineIdx);
            int pos = 0;
            boolean foundOnThisLine = false;

            while (pos <= line.length() - nameLen) {
                if (line.regionMatches(pos, name, 0, nameLen)
                        && isWordBoundaryBefore(line, pos)
                        && isWordBoundaryAfter(line, pos + nameLen)) {
                    foundOnThisLine = true;
                    break; // one entry per line is enough for the matrix
                }
                pos++;
            }
            if (foundOnThisLine) {
                lines.add(lineIdx);
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static boolean isIdentifierChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')   // 'A' is ascii 65
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    private static boolean isWordBoundaryBefore(String line, int pos) {
        return pos == 0 || !isIdentifierChar(line.charAt(pos - 1));
    }

    private static boolean isWordBoundaryAfter(String line, int endPos) {
        return endPos >= line.length() || !isIdentifierChar(line.charAt(endPos));
    }

    // ---------------------------------------------------------------
    // Phase 4 — report building
    // ---------------------------------------------------------------

    private static String buildMatrixReport(List<String> variablenname,
                                              Map<String, Integer> declarationLine,
                                              Map<String, List<Integer>> vorkommen) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cross-Reference Matrix (keyword: \"").append(KEYWORD).append("\")\n");
        sb.append("=".repeat(50)).append("\n");

        for (String name : variablenname) {
            sb.append(String.format("%-20s declared at line %d%n",
                    name, declarationLine.get(name) + 1)); // 1-based for readability
            List<Integer> occ = vorkommen.get(name);
            sb.append("    occurs on lines: ");
            for (int i = 0; i < occ.size(); i++) {
                sb.append(occ.get(i) + 1); // 1-based
                if (i < occ.size() - 1) sb.append(", ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void writeToFile(String path, String content) throws IOException {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(content);
        }
    }
}
