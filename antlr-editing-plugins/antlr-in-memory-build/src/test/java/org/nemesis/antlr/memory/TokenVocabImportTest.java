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
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.memory.output.ParsedAntlrError;
import org.nemesis.jfs.JFS;

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

    @Test
    public void testGeneration() {
//        AntlrGenerationResult lexerResult = bldr.generateIntoJavaPackage(PKG).generateDependencies(true)
//                .generateListener(true).generateVisitor(true)
//                .building(PACKAGE_PATH, PACKAGE_PATH)
//                .run(LEXER_NAME, System.out, true);
//
//        System.out.println("RESULT: " + lexerResult);
//        System.out.println("GENERATED " + lexerResult.newlyGeneratedFiles);
//
//        for (ParsedAntlrError pae : lexerResult.errors) {
//            System.out.println("\n" + pae);
//        }

        AntlrGenerationResult parserResult = bldr.generateIntoJavaPackage(PKG).generateDependencies(true)
                .generateListener(true).generateVisitor(true)
                .building(PACKAGE_PATH, PACKAGE_PATH)
                .run(PARSER_NAME, System.out, true);

        System.out.println("RESULT: " + parserResult);
        System.out.println("GENERATED " + parserResult.newlyGeneratedFiles);

        for (ParsedAntlrError pae : parserResult.errors) {
            System.out.println("\n" + pae);
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
