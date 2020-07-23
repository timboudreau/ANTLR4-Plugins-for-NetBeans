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
package org.nemesis.antlr.project;

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.nemesis.antlr.project.ProjectTestHelper.findAntlrGrammarProject;
import org.nemesis.antlr.project.impl.HeuristicFoldersHelperImplementation;
import org.netbeans.junit.MockServices;
import org.netbeans.modules.maven.NbMavenProjectFactory;

public class FileQueryTest {

    private Path dir;
    private Path antlrGrammarProjectRoot;
    private Path antlrParserGrammar;
    private Path antlrLexerGrammar;
    private Path antlrIncludeFile;

    @Test
    public void testFilesAreResolved() {
        Optional<Path> resolved = FileQuery.find("ANTLRv4").searchingParentOfSearchedForFile()
                .forPathsIn(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS)
                .relativeTo(antlrLexerGrammar);

        assertTrue(resolved.isPresent(), "Resolving lexer grammar from parser grammar searching file parent");
        assertEquals(antlrParserGrammar, resolved.get(),
                "Resolving lexer grammar from parser grammar searching file "
                + "parent succeeded, but with wrong file");

        resolved = FileQuery.find("ANTLRv4")
                .forPathsIn(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS)
                .relativeTo(antlrLexerGrammar);
        assertFalse(resolved.isPresent(), "Should not have found grammar "
                + "without searching siblings");

        resolved = FileQuery.find("LexBasic")
                .forPathsIn(Folders.ANTLR_GRAMMAR_SOURCES, Folders.ANTLR_IMPORTS)
                .relativeTo(antlrLexerGrammar);
        assertTrue(resolved.isPresent(), "Resolving include file relative to"
                + " grammar failed");
        assertEquals(antlrIncludeFile, resolved.get(), "Found wrong file "
                + "for include");
    }

    @BeforeEach
    public void setup() throws IOException, URISyntaxException {
        MockServices.setServices(NbMavenProjectFactory.class,
                HeuristicFoldersHelperImplementation.HeuristicImplementationFactory.class);
        dir = FileUtils.newTempDir("FileQueryTest");
        antlrGrammarProjectRoot = findAntlrGrammarProject();

        antlrParserGrammar = antlrGrammarProjectRoot
                .resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4.g4");
        antlrLexerGrammar = antlrGrammarProjectRoot
                .resolve("src/main/antlr4/org/nemesis/antlr/ANTLRv4Lexer.g4");
        antlrIncludeFile = antlrGrammarProjectRoot
                .resolve("src/main/antlr4/imports/LexBasic.g4");
        for (Path p : new Path[]{antlrGrammarProjectRoot, antlrParserGrammar, antlrLexerGrammar, antlrIncludeFile}) {
            assertTrue(Files.exists(p), "Missing:  + p");
        }
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deltree(dir);
    }
}
