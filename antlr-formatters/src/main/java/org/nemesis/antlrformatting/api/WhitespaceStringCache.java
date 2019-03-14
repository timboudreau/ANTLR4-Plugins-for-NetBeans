package org.nemesis.antlrformatting.api;

import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
final class WhitespaceStringCache {

    private static final int BASE = 12;
    private String[] spaces = new String[BASE];
    private String[] newlines = new String[BASE];

    private int nearestGreaterMultiple(int count) {
        int div = (count / BASE) + 1;
        if (count % BASE == 0) {
            div++;
        }
        return BASE * div;
    }

    private String newSpacesString(int count) {
        char[] c = new char[count];
        Arrays.fill(c, ' ');
        return new String(c);
    }

    private String newNewlinesString(int count) {
        char[] c = new char[count];
        Arrays.fill(c, '\n');
        return new String(c);
    }

    public String spaces(int spaceCount) {
        if (spaceCount > spaces.length - 1) {
            spaces = Arrays.copyOf(spaces, nearestGreaterMultiple(spaceCount));
        }
        String result = spaces[spaceCount];
        if (result == null) {
            result = newSpacesString(spaceCount);
            spaces[spaceCount] = result;
        }
        return result;
    }

    public String newlines(int newlinesCount) {
        if (newlinesCount > newlines.length) {
            newlines = Arrays.copyOf(newlines, nearestGreaterMultiple(newlinesCount));
        }
        String result = newlines[newlinesCount];
        if (result == null) {
            result = newNewlinesString(newlinesCount);
            newlines[newlinesCount] = result;
        }
        return result;
    }
}
