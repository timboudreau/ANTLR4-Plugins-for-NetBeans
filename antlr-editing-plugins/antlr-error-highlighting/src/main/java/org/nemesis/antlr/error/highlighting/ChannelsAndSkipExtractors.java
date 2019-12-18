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

import java.util.function.BiPredicate;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.ANTLRv4Parser.LEXCOM_SKIP;
import org.nemesis.antlr.ANTLRv4Parser.TerminalContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
public class ChannelsAndSkipExtractors {

    @Localize(displayName = "Channel And Skip Directives")
    public static final RegionsKey<ChannelOrSkipInfo> CHSKIP = RegionsKey.create(ChannelOrSkipInfo.class,
            "channel-and-skip-directives");

    @Localize(displayName = "Terminal Nodes in Bad Places")
    public static final RegionsKey<TermCtx> LONELY_TERMINALS
            = RegionsKey.create(TermCtx.class, "lonely-terminals");

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE,
            entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    public static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {
        bldr.extractingRegionsUnder(CHSKIP)
                .whenRuleType(ANTLRv4Parser.LexComChannelContext.class)
                .extractingBoundsFromRuleAndKeyWith(ctx -> {
                    if (ctx.INT() != null) {
                        return new ChannelOrSkipInfo(ChSkip.CHANNEL, ctx.INT().getText());
                    } else {
                        return new ChannelOrSkipInfo(ChSkip.CHANNEL, "1"); // XXX
                    }
                })
                .whenTokenTypeMatches(LEXCOM_SKIP).derivingKeyWith(tok -> {
            return new ChannelOrSkipInfo(ChSkip.SKIP, "-1");
        })
                .finishRegionExtractor();

        bldr.extractingRegionsUnder(LONELY_TERMINALS)
                .summingTokensFor(ANTLRv4Lexer.VOCABULARY)
                .whenRuleType(ANTLRv4Parser.ParserRuleAtomContext.class)
                .whenAncestorRuleOf(ANTLRv4Parser.ParserRuleDefinitionContext.class)
                .extractingKeyAndBoundsFromTerminalNodeWith((ANTLRv4Parser.ParserRuleAtomContext a, BiPredicate<TermCtx, TerminalNode> b) -> {
                    TerminalContext termCtx = a.terminal();
                    if (termCtx != null && termCtx.STRING_LITERAL() != null) {
                        return b.test(new TermCtx(termCtx.STRING_LITERAL()), termCtx.STRING_LITERAL());
                    }
                    return false;
                }).finishRegionExtractor();
    }

    static class TermCtx {

        final String text;
        final int start;
        final int end;

        TermCtx(TerminalNode nd) {
            Token tok = nd.getSymbol();
            start = tok.getStartIndex();
            end = tok.getStopIndex() + 1;
            text = tok.getText();
        }
    }

    public enum ChSkip {
        CHANNEL,
        SKIP;
    }

    static class ChannelOrSkipInfo {

        private final ChSkip kind;
        private final String channelText;

        public ChannelOrSkipInfo(ChSkip kind, String channelText) {
            this.kind = kind;
            this.channelText = channelText;
        }

        public String toString() {
            return kind.name() + ":" + channelText;
        }

        public ChSkip kind() {
            return kind;
        }

        public int channelNumber() {
            if ("-1".equals(channelText)) {
                return 1;
            }
            try {
                return Integer.parseInt(channelText);
            } catch (NumberFormatException ex) {
                return 1;
            }
        }

    }

    ChannelsAndSkipExtractors() {
        throw new AssertionError();
    }
}
