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
package org.nemesis.antlr.live.language;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSextaConsumer;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.range.RangeRelation;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.live.language.AlternativesExtractors.AlternativeKey;
import org.nemesis.antlr.memory.alt.AlternativesInfo;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.memory.alt.RuleAlt;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractors;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.simple.SampleFile;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.test.fixtures.support.GeneratedMavenProject;
import org.nemesis.test.fixtures.support.ProjectTestHelper;
import org.nemesis.test.fixtures.support.TestFixtures;

/**
 *
 * @author Tim Boudreau
 */
public class AlternativePositionDetectionTest {

    private static final ThrowingRunnable shutdown = ThrowingRunnable.oneShot(true);

    @Test
    public void testRustGrammar() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.RUST);
    }

    @Test
    public void testSensors() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.SENSORS);
    }

    @Test
    public void testMathCombinedExpression() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_COMBINED_EXPRESSION);
    }

    @Test
    public void testMathCombinedExpressionRecursive() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_COMBINED_EXPRESSION_RECURSIVE);
    }

    @Test
    public void testMathSplitRecursive() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_SPLIT_RECURSIVE);
    }

    @Test
    public void testMathSplitRecursiveLabeled() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_SPLIT_RECURSIVE_LABELED);
    }

    @Test
    public void testMathSplitRightSideExpression() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_SPLIT_RIGHT_SIDE_EXPRESSION);
    }

    @Test
    public void testMathSplitRightSideRecursiveExpression() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.MATH_SPLIT_RIGHT_SIDE_RECURSIVE_EXPRESSION);
    }

    @Test
    public void testNestedMaps() throws Throwable {
        testAlternativeIndices(AntlrSampleFiles.NESTED_MAPS);
    }

    public void testAlternativeIndices(AntlrSampleFiles file) throws Throwable {
        System.out.println(file.name());
        withAlternativesInfo(file, (virtualGrammarPath, ext, generationResult, altInfos, sr, ours) -> {
            String text = generationResult.jfs().get(StandardLocation.SOURCE_PATH, virtualGrammarPath)
                    .getCharContent(false).toString();

            assertEquals(sr.size(), ours.size(), file.name() + ":Analysis returned a different number of "
                    + "alternatives than Antlr's parser did.");

            for (int i = 0; i < sr.size(); i++) {
                SemanticRegion<RuleAlt> realRegion = sr.forIndex(i);
                SemanticRegion<AlternativeKey> ourRegion = ours.forIndex(i);
                RuleAlt rr = realRegion.key();
                AlternativeKey ok = ourRegion.key();

                assertEquals(rr.rule, ok.rule(), file.name() + ": Alternative indices differ"
                        + " in " + realRegion.key() + " vs. " + ourRegion.key()
                        + " index " + i);
                assertEquals(rr.altIndex, ok.alternativeIndex(), file.name() + ": wrong alternative index "
                        + rr + " vs " + ok);

                RangeRelation rel = ourRegion.relationTo(realRegion);
                switch (rel) {
                    case EQUAL:
                    case CONTAINS:
                        // ok;
                        break;
                    default:
                        StringBuilder sb = new StringBuilder(file.name() + ":\n");
                        sb.append("OURS:\n");
                        ours.forEach(reg -> {
                            sb.append(reg.key().toString()).append(" ").append(reg.start())
                                    .append(":").append(reg.end()).append('\n');
                            String txt = Escaper.CONTROL_CHARACTERS.escape(
                                    text.substring(reg.start(), reg.end()));
                            sb.append("'").append(txt).append("'").append('\n');
                        });

                        sb.append("\n\nANTLR's:\n");
                        sr.forEach(reg -> {
                            sb.append(reg.key()).append(" ").append(reg.start())
                                    .append(":").append(reg.end()).append('\n');
                            String txt = Escaper.CONTROL_CHARACTERS.escape(
                                    text.substring(reg.start(), reg.end()));
                            sb.append("'").append(txt).append("'").append('\n');
                        });

                        fail(file.name() + ": Regions at " + i + " have an unexpected containment "
                                + "relationship - the region from Antlr's GrammarRootAST "
                                + "should be within or equal to the region we extracted, "
                                + "but the relationship to the extracted one is " + rel
                                + ".\nExtracted offsets: " + ourRegion.start() + ":" + ourRegion.end()
                                + " \nAntlr got offsets: " + realRegion.start() + ":" + realRegion.end()
                                + "\n" + sb);
                }
            }
        });
        System.out.println(file.name() + " OK.");
    }

    static void withAlternativesInfo(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile,
            ThrowingSextaConsumer<UnixPath, Extraction, AntlrGenerationResult, AlternativesInfo, SemanticRegions<RuleAlt>, SemanticRegions<AlternativesExtractors.AlternativeKey>> c) throws Throwable {
        withSampleFile(sampleFile, (UnixPath virtualSourcePath, Extraction extraction,
                AntlrGenerationResult generationResult) -> {
            JFS jfs = generationResult.originalJFS();
            try {
                assertNotNull(extraction);
                assertNotNull(generationResult);
                AlternativesInfo altInfos = generationResult.alterantives();
                assertNotNull(altInfos);

                SemanticRegions<RuleAlt> altInfoRegions = altInfos.convert((starts, ends, alts) -> {
                    return SemanticRegions.create(RuleAlt.class, starts, ends, alts, alts.size());
                });

                SemanticRegions<AlternativesExtractors.AlternativeKey> extractedAltRegions
                        = extraction.regions(AlternativesExtractors.OUTER_ALTERNATIVES_WITH_SIBLINGS);
                assertNotNull(extractedAltRegions);

                c.accept(virtualSourcePath, extraction, generationResult, altInfos,
                        altInfoRegions, extractedAltRegions);
            } finally {
                jfs.close();
            }
        });
    }

    static int projectIndex;

    static void withSampleFile(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sampleFile,
            ThrowingTriConsumer<UnixPath, Extraction, AntlrGenerationResult> c) throws Exception, Throwable {

        int ix = projectIndex++;

        UnixPath virtualGrammarPath = UnixPath.get("foo/bar/" + sampleFile.fileName());
        String gname = virtualGrammarPath.rawName();
        String timestamp = Long.toString(System.currentTimeMillis(), 36);

        String projectName = gname + "-" + ix;
        GeneratedMavenProject generatedProject = ProjectTestHelper.projectBuilder()
                .addMainAntlrSource(virtualGrammarPath.toString(), sampleFile)
                .build(gname + "-" + ix + "-" + timestamp);

        generatedProject.deletedBy(shutdown);
        Path originalGrammarFile = generatedProject.get(sampleFile.fileName());

        Extraction ext = Extractors.getDefault().extract(ANTLR_MIME_TYPE,
                GrammarSource.find(originalGrammarFile, ANTLR_MIME_TYPE),
                GrammarFileContext.class);
        assertNotNull(ext, "Null extraction");

        JFS jfs = JFS.builder().withCharset(UTF_8).build();
        JFSFileObject fo = jfs.masquerade(originalGrammarFile, SOURCE_PATH, virtualGrammarPath, UTF_8);

        assertNotNull(fo);
        assertEquals(virtualGrammarPath, fo.path(), "Wrong path");
        AntlrGenerator gen = AntlrGenerator.builder(() -> jfs)
                .withOriginalFile(originalGrammarFile)
                .generateIntoJavaPackage(virtualGrammarPath.getParent().toString('.'))
                .generateAllGrammars(true)
                .generateDependencies(true)
                .requestAlternativesAnalysis()
                .withTokensHash(ext.tokensHash())
                .building(virtualGrammarPath.getParent());

        AntlrGenerationResult res = gen.run(virtualGrammarPath.rawName(), Streams.nullPrintStream(), true);
        assertNotNull(res, "No generation result");
        res.rethrow();
        assertTrue(res.isUsable(), "Bad generation result: " + res);
        c.accept(virtualGrammarPath, ext, res);
    }

    @BeforeAll
    public static void setup() throws Throwable {
        TestFixtures fixtures = DynamicLanguagesTest.initAntlrTestFixtures(false);
        shutdown.andAlways(fixtures.build());
    }

    @AfterAll
    public static void tearDown() throws Throwable {
        shutdown.run();
    }
}
