/* 
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nemesis.antlr.spi.language;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.misc.Interval;
import org.netbeans.spi.lexer.LexerInput;

final class AntlrStreamAdapter implements CharStream {

    private final String name;
    private int index = 0;
    private int mark = 0;
    private final LexerInput input;

    public AntlrStreamAdapter(LexerInput input, String name) {
        this.input = input;
        this.name = name;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public int size() {
        return -1;
    }

    @Override
    public String getSourceName() {
        return name;
    }

    @Override
    public void consume() {
        int character = read();
        if ( character == EOF ) {
            backup( 1 );
            throw new IllegalStateException( "Attempting to consume EOF" );
        }
    }

    @Override
    public int LA(int lookaheadAmount) {
        if ( lookaheadAmount < 0 ) {
            return lookBack( -lookaheadAmount );
        } else if ( lookaheadAmount > 0 ) {
            return lookAhead( lookaheadAmount );
        } else {
            return 0;
        }
    }

    private int lookBack(int amount) {
        backup( amount );
        int character = read();
        for ( int i = 1; i < amount; i++ ) {
            read();
        }
        return character;
    }

    private int lookAhead(int amount) {
        int character = 0;
        for ( int i = 0; i < amount; i++ ) {
            character = read();
        }
        backup( amount );
        return character;
    }

    @Override
    public int mark() {
        return ++mark;
    }

    @Override
    public void release(int marker) {
        mark = marker;
        mark--;
    }

    @Override
    public void seek(int index) {
        if ( index < 0 ) {
            throw new IllegalArgumentException( String.format( "Invalid index (%s < 0)", index ) );
        }

        if ( index < this.index ) {
            backup( this.index - index );
            return;
        }
        while ( this.index < index ) {
            consume();
        }
    }

    private int read() {
        int result = input.read();
        index++;

        if ( result == LexerInput.EOF ) {
            return EOF;
        } else {
            return result;
        }
    }

    private void backup(int count) {
        input.backup( count );
        index -= count;
    }

    @Override
    public String getText(Interval interval) {
        int start = interval.a;
        int stop = interval.b;

        if ( start < 0 || stop < start ) {
            return "";
        }

        final int pos = this.index;
        final int length = interval.length();
        final char[] data = new char[length];

        seek( interval.a );
        int r = 0;
        while ( r < length ) {
            final int character = read();
            if ( character == EOF ) {
                break;
            }

            data[r] = (char) character;
            r++;
        }
        seek( pos );

        if ( r > 0 ) {
            return new String( data, 0, r );
        } else {
            return "";
        }
    }
}
