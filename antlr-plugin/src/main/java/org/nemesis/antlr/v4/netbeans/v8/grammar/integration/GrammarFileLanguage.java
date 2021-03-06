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
package org.nemesis.antlr.v4.netbeans.v8.grammar.integration;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.extraction.nb.api.AbstractFileObjectGrammarSourceImplementation;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4LanguageHierarchy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys;
import org.nemesis.extraction.Extraction;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import org.nemesis.data.named.NamedRegionReferenceSet;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.api.CodeCompletionHandler;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.InstantRenamer;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.csl.api.OccurrencesFinder;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.SemanticAnalyzer;

import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.netbeans.modules.csl.spi.ParserResult;

import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

@LanguageRegistration(mimeType = ANTLR_MIME_TYPE)
public class GrammarFileLanguage extends DefaultLanguageConfig {

    @Override
    public Language<ANTLRv4TokenId> getLexerLanguage() {
        return ANTLRv4LanguageHierarchy.getLanguage();
    }

    @Override
    public String getDisplayName() {
        return "ANTLR v4 grammar";
    }

    @Override
    public Parser getParser() {
        return new NBANTLRv4Parser();
    }

    @Override
    public SemanticAnalyzer<?> getSemanticAnalyzer() {
//        return new AntlrSemanticAnalyzer();
        return super.getSemanticAnalyzer();
    }

    @Override
    public OccurrencesFinder<?> getOccurrencesFinder() {
        return new OC();
    }

    private static OffsetRange toOffsetRange(IndexAddressableItem reg) {
        return new OffsetRange(reg.start(), reg.end());
    }

    @Override
    public CodeCompletionHandler getCompletionHandler() {
        return super.getCompletionHandler();
    }

    static class OC extends OccurrencesFinder<ANTLRv4ParserResult> {

        private int pos;
        private volatile boolean cancelled;
        private final Map<OffsetRange, ColoringAttributes> occurrences = new HashMap<>();

        @Override
        public void setCaretPosition(int i) {
            this.pos = i;
        }

        @Override
        public Map<OffsetRange, ColoringAttributes> getOccurrences() {
            return occurrences;
        }

