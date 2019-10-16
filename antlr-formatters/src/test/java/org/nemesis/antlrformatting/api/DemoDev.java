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
package org.nemesis.antlrformatting.api;

import java.net.URISyntaxException;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.nemesis.simple.language.SimpleLanguageLexer.*;
import static org.nemesis.antlrformatting.api.SLState.*;
import static org.nemesis.antlrformatting.api.SimpleFormattingAction.*;
import org.nemesis.antlrformatting.spi.AntlrFormatterStub;
import org.nemesis.simple.SampleFiles;
import org.nemesis.simple.language.SimpleLanguageLexer;
import static org.nemesis.antlrformatting.api.DemoDev.SimpleFormatterStub.MAX_LINE_LENGTH;

/**
 *
 * @author Tim Boudreau
 */
public class DemoDev {

    private static GoldenFiles<?, ?, SimpleLanguageLexer, Preferences> goldenFiles;

    @EnumSource(SampleFiles.class)
    @ParameterizedTest(name = "test-[{arguments}]")
    public void testSomeMethod(SampleFiles file) throws Exception {
        System.out.println("run " + file);
        MockPreferences prefs = MockPreferences.of(MAX_LINE_LENGTH, 80);
        String filename = prefs.filename(file.name().toLowerCase() + "-", "sim");
        goldenFiles.test(file, prefs, filename, false);
    }

    @BeforeAll
    public static void setup() throws URISyntaxException {
        goldenFiles = new GoldenFiles<>(DemoDev.class, SimpleFormatterStub.class,
                S_WHITESPACE);
    }

    static class SimpleFormatterStub implements AntlrFormatterStub<SLState, SimpleLanguageLexer> {

        static final String MAX_LINE_LENGTH = "maxLineLength";

        @Override
        public void configure(LexingStateBuilder<SLState, ?> stateBuilder,
                FormattingRules rules, Preferences config) {

            assertNotNull(config, "config null");
            stateBuilder.increment(BRACE_DEPTH)
                    .onTokenType(S_OPEN_BRACE)
                    .decrementingWhenTokenEncountered(S_CLOSE_BRACE);

            stateBuilder.pushPosition(BRACE_POSITION)
                    .onTokenType(S_OPEN_BRACE)
                    .poppingOnTokenType(S_CLOSE_BRACE);

            stateBuilder.pushPosition(COLON_POSITION)
                    .onTokenType(S_COLON)
                    .poppingOnTokenType(S_CLOSE_BRACE, S_SEMICOLON);

            int maxLine = config.getInt(MAX_LINE_LENGTH, 80);

            FormattingAction onNextLine
                    = PREPEND_NEWLINE_AND_INDENT
                            .bySpaces(COLON_POSITION);

            FormattingAction onNextLineTrimmed
                    = PREPEND_NEWLINE_AND_INDENT
                            .bySpaces(COLON_POSITION)
                            .trimmingWhitespace();

            FormattingAction doubleNewline
                    = PREPEND_DOUBLE_NEWLINE_AND_INDENT
                            .bySpaces(COLON_POSITION)
                            .trimmingWhitespace();

            FormattingAction doubleIndent = PREPEND_NEWLINE_AND_INDENT
                    .bySpaces(4, COLON_POSITION);

            FormattingAction spaceOrWrap
                    = PREPEND_SPACE.wrappingLines(maxLine, doubleIndent);

            rules.onTokenTypeNot(S_SEMICOLON, S_CLOSE_BRACE, COMMENT,
                    S_COMMA, S_CLOSE_PARENS)
                    .whereNotFirstTokenInSource()
                    .format(spaceOrWrap);

            rules.layer(rls -> {
                rls.onTokenType(ID, LINE_COMMENT, DESCRIPTION, COMMENT)
                        .wherePreviousTokenType(S_SEMICOLON, S_OPEN_BRACE,
                                S_CLOSE_BRACE, LINE_COMMENT, COMMENT,
                                DESCRIPTION)
                        .format(onNextLineTrimmed);

                rls.onTokenType(S_CLOSE_BRACE).format(onNextLine);

                rls.layer(rls2 -> {
                    rls2.onTokenType(DESCRIPTION)
                            .wherePreviousTokenTypeNot(DESCRIPTION)
                            .format(doubleNewline);

                    rls2.onTokenType(COMMENT)
                            .format(onNextLine
                                    .rewritingTokenTextWith(
                                            TokenRewriter.simpleReflow(maxLine)));
                });
            });
        }
    }
}
