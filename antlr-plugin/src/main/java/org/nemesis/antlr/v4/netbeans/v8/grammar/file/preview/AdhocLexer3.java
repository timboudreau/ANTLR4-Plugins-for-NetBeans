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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview;

import java.util.Iterator;
import java.util.function.Supplier;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocLexer3 implements Lexer<AdhocTokenId> {

    private final Supplier<Iterator<Token<AdhocTokenId>>> tokenSupplier;
    private Iterator<Token<AdhocTokenId>> cursor;
    private final String mimeType;

    AdhocLexer3(Supplier<Iterator<Token<AdhocTokenId>>> tokens, String mimeType) {
        this.tokenSupplier = tokens;
        this.mimeType = mimeType;
        cursor();
    }

    private Iterator<Token<AdhocTokenId>> cursor() {
        if (cursor == null) {
            cursor = tokenSupplier.get();
        }
        return cursor;
    }

    @Override
    public Token<AdhocTokenId> nextToken() {
        Iterator<Token<AdhocTokenId>> cursor = cursor();
        if (cursor.hasNext()) {
            return cursor.next();
        }
        return null;
    }

    @Override
    public Object state() {
        return cursor;
    }

    @Override
    public void release() {
        // do nothing
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() 
                + " for " + AdhocMimeTypes.loggableMimeType(mimeType)
                + "(cursor=" + cursor + ")";
    }
}
