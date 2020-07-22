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
package org.nemesis.antlr.memory.tool;

import java.util.ArrayList;
import java.util.List;
import org.antlr.runtime.CharStream;

/**
 * Identical to Antlr v3's AntlrStringStream except that it doesn't
 * copy the CharSequence's contents to a char[], avoiding one pointless
 * memory-copy.  We may be passed CharBuffers from JFS, the CharSequence
 * implementation provided from a Snapshot in Antlr's reference, or a
 * CharSeq from the NetBeans editor, and none of these require making
 * a copy - guessing Antlr does this for performance when reading disk
 * files, to get the I/O done with at once, but that's not what's going
 * on here, ever.
 */
final class CharSequenceCharStream implements CharStream {

    /**
     * How many characters are actually in the buffer
     */
    protected int n;

    /**
     * 0..n-1 index into string of next char
     */
    protected int p = 0;

    /**
     * line number 1..n within the input
     */
    protected int line = 1;

    /**
     * The index of the character relative to the beginning of the line 0..n-1
     */
    protected int charPositionInLine = 0;

    /**
     * tracks how deep mark() calls are nested
     */
    protected int markDepth = 0;

    /**
     * A list of CharStreamState objects that tracks the stream state values
     * line, charPositionInLine, and p that can change as you move through the
     * input stream. Indexed from 1..markDepth. A null is kept @ index 0. Create
     * upon first call to mark().
     */
    protected List<CharStreamState> markers;

    /**
     * Track the last mark() call result value for use in rewind().
     */
    protected int lastMarker;

    /**
     * What is name or source of this char stream?
     */
    public String name;
    private CharSequence data;

    /**
     * Copy data in string to a local char array
     */
    CharSequenceCharStream(CharSequence input) {
        this.data = input;
        this.n = input.length();
    }

    char data(int p) {
        return data.charAt(p);
    }

    /**
     * Reset the stream so that it's in the same state it was when the object
     * was created *except* the data array is not touched.
     */
    public void reset() {
        p = 0;
        line = 1;
        charPositionInLine = 0;
        markDepth = 0;
    }

    @Override
    public void consume() {
        if (p < n) {
            charPositionInLine++;
            if (data(p) == '\n') {
                line++;
                charPositionInLine = 0;
            }
            p++;
        }
    }

    @Override
    public int LA(int i) {
        if (i == 0) {
            return 0; // undefined
        }
        if (i < 0) {
            i++; // e.g., translate LA(-1) to use offset i=0; then data[p+0-1]
            if ((p + i - 1) < 0) {
                return CharStream.EOF; // invalid; no char before first char
            }
        }

        if ((p + i - 1) >= n) {
            return CharStream.EOF;
        }
        return data(p + i - 1);
    }

    @Override
    public int LT(int i) {
        return LA(i);
    }

    /**
     * Return the current input symbol index 0..n where n indicates the last
     * symbol has been read. The index is the index of char to be returned from
     * LA(1).
     */
    @Override
    public int index() {
        return p;
    }

    @Override
    public int size() {
        return n;
    }

    @Override
    public int mark() {
        if (markers == null) {
            markers = new ArrayList<CharStreamState>();
            markers.add(null); // depth 0 means no backtracking, leave blank
        }
        markDepth++;
        CharStreamState state;
        if (markDepth >= markers.size()) {
            state = new CharStreamState();
            markers.add(state);
        } else {
            state = markers.get(markDepth);
        }
        state.p = p;
        state.line = line;
        state.charPositionInLine = charPositionInLine;
        lastMarker = markDepth;
        return markDepth;
    }

    @Override
    public void rewind(int m) {
        CharStreamState state = markers.get(m);
        // restore stream state
        seek(state.p);
        line = state.line;
        charPositionInLine = state.charPositionInLine;
        release(m);
    }

    @Override
    public void rewind() {
        rewind(lastMarker);
    }

    @Override
    public void release(int marker) {
        // unwind any other markers made after m and release m
        markDepth = marker;
        // release this marker
        markDepth--;
    }

    /**
     * consume() ahead until p==index; can't just set p=index as we must update
     * line and charPositionInLine.
     */
    @Override
    public void seek(int index) {
        if (index <= p) {
            p = index; // just jump; don't update stream state (line, ...)
            return;
        }
        // seek forward, consume until p hits index
        while (p < index) {
            consume();
        }
    }

    @Override
    public String substring(int start, int stop) {
//        return new String(data, start, stop - start + 1);
        return data.subSequence(start, stop + 1).toString();
    }

    @Override
    public int getLine() {
        return line;
    }

    @Override
    public int getCharPositionInLine() {
        return charPositionInLine;
    }

    @Override
    public void setLine(int line) {
        this.line = line;
    }

    @Override
    public void setCharPositionInLine(int pos) {
        this.charPositionInLine = pos;
    }

    @Override
    public String getSourceName() {
        return name;
    }

    @Override
    public String toString() {
        return data.toString();
    }

    public class CharStreamState {

        /**
         * Index into the char stream of next lookahead char
         */
        int p;

        /**
         * What line number is the scanner at before processing buffer[p]?
         */
        int line;

        /**
         * What char position 0..n-1 in line is scanner before processing
         * buffer[p]?
         */
        int charPositionInLine;
    }
}
