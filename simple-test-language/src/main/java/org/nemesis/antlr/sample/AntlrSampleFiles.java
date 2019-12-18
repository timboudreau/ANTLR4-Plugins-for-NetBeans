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
package org.nemesis.antlr.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public enum AntlrSampleFiles implements SampleFile<ANTLRv4Lexer, ANTLRv4Parser> {
    RUST("Rust.g4"),
    EBNFS("Ebnf.g4"),
    TIMESTAMPS("Timestamps.g4"),
    EPSILON("Epsilon.g4"),
    NESTED_MAPS("NestedMapGrammar.g4"),
    DUMMY_LANGUAGE("DummyLanguage.g4"),
    ANTLR_LEXER("ANTLRv4Lexer.g4"),
    ANTLR_PARSER("ANTLRv4.g4"),
    ANTLR_LEX_BASIC("LexBasic.g4"),
    SENSORS("Sensors.g4");
    private final String fileName;

    AntlrSampleFiles(String pathFromRoot) {
        this.fileName = pathFromRoot;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public InputStream inputStream() {
        InputStream result = AntlrSampleFiles.class.getResourceAsStream(fileName);
        if (result == null) {
            throw new IllegalStateException(fileName + " not adjacent to "
                    + AntlrSampleFiles.class.getName() + " on classpath");
        }
        return result;
    }

    @Override
    public int length() throws IOException {
        InputStream is = inputStream();
        int result = is.available();
        if (result <= 0) {
            result = 0;
            byte[] bytes = new byte[256];
            int read = 0;
            while ((read = is.read(bytes)) > 0) {
                result += read;
            }
        }
        return result;
    }

    @Override
    public ANTLRv4Lexer lexer() throws IOException {
        ANTLRv4Lexer result = new ANTLRv4Lexer(charStream());
        result.removeErrorListeners();
        return result;
    }

    @Override
    public ANTLRv4Lexer lexer(ANTLRErrorListener l) throws IOException {
        ANTLRv4Lexer result = lexer();
        result.addErrorListener(l);
        return result;
    }

    @Override
    public ANTLRv4Parser parser() throws IOException {
        ANTLRv4Lexer lexer = lexer();
        CommonTokenStream stream = new CommonTokenStream(lexer);
        ANTLRv4Parser result = new ANTLRv4Parser(stream);
        result.removeErrorListeners();;
        return result;
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
                UTF_8), fileName);
    }

    public Document document() throws IOException, BadLocationException {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        doc.insertString(0, text(), null);
        return doc;
    }

    public void copyWithDependencies(Path dir, String pkg) throws IOException {
        pkg = pkg.replace('.', '/');
        Path target = dir.resolve(pkg);
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        Path file = target.resolve(fileName);
        copyTo(file);
        if (this == ANTLR_PARSER || this == ANTLR_LEXER) {
            Path imp = dir.resolve("imports");
            if (!Files.exists(imp)) {
                Files.createDirectories(imp);
            }
            ANTLR_LEX_BASIC.copyTo(imp.resolve(ANTLR_LEX_BASIC.fileName));
        }
    }

    @Override
    public SampleFile related(String name) {
        for (AntlrSampleFiles sf : AntlrSampleFiles.values()) {
            if (sf.fileName().equals(name)) {
                return sf;
            }
        }
        return null;
    }
}
