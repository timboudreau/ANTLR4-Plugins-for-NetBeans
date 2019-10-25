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

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.List;
import javax.tools.StandardLocation;
import org.antlr.v4.tool.Grammar;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
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
    private final UnixPath GRAMMAR_DIR = GRAMMAR.getParent();
    private JFSFileObject fo;
    private JFS jfs;

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
        System.out.println("Errors:");
        for (ParsedAntlrError pae : errors) {
            System.out.println("\n" + pae);
            String el = grammarLine(pae.lineNumber() - 1);
            System.out.println("FULL: '" + el + "'");
            el = el.substring(pae.lineOffset());

            System.out.println("LINE: '" + el + "'");

            String substring = grammarText.substring(pae.fileOffset(), pae.fileOffset() + pae.length());

            System.out.println("SUB: '" + substring + "'");
        }

        System.out.println("***************************\nEPSILON ISSUES:");
        for (EpsilonRuleInfo e : tool.epsilonIssues()) {
            System.out.println(e);
            System.out.println("");
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
        assertNotNull(grammarText, "Grammar not found in resources in " + EpsilonAnalysisTest.class.getPackage());
        fo = jfs.create(GRAMMAR, StandardLocation.SOURCE_PATH, grammarText);
        assertNotNull(fo);
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
