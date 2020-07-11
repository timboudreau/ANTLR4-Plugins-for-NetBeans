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

import com.mastfrog.function.state.Int;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import static com.mastfrog.range.RangeRelation.CONTAINS;
import static com.mastfrog.range.RangeRelation.EQUAL;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4BaseVisitor;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import static org.nemesis.antlr.sample.AntlrSampleFiles.MARKDOWN_LEXER;
import static org.nemesis.antlr.sample.AntlrSampleFiles.MARKDOWN_PARSER;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.simple.SampleFile;
import org.nemesis.source.api.GrammarSource;

/**
 *
 * @author Tim Boudreau
 */
public class ParenthesesNotSuperfluousWhenPrecedingLexerCommandOrActionTest {

    private static SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile;
    private static ANTLRv4Parser.GrammarFileContext grammar;
    private static Extraction ext;

    private static final String SIMPLE_LEXER_GRAMMAR_CLEAN = "lexer grammar Foo;\n\n"
            + "Backticks : BACKTICK BACKTICK BACKTICK;\n\n"
            + "fragment BACKTICK : '`';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_SUPERFLUOUS = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK BACKTICK BACKTICK);\n\n"
            + "fragment BACKTICK : '`';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK BACKTICK BACKTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR2 = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR_AND_EBNF = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK)+ -> pushMode(TummyRub);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_ACTION = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK BACKTICK BACKTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_ACTION_AND_PUSHMODE_NO_OR = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK BACKTICK BACKTICK) { ix++; } -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_ACTION_AND_PUSHMODE = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK | SIDETICK) { ix++; } -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n"
            + "fragment SIDETICK : '~';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_EBNF_GROUP = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK FRONTTICK BACKTICK)+;\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : 'tick';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_1 = "lexer grammar Foo;\n\n"
            + "Backticks : ((BACKTICK | FRONTTICK)) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";
    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_PARENS_BEFORE_PUSHMODE = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_2
            = "lexer grammar Foo;\n\n"
            + "Backticks : ((BACKTICK) | FRONTTICK) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_3
            = "lexer grammar Foo;\n\n"
            + "Backticks : ((BACKTICK) | (FRONTTICK SIDETICK)) -> pushMode(Preformatted);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n"
            + "fragment SIDETICK : '/';\n";

    private static final String SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_4
            = "lexer grammar Foo;\n\n"
            + "Backticks : ((BACKTICK) | (FRONTTICK | SIDETICK));\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : '-';\n"
            + "fragment SIDETICK : '/';\n";

