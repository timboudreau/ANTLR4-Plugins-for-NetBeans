/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.nemesis.antlr.live.parsing.extract;

import org.antlr.v4.runtime.misc.Interval;

import java.io.InputStream;
import java.io.Reader;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import static org.antlr.v4.runtime.IntStream.UNKNOWN_SOURCE_NAME;

/**
 * Vacuum all input from a {@link Reader}/{@link InputStream} and then treat it
 * like a {@code char[]} buffer. Can also pass in a {@link String} or
 * {@code char[]} to use.
 *
 * <p>
 * If you need encoding, pass in stream/reader with correct encoding.</p>
 */
final class CharSequenceCharStream implements CharStream {

    public static final int READ_BUFFER_SIZE = 1024;
    public static final int INITIAL_BUFFER_SIZE = 1024;

    /**
     * The data being scanned
     */
    protected CharSequence data;

    /**
     * How many characters are actually in the buffer
     */
    protected int n;

    /**
     * 0..n-1 index into string of next char
     */
    protected int p = 0;

    /**
     * What is name or source of this char stream?
     */
    public String name;

    public CharSequenceCharStream() {
    }

    /**
     * Copy data in string to a local char array
     */
    public CharSequenceCharStream(String input) {
        this.data = input;
        this.n = input.length();
    }

    /**
     * This is the preferred constructor for strings as no data is copied
     */
    public CharSequenceCharStream(CharSequence seq) {
        this.data = seq;
        this.n = seq.length();
    }

    int data(int ix) {
        return data.charAt(ix);
    }

    /**
     * Reset the stream so that it's in the same state it was when the object
     * was created *except* the data array is not touched.
     */
    public void reset() {
        p = 0;
    }

    @Override
    public void consume() {
        if (p >= n) {
            assert LA(1) == IntStream.EOF;
            throw new IllegalStateException("cannot consume EOF");
        }

        //System.out.println("prev p="+p+", c="+(char)data(p));
        if (p < n) {
            p++;
            //System.out.println("p moves to "+p+" (c='"+(char)data(p)+"')");
        }
    }

    @Override
    public int LA(int i) {
        if (i == 0) {
            return 0; // undefined
        }
        if (i < 0) {
            i++; // e.g., translate LA(-1) to use offset i=0; then data(p+0-1)
            if ((p + i - 1) < 0) {
                return IntStream.EOF; // invalid; no char before first char
            }
        }

        if ((p + i - 1) >= n) {
            //System.out.println("char LA("+i+")=EOF; p="+p);
            return IntStream.EOF;
        }
        //System.out.println("char LA("+i+")="+(char)data(p+i-1)+"; p="+p);
        //System.out.println("LA("+i+"); p="+p+" n="+n+" data.length="+data.length);
        return data(p + i - 1);
    }

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

    /**
     * mark/release do nothing; we have entire buffer
     */
    @Override
    public int mark() {
        return -1;
    }

    @Override
    public void release(int marker) {
    }

    /**
     * consume() ahead until p==index; can't just set p=index as we must update
     * line and charPositionInLine. If we seek backwards, just set p
     */
    @Override
    public void seek(int index) {
        if (index <= p) {
            p = index; // just jump; don't update stream state (line, ...)
            return;
        }
        // seek forward, consume until p hits index or n (whichever comes first)
        index = Math.min(index, n);
        while (p < index) {
            consume();
        }
    }

    @Override
    public String getText(Interval interval) {
        int start = interval.a;
        int stop = interval.b;
        if (stop >= n) {
            stop = n - 1;
        }
        int count = stop - start + 1;
        if (start >= n) {
            return "";
        }
        return data.subSequence(start, start + count).toString();
    }

    @Override
    public String getSourceName() {
        if (name == null || name.isEmpty()) {
            return UNKNOWN_SOURCE_NAME;
        }

        return name;
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
