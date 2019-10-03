/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
    RUST_PARSER;

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
            default:
                throw new AssertionError(file);
        }
    }
}