        @Override
        public void run(ANTLRv4ParserResult t, SchedulerEvent se) {
            cancelled = false;
            occurrences.clear();
            ANTLRv4SemanticParser sem = t.semanticParser();
            if (sem != null) {
                Extraction ex = sem.extraction();
                if (ex != null) {
                    // First see if the caret is in a rule definition's name
                    NamedSemanticRegion<?> el = ex.namedRegions(AntlrKeys.RULE_NAMES).at(pos);
                    NamedRegionReferenceSets<RuleTypes> refs = ex.references(AntlrKeys.RULE_NAME_REFERENCES);
                    if (el == null && refs != null) {
                        // If not, see if the caret is on a reference name
                        el = refs.at(pos);
                    }
                    if (el != null) {
                        // If we were in a name or reference to one, highlight
                        // its declaration and all references
                        String name = el.name();
                        NamedSemanticRegion<?> decl = ex.namedRegions(AntlrKeys.RULE_NAMES).regionFor(name);
                        if (decl != null) {
                            occurrences.put(toOffsetRange(decl), ColoringAttributes.MARK_OCCURRENCES);
                        }
                        if (refs != null) {
                            // Highlight reference occurrences
                            NamedRegionReferenceSet<RuleTypes> refsToName = refs.references(name);
                            if (refsToName != null && refsToName.size() > 0) {
                                for (NamedSemanticRegionReference<RuleTypes> ref : refsToName) {
                                    occurrences.put(toOffsetRange(ref), ColoringAttributes.MARK_OCCURRENCES);
                                    if (cancelled) {
                                        occurrences.clear();
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        // Not a name, but we might be in a foreign (unresolved) name reference, so
                        // check for that
                        SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = ex.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
                        SemanticRegion<UnknownNameReference<RuleTypes>> reg = unknowns.at(pos);
                        if (reg != null) {
                            List<? extends SemanticRegion<UnknownNameReference<RuleTypes>>> others = unknowns.collect(r -> {
                                return reg.key().name().equals(r.name());
                            });
                            for (SemanticRegion<UnknownNameReference<RuleTypes>> r : others) {
                                occurrences.put(toOffsetRange(r), ColoringAttributes.MARK_OCCURRENCES);
                                if (cancelled) {
                                    occurrences.clear();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public Class<? extends Scheduler> getSchedulerClass() {
            return Scheduler.CURSOR_SENSITIVE_TASK_SCHEDULER;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    @Override
    public boolean hasOccurrencesFinder() {
        return true;
    }

//    @Override
//    public Formatter getFormatter() {
//        return AntlrFormatters.forMimeType(ANTLR_MIME_TYPE);
//    }
//
//    @Override
//    public boolean hasFormatter() {
//        return AntlrFormatters.hasFormatter(ANTLR_MIME_TYPE);
//    }

    @Override
    public DeclarationFinder getDeclarationFinder() {
        return new DecFinder();
    }

    @Override
    public InstantRenamer getInstantRenamer() {
        return new IR();
    }

    private static final class IR implements InstantRenamer {

        @Override
        public boolean isRenameAllowed(ParserResult pr, int pos, String[] strings) {
            if (pr instanceof ANTLRv4ParserResult) {
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    Extraction ext = sem.extraction();
                    NamedSemanticRegion<RuleTypes> elem = ext.namedRegions(AntlrKeys.RULE_NAMES).at(pos);
                    if (elem == null) {
                        elem = ext.nameReferences(AntlrKeys.RULE_NAME_REFERENCES).at(pos);
                    }
                    if (elem != null) {
                        switch (elem.kind()) {
                            case NAMED_ALTERNATIVES:
                                return false;
                            default:
                                return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public Set<OffsetRange> getRenameRegions(ParserResult pr, int pos) {
            if (pr instanceof ANTLRv4ParserResult) {
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    Extraction ext = sem.extraction();
                    NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrKeys.RULE_NAMES);
                    NamedSemanticRegion<RuleTypes> elem = nameds.at(pos);
                    NamedRegionReferenceSets<RuleTypes> refs = ext.nameReferences(AntlrKeys.RULE_NAME_REFERENCES);
                    if (elem == null) {
                        elem = refs.at(pos);
                    }
                    if (elem != null) {
                        Set<OffsetRange> all = new HashSet<>(20);
                        String name = elem.name();
                        NamedSemanticRegion<RuleTypes> decl = nameds.regionFor(name);
                        all.add(toOffsetRange(decl));
                        for (NamedSemanticRegionReference<RuleTypes> r : refs.references(name)) {
                            all.add(toOffsetRange(r));
                        }
                        return all;
                    }
                }
            }
            return Collections.emptySet();
        }
    }

    @Override
    public String getPreferredExtension() {
        return "g4";
    }

    @Override
    public String getLineCommentPrefix() {
        return "//";
    }

    static final class DecFinder implements DeclarationFinder {

        private DeclarationLocation locationFrom(FileObject file, NamedSemanticRegion<RuleTypes> decl) {
            return new DeclarationLocation(file, decl.start(),
                    new EH(decl, file));
        }

        @Override
        public DeclarationLocation findDeclaration(ParserResult pr, int pos) {
            if (pr instanceof ANTLRv4ParserResult) {
                FileObject file = pr.getSnapshot().getSource().getFileObject();
                if (file == null) {
                    return DeclarationLocation.NONE;
                }
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    Extraction extractions = sem.extraction();
                    // First check references
                    NamedRegionReferenceSets<RuleTypes> refs = extractions.references(AntlrKeys.RULE_NAME_REFERENCES);
                    NamedSemanticRegionReference<RuleTypes> ref = refs.at(pos);
                    if (ref != null) {
                        return locationFrom(file, ref.referencing());
                    }
                    SemanticRegions<UnknownNameReference<RuleTypes>> unknowns = extractions.unknowns(AntlrKeys.RULE_NAME_REFERENCES);
                    SemanticRegion<UnknownNameReference<RuleTypes>> unknownRefRegion = unknowns.at(pos);
                    if (unknownRefRegion != null) {
                        UnknownNameReference<RuleTypes> unknownRef = unknownRefRegion.key();
                        AttributedForeignNameReference<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved;
                        try {
                            resolved = unknownRef.resolve(extractions, AntlrExtractor.resolver());
                            if (resolved != null) {
                                FileObject fo = AbstractFileObjectGrammarSourceImplementation.fileObjectFor(resolved.source());
                                if (fo != null) {
                                    NamedSemanticRegion<RuleTypes> refItem = resolved.element();
                                    return locationFrom(fo, refItem);
                                }
                            }
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    NamedSemanticRegions<ImportKinds> imports = extractions.namedRegions(AntlrKeys.IMPORTS);
                    NamedSemanticRegion<ImportKinds> importItem = imports.at(pos);
                    if (importItem != null) {
                        GrammarSource<?> src = extractions.resolveRelative(importItem.name());
                        if (src != null) {
                            FileObject fo = AbstractFileObjectGrammarSourceImplementation.fileObjectFor(src);
                            if (fo != null) {
                                return new DeclarationLocation(fo, 0, new FileStartHandle(fo, importItem.name()));
                            }
                        }
                    }
                }
            }
            return DeclarationLocation.NONE;
        }

        static final class FileStartHandle implements ElementHandle {

            private final FileObject file;
            private final String name;

            public FileStartHandle(FileObject file, String name) {
                this.file = file;
                this.name = name;
            }

            @Override
            public FileObject getFileObject() {
                return file;
            }

            @Override
            public String getMimeType() {
                return file.getMIMEType();
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getIn() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return ElementKind.FIELD;
            }

            @Override
            public Set<Modifier> getModifiers() {
                return EnumSet.of(Modifier.PUBLIC);
            }

            @Override
            public boolean signatureEquals(ElementHandle eh) {
                return eh instanceof FileStartHandle
                        && ((FileStartHandle) eh).name.equals(name)
                        && ((FileStartHandle) eh).file.equals(file);
            }

            @Override
            public OffsetRange getOffsetRange(ParserResult pr) {
                return new OffsetRange(0, 1);
            }
        }

        static final class EH implements ElementHandle {

            private final NamedSemanticRegion<RuleTypes> region;
            private final FileObject file;

            EH(NamedSemanticRegion<RuleTypes> region, FileObject file) {
                this.region = region.snapshot();
                this.file = file;
            }

            @Override
            public FileObject getFileObject() {
                return file;
            }

            @Override
            public String getMimeType() {
                return file.getMIMEType();
            }

            @Override
            public String getName() {
                return region.name();
            }

            @Override
            public String getIn() {
                return null;
            }

            @Override
            public ElementKind getKind() {
                return ElementKind.FIELD;
            }

            @Override
            public Set<Modifier> getModifiers() {
                return EnumSet.of(Modifier.PUBLIC);
            }

            @Override
            public boolean signatureEquals(ElementHandle eh) {
                if (eh instanceof EH) {
                    EH e = (EH) eh;
                    return e.getName().equals(getName());
                }
                return false;
            }

            @Override
            public OffsetRange getOffsetRange(ParserResult pr) {
                if (pr instanceof ANTLRv4ParserResult) {
                    ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                    ANTLRv4SemanticParser sem = res.semanticParser();
                    if (sem != null) {
                        Extraction extraction = sem.extraction();
                        NamedSemanticRegions<RuleTypes> names = extraction.namedRegions(AntlrKeys.RULE_NAMES);
                        if (names.contains(getName())) {
                            return toOffsetRange(names.regionFor(getName()));
                        }
                    }
                }
                return OffsetRange.NONE;
            }
        }

        @Override
        public OffsetRange getReferenceSpan(Document dcmnt, int i) {
            // PENDING : Should detect caret position and if it's a hyperlinkable
            // position, return the span
            return OffsetRange.NONE;
        }
    }
}
