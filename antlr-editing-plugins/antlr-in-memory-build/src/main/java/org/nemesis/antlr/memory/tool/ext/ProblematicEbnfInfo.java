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
package org.nemesis.antlr.memory.tool.ext;

import java.util.Arrays;
import java.util.List;

/**
 * Provides coordinates and text for erroneous EBNFs that can match the empty
 * string. These are errors, but by default, Antlr does not provide enough
 * information to clearly know the root of the problem.
 *
 * @author Tim Boudreau
 */
public final class ProblematicEbnfInfo {

    private final String[] nonWhitespaceTokenText;
    private final int start;
    private final int end;
    private final int startLine;
    private final int startOffsetInLine;
    private final int endLine;
    private final int endOffsetInLine;

    public ProblematicEbnfInfo(int start, int end, List<String> items,
            int startLine, int startOffsetInLine, int endLine, int endOffsetInLine) {
        this.nonWhitespaceTokenText = items.toArray(new String[items.size()]);
        this.start = start;
        this.end = end;
        this.startLine = startLine;
        this.startOffsetInLine = startOffsetInLine;
        this.endLine = endLine;
        this.endOffsetInLine = endOffsetInLine;
    }

    public int startLine() {
        return startLine;
    }

    public int startCharOffsetInLine() {
        return startOffsetInLine;
    }

    public int endLine() {
        return endLine;
    }

    public int endCharOffsetInLine() {
        return endOffsetInLine;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public String text() {
        // Return some text that does not contain newlines and is
        // generally properly spaced, since we need text for error
        // annotations without formatting, and the item may span
        // multiple lines
        StringBuilder sb = new StringBuilder(40);
        boolean lastWasPunc = true;
        for (String item : this.nonWhitespaceTokenText) {
            boolean nowPunc = isPunc(item);
            if (!lastWasPunc && !nowPunc) {
                sb.append(' ');
            }
            sb.append(item);
            lastWasPunc = nowPunc;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return start + ":" + end + ":" + text()
                + " lineStart " + startLine + ":" + startOffsetInLine
                + " lineEnd " + endLine + ":" + endOffsetInLine;
    }

    private static boolean isPunc(String txt) {
        if (txt.length() == 1) {
            char c = txt.charAt(0);
            if (Character.isAlphabetic(c) || Character.isDigit(c)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + Arrays.deepHashCode(this.nonWhitespaceTokenText);
        hash = 71 * hash + this.start;
        hash = 71 * hash + this.end;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ProblematicEbnfInfo other = (ProblematicEbnfInfo) obj;
        if (this.start != other.start) {
            return false;
        }
        if (this.end != other.end) {
            return false;
        }
        return Arrays.deepEquals(this.nonWhitespaceTokenText,
                other.nonWhitespaceTokenText);
    }
}
