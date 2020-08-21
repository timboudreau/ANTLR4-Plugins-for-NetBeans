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
package org.nemesis.antlr.memory.tool;

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import static java.nio.charset.StandardCharsets.UTF_8;
import javax.tools.StandardLocation;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.antlr.v4.tool.Grammar;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;
import org.nemesis.jfs.nio.BlockStorageKind;

/**
 *
 * @author Tim Boudreau
 */
public class RefiningLeftRecursionDetectorTest {

    private final UnixPath GRAMMAR = UnixPath.get("com/mastfrog/LeftRecursion.g4");
    private JFS jfs;
    private String grammarText;
    private JFSFileObject fo;

    @Test
    public void testAnalysis() throws Exception {
        MemoryTool tool = ToolContext.create(GRAMMAR.getParent(),
                jfs, StandardLocation.SOURCE_PATH, StandardLocation.SOURCE_OUTPUT, Streams.nullPrintStream());
        Grammar g = tool.loadGrammar(GRAMMAR.getFileName().toString());
        assertNotNull(g);
        assertNotNull(g.atn);
//        tool.process(g, true);
        RefiningLeftRecursionDetector ref = new RefiningLeftRecursionDetector(g, g.atn);
        ref.check();
        System.out.println("reult " + ref.listOfRecursiveCycles);
    }

    @BeforeEach
    public void setup() throws IOException {
        jfs = JFS.builder().useBlockStorage(BlockStorageKind.HEAP)
                .withCharset(UTF_8).build();
        grammarText = Streams.readResourceAsUTF8(EpsilonAnalysisTest.class, "LeftRecursion.g4");
        assertNotNull(grammarText, "Grammar " + GRAMMAR.getFileName() + " not found in resources in "
                + EpsilonAnalysisTest.class.getPackage());
        fo = jfs.create(GRAMMAR, SOURCE_PATH, grammarText);
        assertNotNull(fo);

    }

}
