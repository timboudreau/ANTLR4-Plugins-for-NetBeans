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
package org.nemesis.antlr.file;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.ANTLRv4Parser.GrammarFileContext;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.impl.AntlrExtractor;
import org.nemesis.antlr.sample.AntlrSampleFiles;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.Extractor;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.simple.SampleFile;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.impl.GSAccessor;
import org.nemesis.source.spi.GrammarSourceImplementation;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractionsTest {

    private ThrowingRunnable shutdown;
    private Extraction extraction;

    @Test
    public void test() {
        NamedRegionReferenceSets<RuleTypes> refs
                = extraction.references(
                        AntlrKeys.RULE_NAME_REFERENCES);

        assertNotNull(refs);
        assertFalse(refs.isEmpty());

        for (NamedRegionReferenceSet<RuleTypes> ref : refs) {
            if (!ref.isEmpty()) {
                System.out.println(ref.name());
                for (NamedSemanticRegionReference<RuleTypes> x : ref) {
                    System.out.println("  Ref " + x);
                }
            }
        }

        SemanticRegions<String> rules = extraction.regions(AntlrKeys_SyntaxTreeNavigator_Registration.TREE_NODES);
        NamedSemanticRegions<RuleTypes> bds = extraction.namedRegions(AntlrKeys.RULE_BOUNDS);
        NamedSemanticRegions<RuleTypes> names = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        assertArrayEquals(bds.nameArray(), names.nameArray());
        for (NamedSemanticRegion<RuleTypes> nsr : names) {
            String nm = nsr.name();
            NamedSemanticRegion<RuleTypes> body = bds.regionFor(nm);
            assertTrue(nsr.start() >= body.start(), "Rule name and rule body start should be the same: " + nsr + " and " + body);
            assertNotEquals(nsr.end(), body.end(), "Rule name and rule body end should NOT be the same: " + nsr + " and " + body);
            assertTrue(nsr.end() < body.end(), "Rule name and rule body end should NOT be the same: " + nsr + " and " + body);
        }
    }

    @BeforeEach
    public void setup() throws IOException {
        shutdown = ThrowingRunnable.oneShot(true);
        ExtractorBuilder<? super GrammarFileContext> extractorbuilder = Extractor.builder(
                GrammarFileContext.class, ANTLR_MIME_TYPE);
        assertNotNull(extractorbuilder);
        AntlrExtractor.populateBuilder(extractorbuilder);
        Extractor<? super GrammarFileContext> extractor = extractorbuilder.build();
        assertNotNull(extractor);
        List<CommonToken> toks = AntlrSampleFiles.SENSORS.tokens();
        ANTLRv4Parser p = AntlrSampleFiles.SENSORS.parser();
        GrammarSource<SampleFile> gs
                = GSAccessor.getDefault().newGrammarSource(new GS(AntlrSampleFiles.SENSORS));

        assertNotNull(gs);
        extraction = extractor.extract(p.grammarFile(), gs, toks);
    }

    @AfterEach
    public void tearDown() throws Throwable {
        if (shutdown != null) {
            shutdown.run();
        }
    }

    static final class GS extends GrammarSourceImplementation<SampleFile> {

        private final SampleFile file;

        public GS(SampleFile file) {
            super(SampleFile.class);
            this.file = file;
        }

        @Override
        public String name() {
            return file.fileName();
        }

        @Override
        public CharStream stream() throws IOException {
            return file.charStream();
        }

        @Override
        public GrammarSourceImplementation<?> resolveImport(String name) {
            SampleFile rel = file.related(name);
            return rel == null ? null : new GS(rel);
        }

        @Override
        public SampleFile source() {
            return file;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String computeId() {
            return (file.getClass().getName() + "." + file.fileName());
        }

        @Override
        public long lastModified() throws IOException {
            return start;
        }
        static final long start = System.currentTimeMillis();
    }
}
