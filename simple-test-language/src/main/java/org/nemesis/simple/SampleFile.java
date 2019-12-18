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
package org.nemesis.simple;

import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;

/**
 * For tests, an interface to allow provision of sample files for testing.
 *
 * @author Tim Boudreau
 */
public interface SampleFile<L extends Lexer, P extends Parser> extends Supplier<String> {

    CharStream charStream() throws IOException;

    InputStream inputStream();

    int length() throws IOException;

    L lexer() throws IOException;

    L lexer(ANTLRErrorListener l) throws IOException;

    P parser() throws IOException;

    String text() throws IOException;

    default void copyTo(Path path) throws IOException {
        Files.write(path, text().getBytes(UTF_8), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public default String get() {
        try {
            return text();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    default List<CommonToken> tokens() throws IOException {
        List<CommonToken> result = new ArrayList<>();
        L l = lexer();
        int ix = 0;
        for (Token t = l.nextToken(); t.getType() != Token.EOF; t = l.nextToken(), ix++) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix);
            result.add(ct);
        }
        return result;
    }

    default SampleFile related(String name) {
        return null;
    }

    String fileName();
}
