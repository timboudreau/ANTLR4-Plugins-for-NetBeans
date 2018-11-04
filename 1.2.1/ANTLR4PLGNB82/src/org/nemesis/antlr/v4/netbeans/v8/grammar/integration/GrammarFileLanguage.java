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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.text.Document;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4LanguageHierarchy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.coloring.ANTLRv4TokenId;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser.ANTLRv4ParserResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;

import org.netbeans.api.lexer.Language;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.DeclarationFinder;
import org.netbeans.modules.csl.api.ElementHandle;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Formatter;
import org.netbeans.modules.csl.api.InstantRenamer;
import org.netbeans.modules.csl.api.KeystrokeHandler;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.csl.api.OccurrencesFinder;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.SemanticAnalyzer;
import org.netbeans.modules.csl.spi.CommentHandler;

import org.netbeans.modules.csl.spi.DefaultLanguageConfig;
import org.netbeans.modules.csl.spi.LanguageRegistration;
import org.netbeans.modules.csl.spi.ParserResult;

import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.Scheduler;
import org.netbeans.modules.parsing.spi.SchedulerEvent;
import org.openide.filesystems.FileObject;

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
                RuleElement el = sem.ruleElementAtPosition(pos);
                if (el != null) {
                    for (RuleElement ref : sem.allReferencesTo(el)) {
                        occurrences.put(new OffsetRange(ref.getStartOffset(), ref.getEndOffset()), ColoringAttributes.MARK_OCCURRENCES);
                    }
                    if (cancelled) {
                        occurrences.clear();
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
        return super.getFormatter(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean hasFormatter() {
        return super.hasFormatter(); //To change body of generated methods, choose Tools | Templates.
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
        public boolean isRenameAllowed(ParserResult pr, int i, String[] strings) {
            if (pr instanceof ANTLRv4ParserResult) {
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    RuleElement elem = sem.ruleElementAtPosition(i);
                    if (elem == null) {
                        return false;
                    }
                    switch (elem.kind()) {
                        case PARSER_NAMED_ALTERNATIVE_SUBRULE:
                            return false;
                        default:
                            return true;
                    }
                }
            }
            return false;
        }

        @Override
        public Set<OffsetRange> getRenameRegions(ParserResult pr, int i) {
            if (pr instanceof ANTLRv4ParserResult) {
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    RuleElement elem = sem.ruleElementAtPosition(i);
                    if (elem != null) {
                        Collection<RuleElement> all = sem.allReferencesTo(elem);
                        Set<OffsetRange> result = new HashSet<>();
                        for (RuleElement e : all) {
                            result.add(new OffsetRange(e.getStartOffset(), e.getEndOffset()));
                        }
                        return result;
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

    @Override
    public KeystrokeHandler getKeystrokeHandler() {
        return super.getKeystrokeHandler(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public CommentHandler getCommentHandler() {
        return super.getCommentHandler(); //To change body of generated methods, choose Tools | Templates.
    }


    static final class DecFinder implements DeclarationFinder {

        @Override
        public DeclarationLocation findDeclaration(ParserResult pr, int i) {
            if (pr instanceof ANTLRv4ParserResult) {
                FileObject file = pr.getSnapshot().getSource().getFileObject();
                if (file == null) {
                    return DeclarationLocation.NONE;
                }
                ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                ANTLRv4SemanticParser sem = res.semanticParser();
                if (sem != null) {
                    RuleElement elem = sem.ruleElementAtPosition(i);
                    if (elem != null) {
                        RuleDeclaration decl = sem.declarationOf(elem);
                        if (decl != null) {
                            DeclarationLocation loc = new DeclarationLocation(file, decl.getStartOffset(),
                                    new EH(decl, file));
                            return loc;
                        }
                    }
                }
            }
            return DeclarationLocation.NONE;
        }

        static final class EH implements ElementHandle {

            private final RuleDeclaration decl;
            private final FileObject fo;

            public EH(RuleDeclaration decl, FileObject fo) {
                this.decl = decl;
                this.fo = fo;
            }

            @Override
            public FileObject getFileObject() {
                return fo;
            }

            @Override
            public String getMimeType() {
                return "text/x-g4";
            }

            @Override
            public String getName() {
                return decl.getRuleID();
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
                return Collections.singleton(Modifier.PUBLIC);
            }

            @Override
            public boolean signatureEquals(ElementHandle eh) {
                if (eh instanceof EH) {
                    EH e = (EH) eh;
                    return e.decl.getRuleID().equals(decl.getRuleID());
                }
                return false;
            }

            @Override
            public OffsetRange getOffsetRange(ParserResult pr) {
                if (pr instanceof ANTLRv4ParserResult) {
                    FileObject file = pr.getSnapshot().getSource().getFileObject();
                    ANTLRv4ParserResult res = (ANTLRv4ParserResult) pr;
                    ANTLRv4SemanticParser sem = res.semanticParser();
                    if (sem != null) {
                        RuleElement elem = sem.declarationOf(decl);
                        if (elem != null) {
                            return new OffsetRange(elem.getStartOffset(), elem.getEndOffset());
                        } else {
                            return null;
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
