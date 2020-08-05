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
package org.nemesis.antlr.error.highlighting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public class ChannelsAndSkipExtractorsTest {

    static Extraction extraction;

    @Test
    public void testSomeMethod() {
        System.out.println("ls " + extraction);
        System.out.println("keys " + extraction.regionKeys());
        SemanticRegions<HintsAndErrorsExtractors.SingleTermCtx> regions = extraction.regions(HintsAndErrorsExtractors.SOLO_STRING_LITERALS);
        System.out.println("GOT " + regions);
    }

    @BeforeAll
    public static void setup() throws IOException {
        ExtractorBuilder<GrammarFileContext> eb = Extractor.builder(GrammarFileContext.class, ANTLR_MIME_TYPE);
        HintsAndErrorsExtractors.populateBuilder(eb);
        Extractor<GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = AntlrSampleFiles.PROTOBUF_3.lexer();
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = AntlrSampleFiles.PROTOBUF_3.parser();
        extraction = ext.extract(parser.grammarFile(), GrammarSource.find(AntlrSampleFiles.PROTOBUF_3.charStream(), ANTLR_MIME_TYPE), tokens);
        assertFalse(extraction.isPlaceholder());
    }

}
