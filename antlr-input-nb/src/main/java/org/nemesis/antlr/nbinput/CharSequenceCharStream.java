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
package org.nemesis.antlr.nbinput;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Implementation of Antlr's CharStream which doesn't require a memory-copy of
 * the original characters into a char[] as all of Antlr's built-in
 * implementations do (desirable for fast batch processing, not at all desirable
 * in an IDE where memory is at a premium).
 *
 * @author Tim Boudreau
 */
public final class CharSequenceCharStream implements CharStream {

    protected CharSequence data;
    private int n;
    private int p;
    private final String name;

    public CharSequenceCharStream(String name, CharSequence seq) {
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
