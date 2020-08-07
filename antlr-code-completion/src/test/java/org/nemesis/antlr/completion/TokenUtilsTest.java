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
package org.nemesis.antlr.completion;

import com.mastfrog.util.search.Bias;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.mastfrog.antlr.code.completion.spi.CaretTokenRelation;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class TokenUtilsTest {

    @Test
    public void testFindCaretToken() throws IOException {
        testFindCaret(AntlrSampleFiles.TIMESTAMPS);
    }

    private void testFindCaret(SampleFile file) throws IOException {
        Lexer lex = file.lexer();
        Map<Integer, CommonToken> tokenForFilePosition = new HashMap<>();
        Map<Integer, CommonToken> tokenForIndex = new HashMap<>();
        List<CommonToken> toks = tokensForLexer(lex, tokenForFilePosition, tokenForIndex);
        for (Map.Entry<Integer, CommonToken> e : tokenForFilePosition.entrySet()) {
            int pos = e.getKey();
            CommonToken t = e.getValue();
            int index = TokenUtils.findCaretToken(pos, toks, 0, toks.size());
            assertEquals(e.getValue().getTokenIndex(), index, "Wrong index " + index
                    + " returned - should be " + t.getTokenIndex() + " for '" + t.getText() + "' at " + pos);

            CaretTokenRelation expectedRelation = pos == t.getStartIndex()
                    ? CaretTokenRelation.AT_TOKEN_START
                    : CaretTokenRelation.WITHIN_TOKEN;

            CaretTokenInfo info = (CaretTokenInfo) TokenUtils.caretTokenInfo(pos, toks);
            assertNotNull(info);
            boolean isEof = e.getValue().getTokenIndex() == Token.EOF;
            if (!isEof) {
                assertEquals(pos, info.caretPositionInDocument());
                assertTrue(info.isUserToken());
                assertEquals(t.getStartIndex(), info.tokenStart());
                assertEquals(t.getStopIndex(), info.tokenStop());
                assertEquals(t.getStopIndex() + 1, info.tokenEnd());
                assertEquals((t.getStopIndex() + 1) - t.getStartIndex(),
                        info.tokenLength());

                assertEquals(info.tokenText().length(), info.tokenLength());
                assertSame(info.token(), t);
                assertEquals(t.getText(), info.tokenText());
                assertEquals(expectedRelation, info.caretRelation());

                assertEquals(pos - t.getStartIndex(),
                        info.caretDistanceBackwardsToTokenStart(),
                        "Distance backwards to start for " + info);

                assertEquals(t.getStopIndex() - pos,
                        info.caretDistanceForwardsToTokenEnd(),
                        "Wrong distance for " + pos + " '" + t.getText()
                        + "' for " + info);

                if (pos == t.getStartIndex()) {
                    CaretTokenInfo backward = info.biasedBy(Bias.BACKWARD);
                    String msg = "bwd " + backward + " from " + info + " @ " + pos + " with " + t;
                    if (t.getTokenIndex() > 0) {
                        CommonToken expectedPrevToken = toks.get(
                                t.getTokenIndex() - 1);
                        assertSame(expectedPrevToken, backward.token(), msg);

                        assertEquals(CaretTokenRelation.AT_TOKEN_END,
                                backward.caretRelation(), msg);

                        assertSame(backward, backward.biasedBy(Bias.BACKWARD), msg);
                        CaretTokenInfo fwd = backward.biasedBy(Bias.FORWARD);
                        assertNotSame(backward, fwd, "fwd " + fwd + " from " + msg);
                        assertSame(t, fwd.token(), "fwd " + fwd + " from " + msg);
                        assertEquals(t.getStartIndex(), fwd.tokenStart(), "fwd " + fwd + " from " + msg);
                    } else {
                        assertFalse(backward.isUserToken(), "Should get the empty "
                                + "CaretTokenInfo instance for -1st token");
                    }

                    assertEquals(t.getText(), info.trailingTokenText());
                    assertEquals("", info.leadingTokenText());
                    assertEquals(CaretTokenRelation.AT_TOKEN_START,
                            info.caretRelation());
                } else if (pos == t.getStopIndex()) {
                    CaretTokenInfo forward = info.biasedBy(Bias.FORWARD);
                    assertNotNull(forward);
                    assertNotSame(t, forward.token());
                    if (t.getTokenIndex() < toks.size() - 1) {
                        CommonToken expectedNextToken = toks.get(
                                t.getTokenIndex() + 1);

                        assertSame(expectedNextToken, forward.token());
                        assertEquals(CaretTokenRelation.AT_TOKEN_START,
                                forward.caretRelation());

                        assertEquals(expectedNextToken.getStartIndex(),
                                forward.tokenStart());

                        CaretTokenInfo bwd = forward.biasedBy(Bias.BACKWARD);
                        assertSame(t, bwd.token());
                        assertEquals(t.getStopIndex() + 1,
                                bwd.caretPositionInDocument());

                    } else {
                        assertFalse(forward.isUserToken());
                    }
                    assertEquals(t.getText().substring(0,
                            t.getText().length() - 1),
                            info.leadingTokenText());

                    assertEquals(new String(new char[]{t.getText().charAt(
                        t.getText().length() - 1)}), info.trailingTokenText());

                    assertEquals(CaretTokenRelation.WITHIN_TOKEN,
                            info.caretRelation());
                }
            } else {
                assertEquals(-1, info.tokenStart());
                assertEquals(-1, info.tokenStop());
                assertEquals(-1, info.tokenEnd());
                assertEquals(0, info.tokenLength());
                assertEquals("", info.tokenText());
            }
        }
    }

    private static List<CommonToken> tokensForLexer(Lexer lex,
            Map<Integer, CommonToken> tokenForFilePosition,
            Map<Integer, CommonToken> tokenForIndex) {
        List<CommonToken> toks = new ArrayList<>();
        int ix = 0;
        for (Token tok = lex.nextToken(); tok.getType() != Token.EOF; tok = lex.nextToken()) {
            CommonToken ct = new CommonToken(tok);
            ct.setTokenIndex(ix++);
            tokenForIndex.put(ix - 1, ct);
            toks.add(ct);
            for (int i = ct.getStartIndex(); i <= ct.getStopIndex(); i++) {
                tokenForFilePosition.put(i, ct);
            }
        }
        return toks;
    }
}
