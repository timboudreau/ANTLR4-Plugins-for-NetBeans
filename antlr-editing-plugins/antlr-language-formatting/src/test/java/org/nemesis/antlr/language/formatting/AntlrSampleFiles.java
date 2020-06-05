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
package org.nemesis.antlr.language.formatting;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.simple.SampleFile;
import org.nemesis.test.fixtures.support.ProjectTestHelper;

/**
 *
 * @author Tim Boudreau
 */
public enum AntlrSampleFiles implements SampleFile {

    ANTLR,
    TOKENS_LEXER,
    TOKENS_PARSER,
    SENSORS_PARSER,
    RUST_PARSER,
    MARKDOWN_LEXER,
    MARKDOWN_LEXER_2;

    @Override
    public CharStream charStream() throws IOException {
        return CharStreams.fromString(text());
    }

    @Override
    public InputStream inputStream() {
        try {
            return Files.newInputStream(path(this), StandardOpenOption.READ);
        } catch (IOException | URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public int length() throws IOException {
        try {
            return (int) Files.size(path(this));
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public ANTLRv4Lexer lexer() throws IOException {
        ANTLRv4Lexer result = new ANTLRv4Lexer(charStream());
        result.removeErrorListeners();
        return result;
    }

    @Override
    public ANTLRv4Lexer lexer(ANTLRErrorListener l) throws IOException {
        ANTLRv4Lexer lex = lexer();
        lex.addErrorListener(l);
        return lex;
    }

    @Override
    public ANTLRv4Parser parser() throws IOException {
        ANTLRv4Parser result = new ANTLRv4Parser(new CommonTokenStream(lexer()));
        result.removeErrorListeners();
        return result;
    }

    @Override
    public String text() throws IOException {
        try {
            return new String(Files.readAllBytes(path(this)), UTF_8);
        } catch (URISyntaxException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Path path(AntlrSampleFiles file) throws IOException, URISyntaxException {
        ProjectTestHelper helper = ProjectTestHelper.relativeTo(G4FormatterStubTest.class);
        switch (file) {
            case ANTLR:
                return helper.findAntlrGrammarProjectDir()
                        .resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4.g4");
            case TOKENS_LEXER:
                return helper.findChildProjectWithChangedAntlrDirAndEncoding()
                        .resolve("src/main/antlr/source/org/nemesis/tokens/TokensLexer.g4");
            case TOKENS_PARSER:
                return helper.findChildProjectWithChangedAntlrDirAndEncoding()
                        .resolve("src/main/antlr/source/org/nemesis/tokens/Tokens.g4");
            case SENSORS_PARSER:
                return helper.projectBaseDir().resolve(
                        "../antlr-language-formatting-ui/src/main/resources/org/nemesis/"
                        + "antlr/language/formatting/ui/Sensors-g4.txt");
            case RUST_PARSER:
                return helper.projectBaseDir().resolve(
                        "src/main/resources/org/nemesis/antlr/language/formatting/Rust.g4");
            case MARKDOWN_LEXER:
                return helper.projectBaseDir().resolve(
                        "src/test-golden/MarkdownLexer.g4");
            case MARKDOWN_LEXER_2:
                return helper.projectBaseDir().resolve(
                        "src/test-golden/MarkdownLexerV2.g4");
            default:
                throw new AssertionError(file);
        }
    }

    @Override
    public String fileName() {
        try {
            return path(this).getFileName().toString();
        } catch (Exception ex) {
            throw new AssertionError(name(), ex);
        }
    }
}
