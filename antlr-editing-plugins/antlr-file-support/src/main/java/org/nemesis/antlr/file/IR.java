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

import javax.swing.text.StyledDocument;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.refactoring.InstantRenameAction;
import org.nemesis.antlr.refactoring.RenameParticipant;
import org.nemesis.antlr.refactoring.spi.RenamePostProcessor;
import org.nemesis.antlr.refactoring.spi.RenameQueryResult;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.charfilter.CharPredicates;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
public class IR
        extends RenameParticipant<RuleTypes, NameReferenceSetKey<RuleTypes>, NamedSemanticRegion<RuleTypes>, NamedRegionReferenceSets<RuleTypes>>
        implements /*RenameAugmenter */
        RenamePostProcessor {

    @NbBundle.Messages("in-place-refactoring=&Rename")
    @EditorActionRegistration(mimeType = ANTLR_MIME_TYPE, noIconInMenu = true, category
            = "Refactoring", name = "in-place-refactoring")
    public static InstantRenameAction inplaceRename() {
        return InstantRenameAction.builder()
                .add(AntlrKeys.RULE_NAME_REFERENCES, new IR(), CharFilter.excluding(CharPredicates.JAVA_IDENTIFIER_START, CharPredicates.JAVA_IDENTIFIER_PART))
                .add(AntlrKeys.GRAMMAR_TYPE, new RenameGrammarParticipant())
                .build();
    }

    @MimeRegistration(mimeType = ANTLR_MIME_TYPE, service
            = HighlightsLayerFactory.class)
    public static HighlightsLayerFactory highlights() {
        return InstantRenameAction.highlightsFactory();
    }

    @Override
    protected RenameQueryResult isRenameAllowed(Extraction ext, NameReferenceSetKey<RuleTypes> key, NamedSemanticRegion<RuleTypes> item, NamedRegionReferenceSets<RuleTypes> collection, int caretOffset, String identifier) {
        return super.proceedAndPostProcessWith(this);
    }

    public void nameUpdated(String orig, String newName, StyledDocument doc) {
        System.out.println("UPDATED: '" + orig + "' -> '" + newName + "'");
    }

    @Override
    public void cancelled() {
        System.out.println("CANCELLED");
    }

    public void onRenameCompleted(String original, String nue, Runnable undo) {
        System.out.println("COMPLETED: '" + original + "' -> '" + nue + "' - undo " + undo);
    }

    static class RenameGrammarParticipant extends RenameParticipant<GrammarDeclaration, SingletonKey<GrammarDeclaration>, SingletonEncounter<GrammarDeclaration>, SingletonEncounters<GrammarDeclaration>> {

        @Override
        protected RenameQueryResult isRenameAllowed(Extraction ext, SingletonKey<GrammarDeclaration> key, SingletonEncounter<GrammarDeclaration> item, SingletonEncounters<GrammarDeclaration> collection, int caretOffset, String identifier) {
            return super.useRefactoringAPI();
        }

    }
}
