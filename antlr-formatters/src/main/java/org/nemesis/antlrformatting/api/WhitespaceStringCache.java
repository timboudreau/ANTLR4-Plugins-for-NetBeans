/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlrformatting.api;

import java.util.Arrays;

/**
 * Keeps a cache of space-strings so, for example, we don't allocate a new
 * four-character string for every one stop indent.
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
        if (count > 1024 * 10) {
            throw new IllegalArgumentException("Insane number of spaces. "
                    + "Something has run amok: " + count);
        }
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
        if (spaceCount > 80) {
            return newSpacesString(spaceCount);
        }
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
        if (newlinesCount > 20) {
            return newNewlinesString(newlinesCount);
        }
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
