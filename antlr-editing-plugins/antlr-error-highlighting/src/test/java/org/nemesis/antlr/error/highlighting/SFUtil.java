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
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import static org.nemesis.antlr.sample.AntlrSampleFiles.NESTED_MAPS_WITH_SUPERFLUOUS_PARENTHESES;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.simple.SampleFile;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * For bizarre reasons, if SuperfluousParenthesesDetectionTest mentions the
 * class Extraction in fields or methods, the Maven JUnit runner does not detect
 * it to be a test class and fails. So put this stuff here. WTF.
 *
 * @author Tim Boudreau
 */
public class SFUtil {

    static SampleFile<ANTLRv4Lexer, ANTLRv4Parser> nestedMaps;
    static GrammarFileContext grammar;

    static Extraction nmExtraction() throws IOException {
        nestedMaps = NESTED_MAPS_WITH_SUPERFLUOUS_PARENTHESES;
        ExtractorBuilder<ANTLRv4Parser.GrammarFileContext> eb = Extractor.builder(ANTLRv4Parser.GrammarFileContext.class, ANTLR_MIME_TYPE);
        ChannelsAndSkipExtractors.populateBuilder(eb);
        Extractor<ANTLRv4Parser.GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = AntlrSampleFiles.NESTED_MAPS_WITH_SUPERFLUOUS_PARENTHESES.lexer();
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = nestedMaps.parser();
        Extraction nmExtraction = ext.extract(parser.grammarFile(),
                GrammarSource.find(nestedMaps.charStream(),
                        ANTLR_MIME_TYPE), tokens);
        grammar = parser.grammarFile();
        assertFalse(nmExtraction.isPlaceholder());
        return nmExtraction;
    }
}
