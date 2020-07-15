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
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.strings.LevenshteinDistance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.attribution.ImportFinder;
import org.openide.filesystems.FileObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - replacement grammar name",
    "replaceWith=Replace with {0}",
    "# {0} - replacement grammar name",
    "deleteImport=Delete import of {0}"
})
@ServiceProvider(service = AntlrHintGenerator.class)
public final class SuggestImportHintGenerator extends NonHighlightingHintGenerator {

    @Override
    protected void generate(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes, Document doc, PositionFactory positions) throws BadLocationException {
        ImportFinder imf = ImportFinder.forMimeType(AntlrConstants.ANTLR_MIME_TYPE);
        Set<NamedSemanticRegion<?>> unresolved = new HashSet<>();
        imf.allImports(extraction, unresolved);
        if (!unresolved.isEmpty()) {
            LOG.log(Level.FINER, "Found unresolved imports {0} in {1}",
                    new Object[]{unresolved, extraction.source()});
            for (NamedSemanticRegion<?> n : unresolved) {
                PositionRange rng = positions.range(n);
                try {
                    addFixImportError(extraction, fixes, n, rng);
                } catch (BadLocationException ble) {
                    LOG.log(Level.WARNING, "Exception adding hint for unresolved: " + n, ble);
                }
            }
        }
    }

    private <T extends Enum<T>> void addFixImportError(Extraction extraction, Fixes fixes, NamedSemanticRegion<T> reg, PositionRange rng) throws BadLocationException {
        String target = reg.name();
        LOG.log(Level.FINEST, "Try to add import fix of {0} in {1} for {2}",
                new Object[]{target, extraction.source(), reg});
        String errId = "unresolv:" + target;
        fixes.ifUnusedErrorId(errId, () -> {
            fixes.addError(errId, reg, Bundle.unresolved(reg.name()), fixer -> {
                Optional<FileObject> ofo = extraction.source().lookup(FileObject.class);
                if (ofo.isPresent()) {
                    FileObject fo = ofo.get();
                    Map<String, FileObject> nameForGrammar = new HashMap<>();
                    Iterable<FileObject> allAntlrFilesInProject = CollectionUtils.concatenate(
                            Folders.ANTLR_GRAMMAR_SOURCES.allFiles(fo),
                            Folders.ANTLR_IMPORTS.allFiles(fo)
                    );

                    int ct = 0;
                    for (FileObject possibleImport : allAntlrFilesInProject) {
                        ct++;
                        if (possibleImport.equals(fo)) {
                            LOG.log(Level.FINEST, "Skip possible import of self {0}",
                                    possibleImport);
                            continue;
                        }
                        LOG.log(Level.FINEST, "Add possible import: {0}",
                                possibleImport.getNameExt());
                        nameForGrammar.put(possibleImport.getName(), fo);
                    }
                    LOG.log(Level.FINEST, "Found {0} possible imports for {1}: {2}",
                            new Object[]{ct, extraction.source(), nameForGrammar});

                    List<String> matches = LevenshteinDistance.topMatches(5,
                            target, new ArrayList<>(nameForGrammar.keySet()));
                    if (LOG.isLoggable(Level.FINEST)) {
                        LOG.log(Level.FINEST, "Possible import matches for '{0}' are {1} from {2}",
                                new Object[]{target, matches, nameForGrammar.keySet()});
                    }
                    for (String match : matches) {
                        if (match.equals(target)) {
                            continue;
                        }
                        fixer.addFix(Bundle.replaceWith(match), bag -> {
                            bag.replace(rng, match);
                        });
                    }
                    fixer.addFix(Bundle.deleteImport(target), bag -> {
                        bag.delete(rng);
                    });
                } else {
                    LOG.log(Level.WARNING, "No fileobject could be looked up "
                            + "from {0}", extraction.source());
                }
            });
        });
    }
}
