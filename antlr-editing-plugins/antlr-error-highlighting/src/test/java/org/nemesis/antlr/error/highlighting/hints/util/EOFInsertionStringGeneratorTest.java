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
package org.nemesis.antlr.error.highlighting.hints.util;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.error.highlighting.hints.util.NameUtils.generateLexerRuleName;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class EOFInsertionStringGeneratorTest {

    @Test
    public void testSomeMethod() throws IOException {
        testOne(AntlrSampleFiles.MARKDOWN_PARSER.withText(EOFInsertionStringGeneratorTest::removeEof), "foo");
    }

    private CharSequence testOne(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sample, String expect) throws IOException {
        ANTLRv4Parser parser = sample.parser();
        EOFInsertionStringGenerator gen = new EOFInsertionStringGenerator(parser);
        CharSequence result = parser.grammarFile().accept(gen);
        return result;
    }

    static String removeEof(String s) {
        return s.replaceAll("EOF\\?", "").replaceAll("EOF", "");
    }

    @Test
    public void testGenLexerRuleName() {
        assertEquals("Syntax", generateLexerRuleName("syntax"));
        assertEquals("Foooood", generateLexerRuleName("foOoOoD"));
        assertEquals("Q", generateLexerRuleName("'q'"));
        assertEquals("Q", generateLexerRuleName("q"));
        assertEquals("Backslash", generateLexerRuleName("\\"));
        assertEquals("Slash", generateLexerRuleName("/"));
        assertEquals("Pipe", generateLexerRuleName("|"));
    }
}
