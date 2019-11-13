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
import org.antlr.v4.runtime.tree.ParseTree;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.BlockContext;
import org.nemesis.antlr.ANTLRv4Parser.EbnfContext;
import org.nemesis.antlr.ANTLRv4Parser.EbnfSuffixContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAlternativeContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleAtomContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleDefinitionContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleElementContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleIdentifierContext;
import org.nemesis.antlr.ANTLRv4Parser.ParserRuleReferenceContext;
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

    static <T> T ancestor(ParseTree rule, Class<T> type) {
        ParseTree r = rule;
        while (r != null) {
            if (type.isInstance(r)) {
                return type.cast(r);
            }
            r = r.getParent();
        }
        return null;
    }

    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE, entryPoint = ANTLRv4Parser.GrammarFileContext.class)
    static void populateBuilder(ExtractorBuilder<? super ANTLRv4Parser.GrammarFileContext> bldr) {

        bldr.extractNamedRegionsKeyedTo(EbnfItem.class)
                .recordingNamePositionUnder(SOLO_EBNFS)
                .whereRuleIs(ANTLRv4Parser.ParserRuleIdentifierContext.class)
                .whenInAncestorRule(ANTLRv4Parser.ParserRuleAlternativeContext.class)
                .whereParentHierarchy(pb -> {
                    pb.withParentType(ParserRuleReferenceContext.class)
                            .withParentType(ParserRuleAtomContext.class)
                            .withParentType(ParserRuleElementContext.class).thatHasOnlyOneChild();
                })
                .derivingNameWith((ParserRuleIdentifierContext pric) -> {
//                    System.out.println("try ParserRuleIdentifierContext '" + Strings.escape(pric.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE)
//                            + "' in " + Strings.escape(pric.getParent().getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    // Find the pattern
                    // foo : someRule*; or foo : someRule?;
                    ParserRuleAlternativeContext anc = ancestor(pric, ParserRuleAlternativeContext.class);
                    if (anc.getChildCount() > 1) {
//                        System.out.println("    child count too high");
                        return null;
                    }
                    ANTLRv4Parser.EbnfSuffixContext ebnf = ancestor(pric, ParserRuleElementContext.class).ebnfSuffix();
                    if (ebnf != null) {
                        NamedRegionData<EbnfItem> result = createEbnfHintRegionData(ebnf, pric);
//                        System.out.println("      result " + result);
                        return result;
                    } else {
//                        System.out.println("   no ebnf");
                    }
                    return null;
                })
                .whereRuleIs(BlockContext.class)
                .whenInAncestorRule(EbnfContext.class)
                .whereParentHierarchy(pb -> {
                    pb.withParentType(EbnfContext.class).thatMatches((EbnfContext eb) -> {
                        // Filter the context for only the case where it is *, *? or +
                        if (eb.ebnfSuffix() != null) {
                            EbnfSuffixContext suffix = eb.ebnfSuffix();
                            if (suffix != null) {
                                boolean result = (suffix.STAR() != null || suffix.QUESTION() != null)
                                        && suffix.PLUS() == null;
//                                System.out.println("TESTING EBNF '" + suffix.getText() + "' result " + result);
                                return result;
                            }
                        }
                        return false;
                    }).withParentType(ParserRuleElementContext.class);
                })
                .derivingNameWith((BlockContext block) -> {
                    // Find the pattern
                    // foo : (a | b | c)*
                    EbnfContext ebnf = ancestor(block, EbnfContext.class);
//                    System.out.println("try Block '" + Strings.escape(block.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE)
//                            + "' in " + Strings.escape(block.getParent().getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
//                    if (ebnf.ebnfSuffix() != null) {
                    NamedRegionData<EbnfItem> result = createEbnfHintRegionData(ebnf.ebnfSuffix(), block);
//                    System.out.println("      result " + result);
                    return result;
//                    } else {
//                        System.out.println("  no ebnf");
//                    }
//                    return null;
                })
                .whereRuleIs(ParserRuleElementContext.class)
                .whenInAncestorRule(ParserRuleDefinitionContext.class)
                .whereParentHierarchy(pb -> {
                    pb.withParentType(ParserRuleAlternativeContext.class)
                            .skippingParent()
                            .withParentType(ParserRuleDefinitionContext.class);

                })
                .derivingNameWith((ParserRuleElementContext ctx) -> {
//                    System.out.println("try ParserRuleIdentifierContext '" + Strings.escape(ctx.getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE)
//                            + "' in " + Strings.escape(ctx.getParent().getText(), Escaper.NEWLINES_AND_OTHER_WHITESPACE));
                    // Find the pattern
                    // foo : SomeLexerToken*
                    if (ctx.getChildCount() == 2) {
//                        System.out.println("  has cc 2");
                        for (int i = 0; i < 2; i++) {
//                            System.out.println("     CHILD " + i + ": '" + ctx.getChild(i).getText() + "' "
//                                    + ctx.getChild(i).getClass().getSimpleName());
                        }
//                        System.out.println("ebnf suffix: '" + (ctx.ebnfSuffix() == null ? "null'" : ctx.ebnfSuffix().getText() + "'"));
//                        System.out.println("EBNF: '" + (ctx.ebnf() == null ? "null'" : ctx.ebnf().getText() + "'"));
                        EbnfSuffixContext ebnf = null;
                        if (ctx.ebnfSuffix() != null) {
                            ebnf = ctx.ebnfSuffix();
//                            System.out.println("  found on ctx? " + (ebnf != null));
                        } else {
                            if (ctx.ebnf() != null) {
                                ebnf = ctx.ebnf().ebnfSuffix();
//                                System.out.println("  found on ctx.ebnf() " + (ebnf != null));
                            }
                        }
//                        EbnfSuffixContext ebnf = ctx.ebnfSuffix() != null ? ctx.ebnf() != null ? ctx.ebnf().ebnfSuffix() : null : null;
                        if (ebnf != null) {
                            NamedRegionData<EbnfItem> result = createEbnfHintRegionData(ebnf, ctx);
//                            System.out.println("      result " + result);
                            return result;
                        } else {
//                            System.out.println("   but no ebnf");
                        }
                    }
                    return null;
                }).finishNamedRegions();
    }

    private static boolean isIsolatedEbnf(ParserRuleDefinitionContext def) {

        return false;
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
