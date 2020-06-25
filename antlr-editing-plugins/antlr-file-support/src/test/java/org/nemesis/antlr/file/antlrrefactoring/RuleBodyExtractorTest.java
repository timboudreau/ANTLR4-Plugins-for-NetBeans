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
package org.nemesis.antlr.file.antlrrefactoring;

import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.sample.AntlrSampleFiles;

/**
 *
 * @author Tim Boudreau
 */
public class RuleBodyExtractorTest {

    @Test
    public void testSomeMethod() throws IOException {
//        ANTLRv4Parser parser = AntlrSampleFiles.RETURNS_TEST.parser();
//        CharSequence seq = parser.grammarFile().accept(new RuleBodyExtractor("number", parser));
//        System.out.println("NUMBER:\n" + seq);
        RuleBodyExtractor ext = testOne(AntlrSampleFiles.MARKDOWN_PARSER, "innerContent", "(content | embeddedImage)+? (whitespace (content | embeddedImage))*?");
        ext = testOne(AntlrSampleFiles.MARKDOWN_PARSER, "preformatted", "OpenPreformattedText PreformattedContent ClosePreformattedContent");
        assertEquals(0, ext.labeledAlternativeCount());
        assertEquals(1, ext.labeledAtomCount());
        assertEquals(0, ext.blockCount());
        ext = testOne(AntlrSampleFiles.MARKDOWN_PARSER, "orderedList", "(firstOrderedListItem orderedListItem* (orderedList (returningOrderedListItem orderedListItem*)*)) | (firstOrderedListItem orderedListItem*) | orderedListItem (firstOrderedListItem orderedListItem* (orderedList (returningOrderedListItem orderedListItem*)*))");
        ext = testOne(AntlrSampleFiles.MARKDOWN_PARSER, "content", "text | bold | code | italic | strikethrough | link | bracketed | parenthesized");
        assertEquals(0, ext.labeledAlternativeCount());
        assertEquals(0, ext.labeledAtomCount());
        assertEquals(0, ext.blockCount());
        ext = testOne(AntlrSampleFiles.RUST, "variable_binding", "(Let variable_props variable_spec) | (Let variable_props variable_pattern Equals expression_pattern) | (Let variable_props variable_spec Equals assignee_props expression variable_cast?) | (Let variable_props type_spec variable_pattern ((Equals assignee_props variable_name) | expression))");
        assertEquals(6, ext.blockCount());
        assertEquals(4, ext.labeledAlternativeCount());
        assertEquals(15, ext.labeledAtomCount());
        ext = testOne(AntlrSampleFiles.RUST, "BlockComment", "BlockCommentPrefix (~[*/] | Slash* BlockComment | Slash+ (~[*/]) | Asterisk+ ~[*/])* Asterisk+ Slash");
        ext = testOne(AntlrSampleFiles.RUST, "CHAR", "~['\"\\r\\n\\\\\\ud800-\\udfff] | [\\ud800-\\udbff] [\\udc00-\\udfff] | SIMPLE_ESCAPE | '\\\\x' [0-7] [0-9a-fA-F] | '\\\\u{' [0-9a-fA-F]+ RightBrace");
        ext = testOne(AntlrSampleFiles.RETURNS_TEST, "number", "Integer");
        assertEquals(1, ext.atomCount());
        assertEquals(0, ext.labeledAlternativeCount());
        assertEquals(0, ext.labeledAtomCount());
        assertEquals(0, ext.blockCount());
    }


    private RuleBodyExtractor testOne(AntlrSampleFiles sample, String rule, String expect) throws IOException {
        ANTLRv4Parser parser = sample.parser();
        RuleBodyExtractor ext = new RuleBodyExtractor(rule, parser);
        CharSequence seq = parser.grammarFile().accept(ext);
        assertNotNull(seq);
        assertTrue(seq.length() > 0);
        assertEquals(expect, seq.toString());
        return ext;
    }
}
