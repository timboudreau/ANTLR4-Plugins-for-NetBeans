/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.memory;

import org.nemesis.antlr.memory.alt.AlternativesInfo;
import org.nemesis.antlr.memory.alt.RuleAlt;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.range.DataIntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import static com.mastfrog.util.collections.CollectionUtils.mutableSetOf;
import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class AlternativesTest {

    @Test
    public void testAlts2() throws Throwable {
        withSampleFile(AntlrSampleFiles.RUST, res -> {
            AlternativesInfo alts = res.alterantives();
            assertNotNull(alts);

            sanityCheckAlts(alts);

            String text = AntlrSampleFiles.RUST.text();
            alts.forEach((start, end, alt) -> {
                String txt = text.substring(start, end);
                assertFalse(txt.isEmpty());
//                System.out.println(alt);
//                System.out.println("  '" + Escaper.CONTROL_CHARACTERS.escape(txt) + "'");
                switch(alt.rule) {
                    case "type_hint":
                        switch(alt.altIndex) {
                            case 1 :
                                assertEquals("LeftAngleBracket type_spec As type_spec RightAngleBracket", txt);
                                break;
                            default :
                                fail("Extra element for type_hint: " + alt + " @ " + start + ":" + end);
                        }
                        break;
                    case "unsigned_int_subtype":
                        switch(alt.altIndex) {
                            case 4 :
                                assertEquals("U64", txt);
                                assertEquals(7812, start);
                                assertEquals(7815, end);
                                break;
                        }
                        break;
                    case "while_loop":
                        switch(alt.altIndex) {
                            case 1 :
                                assertEquals("While boolean_expression block", txt);
                                break;
                            default :
                                fail("Extra element for while_loop: " + alt + " @ " + start + ":" + end);
                        }
                }
            });
        });
    }

    @Test
    public void testAlternatives() throws Throwable {
        withSampleFile(AntlrSampleFiles.MATH_SPLIT_RECURSIVE_LABELED, res -> {
            AlternativesInfo alts = res.alterantives();
            assertNotNull(alts);

            String text = AntlrSampleFiles.MATH_SPLIT_RECURSIVE_LABELED.text();

            assertAlts(alts,
                    "compilationUnit:1",
                    "math:1",
                    "statement:1",
                    "assertion:1",
                    "assertionRightSide:1",
                    "expression:1(LiteralExpression)",
                    "expression:2(SubtractionExpression)",
                    "expression:3(AdditionExpression)",
                    "expression:4(DivisionExpression)",
                    "expression:5(MultiplicationExpression)",
                    "expression:6(ExponentialExpression)",
                    "expression:7(ModuloExpression)",
                    "expression:8(ParenthesizedExpression)",
                    "number:1",
                    "word:1");

            alts.forEach((start, end, alt) -> {
                String txt = text.substring(start, end);
                switch (alt.rule) {
                    case "compilationUnit":
                        assertEquals(1, alt.altIndex);
                        assertEquals("(\n        word\n        | math )+ EOF", txt);
                        break;
                    case "expression":
                        switch (alt.altIndex) {
                            case 1:
                                assertEquals("number #LiteralExpression", txt);
                                break;
                            case 2:
                                assertEquals("expression Minus expression #SubtractionExpression", txt);
                                break;
                            case 3:
                                assertEquals("expression Plus expression #AdditionExpression", txt);
                                break;
                            case 4:
                                assertEquals("expression DividedBy expression #DivisionExpression", txt);
                                break;
                            case 5:
                                assertEquals("expression Times expression #MultiplicationExpression", txt);
                                break;
                            case 6:
                                assertEquals("expression Pow expression #ExponentialExpression", txt);
                                break;
                            case 7:
                                assertEquals("expression Mod expression #ModuloExpression", txt);
                                break;
                            case 8:
                                assertEquals("OpenParen expression CloseParen #ParenthesizedExpression", txt);
                                break;
                            default:
                                fail("Unexpected expression element: " + alt + " with text '" + txt + "'");
                        }
                }
            });

        });

    }

    private void assertAlts(AlternativesInfo alts, String... strings) {
        Set<String> absent = mutableSetOf(strings);
        assertEquals(strings.length, absent.size(), "Test bug: duplicate "
                + "strings in " + Arrays.toString(strings));
        Set<String> found = new HashSet<>();
        alts.forEach((start, end, info) -> {
            String str = info.toString();
            boolean foundIt = absent.remove(str);
            if (!foundIt) {
                str = str + " " + start + ":" + end;
                foundIt = absent.remove(str);
            }
            if (foundIt) {
                found.add(str);
            }
        });
        assertTrue(absent.isEmpty(), () -> "Not found: " + absent);
        assertEquals(found.size(), strings.length);

        sanityCheckAlts(alts);
    }

    private void sanityCheckAlts(AlternativesInfo alts) {
        List<DataIntRange<RuleAlt, ? extends DataIntRange<RuleAlt, ?>>> l = new ArrayList<>();
        alts.forEach((start, end, info) -> {
            l.add(Range.ofCoordinates(start, end, info));
        });
        for (int i = 1; i < l.size(); i++) {
            DataIntRange<RuleAlt, ? extends DataIntRange<RuleAlt, ?>> prev = l.get(i - 1);
            DataIntRange<RuleAlt, ? extends DataIntRange<RuleAlt, ?>> curr = l.get(i);
            RangeRelation rel = curr.relationTo(prev);
            switch (rel) {
                case AFTER:
                    // ok
                    break;
                default:
                    fail("Ranges out of sequence at " + i + ":\n" + prev
                            + " and \n" + curr + ": " + rel);
            }
        }
    }

    static void withSampleFile(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sf,
            ThrowingConsumer<AntlrGenerationResult> c) throws Exception, Throwable {

        JFS jfs = JFS.builder().build();
        String gname = UnixPath.get(sf.fileName()).rawName();
//        JFSFileObject fo = map(gname + ".g4", "at/x/" + sf.fileName(), jfs);
        JFSFileObject fo = jfs.create(UnixPath.get("at/x/" + sf.fileName()), SOURCE_PATH, sf.text());
        UnixPath mainGrammar = fo.path();

        AntlrGenerator gen = AntlrGenerator.builder(() -> jfs)
                .withOriginalFile(Paths.get(sf.fileName()))
                .generateAllGrammars(true)
                .generateDependencies(true)
                .withTokensHash("-x-")
                .requestAlternativesAnalysis()
                .building(mainGrammar.getParent());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pw = new PrintStream(out, true, "UTF-8");
        AntlrGenerationResult res = gen.run(mainGrammar.rawName(), pw, true);
        res.rethrow();
        assertTrue(res.isUsable(), () -> "Unusable result " + res + ":\n" + new String(out.toByteArray(), UTF_8));
        c.accept(res);
    }
}
