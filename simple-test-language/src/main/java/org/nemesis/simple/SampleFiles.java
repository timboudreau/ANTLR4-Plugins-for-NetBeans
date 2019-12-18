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
import java.io.InputStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.nemesis.simple.language.SimpleLanguageLexer;
import org.nemesis.simple.language.SimpleLanguageParser;

/**
 *
 * @author Tim Boudreau
 */
public enum SampleFiles implements SampleFile<SimpleLanguageLexer, SimpleLanguageParser> {

    BASIC("basic.sim"),
    MINIMAL("minimal.sim"),
    ABSURDLY_MINIMAL("absurdly-minimal.sim"),
    MUCH_NESTING("much-nesting.sim"),
    MUCH_NESTING_WITH_EXTRA_NEWLINES("much-nesting-extra-newlines.sim"),
    MUCH_NESTING_UNFORMATTED("much-nesting-unformatted.sim"),
    MINIMAL_MULTILINE("minimal-with-multiline-comments.sim"),
    LONG_ITEMS("minimal_with_long_items.sim"),
    FORMATTING_TUTORIAL("formatting-tutorial.sim");

    private final String name;

    SampleFiles(String name) {
        this.name = name;
    }

    @Override
    public String fileName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int length() throws IOException {
        return text().length();
    }

    private String text;

    @Override
    public String text() throws IOException {
        if (text != null) {
            return text;
        }
        CharStream cs = charStream();
        StringBuilder sb = new StringBuilder(512);
        for (;;) {
            int val = cs.LA(1);
            if (val == -1) {
                break;
            }
            char c = (char) val;
            sb.append(c);
            cs.consume();
        }
        return text = sb.toString();
    }

    @Override
    public CharStream charStream() throws IOException {
        return CharStreams.fromReader(new InputStreamReader(inputStream(),
                UTF_8), name);
    }

    @Override
    public InputStream inputStream() {
        InputStream result = SampleFiles.class.getResourceAsStream(name);
        assert result != null : name + " not found in " + SampleFiles.class.getPackage().getName().replace('.', '/');
        return result;
    }

    @Override
    public SimpleLanguageLexer lexer() throws IOException {
        SimpleLanguageLexer lexer = new SimpleLanguageLexer(charStream());
        lexer.removeErrorListeners();
        return lexer;
    }

    @Override
    public SimpleLanguageLexer lexer(ANTLRErrorListener l) throws IOException {
        SimpleLanguageLexer lexer = new SimpleLanguageLexer(charStream());
        lexer.removeErrorListeners();
        lexer.addErrorListener(l);
        return lexer;
    }

    @Override
    public SimpleLanguageParser parser() throws IOException {
        SimpleLanguageLexer lexer = lexer();
        CommonTokenStream cts = new CommonTokenStream(lexer, 0);
        SimpleLanguageParser result = new SimpleLanguageParser(cts);
        result.removeErrorListeners();
        return result;
    }
}
