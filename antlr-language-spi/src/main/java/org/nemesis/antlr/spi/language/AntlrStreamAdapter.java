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
