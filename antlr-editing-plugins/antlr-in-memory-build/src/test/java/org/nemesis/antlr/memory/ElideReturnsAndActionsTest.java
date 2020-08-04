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
package org.nemesis.antlr.memory;

import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.memory.TokenVocabImportTest.loadRelativeDocument;
import org.nemesis.jfs.JFS;

/**
 *
 * @author Tim Boudreau
 */
public class ElideReturnsAndActionsTest {

    private static final String PKG = "com.woogle";
    private static final String PKG_PATH = PKG.replace('.', '/');
    private static final String GRAMMAR_NAME = "ReturnsTest.g4";
    private static final UnixPath GRAMMAR_PATH = UnixPath.get(PKG_PATH + "/" + GRAMMAR_NAME);
    private static final UnixPath PACKAGE_PATH = UnixPath.get(PKG_PATH);
    private AntlrGeneratorBuilder<AntlrGenerator> bldr;

    @Test
    public void test() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            AntlrGenerationResult parserResult = bldr.generateIntoJavaPackage(PKG).generateDependencies(true)
                    .generateListener(true).generateVisitor(true)
                    .building(PACKAGE_PATH, PACKAGE_PATH)
                    .run(GRAMMAR_NAME, ps, true);

            assertNotNull(parserResult);
            assertTrue(parserResult.isSuccess(), parserResult::toString);

            assertTrue(parserResult.errors.isEmpty(), parserResult.errors::toString);
            assertTrue(parserResult.isUsable(), parserResult::toString);
            Set<String> jfsContents = new HashSet<>();
            Set<String> generatedFilesAccordingToParserResult = new HashSet<>();
            parserResult.jfs.listAll((loc, file) -> {
                System.out.println('"' + file.getName() + "\",");
                jfsContents.add(file.getName());
            });
            parserResult.newlyGeneratedFiles.forEach((nm )-> {
                System.out.println(nm.path());
//                assertFalse(nm.path().toString().endsWith(".g4"), nm.toString());
                generatedFilesAccordingToParserResult.add(nm.path().toString());
            });
            String parserCode = parserResult.jfs.get(StandardLocation.SOURCE_PATH, UnixPath.get("com/woogle/ReturnsTestParser.java")).getCharContent(false).toString();
            assertFalse(parserCode.contains("parseInt"));
            System.out.println(parserCode);
        } catch (Exception | Error ex) {
            String out = new String(baos.toByteArray(), UTF_8);
            AssertionError err = new AssertionError("Build output: " + out, ex);
            throw err;
        }
    }

    @BeforeEach
    public void setup() throws IOException, BadLocationException {
        JFS jfs = JFS.builder().build();
        jfs.masquerade(loadRelativeDocument(GRAMMAR_NAME), StandardLocation.SOURCE_PATH, GRAMMAR_PATH);
        bldr = AntlrGenerator.builder(() -> jfs)
                .grammarSourceInputLocation(StandardLocation.SOURCE_PATH)
                .javaSourceOutputLocation(StandardLocation.SOURCE_PATH)
                .withOriginalFile(Paths.get("ElideReturnsAndActionsTest"))
                .withTokensHash("-yyy-")
                .generateIntoJavaPackage(PKG);
    }
}
