package org.jabref.logic.importer.fetcher;

public class IEEEcleanup {

    private  void badCharHeuristic( char []str, int size,int badchar[])
    {

        // Initialize all occurrences as -1
        for (int i = 0; i < 256; i++){
            badchar[i] = -1;
        }


        // Fill the actual value of last occurrence
        // of a character (indices of table are ascii and values are index of occurrence)
        for (int i = 0; i < size; i++){
            badchar[(int) str[i]] = i;
        }
    }

    /* A pattern searching function that uses Bad
    Character Heuristic of Boyer Moore Algorithm */
    public String clean(String str, String t) {
        int m = t.length();
        int n = str.length();
        StringBuilder s = new StringBuilder(str);
        int[] badChar = new int[256];

        badCharHeuristic(t.toCharArray(), m, badChar);

        int i = 0;
        while (i <= n - m) {
            int j = m - 1;

            // Keep reducing the index j of pattern while characters of pattern
            // and string are matching at this shift s
            while (j >= 0 && t.charAt(j) == str.charAt(i + j)) {
                j--;
            }

            // If the pattern is present at current shift, then remove it
            if (j < 0) {
                s.delete(i, i + m);
                n = s.length();
                i += m;
            }
            else {
                // Shift the pattern so that the bad character in text aligns with the last occurrence of it in pattern.
                i += Math.max(1, j - badChar[s.charAt(i + j)]);
            }
        }
        return s.toString();
    }
}
