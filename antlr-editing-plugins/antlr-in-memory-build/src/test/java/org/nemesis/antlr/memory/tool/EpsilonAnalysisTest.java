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
package org.nemesis.antlr.memory.tool;

import org.nemesis.antlr.memory.tool.ext.EpsilonRuleInfo;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.StandardLocation;
import org.antlr.v4.tool.Grammar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.antlr.memory.tool.ext.ProblematicEbnfInfo;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.nio.BlockStorageKind;

/**
 *
 * @author Tim Boudreau
 */
public class EpsilonAnalysisTest {

    private String grammarText;
    private final UnixPath GRAMMAR = UnixPath.get("com/mastfrog/Epsilon.g4");
    private final UnixPath GRAMMAR2 = UnixPath.get("com/mastfrog/Epsilons2.g4");
    private final UnixPath GRAMMAR3 = UnixPath.get("com/mastfrog/SyntaxErrors.g4");
    private final UnixPath GRAMMAR4 = UnixPath.get("com/mastfrog/Epsilons3.g4");
    private final UnixPath GRAMMAR_DIR = GRAMMAR.getParent();
    private JFSFileObject fo;
    private JFS jfs;
    private JFSFileObject fo2;
    private JFS jfs2;
    private String grammarText2;
    private JFS jfs3;
    private String grammarText3;
    private JFSFileObject fo3;
    private JFS jfs4;
    private String grammarText4;
    private JFSFileObject fo4;

    @Test
    public void testAnalysis() throws UnsupportedEncodingException {
        PrintIt printit = new PrintIt();
        MemoryTool tool = ToolContext.create(GRAMMAR_DIR, jfs, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, printit);
        Grammar g = tool.loadGrammar(GRAMMAR.getFileName().toString());
        assertNotNull(g);

        assertTrue(tool.hasEpsilonIssues(), "Epsilon problems should have been detected");

        List<ParsedAntlrError> errors = tool.errors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());

//        System.out.println("output:\n********************\n" + printit + "\n***********************\n");
//        System.out.println("Errors:");
        for (ParsedAntlrError pae : errors) {
//            System.out.println("\n" + pae);
            String el = grammarLine(pae.lineNumber() - 1);
//            System.out.println("FULL: '" + el + "'");
            el = el.substring(pae.lineOffset());
            String substring = grammarText.substring(pae.fileOffset(), pae.fileOffset() + pae.length());
        }

