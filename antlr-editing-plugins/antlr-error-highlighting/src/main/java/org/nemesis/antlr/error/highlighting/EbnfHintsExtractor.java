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

import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAlternativeContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAtomContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleIdentifierContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.NamedRegionData;
import org.nemesis.extraction.key.NamedRegionKey;

/**
 *
 * @author Tim Boudreau
 */
final class EbnfHintsExtractor {

    public static final NamedRegionKey<EbnfItem> SOLO_EBNFS
            = NamedRegionKey.create("solo-ebnfs", EbnfItem.class);

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE, entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {

        bldr.extractNamedRegionsKeyedTo(EbnfItem.class)
                .recordingNamePositionUnder(SOLO_EBNFS)
                .whereRuleIs(ANTLRv4Parser.ParserRuleIdentifierContext.class)
                .whenInAncestorRule(ANTLRv4Parser.ParserRuleAlternativeContext.class)
                // Find the pattern
                // foo : someRule*; or foo : someRule?;
                .derivingNameWith((ParserRuleIdentifierContext pric) -> {
                    return withParentAs(ANTLRv4Parser.ParserRuleReferenceContext.class, pric, ruleRef -> {
                        return withParentAs(ANTLRv4Parser.ParserRuleAtomContext.class, ruleRef, atom -> {
                            return withParentAs(ANTLRv4Parser.ParserRuleElementContext.class, atom, elementContext -> {
                                ANTLRv4Parser.EbnfSuffixContext suffix = elementContext.ebnfSuffix();
                                if (suffix != null) {
                                    createEbnfHintRegionData(suffix, pric);
                                }
                                return null;
                            });
                        });
                    });
                })
                // Find the pattern
                // foo : (a | b | c)*
                .whereRuleIs(BlockContext.class)
                .whenInAncestorRule(EbnfContext.class)
                .derivingNameWith((BlockContext block) -> {
                    // Only create hint if there is not some other content
                    // in this rule which would make it safe with the empty
                    // string - otherwise we would match foo : bar (x | y)*
                    // which is perfectly safe
                    return withAncestorOf(ParserRuleAlternativeContext.class, block, prlac -> {
                        if (prlac.getChildCount() == 1) {
                            return withParentAs(EbnfContext.class, block, (EbnfContext ebnf) -> {
                                ANTLRv4Parser.EbnfSuffixContext suffix = ebnf.ebnfSuffix();
                                if (suffix != null) {
                                    return createEbnfHintRegionData(suffix, block);
                                }
                                return null;
                            });
                        }
                        return null;
                    });
                })
                // Find the pattern
                // foo : SomeLexerToken*
                .whereRuleIs(ParserRuleAtomContext.class)
                .whenInAncestorRule(ParserRuleAlternativeContext.class)
                .derivingNameWith((ParserRuleAtomContext atom) -> {
                    if (atom.getChildCount() == 1) {
//                        withAncestorOf(ParserRuleAlternativeContext.class, atom, prlac -> {
//                            if (prlac.getChildCount() == 1) {
                                return withParentAs(ParserRuleElementContext.class, atom, (ParserRuleElementContext elem) -> {
                                    if (elem.ebnfSuffix() != null && elem.getChildCount() == 2) {
                                        return createEbnfHintRegionData(elem.ebnfSuffix(), elem);
                                    }
                                    return null;
                                });
//                            }
//                            return null;
//                        });
                    }
                    return null;
                })
                .finishNamedRegions();
    }

    private static <T extends ParserRuleContext, R> R withAncestorOf(Class<T> type, ParserRuleContext ctx, Function<T, R> c) {
        ctx = ctx.getParent();
        while (ctx != null) {
            if (type.isInstance(ctx)) {
                return c.apply(type.cast(ctx));
            }
            ctx = ctx.getParent();
        }
        return null;
    }

    private static NamedRegionData<EbnfItem> createEbnfHintRegionData(ANTLRv4Parser.EbnfSuffixContext suffix, ParserRuleContext pric) {
        EbnfItem item = EbnfItem.forEbnfSuffix(suffix);
        if (item.canMatchEmptyString()) {
            return NamedRegionData.create(pric.getText() + suffix.getText(), item,
                    pric.getStart().getStartIndex(),
                    suffix.getStop().getStopIndex() + 1);
        }
        return null;
    }

    private static <T extends ParserRuleContext, R> R withParentAs(Class<T> type, ParserRuleContext child, Function<T, R> c) {
        ParserRuleContext ctx = child.getParent();
        if (ctx != null && type.isInstance(ctx)) {
            return c.apply(type.cast(ctx));
        }
        return null;
    }
}
