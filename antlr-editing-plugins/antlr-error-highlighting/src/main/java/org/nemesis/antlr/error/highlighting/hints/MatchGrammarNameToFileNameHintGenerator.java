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
package org.nemesis.antlr.error.highlighting.hints;

import org.nemesis.antlr.error.highlighting.spi.AntlrHintGenerator;
import org.nemesis.antlr.error.highlighting.spi.NonHighlightingHintGenerator;
import java.util.Objects;
import java.util.Optional;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - the file name",
    "# {1} - the grammar name as found in the grammar file",
    "fileAndGrammarMismatch=File name {0} does not match grammar name {1}",
    "# {0} - the file name",
    "# {1} - the grammar name as found in the grammar file",
    "replaceGrammarName=Replace {0} with {1}"
})
@ServiceProvider(service=AntlrHintGenerator.class)
public final class MatchGrammarNameToFileNameHintGenerator extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction,
            AntlrGenerationResult res, ParseResultContents populate, Fixes fixes,
            Document doc, PositionFactory positions) throws BadLocationException {
        SingletonEncounters<GrammarDeclaration> grammarDecls = extraction.singletons(AntlrKeys.GRAMMAR_TYPE);
        if (grammarDecls.hasEncounter()) {
            SingletonEncounters.SingletonEncounter<GrammarDeclaration> decl = grammarDecls.first();
            PositionRange rng = positions.range(decl);
            assert decl != null;
            Optional<FileObject> file = extraction.source().lookup(FileObject.class);
            if (file != null) {
                String name = file.get().getName();
                if (!Objects.equals(name, decl.get().name())) {
                    String msg = Bundle.fileAndGrammarMismatch(name, decl.get().name());
                    String errId = "bad-name-" + name;
                    fixes.ifUnusedErrorId(errId, () -> {
                        fixes.addError(errId, rng,
                                msg, fixen -> {
                                    fixen.addFix(() -> Bundle.replaceGrammarName(decl.get().name(),
                                    extraction.source().name()), bag -> {
                                        bag.replace(rng, name);
                                    });
                                });
                    });
                }
            }
        }
    }
}
