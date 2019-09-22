package org.nemesis.extraction.nb;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 *
 * @author Tim Boudreau
 */
public final class CharSequenceCharStream implements CharStream {

    protected CharSequence data;
    private int n;
    private int p;
    private final String name;
    private final SnapshotGrammarSource outer;

    public CharSequenceCharStream(String name, CharSequence seq, final SnapshotGrammarSource outer) {
        this.outer = outer;
        this.data = seq;
        this.n = seq.length();
        this.name = name;
    }

    int data(int ix) {
        return data.charAt(ix);
    }

    public void reset() {
        p = 0;
    }

    @Override
    public void consume() {
        if (p >= n) {
            assert LA(1) == IntStream.EOF;
            throw new IllegalStateException("cannot consume EOF");
        }
        if (p < n) {
            p++;
        }
    }

    @Override
    public int LA(int i) {
        if (i == 0) {
            return 0;
        }
        if (i < 0) {
            i++;
            if ((p + i - 1) < 0) {
                return IntStream.EOF;
            }
        }
        if ((p + i - 1) >= n) {
            return IntStream.EOF;
        }
        return data(p + i - 1);
    }

    public int LT(int i) {
        return LA(i);
    }

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
        return -1;
    }

    @Override
    public void release(int marker) {
    }

    @Override
    public void seek(int index) {
        if (index <= p) {
            p = index;
            return;
        }
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
        return name == null ? UNKNOWN_SOURCE_NAME : name;
    }

    @Override
    public String toString() {
        return data.toString();
    }

}
