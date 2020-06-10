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

import static com.mastfrog.util.collections.CollectionUtils.setOf;
import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashSet;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class TokenVocabImportTest {

    private static final String PKG = "com.poozle";
    private static final String PKG_PATH = PKG.replace('.', '/');

    private static final String LEXER_NAME = "MarkdownLexer.g4";
    private static final String PARSER_NAME = "MarkdownParser.g4";
    private static final UnixPath PARSER_PATH = UnixPath.get(PKG_PATH + "/" + PARSER_NAME);
    private static final UnixPath LEXER_PATH = UnixPath.get(PKG_PATH + "/" + LEXER_NAME);
    private static final UnixPath PACKAGE_PATH = UnixPath.get(PKG_PATH);

    private AntlrGeneratorBuilder<AntlrGenerator> bldr;
    private static final Set<String> EXPECTED_JFS_CONTENTS = setOf("com/poozle/MarkdownLexer.tokens",
            "com/poozle/MarkdownParser.java",
            "com/poozle/MarkdownParserVisitor.java",
            "com/poozle/MarkdownParserListener.java",
            "com/poozle/MarkdownParserBaseListener.java",
            "com/poozle/MarkdownLexer.interp",
            "com/poozle/MarkdownLexer.java",
            "com/poozle/MarkdownParserBaseVisitor.java",
            "com/poozle/MarkdownLexer.g4",
            "com/poozle/MarkdownParser.g4",
            "com/poozle/MarkdownParser.tokens",
            "com/poozle/MarkdownParser.interp");

    @Test
    public void testLexerGrammarsAreGeneratedOnDemandWhenBuildingParserGrammar() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos)) {
            AntlrGenerationResult parserResult = bldr.generateIntoJavaPackage(PKG).generateDependencies(true)
                    .generateListener(true).generateVisitor(true)
                    .building(PACKAGE_PATH, PACKAGE_PATH)
                    .run(PARSER_NAME, ps, true);

            assertNotNull(parserResult);
            assertTrue(parserResult.isSuccess(), parserResult::toString);
            JFSFileObject tokensFile = parserResult.jfs.get(StandardLocation.SOURCE_PATH,
                    UnixPath.get("com/poozle/MarkdownLexer.tokens"));
            assertNotNull(tokensFile);
            assertEquals(2, parserResult.allGrammars.size(), parserResult.allGrammars::toString);

            assertTrue(parserResult.newlyGeneratedFiles.contains(tokensFile), parserResult.newlyGeneratedFiles::toString);
            assertTrue(parserResult.errors.isEmpty(), parserResult.errors::toString);
            assertTrue(parserResult.isUsable(), parserResult::toString);
            Set<String> jfsContents = new HashSet<>();
            Set<String> generatedFilesAccordingToParserResult = new HashSet<>();
            parserResult.jfs.listAll((loc, file) -> {
                System.out.println('"' + file.getName() + "\",");
                jfsContents.add(file.getName());
            });
            parserResult.newlyGeneratedFiles.forEach(jfsFo -> {
                String nm = jfsFo.getName();
                assertFalse(nm.endsWith(".g4"), nm);
                generatedFilesAccordingToParserResult.add(nm);
            });
            Set<String> expectedGenerated = new HashSet<>(EXPECTED_JFS_CONTENTS);
            expectedGenerated.remove("com/poozle/MarkdownLexer.g4");
            expectedGenerated.remove("com/poozle/MarkdownParser.g4");

            assertEquals(EXPECTED_JFS_CONTENTS, jfsContents);
            assertEquals(expectedGenerated, generatedFilesAccordingToParserResult);
        } catch (Exception | Error ex) {
            String out = new String(baos.toByteArray(), UTF_8);
            AssertionError err = new AssertionError("Build output: " + out, ex);
            throw err;
        }
    }

    @BeforeEach
    public void setup() throws IOException, BadLocationException {
        JFS jfs = JFS.builder().build();
        jfs.masquerade(loadRelativeDocument(LEXER_NAME), StandardLocation.SOURCE_PATH, LEXER_PATH);
        jfs.masquerade(loadRelativeDocument(PARSER_NAME), StandardLocation.SOURCE_PATH, PARSER_PATH);
        bldr = AntlrGenerator.builder(jfs)
                .grammarSourceInputLocation(StandardLocation.SOURCE_PATH)
                .javaSourceOutputLocation(StandardLocation.SOURCE_PATH)
                .generateIntoJavaPackage(PKG);
    }

    private static Document loadRelativeDocument(String name) throws IOException, BadLocationException {
        try (InputStream in = TokenVocabImportTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name + " not adjacent to " + TokenVocabImportTest.class.getName() + " on classpath");
            DefaultStyledDocument doc = new DefaultStyledDocument();
            doc.insertString(0, Streams.readUTF8String(in), null);
            return doc;
        }
    }
}