        List<String> texts = new ArrayList<>();
        for (EpsilonRuleInfo e : tool.epsilonIssues()) {
//            System.out.println(e);
            texts.add(e.problem().text());
        }
        assertEquals(Arrays.asList("Hex*", "(ModA|ModB|ModC)*"), texts);
    }

    @Test
    public void testOffsets() throws Exception {
        PrintIt printit = new PrintIt();
        MemoryTool tool = ToolContext.create(GRAMMAR_DIR,
                jfs2, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, printit);

        Grammar g = tool.loadGrammar(GRAMMAR2.getFileName().toString());
        System.out.println(printit);
        assertNotNull(g);

        assertTrue(tool.hasEpsilonIssues(), "Epsilon problems should have been detected");

        List<ParsedAntlrError> errors = tool.errors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        List<ProblematicEbnfInfo> problems = new ArrayList<>();
        Map<ProblematicEbnfInfo, ParsedAntlrError> errorForProblem = new HashMap<>();
        for (ParsedAntlrError pae : errors) {
            EpsilonRuleInfo eri = pae.info(EpsilonRuleInfo.class);
            if (eri != null) {
                ProblematicEbnfInfo p = eri.problem();
                if (p != null) {
                    problems.add(p);
                    errorForProblem.put(p, pae);
//                    System.out.println(pae + " - " + p);
                }
            }
        }
        List<String> problemText = new ArrayList<>();
        assertFalse(problems.isEmpty());
        for (ProblematicEbnfInfo p : problems) {
            problemText.add(p.text());
        }
// [lines=DESCRIPTION*, K_NAMESPACE?, foo(K_NAMESPACE?|foo)?, DESC_DELIMITER?, K_NAMESPACE?, desc?
        for (int i = 0; i < problemText.size(); i++) {
            String curr = problemText.get(i);
            String msg = "Problem text not extracted correctly for problem " + i + " in "
                    + problemText;
            switch (i) {
                case 0:
                    assertEquals("lines=DESCRIPTION*", curr, msg);
                    break;
                case 1:
                    assertEquals("K_NAMESPACE?", curr, msg);
                    break;
                case 2:
                    assertEquals("foo(K_NAMESPACE?|foo)?", curr, msg);
                    break;
                case 3:
                    assertEquals("DESC_DELIMITER?", curr, msg);
                    break;
                case 4:
                    assertEquals("K_NAMESPACE?", curr, msg);
                    break;
                case 5:
                    assertEquals("desc?", curr, msg);
                    break;
                default:
                    fail("Unexpected problem: " + i
                            + ": " + curr + " in " + problemText);
            }
        }
    }

    @Test
    public void testSyntaxErrorPositions() throws Exception {
        PrintIt printit = new PrintIt();
        MemoryTool tool = ToolContext.create(GRAMMAR_DIR,
                jfs3, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, printit);

        Grammar g = tool.loadGrammar(GRAMMAR3.getFileName().toString());
//        System.out.println(printit);
        assertNotNull(g);

        List<ParsedAntlrError> errors = tool.errors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        List<String> all = new ArrayList<>(errors.size());
        for (ParsedAntlrError pae : errors) {
            String substring = grammarText3.substring(pae.fileOffset(), pae.fileOffset() + pae.length());
            all.add(substring);
        }
        assertEquals(Arrays.asList("!::%", "plork", "7739", "[[gurk}}]]"), all);
    }

    @Test
    public void testLexerRuleEpsilons() throws Exception {
        PrintIt printit = new PrintIt();
        MemoryTool tool = ToolContext.create(GRAMMAR_DIR,
                jfs4, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, printit);

        Grammar g = tool.loadGrammar(GRAMMAR4.getFileName().toString());
        assertNotNull(g);

        assertTrue(tool.hasEpsilonIssues(), "Epsilon problems should have been detected");

        List<ParsedAntlrError> errors = tool.errors();
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
        List<ProblematicEbnfInfo> problems = new ArrayList<>();
        Map<ProblematicEbnfInfo, ParsedAntlrError> errorForProblem = new HashMap<>();
        for (ParsedAntlrError pae : errors) {
            EpsilonRuleInfo eri = pae.info(EpsilonRuleInfo.class);
            if (eri != null) {
                ProblematicEbnfInfo p = eri.problem();
                if (p != null) {
                    problems.add(p);
                    errorForProblem.put(p, pae);
                }
            }
//            System.out.println(pae);
        }
        List<String> problemText = new ArrayList<>();
        assertFalse(problems.isEmpty());
        for (ProblematicEbnfInfo p : problems) {
            problemText.add(p.text());
        }
        for (int i = 0; i < problems.size(); i++) {
            String curr = problems.get(i).text();
            String msg = "Wrong problem at " + i + ": " + curr + " in " + problemText;
            switch (i) {
                case 0:
                    assertEquals("GOOP*", curr, msg);
                    break;
                case 1:
                    assertEquals("lines=DESCRIPTION*", curr, msg);
                    break;
                case 2:
                    assertEquals("(DESC_DELIMITER?TRUE*)*", curr, msg);
                    break;
                case 3:
                    assertEquals("DESC_DELIMITER?", curr, msg);
                    break;
                case 4:
                    assertEquals("DESC_DELIMITER?TRUE*", curr, msg);
                    break;
                case 5:
                    assertEquals("desc?", curr, msg);
                    break;
                case 6:
                    assertEquals("OP?", curr, msg);
                    break;
                default:
                    fail("Unexpected problem: " + i + ": " + curr
                            + " in " + problemText);
            }
        }
    }

    private String grammarLine(int line) {
        String[] s = grammarText.split("\n");
        return s[line];
    }

    @BeforeEach
    public void setup() throws IOException {
        jfs = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withCharset(UTF_8).build();
        grammarText = Streams.readResourceAsUTF8(EpsilonAnalysisTest.class, "Epsilon.g4");
        assertNotNull(grammarText, "Grammar not found in resources in "
                + EpsilonAnalysisTest.class.getPackage());
        fo = jfs.create(GRAMMAR, StandardLocation.SOURCE_PATH, grammarText);
        assertNotNull(fo);

        jfs2 = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withCharset(UTF_8).build();
        grammarText2 = Streams.readResourceAsUTF8(EpsilonAnalysisTest.class, "Epsilons2.g4");
        assertNotNull(grammarText2, "Grammar not found in resources in "
                + EpsilonAnalysisTest.class.getPackage());
        fo2 = jfs2.create(GRAMMAR2, StandardLocation.SOURCE_PATH, grammarText2);
        assertNotNull(fo2);

        jfs3 = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withCharset(UTF_8).build();
        grammarText3 = Streams.readResourceAsUTF8(EpsilonAnalysisTest.class, "SyntaxErrors.g4");
        assertNotNull(grammarText3, "Grammar not found in resources in "
                + EpsilonAnalysisTest.class.getPackage());
        fo3 = jfs3.create(GRAMMAR3, StandardLocation.SOURCE_PATH, grammarText3);
        assertNotNull(fo3);

        jfs4 = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withCharset(UTF_8).build();
        grammarText4 = Streams.readResourceAsUTF8(EpsilonAnalysisTest.class, "Epsilons3.g4");
        assertNotNull(grammarText4, "Grammar not found in resources in "
                + EpsilonAnalysisTest.class.getPackage());
        fo4 = jfs4.create(GRAMMAR4, StandardLocation.SOURCE_PATH, grammarText4);
        assertNotNull(fo4);
    }

    private static final class PrintIt extends PrintStream {

        PrintIt() throws UnsupportedEncodingException {
            this(new ByteArrayOutputStream());
        }

        PrintIt(ByteArrayOutputStream out) throws UnsupportedEncodingException {
            super(out, true, "UTF-8");
        }

        @Override
        public String toString() {
            ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
            return new String(baos.toByteArray(), UTF_8);
        }
    }
}
