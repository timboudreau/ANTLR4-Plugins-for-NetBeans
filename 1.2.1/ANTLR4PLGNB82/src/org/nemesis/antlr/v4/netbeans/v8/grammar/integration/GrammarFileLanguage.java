/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4LanguageHierarchy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor.RuleTypes;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.IndexAddressable.IndexAddressableItem;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedRegionExtractorBuilder.UnknownNameReference;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.NamedSemanticRegions.NamedSemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.SemanticRegions.SemanticRegion;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting.AntlrFormatter;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.api.CodeCompletionHandler;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Formatter;
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

@LanguageRegistration(mimeType = "text/x-g4")
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
                    NamedSemanticRegion<?> el = ex.namedRegions(AntlrExtractor.RULE_NAMES).at(pos);
                    NamedSemanticRegions.NamedRegionReferenceSets<AntlrExtractor.RuleTypes> refs = ex.references(AntlrExtractor.RULE_NAME_REFERENCES);
                    if (el == null && refs != null) {
                        // If not, see if the caret is on a reference name
                        el = refs.at(pos);
                    }
                    if (el != null) {
                        // If we were in a name or reference to one, highlight
                        // its declaration and all references
                        String name = el.name();
                        NamedSemanticRegion<?> decl = ex.namedRegions(AntlrExtractor.RULE_NAMES).regionFor(name);
                        if (decl != null) {
                            occurrences.put(toOffsetRange(decl), ColoringAttributes.MARK_OCCURRENCES);
                        }
                        if (refs != null) {
                            // Highlight reference occurrences
                            NamedSemanticRegions.NamedRegionReferenceSets.NamedRegionReferenceSet<AntlrExtractor.RuleTypes> refsToName = refs.references(name);
                            if (refsToName != null && refsToName.size() > 0) {
                                for (NamedSemanticRegions.NamedSemanticRegionReference<AntlrExtractor.RuleTypes> ref : refsToName) {
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
                        SemanticRegions<UnknownNameReference<?>> unknowns = ex.unknowns(AntlrExtractor.RULE_NAME_REFERENCES);
                        SemanticRegion<UnknownNameReference<?>> reg = unknowns.at(pos);
                        if (reg != null) {
                            List<? extends SemanticRegion<UnknownNameReference<?>>> others = unknowns.collect(r -> {
                                return reg.key().name().equals(r.name());
                            });
                            for (SemanticRegion<UnknownNameReference<?>> r : others) {
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

    @Override
    public Formatter getFormatter() {
        return new AntlrFormatter();
    }

    @Override
    public boolean hasFormatter() {
        return true;
    }

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
                    NamedSemanticRegion<RuleTypes> elem = ext.namedRegions(AntlrExtractor.RULE_NAMES).at(pos);
                    if (elem == null) {
                        elem = ext.nameReferences(AntlrExtractor.RULE_NAME_REFERENCES).at(pos);
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
                    NamedSemanticRegions<RuleTypes> nameds = ext.namedRegions(AntlrExtractor.RULE_NAMES);
                    NamedSemanticRegion<RuleTypes> elem = nameds.at(pos);
                    NamedSemanticRegions.NamedRegionReferenceSets<RuleTypes> refs = ext.nameReferences(AntlrExtractor.RULE_NAME_REFERENCES);
                    if (elem == null) {
                        elem = refs.at(pos);
                    }
                    if (elem != null) {
                        Set<OffsetRange> all = new HashSet<>(20);
                        String name = elem.name();
                        NamedSemanticRegion<RuleTypes> decl = nameds.regionFor(name);
                        all.add(toOffsetRange(decl));
                        for (NamedSemanticRegions.NamedSemanticRegionReference<RuleTypes> r : refs.references(name)) {
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

        private DeclarationLocation locationFrom(FileObject file, NamedSemanticRegion decl) {
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
                    NamedSemanticRegions.NamedRegionReferenceSets<RuleTypes> refs = extractions.references(AntlrExtractor.RULE_NAME_REFERENCES);
                    NamedSemanticRegions.NamedSemanticRegionReference<RuleTypes> ref = refs.at(pos);
                    if (ref != null) {
                        return locationFrom(file, ref.referencing());
                    }
                    SemanticRegions<NamedRegionExtractorBuilder.UnknownNameReference<?>> unknowns = extractions.unknowns(AntlrExtractor.RULE_NAME_REFERENCES);
                    SemanticRegion<NamedRegionExtractorBuilder.UnknownNameReference<?>> unknownRefRegion = unknowns.at(pos);
                    if (unknownRefRegion != null) {
                        UnknownNameReference<?> unknownRef = unknownRefRegion.key();
                        NamedRegionExtractorBuilder.ResolvedForeignNameReference<GenericExtractorBuilder.GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resolved;
                        try {
                            resolved = unknownRef.resolve(extractions, AntlrExtractor.resolver());
                            if (resolved != null) {
                                FileObject fo = resolved.source().toFileObject();
                                if (fo != null) {
                                    NamedSemanticRegion<RuleTypes> refItem = resolved.element();
                                    return locationFrom(fo, refItem);
                                }
                            }
                        } catch (IOException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    NamedSemanticRegions<AntlrExtractor.ImportKinds> imports = extractions.namedRegions(AntlrExtractor.IMPORTS);
                    NamedSemanticRegion<AntlrExtractor.ImportKinds> importItem = imports.at(pos);
                    if (importItem != null) {
                        GenericExtractorBuilder.GrammarSource<?> src = extractions.resolveRelative(importItem.name());
                        if (src != null) {
                            FileObject fo = src.toFileObject();
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
                        NamedSemanticRegions<RuleTypes> names = extraction.namedRegions(AntlrExtractor.RULE_NAMES);
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
