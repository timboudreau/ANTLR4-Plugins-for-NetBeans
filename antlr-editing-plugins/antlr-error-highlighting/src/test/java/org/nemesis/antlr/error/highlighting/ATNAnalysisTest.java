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

import com.mastfrog.util.path.UnixPath;
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.PrintStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import javax.tools.StandardLocation;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.memory.AntlrGenerator;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.jfs.JFS;
import org.nemesis.simple.SampleFile;

/**
 *
 * @author Tim Boudreau
 */
public class ATNAnalysisTest {

    @Test
    public void testAnalysis() throws Throwable {
//        ATNAnalysis ana = analyze(AntlrSampleFiles.MARKDOWN_PARSER, AntlrSampleFiles.MARKDOWN_LEXER);
        ATNAnalysis ana = analyze(AntlrSampleFiles.RUST);
//        ATNAnalysis ana = analyze(AntlrSampleFiles.create(OPT));
        ana.analyze();
    }

    private static ATNAnalysis analyze(SampleFile<ANTLRv4Lexer, ANTLRv4Parser> sample, SampleFile<?, ?>... more) throws Throwable {
        AntlrGenerationResult genRes = parse(sample, more);
        genRes.rethrow();
        assertTrue(genRes.isUsable());
        assertTrue(genRes.isSuccess());
        assertNotNull(genRes);
        System.out.println("GRAMMAR " + genRes.mainGrammar);
        assertNotNull(genRes.mainGrammar);
        ATNAnalysis anal = new ATNAnalysis(genRes.mainGrammar);
        return anal;
    }

    private static AntlrGenerationResult parse(SampleFile<?, ?> sample, SampleFile<?, ?>... more) throws IOException {
        JFS jfs = JFS.builder().withCharset(UTF_8).build();

        UnixPath mainGrammar = UnixPath.get("com/foo/" + sample.fileName());
        jfs.create(mainGrammar, StandardLocation.SOURCE_PATH, sample.text());
        for (SampleFile<?, ?> dep : more) {
            UnixPath depGrammar = UnixPath.get("com/foo/" + dep.fileName());
            jfs.create(depGrammar, StandardLocation.SOURCE_PATH, dep.text());
        }

        AntlrGenerator gen = AntlrGenerator.builder(jfs)
//                .forceATN(true)
//                .generateAtnDot(true)
                .generateIntoJavaPackage("com.foo")
                .generateListener(true)
                .generateVisitor(true)
                .generateAllGrammars(true)
                .grammarSourceInputLocation(StandardLocation.SOURCE_PATH)
                .javaSourceOutputLocation(StandardLocation.SOURCE_OUTPUT)
                .log(true)
                .longMessages(true)
                .building(mainGrammar.getParent());

        return gen.run(sample.fileName(), new PrintStream(Streams.nullOutputStream()), true);
    }

    static final String OPT = "lexer grammar Goober;\n\n"
            + "Thing : Anything | Something | Nothing;\n"
            + "Anything : .;\n"
            + "Something : 'something';\n"
            + "Nothing : [\\r]|[\\n]|[\\t]|[ ];\n";

}