    private static final String TOP_LEVEL_UNNECESSARY_PARENS = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK | BACKTICK);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : 'tick';\n";

    private static final String TOP_LEVEL_UNNECESSARY_PARENS_2 = "lexer grammar Foo;\n\n"
            + "Backticks : ((BACKTICK) | FRONTTICK | (SIDETICK));\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : 'tick';\n"
            + "fragment SIDETICK : '/';";

    private static final String TOP_LEVEL_NECESSARY_PARENS = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | FRONTTICK) | SIDETICK;\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : 'tick';\n"
            + "fragment SIDETICK : '/';";

    private static final String TOP_LEVEL_NECESSARY_AND_UNNECESSARY_PARENS = "lexer grammar Foo;\n\n"
            + "Backticks : (BACKTICK | (FRONTTICK)) | (SIDETICK);\n\n"
            + "fragment BACKTICK : '`';\n"
            + "fragment FRONTTICK : 'tick';\n"
            + "fragment SIDETICK : '/';";

    private static final String COMBINED_PARSER_OUTER_SUPERFLUOUS = "parser grammar Foo;\n\n"
            + "compilation_unit : (things | dogs | shoes | ennui);\n";

    private static final String COMBINED_PARSER_ALT_LABELED = "parser grammar Foo;\n\n"
            + "compilation_unit : (things bugs | dogs) #ThingDogs\n"
            + "    | shoes #Shoes\n"
            + "    | ennui #Ennui;\n";

    private static final String COMBINED_PARSER_OUTER_EBNF = "parser grammar Foo;\n\n"
            + "compilation_unit : (things | dogs | shoes | ennui)+ EOF;\n";

    private static final String COMBINED_PARSER_NECESSARY = "parser grammar Foo;\n\n"
            + "compilation_unit : (things | dogs) | shoes | ennui;\n";

    private static final String COMBINED_PARSER_NECESSARY_BUT_WRAPPED = "parser grammar Foo;\n\n"
            + "compilation_unit : ((things | dogs) | shoes | ennui);\n";

    private static final String COMBINED_PARSER_INNER_SUPERFLUOUS = "parser grammar Foo;\n\n"
            + "compilation_unit : (things dogs) shoes | ennui;\n";

    private static final String COMBINED_PARSER_INNER_DOUBLE_SUPERFLUOUS = "parser grammar Foo;\n\n"
            + "compilation_unit : (things dogs) (shoes) | ennui;\n";

    private static final String COMBINED_PARSER_EBNF_IN_SUPERFLUOUS = "parser grammar Foo;\n\n"
            + "compilation_unit : (things (dogs)+) (shoes) | ennui;\n";

    private static final String COMBINED_PARSER_EBNFS = "parser grammar Foo;\n\n"
            + "compilation_unit : things (dogs (shoes | ennui)*)?;\n";

    private static final String COMBINED_PARSER_EBNFS2 = "parser grammar Foo;\n\n"
            + "compilation_unit : things dogs (shoes (dogs shoes)*);\n";

    private static final String COMBINED_PARSER_INNER_DOUBLE_NESTED_SUPERFLUOUS = "parser grammar Foo;\n\n"
            + "compilation_unit : (things (dogs)) (shoes) | ennui;\n";

    @Test
    public void testParserOuterSuperfluous() throws Exception {
        testDynamic(COMBINED_PARSER_OUTER_SUPERFLUOUS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext, "compilation_unit");
            assertExpectedRegions(regs, sampleFile, grammar, "(things | dogs | shoes | ennui)");
        });
    }

    @Test
    public void testParserOuterAltLabeled() throws Exception {
        testDynamic(COMBINED_PARSER_ALT_LABELED, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext);
            assertEquals(0, regs.size());
        });
    }

    @Test
    public void testParserInnerDoubleSuperfluous() throws Exception {
        testDynamic(COMBINED_PARSER_INNER_DOUBLE_SUPERFLUOUS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext, "compilation_unit");
            assertExpectedRegions(regs, sampleFile, grammar, "(things dogs)", "(shoes)");
        });
    }

    @Test
    public void testParserInnerDoubleNestedSuperfluous() throws Exception {
        testDynamic(COMBINED_PARSER_INNER_DOUBLE_NESTED_SUPERFLUOUS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext, "compilation_unit");
            assertExpectedRegions(regs, sampleFile, grammar, "(things (dogs))", "(dogs)", "(shoes)");
        });
    }

    @Test
    public void testParserInnerSuperfluous() throws Exception {
        testDynamic(COMBINED_PARSER_INNER_SUPERFLUOUS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext, "compilation_unit");
            assertExpectedRegions(regs, sampleFile, grammar, "(things dogs)");
        });
    }

    @Test
    public void testParserNecessary() throws Exception {
        testDynamic(COMBINED_PARSER_NECESSARY, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext);
            assertTrue(regs.isEmpty());
        });
    }

    @Test
    public void testParserNecessaryButWrapped() throws Exception {
        testDynamic(COMBINED_PARSER_NECESSARY_BUT_WRAPPED, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext, "compilation_unit");
            assertExpectedRegions(regs, sampleFile, grammar, "((things | dogs) | shoes | ennui)");
        });
    }

    @Test
    public void testParserOuterEbnf() throws Exception {
        testDynamic(COMBINED_PARSER_OUTER_EBNF, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile,
                    grammar, ext);
            assertTrue(regs.isEmpty());
        });
    }

    @Test
    public void testTopLevelUnnecessaryParensPostfix() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_4, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "((BACKTICK) | (FRONTTICK | SIDETICK))", "(BACKTICK)");
        });
    }

    @Test
    public void testLexerNoParens() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_CLEAN, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regions = ext.regions(ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES);
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
            assertNotNull(regions, "Empty regions should never be null");
            assertTrue(regions.isEmpty(), "No parentheses in this grammar - certainly "
                    + "should be no *superfluous* ones");
        });
    }

    @Test
    public void testLexerSuperfluousParens() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_SUPERFLUOUS, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
        });
    }

    @Test
    public void testLexerEbnfGroup() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_EBNF_GROUP, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testLexerWithLexerCommands() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
        });
    }

    @Test
    public void testLexerWithLexerCommandsButOrClauseInParens() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testLexerWithLexerCommandsButOrClauseInParens2() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR2, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndMultiplyNestedParens1() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_1, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            // Removing BOTH of these results in an invalid source; removing either one
            // results in no semantic change
            assertExpectedRegions(regs, sampleFile, grammar, "((BACKTICK | FRONTTICK))", "(BACKTICK | FRONTTICK)");
            String txt = textOf(regs.iterator().next(), sampleFile);
            assertEquals("((BACKTICK | FRONTTICK))", txt, "Wrong region detected");
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndParensBeforePushmode() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_PARENS_BEFORE_PUSHMODE, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
            assertTrue(regs.isEmpty());
        });
    }

    @Test
    public void testTopLevelUnnecessaryParens() throws Exception {
        testDynamic(TOP_LEVEL_UNNECESSARY_PARENS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "(BACKTICK | FRONTTICK | BACKTICK)");
            String txt = textOf(regs.iterator().next(), sampleFile);
            assertEquals("(BACKTICK | FRONTTICK | BACKTICK)", txt, "Wrong region detected");
        });
    }

    @Test
    public void testTopLevelUnnecessaryParens2() throws Exception {
        testDynamic(TOP_LEVEL_UNNECESSARY_PARENS_2, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "((BACKTICK) | FRONTTICK | (SIDETICK))", "(BACKTICK)", "(SIDETICK)");
            String txt = textOf(regs.iterator().next(), sampleFile);
            assertEquals("((BACKTICK) | FRONTTICK | (SIDETICK))", txt, "Wrong region detected");
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndMultiplyNestedParens2() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_2, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "(BACKTICK)");
            assertEquals(1, regs.size());
            String txt = textOf(regs.iterator().next(), sampleFile);
            assertEquals("(BACKTICK)", txt, "Wrong region detected");
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndMultiplyNestedParens3() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_MULTIPLY_NESTED_PARENS_3, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "(BACKTICK)", "(FRONTTICK SIDETICK)");
        });
    }

    @Test
    public void testLexerWithLexerCommandsButOrClauseInParensAndEbnf() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_PUSH_MODE_AND_OR_AND_EBNF, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndAction() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_ACTION_AND_PUSHMODE, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testLexerWithLexerCommandsAndActionButUngroupable() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_ACTION_AND_PUSHMODE_NO_OR, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "(BACKTICK BACKTICK BACKTICK)");
        });
    }

    @Test
    public void testLexerWithAction() throws Exception {
        testDynamic(SIMPLE_LEXER_GRAMMAR_WITH_ACTION, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
        });
    }

    @Test
    public void testNecessaryParens() throws Exception {
        testDynamic(TOP_LEVEL_NECESSARY_PARENS, (sampleFile, grammar, ext) -> {
            assertRulesWithSuperfluousParens(sampleFile, grammar, ext);
        });
    }

    @Test
    public void testNecessaryAndUnnecessaryParens() throws Exception {
        testDynamic(TOP_LEVEL_NECESSARY_AND_UNNECESSARY_PARENS, (sampleFile, grammar, ext) -> {
            SemanticRegions<Integer> regs = assertRulesWithSuperfluousParens(sampleFile, grammar, ext, "Backticks");
            assertExpectedRegions(regs, sampleFile, grammar, "(FRONTTICK)", "(SIDETICK)");
        });
    }

    @Test
    public void testDetection() throws Exception {
        String txt = sampleFile.text();
        System.out.println("TEXT: " + txt);
        System.out.println("\n------------------\n");
        SemanticRegions<Integer> regions = ext.regions(ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES);
        for (SemanticRegion<Integer> reg : regions) {
            String sub = txt.substring(reg.start(), reg.end());
            System.out.println("REGION: " + reg.start() + ":" + reg.end() + "  '"
                    + sub + "' in rule " + ruleNameForRegion(reg));
        }
    }

    private void assertExpectedRegions(SemanticRegions<?> regs, SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sf,
            GrammarFileContext ctx, String... expectedRegionBodies) throws IOException {
        boolean haveExtraRegions = regs.size() > expectedRegionBodies.length;
        Set<String> all = new HashSet<>(Arrays.asList(expectedRegionBodies));
        Map<String, SemanticRegion<?>> unexpected = new HashMap<>();
        StringBuilder allRegions = new StringBuilder();
        for (SemanticRegion<?> reg : regs) {
            String bodyText = textOf(reg, sf);
            if (allRegions.length() > 0) {
                allRegions.append(", ");
            }
            allRegions.append("'").append(bodyText).append("'");
            if (!all.contains(bodyText)) {
                unexpected.put(bodyText, reg);
            } else {
                all.remove(bodyText);
            }
        }
        allRegions.append('\n');
        allRegions.insert(0, "All detected regions: ");
        if (!unexpected.isEmpty() || !all.isEmpty()) {
            StringBuilder sb = new StringBuilder(allRegions.toString());
            if (!all.isEmpty()) {
                for (String notFound : all) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append("Did not find a region with text '").append(notFound).append("'");
                }
            }
            for (Map.Entry<String, SemanticRegion<?>> e : unexpected.entrySet()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("Unexpected region ").append(e.getValue().start()).append(":").append(e.getValue().end()).append(": '").append(e.getKey()).append('\'');
            }
            sb.append('\n');
            out(ctx, sb, 0, 0);
            sb.append("Full file:\n").append(sf.text());
            fail(sb.toString());
        }
        assertFalse(haveExtraRegions, "Extra regions");
    }

    private static SemanticRegions<Integer> assertRulesWithSuperfluousParens(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile, GrammarFileContext parseTree, Extraction extraction, String... rules) throws IOException, Exception {
        Set<String> expRules = new HashSet<>(Arrays.asList(rules));
        SemanticRegions<Integer> regions = extraction.regions(ChannelsAndSkipExtractors.SUPERFLUOUS_PARENTEHSES);
        if (expRules.isEmpty()) {
            boolean regionsEmpty = regions.isEmpty();
            if (!regionsEmpty) {
                StringBuilder sb = new StringBuilder();
                int ix = 0;
                for (SemanticRegion<Integer> reg : regions) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(++ix).append(". Unexpected superfluous parens region ")
                            .append(reg.start()).append(':').append(reg.end())
                            .append(" '").append(textOf(reg, sampleFile)).append("' in rule '")
                            .append(ruleNameForRegion(reg, parseTree, sampleFile))
                            .append("' with text '").append(
                            ruleTextForRegion(reg, parseTree, sampleFile)).append('\'')
                            .append("\n").append(ruleTreeForRegion(reg, parseTree, sampleFile))
                            .append("\nFull Text:\n").append(sampleFile.text());
                }
                fail(sb.toString());
            }
        } else {
            Set<String> encountered = new HashSet<>();
            List<SemanticRegion<Integer>> surprises = new ArrayList<>();
            for (SemanticRegion<Integer> reg : regions) {
                String name = ruleNameForRegion(reg, parseTree, sampleFile);
                encountered.add(name);
                if (!expRules.contains(name)) {
                    surprises.add(reg);
                }
            }
            if (!encountered.equals(expRules)) {
                Set<String> notFound = new TreeSet<>(expRules);
                notFound.removeAll(encountered);
                StringBuilder sb = new StringBuilder();
                if (!notFound.isEmpty()) {
                    sb.append("Superfluous parentheses not detected in some rules: ")
                            .append(notFound).append('\n');
                    if (surprises.isEmpty()) {
                        out(parseTree, sb, 0, 0);
                    }
                    sb.append("\nFull text:\n").append(sampleFile.text());
                }
                int ix = 0;
                for (SemanticRegion<Integer> reg : surprises) {
                    String surpriseRuleName = ruleNameForRegion(reg, parseTree, sampleFile);
                    sb.append(++ix).append(". Unexpected superfluous parens region ")
                            .append(reg.start()).append(':').append(reg.end())
                            .append(" '").append(textOf(reg, sampleFile)).append("' in rule '")
                            .append(surpriseRuleName)
                            .append("' with text '").append(
                            ruleTextForRegion(reg, parseTree, sampleFile)).append('\'')
                            .append("\n").append(ruleTreeForRegion(reg, parseTree, sampleFile));;
                }
                fail(sb.toString());
            }
        }
        return regions;
    }

    private String textOf(SemanticRegion<?> reg) throws IOException {
        return textOf(reg, sampleFile);
    }

    private String ruleNameForRegion(SemanticRegion<?> region) throws Exception {
        return ruleNameForRegion(region, grammar, sampleFile);
    }

    private static String textOf(SemanticRegion<?> reg, SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile) throws IOException {
        return sampleFile.text().substring(reg.start(), reg.end());
    }

    private static String ruleNameForRegion(SemanticRegion<?> region, GrammarFileContext grammar, SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile) throws Exception {
        Obj<String> result = Obj.create();
        grammar.accept(new ANTLRv4BaseVisitor<Void>() {
            @Override
            public Void visitChildren(RuleNode node) {
                if (result.isSet()) {
                    return null;
                }
                return super.visitChildren(node);
            }

            @Override
            public Void visitErrorNode(ErrorNode node) {
                System.out.println("ERR NODE " + node);
                return super.visitErrorNode(node); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        result.set(ctx.parserRuleDeclaration().parserRuleIdentifier().getText());
                }
                return super.visitParserRuleSpec(ctx);
            }

            @Override
            public Void visitFragmentRuleSpec(ANTLRv4Parser.FragmentRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        result.set(ctx.fragmentRuleDeclaration().fragmentRuleIdentifier().getText());
                }
                return super.visitFragmentRuleSpec(ctx);
            }

            @Override
            public Void visitTokenRuleSpec(ANTLRv4Parser.TokenRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        result.set(ctx.tokenRuleDeclaration().tokenRuleIdentifier().getText());
                }
                return super.visitTokenRuleSpec(ctx);
            }
        });
        assertTrue(result.isSet(), "Did not find a rule containing " + region + " '" + textOf(region, sampleFile) + "'");
        return result.get();
    }

    private static String ruleTextForRegion(SemanticRegion<?> region, GrammarFileContext grammar, SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile) throws Exception {
        Obj<String> result = Obj.create();
        grammar.accept(new ANTLRv4BaseVisitor<Void>() {
            @Override
            public Void visitChildren(RuleNode node) {
                if (result.isSet()) {
                    return null;
                }
                return super.visitChildren(node);
            }

            @Override
            public Void visitErrorNode(ErrorNode node) {
                System.out.println("ERR NODE " + node);
                return super.visitErrorNode(node); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Void visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        try {
                        result.set(sampleFile.text().substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1));
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
                return super.visitParserRuleSpec(ctx);
            }

            @Override
            public Void visitFragmentRuleSpec(ANTLRv4Parser.FragmentRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        try {
                        result.set(sampleFile.text().substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1));
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
                return super.visitFragmentRuleSpec(ctx);
            }

            @Override
            public Void visitTokenRuleSpec(ANTLRv4Parser.TokenRuleSpecContext ctx) {
                IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                RangeRelation rel = rng.relationTo(region);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        try {
                        result.set(sampleFile.text().substring(ctx.start.getStartIndex(), ctx.stop.getStopIndex() + 1));
                    } catch (IOException ex) {
                        throw new AssertionError(ex);
                    }
                }
                return super.visitTokenRuleSpec(ctx);
            }
        });
        assertTrue(result.isSet(), "Did not find a rule containing " + region + " '" + textOf(region, sampleFile) + "'");
        return result.get();
    }

    private static String ruleTreeForRegion(SemanticRegion<?> region, GrammarFileContext grammar, SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile) throws Exception {
        Obj<ParserRuleContext> result = Obj.create();
        Int greatestDepth = Int.create();
        grammar.accept(new ANTLRv4BaseVisitor<Void>() {
            int depth;

            @Override
            public Void visitChildren(RuleNode node) {
                if (result.isSet()) {
                    return null;
                }
                if (node instanceof ParserRuleContext) {
                    ParserRuleContext ctx = (ParserRuleContext) node;
                    IntRange rng = Range.ofCoordinates(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
                    RangeRelation rel = rng.relationTo(region);
                    switch (rel) {
                        case EQUAL:
                            if (greatestDepth.getAsInt() < depth) {
                                result.set(ctx);
                                greatestDepth.set(depth);
                            }
                    }
                }
                depth++;
                try {
                    return super.visitChildren(node);
                } finally {
                    depth--;
                }
            }
        });
        assertTrue(result.isSet(), "Nothing found for " + region.start() + ":" + region.end());
        ParserRuleContext ctx = result.get();
        StringBuilder sb = new StringBuilder();
        int depth = greatestDepth.getAsInt();
        while (ctx != null) {
            char[] space = new char[depth * 2];
            Arrays.fill(space, ' ');
            if (sb.length() > 0) {
                sb.insert(0, '\n');
            }
            sb.insert(0, new String(space) + depth + ". " + ctx.getClass().getSimpleName()
                    + ": '" + truncate(ctx.getText(), 36) + "'");
            depth--;
            ctx = ctx.getParent();
        }
        sb.append("\n---------------- Subtree ----------------\n");
        out(result.get(), sb, 0, greatestDepth.getAsInt());
        sb.append("\n---------------- Full Tree --------------\n");
        out(grammar, sb, 0, 0);
        return sb.toString();
    }

    static void out(ParserRuleContext ctx, StringBuilder into, int depth, int offset) {
        char[] c = new char[depth * 2];
        Arrays.fill(c, ' ');
        into.append(c);
        into.append(depth + offset).append(". ").append(ctx.getClass().getSimpleName())
                .append(": ").append(truncate(ctx.getText(), 36)).append("'\n");
        int ct = ctx.getChildCount();
        for (int i = 0; i < ct; i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext) {
                ParserRuleContext prc = (ParserRuleContext) child;
                out(prc, into, depth + 1, offset);
            }
        }
    }

    static String truncate(String text, int length) {
        if (text.length() > length) {
            text = text.substring(0, length) + "...";
        }
        return text;
    }

    @BeforeAll
    public static void setup() throws IOException {
        ext = buildExtraction();
    }

    static Extraction buildExtraction() throws IOException {
        // Create a combined grammar out of the standalone parser and lexer
        sampleFile = MARKDOWN_PARSER.withText(orig -> {
            try {
                String lexer = MARKDOWN_LEXER.text();
                orig = orig.replace("parser grammar", "grammar");
                int ix = lexer.indexOf("OpenHeading");
                lexer = lexer.substring(ix);
                // No generate some superfluous parentheses
                lexer = Strings.literalReplaceAll("NEWLINE? UNDERSCORE SAFE_PUNCTUATION?", "(NEWLINE? UNDERSCORE SAFE_PUNCTUATION?)", lexer);
                lexer = Strings.literalReplaceAll("NEWLINE? STRIKE SAFE_PUNCTUATION?", "(NEWLINE? STRIKE SAFE_PUNCTUATION?) {}", lexer);
                lexer = Strings.literalReplaceAll("NEWLINE? SAFE_PUNCTUATION? OPEN_PAREN", "(NEWLINE? SAFE_PUNCTUATION? OPEN_PAREN)", lexer);
                return orig + lexer;
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        });
        ExtractorBuilder<ANTLRv4Parser.GrammarFileContext> eb = Extractor.builder(ANTLRv4Parser.GrammarFileContext.class, ANTLR_MIME_TYPE);
        ChannelsAndSkipExtractors.populateBuilder(eb);
        Extractor<ANTLRv4Parser.GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = sampleFile.lexer();
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = sampleFile.parser();
        grammar = parser.grammarFile();
        assertNotNull(grammar);
        Extraction nmExtraction = ext.extract(grammar,
                GrammarSource.find(sampleFile.charStream(),
                        ANTLR_MIME_TYPE), tokens);
        assertFalse(nmExtraction.isPlaceholder());
        return nmExtraction;
    }

    static void testDynamic(String text, ThrowingTriConsumer<SampleFile<ANTLRv4Lexer, ANTLRv4Parser>, GrammarFileContext, Extraction> runner) throws Exception {
        SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFileLocal = AntlrSampleFiles.create(text);
        ExtractorBuilder<ANTLRv4Parser.GrammarFileContext> eb = Extractor.builder(ANTLRv4Parser.GrammarFileContext.class, ANTLR_MIME_TYPE);
        ChannelsAndSkipExtractors.populateBuilder(eb);
        Extractor<ANTLRv4Parser.GrammarFileContext> ext = eb.build();
        List<Token> tokens = new ArrayList<>();
        ANTLRv4Lexer lex = sampleFileLocal.lexer();
        int ix = 0;
        for (Token t = lex.nextToken(); t.getType() != ANTLRv4Lexer.EOF; t = lex.nextToken()) {
            CommonToken ct = new CommonToken(t);
            ct.setTokenIndex(ix++);
            tokens.add(ct);
        }
        lex.reset();
        ANTLRv4Parser parser = sampleFileLocal.parser();
        GrammarFileContext grammarLocal = parser.grammarFile();
        assertNotNull(grammarLocal);
        Extraction extraction = ext.extract(grammarLocal,
                GrammarSource.find(sampleFileLocal.charStream(),
                        ANTLR_MIME_TYPE), tokens);
        assertFalse(extraction.isPlaceholder());
        runner.apply(sampleFileLocal, grammarLocal, extraction);
    }

}
