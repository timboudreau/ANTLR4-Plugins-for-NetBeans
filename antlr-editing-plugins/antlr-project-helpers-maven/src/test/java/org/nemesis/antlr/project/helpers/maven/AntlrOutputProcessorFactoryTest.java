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
package org.nemesis.antlr.project.helpers.maven;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.project.helpers.maven.AntlrOutputProcessorFactory.AntlrLineProcessor.ErrInfo;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrOutputProcessorFactoryTest {

    private static final String ERR_1 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:0: error: syntax error: '}' came as a complete surprise to me [error 50]";
    private static final String ERR_2 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:0]: syntax error: '}' came as a complete surprise to me";
    private static final String ERR_3 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:0: error: parser rule gwerb not allowed in lexer [error 53]";
    private static final String ERR_4 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:0]: parser rule gwerb not allowed in lexer";
    private static final String ERR_5 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4:130:8: error: reference to parser rule gwerb in lexer rule gwerb [error 160]";
    private static final String ERR_6 = "/home/tim/work/personal/imagine/Imagine/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:8]: reference to parser rule gwerb in lexer rule gwerb";
    private static final String ERR_7 = "/home/folder\\ with\\ spaces/markdown-grammar/src/main/antlr4/org/imagine/markdown/grammar/MarkdownLexer.g4 [130:8]: reference to parser rule gwerb in lexer rule gwerb";

    private static final String W_ERR_1 = "C:\\foo\\bar\\MarkdownLexer.md:130:0: error: syntax error: '}' came as a complete surprise to me [error 50]";

    @Test
    public void testParseErrors() {
        for (String err : new String[] {ERR_1, ERR_2, ERR_3, ERR_4, ERR_5, ERR_6, ERR_7}){
            testOne(err);
        }
    }

    private void testOne(String err) {
        ErrInfo info = ErrInfo.parse(err);
        assertNotNull(info);
        System.out.println("ERR: " + info);
    }

}
