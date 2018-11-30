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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;

import org.antlr.v4.runtime.Token;

import org.antlr.v4.runtime.tree.TerminalNode;

import org.apache.bcel.classfile.JavaClass;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.IMPORT;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.OUTPUT;
import static org.nemesis.antlr.v4.netbeans.v8.AntlrFolders.SOURCE;

import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ChannelsSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ClassIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.DelegateGrammarContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.EbnfSuffixContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.FragmentRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.IdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LabeledParserRuleElementContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComChannelContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComModeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComPushModeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexerCommandContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ModeDecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleLabeledAlternativeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleReferenceContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.SuperClassSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TerminalContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.BlockElement;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.Collector;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.EbnfElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarSummary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleReference;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElement;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.FRAGMENT;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.LEXER;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleElementTarget.PARSER;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.JavaClassHelper;

import org.netbeans.api.project.Project;

import org.netbeans.modules.csl.api.Severity;

import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4SemanticParser extends ANTLRv4BaseListener {

    private final GrammarSummary summary;
    private final GrammarType grammarType;

    private final Map<String, RuleDeclaration> tokenRuleDeclarations = new HashMap<>();
    private final Map<String, RuleDeclaration> parserRuleDeclarations = new HashMap<>();
    private final Map<String, RuleDeclaration> fragmentRuleDeclarations = new HashMap<>();
    private final Map<String, RuleDeclaration> namedAlternatives = new HashMap<>();
    private final List<EbnfElement> ebnfs = new ArrayList<>();

    private final List<String> parserRuleIds = new LinkedList<>();
    private final List<String> allParserRuleIds = new LinkedList<>();

    private String firstParserRuleDeclaration;
    private String firstImportedParserRule;
    private final Map<String, RuleReference> lexerRuleReferences = new HashMap<>();
    private final Map<String, RuleReference> parserRuleReferences = new HashMap<>();
    private final Map<String, Collection<RuleReference>> allLexerRuleReferences = new HashMap<>();
    private final Map<String, Collection<RuleReference>> allParserRuleReferences = new HashMap<>();
    private final Map<String, Collection<RuleReference>> allFragmentRuleReferences = new HashMap<>();
    private final Map<String, Integer> numberOfRulesPassingInAMode = new HashMap<>();

    private final List<String> usedChannels = new ArrayList<>();

    private final Set<String> alternatives = new HashSet<>();
    private final Set<String> elementLabels = new HashSet<>();

    private final List<ParsingError> semanticErrors = new LinkedList<>();
    private final List<ParsingError> semanticWarnings = new LinkedList<>();
    private final boolean semanticErrorRequired;
    private RuleDeclaration lastParserRule;
    private final List<BlockElement> blocks = new ArrayList<>();
    private int blockDepth = 0;
    private final Optional<Path> grammarFilePath;
    private RuleTree ruleTree;

    public boolean encounteredError() {
        return getErrorNumber() != 0;
    }

    public int getErrorNumber() {
        return semanticErrors.size();
    }

    public List<ParsingError> getSemanticErrors() {
        return semanticErrors;
    }

    public boolean encounteredWarning() {
        return getWarningNumber() != 0;
    }

    public int getWarningNumber() {
        return semanticWarnings.size();
    }

    public List<ParsingError> getSemanticWarnings() {
        return semanticWarnings;
    }

    public GrammarType getGrammarType() {
        return summary.getGrammarType();
    }

    public String getGrammarName() {
        return summary.getGrammarName();
    }

    public Map<String, RuleDeclaration> getTokenRuleDeclarations() {
        return tokenRuleDeclarations;
    }

    public Map<String, RuleDeclaration> getFragmentRuleDeclarations() {
        return fragmentRuleDeclarations;
    }

    public Map<String, RuleDeclaration> getParserRuleDeclarations() {
        return parserRuleDeclarations;
    }

    public Map<String, RuleReference> getParserRuleReferences() {
        return parserRuleReferences;
    }

    public Map<String, RuleReference> getLexerRuleReferences() {
        return lexerRuleReferences;
    }

    public String getFirstParserRule() {
        return (this.firstParserRuleDeclaration != null)
                ? this.firstParserRuleDeclaration
                : this.firstImportedParserRule;
    }

    public String getPackageName() {
        return summary.getPackageName();
    }

    public List<String> getJavaImports() {
        return summary.getJavaImports();
    }

    public Optional<Path> grammarFilePath() {
        return grammarFilePath;
    }

    private Optional<Path> grammarFileParent() {
        if (grammarFilePath.isPresent()) {
            return Optional.of(grammarFilePath.get().getParent());
        }
        return Optional.empty();
    }

    private Optional<String> grammarNameFromFile() {
        if (grammarFilePath.isPresent()) {
            Path file = grammarFilePath.get();
            if (file.getNameCount() > 0) {
                String result = file.getName(file.getNameCount() - 1).toString();
                if (result.endsWith(".g4")) {
                    result = result.substring(0, result.length() - 3);
                }
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    public Optional<Project> project() {
        if (grammarFilePath.isPresent()) {
            return ProjectHelper.getProject(grammarFilePath.get());
        }
        return Optional.empty();
    }

    public Optional<Path> projectDirectory() {
        Optional<Project> prj = project();
        if (prj.isPresent()) {
            FileObject root = prj.get().getProjectDirectory();
            File file = FileUtil.toFile(root);
            return file != null ? Optional.of(file.toPath()) : Optional.empty();
        }
        return Optional.empty();
    }

    public ANTLRv4SemanticParser(Optional<Path> grammarFilePath, GrammarSummary summary) {
        assert summary != null;
        this.grammarFilePath = grammarFilePath;
        this.summary = summary;
        grammarType = summary.getGrammarType();
        semanticErrorRequired = checkSemanticErrorRequired();
        parserRuleIds.addAll(summary.getParserRuleIds());
        allParserRuleIds.addAll(summary.getImportedParserRuleIds());
        allParserRuleIds.addAll(parserRuleIds);
    }

    public GrammarSummary summary() {
        return summary;
    }

    private boolean checkSemanticErrorRequired() {
        boolean result = true;
        Optional<Path> prjDir = projectDirectory();
        if (prjDir.isPresent()) {
            Path propertyFilePath = prjDir.get().resolve("disable_semantic_errors.properties");
            if (Files.exists(propertyFilePath)) {

                PropertyProvider propertyProvider = PropertyUtils.
                        propertiesFilePropertyProvider(propertyFilePath.toFile());
                PropertyEvaluator propertyEvaluator = PropertyUtils.
                        sequentialPropertyEvaluator(null, propertyProvider);
                String FilesInString = propertyEvaluator.getProperty(
                        "antlr.semantic.errors.files.excluded");
                if (FilesInString != null) {
                    String[] files = FilesInString.split(",");
                    ArrayList<String> excludedFiles = new ArrayList<>();
                    for (String file : files) {
                        Path excludedFilepath = prjDir.get().resolve(file.trim());
                        excludedFiles.add(excludedFilepath.toString());
                    }
                    Optional<String> grammarName = this.grammarNameFromFile();
                    if (grammarName.isPresent() && excludedFiles.contains(grammarName.get())) {
                        result = false;
                    }
                }
            }
        }
        return result;
    }

    ANTLRv4Parser.GrammarFileContext grammarFile;

    public ANTLRv4Parser.GrammarFileContext grammarFile() {
        return grammarFile;
    }

    public void discardParseTree() {
        grammarFile = null;
    }

    @Override
    public void exitGrammarFile(ANTLRv4Parser.GrammarFileContext ctx) {
        grammarFile = ctx;
        // Only at this point do we know which rules are fragments, so we
        // can shuffle them into our fragment reference collection - they
        // are indistinguishable at parse time
        Set<String> fragmentIds = new HashSet<>(fragmentRuleDeclarations.keySet());
        for (String id : fragmentIds) {
            Collection<RuleReference> lexerRefs = lexerRuleReferencesFor(id, false);
            if (lexerRefs != null) {
                allLexerRuleReferences.remove(id);
                allFragmentRuleReferences.put(id, lexerRefs);
                for (RuleReference ref : lexerRefs) {
                    ref.setTarget(FRAGMENT);
                }
            }
        }
        RuleNameFinder rnf = new RuleNameFinder();
        ctx.accept(rnf);
        String[] pruleIds = rnf.names();
        BitSetTreeBuilder rtb2 = new BitSetTreeBuilder(pruleIds);
        ctx.accept(rtb2);
        ruleTree = rtb2.toRuleTree().strings(pruleIds);
        /*
        System.out.println("IN top: " + ruleTree.topLevelOrOrphanRules());
        System.out.println("IN bottom: " + ruleTree.bottomLevelRules());
        System.out.println("\nEIGENVECTOR CENTRALITY: ");
        for (Score s : ((StringRuleTree) ruleTree).eigenvectorCentrality()) {
            System.out.println(" - " + s);
        }
        System.out.println("\nPAGERANK CENTRALITY: ");
        for (Score s : ((StringRuleTree) ruleTree).pageRank()) {
            System.out.println(" - " + s);
        }
        System.out.println("DISJOINT: " + ((StringRuleTree) ruleTree).disjointItems());

        System.out.println("\nIMPORTANT-EI: " + ((StringRuleTree) ruleTree).disjunctionOfClosureOfMostCentralNodes());
        System.out.println("\nIMPORTANT-PG: " + ((StringRuleTree) ruleTree).disjunctionOfClosureOfHighestRankedNodes());

        for (String r : pruleIds) {
            System.out.println("IN " + r + " closure-size: " + ruleTree.closureSize(r));
            System.out.println("IN " + r + " rev-closure-size: " + ruleTree.reverseClosureSize(r));
            System.out.println("IN " + r + " ir-count: " + ruleTree.inboundReferenceCount(r));
            System.out.println("IN " + r + " or-count: " + ruleTree.outboundReferenceCount(r));
            System.out.println("IN " + r + " closure: " + ruleTree.closureOf(r));
            System.out.println("IN " + r + " rev-closure: " + ruleTree.reverseClosureOf(r));
        }
         */
    }

    public RuleTree ruleTree() {
        return ruleTree;
    }

    public RuleElement ruleElementAtPosition(int pos) {
        for (RuleElement el : this.allElements()) {
            if (el.overlaps(pos)) {
                return el;
            }
        }
        return null;
    }

    public List<RuleElement> allElementsOfType(RuleElementTarget target) {
        Set<RuleElement> all = new HashSet<>();
        Map<String, RuleDeclaration> declarations;
        Map<String, Collection<RuleReference>> references;
        switch (target) {
            case FRAGMENT:
                declarations = fragmentRuleDeclarations;
                references = allFragmentRuleReferences;
                break;
            case LEXER:
                declarations = tokenRuleDeclarations;
                references = allLexerRuleReferences;
                break;
            case PARSER:
                declarations = parserRuleDeclarations;
                references = allParserRuleReferences;
                break;
            default:
                throw new AssertionError(target);
        }
        all.addAll(declarations.values());
        for (Collection<RuleReference> c : references.values()) {
            all.addAll(c);
        }
        List<RuleElement> result = new ArrayList<>(all);
        Collections.sort(result);
        return result;
    }

    public RuleDeclaration declarationOf(RuleElement el) {
        Map<String, RuleDeclaration> declarations;
        switch (el.kind().target()) {
            case FRAGMENT:
                declarations = fragmentRuleDeclarations;
                break;
            case LEXER:
                declarations = tokenRuleDeclarations;
                break;
            case PARSER:
                declarations = parserRuleDeclarations;
                break;
            default:
                throw new AssertionError(el.kind().target());
        }
        return declarations.get(el.getRuleID());
    }

    public List<RuleElement> allReferencesTo(RuleElement el) {
        Set<RuleElement> all = new HashSet<>();
        Map<String, RuleDeclaration> declarations;
        Map<String, Collection<RuleReference>> references;
        switch (el.kind().target()) {
            case FRAGMENT:
                declarations = fragmentRuleDeclarations;
                references = allFragmentRuleReferences;
                break;
            case LEXER:
                declarations = tokenRuleDeclarations;
                references = allLexerRuleReferences;
                break;
            case PARSER:
                declarations = parserRuleDeclarations;
                references = allParserRuleReferences;
                break;
            default:
                throw new AssertionError(el.kind().target());
        }
        RuleDeclaration decl = declarations.get(el.getRuleID());
        if (decl != null) {
            all.add(decl);
        }
        Collection<RuleReference> refs = references.get(el.getRuleID());
        if (refs != null) {
            all.addAll(refs);
        }

        List<RuleElement> result = new ArrayList<>(all);
        Collections.sort(result);
        return result;
    }

    Collection<RuleReference> parserRuleReferencesFor(String id, boolean create) {
        Collection<RuleReference> result = allParserRuleReferences.get(id);
        if (result == null && create) {
            result = new ArrayList<>(10);
            allParserRuleReferences.put(id, result);
        }
        return result;
    }

    Collection<RuleReference> lexerRuleReferencesFor(String id, boolean create) {
        Collection<RuleReference> result = allLexerRuleReferences.get(id);
        if (result == null && create) {
            result = new ArrayList<>(10);
            allLexerRuleReferences.put(id, result);
        }
        return result;
    }

    Collection<RuleReference> fragmentRuleReferencesFor(String id, boolean create) {
        Collection<RuleReference> result = allFragmentRuleReferences.get(id);
        if (result == null && create) {
            result = new ArrayList<>(10);
            allFragmentRuleReferences.put(id, result);
        }
        return result;
    }

    void addLexerRuleReference(String id, RuleReference ref) {
        lexerRuleReferencesFor(id, true).add(ref);
    }

    void addParserRuleReference(String id, RuleReference ref) {
        parserRuleReferencesFor(id, true).add(ref);
    }

    void addFragmentRuleReference(String id, RuleReference ref) {
        fragmentRuleReferencesFor(id, true).add(ref);
    }

    public Collection<RuleReference> getParserRuleReferences(String id) {
        Collection<RuleReference> result = parserRuleReferencesFor(id, false);
        return result == null ? Collections.<RuleReference>emptySet() : result;
    }

    public Collection<RuleReference> getLexerRuleReferences(String id) {
        Collection<RuleReference> result = lexerRuleReferencesFor(id, false);
        return result == null ? Collections.<RuleReference>emptySet() : result;
    }

    public Collection<RuleReference> getFragmentRuleReferences(String id) {
        Collection<RuleReference> result = fragmentRuleReferencesFor(id, false);
        return result == null ? Collections.<RuleReference>emptySet() : result;
    }

    public RuleReference lexerRuleReferenceOverlapping(int position) {
        for (Map.Entry<String, Collection<RuleReference>> e : allLexerRuleReferences.entrySet()) {
            for (RuleReference ref : e.getValue()) {
                if (ref.overlaps(position)) {
                    return ref;
                }
            }
        }
        return null;
    }

    public RuleReference parserRuleReferenceOverlapping(int position) {
        for (Map.Entry<String, Collection<RuleReference>> e : allParserRuleReferences.entrySet()) {
            for (RuleReference ref : e.getValue()) {
                if (ref.overlaps(position)) {
                    return ref;
                }
            }
        }
        return null;
    }

    public RuleReference fragmentRuleReferenceOverlapping(int position) {
        for (Map.Entry<String, Collection<RuleReference>> e : allFragmentRuleReferences.entrySet()) {
            for (RuleReference ref : e.getValue()) {
                if (ref.overlaps(position)) {
                    return ref;
                }
            }
        }
        return null;
    }

    public List<RuleDeclaration> allDeclarations() {
        Set<RuleDeclaration> all = new HashSet<>(
                parserRuleDeclarations.size() + tokenRuleDeclarations.size()
                + fragmentRuleDeclarations.size());
        all.addAll(parserRuleDeclarations.values());
        all.addAll(tokenRuleDeclarations.values());
        all.addAll(fragmentRuleDeclarations.values());
        List<RuleDeclaration> result = new ArrayList<>(all);
        Collections.sort(result);
        return result;
    }

    public List<RuleElement> allElements() {
        Set<RuleElement> all = new HashSet<>();
        for (Map.Entry<String, Collection<RuleReference>> e : allLexerRuleReferences.entrySet()) {
            all.addAll(e.getValue());
        }
        for (Map.Entry<String, Collection<RuleReference>> e : allParserRuleReferences.entrySet()) {
            all.addAll(e.getValue());
        }
        for (Map.Entry<String, Collection<RuleReference>> e : allFragmentRuleReferences.entrySet()) {
            all.addAll(e.getValue());
        }
        all.addAll(parserRuleDeclarations.values());
        all.addAll(tokenRuleDeclarations.values());
        all.addAll(fragmentRuleDeclarations.values());
        List<RuleElement> result = new ArrayList<>(all);
        Collections.sort(result);
        return result;
    }

    public RuleDeclaration getParserRuleDeclaration(String id) {
        return parserRuleDeclarations.get(id);
    }

    public RuleDeclaration getLexerRuleDeclaration(String id) {
        return tokenRuleDeclarations.get(id);
    }

    public RuleDeclaration getFragmentRuleDeclaration(String id) {
        return fragmentRuleDeclarations.get(id);
    }

    @Override
    public String toString() {
        Optional<String> gn = this.grammarNameFromFile();
        StringBuilder sb = new StringBuilder(gn.isPresent() ? gn.get() : "<no-file>").append('{');
        sb.append("\n parserRules:");
        for (Map.Entry<String, RuleDeclaration> e : parserRuleDeclarations.entrySet()) {
            sb.append("\n  ").append(e.getValue());
        }
        sb.append("\n parserRuleReferences:");
        for (Map.Entry<String, Collection<RuleReference>> e : allParserRuleReferences.entrySet()) {
            sb.append("\n  ").append(e.getKey());
            for (RuleReference ref : e.getValue()) {
                sb.append("\n    ").append(ref);
            }
        }
        sb.append("lexerRules:");
        for (Map.Entry<String, RuleDeclaration> e : tokenRuleDeclarations.entrySet()) {
            sb.append("\n  ").append(e.getValue());
        }
        sb.append("\n lexerRuleReferences:");
        for (Map.Entry<String, Collection<RuleReference>> e : allParserRuleReferences.entrySet()) {
            sb.append("\n  ").append(e.getKey());
            for (RuleReference ref : e.getValue()) {
                sb.append("\n    ").append(ref);
            }
        }
        sb.append("fragmentRules:");
        for (Map.Entry<String, RuleDeclaration> e : fragmentRuleDeclarations.entrySet()) {
            sb.append("\n  ").append(e.getValue());
        }
        sb.append("\n fragmentRuleReferences:");
        for (Map.Entry<String, Collection<RuleReference>> e : allFragmentRuleReferences.entrySet()) {
            sb.append("\n  ").append(e.getKey());
            for (RuleReference ref : e.getValue()) {
                sb.append("\n    ").append(ref);
            }
        }
        return sb.append('}').toString();
    }

    /**
     * Called at the end of an ANTLR import statement.
     *
     * @param ctx
     */
    @Override
    public void exitDelegateGrammar(DelegateGrammarContext ctx) {
        GrammarIdentifierContext gic = ctx.grammarIdentifier();
        if (gic != null) {
            IdentifierContext ic = gic.identifier();
            if (ic != null) {
                TerminalNode idTN = ic.ID();
                if (idTN != null) {
                    Token idToken = idTN.getSymbol();
                    if (idToken != null) {
                        String importedGrammarName = idToken.getText();
                        if (!importedGrammarName.equals("<missing ID>")) {
                            int startOffset = idToken.getStartIndex();
                            int endOffset = idToken.getStopIndex() + 1;
                            Optional<String> name = this.grammarNameFromFile();
                            if (semanticErrorRequired && name.isPresent()) {
                                if (importedGrammarName.equals(name.get())) {
                                    String key = "antlr.error.import.a"
                                            + ".grammar.cannot.import"
                                            + ".itself";
                                    String displayName = "A grammar cannot"
                                            + " import itself ";
                                    String description = displayName;
                                    addError(key, startOffset, endOffset, displayName, description);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void exitSuperClassSpec(SuperClassSpecContext ctx) {
//        System.out.println("ANTLRv4SemanticParser:exitSuperClassSpec(SuperClassSpecContext) : begin");
        // A class identifier may contain a package name so identifier returns
        // a list of identifiers
        ClassIdentifierContext cic = ctx.classIdentifier();
        if (cic != null) {
            String fullQualifiedSuperClass = summary.getSuperClass();
            if (fullQualifiedSuperClass == null) {
                if (semanticErrorRequired) {
                    Token superClassToken = ctx.SUPER_CLASS().getSymbol();
                    int startOffset = superClassToken.getStartIndex();
                    int stopOffset = superClassToken.getStopIndex();
                    String key = "antlr.error.super.class.missing";
                    String displayName = "no super class defined";
                    String description = displayName;
                    addError(key, startOffset, stopOffset, displayName, description);
                }
            } else {
                int targetStart = cic.getStart().getStartIndex();
                int targetEnd = cic.getStop().getStopIndex() + 1;
                int start = fullQualifiedSuperClass.lastIndexOf(".");
                // Next statement is valid even if start = -1
                String className = fullQualifiedSuperClass.substring(start + 1);
                String superClassPackage;
                if (start == -1) {
                    superClassPackage = "";
                } else {
                    superClassPackage = fullQualifiedSuperClass.substring(0, start);
                }

                // There is a link to fullQualifiedSuperClass transformed in directory
                String superClassFilePathString = fullQualifiedSuperClass.replace(".", "/");
                superClassFilePathString += ".java";
                // The first place where Java source may be is:
                // - in antlr.generator.dest.dir (mandatory property) concatenated with
                //   its potential package transformed in directory structure if the project is
                //   ant-based,
                // - in target/generated-sources/antlr4 concatenated with its potential
                //   package transformed in directory structure if project is Maven-based.
                Optional<Project> prj = project();
                if (prj.isPresent()) {
                    File srcDir = ProjectHelper.getJavaSourceDir(prj.get());
                    if (srcDir != null) {
                        Path superClassPath = Paths.get(srcDir.getPath(),
                                superClassFilePathString);
                        File superClassFile = superClassPath.toFile();
//                    System.out.println("- super class file=" +
//                                       superClassFile.getPath());
                        // If there is no package defined in superClass then there may be a
                        // Java import statement telling where to find the source
                        if ("".equals(superClassPackage)) {
                            List<String> javaImports = summary.getJavaImports();
                            Iterator<String> it2 = javaImports.iterator();
                            String fullQualifiedImportedClassName;
                            String importedClassName;
                            boolean importFound = false;
                            while (it2.hasNext() && !importFound) {
                                fullQualifiedImportedClassName = it2.next();
                                start = fullQualifiedImportedClassName.lastIndexOf(".");
                                importedClassName = fullQualifiedImportedClassName.substring(start + 1);
                                // here classNameWithPackage is just a class name
                                // because superClassPackageName is empty
//                            System.out.println("- imported class name=" + importedClassName);
                                if (importedClassName.equals(className)) {
                                    importFound = true;
                                    fullQualifiedSuperClass
                                            = fullQualifiedImportedClassName;
                                    superClassFilePathString
                                            = fullQualifiedImportedClassName.replace(".", "/");
                                    superClassFilePathString
                                            = superClassFilePathString + ".java";
//                                System.out.println("- super class file path="
//                                                  + superClassFilePathString);
                                    superClassPath = Paths.get(srcDir.getPath(),
                                            superClassFilePathString);
                                    superClassFile = superClassPath.toFile();
                                }
                            }
                        }
                        // Now if the class has a package it is found so we can look
                        // for our Java source in the project directories
                        if (!superClassFile.exists() && prj.isPresent()) {
                            // The class source has not been found in project
                            // directories so perhaps there is a corresponding .class
                            // file in project libraries
//                        System.out.println("- file not found in source directory of current project");
                            JavaClass javaSuperClass
                                    = JavaClassHelper.getJavaClassInLibraries(prj.get(), fullQualifiedSuperClass);
                            if (javaSuperClass == null) {
                                // We didn't find our super class in project libraries
                                // either so this class is not accessible or does not exist
                                if (semanticErrorRequired) {
                                    String key = "antlr.error.super.class.not."
                                            + "found";
                                    String displayName = "unable to find super"
                                            + " class in Java sources or in libraries"
                                            + " of current project";
                                    String description = displayName;
                                    addError(key, targetStart, targetEnd, displayName, description);
                                }
                            } else {
                                // We have found a class in one of project libraries
                                // but is it implementing a valid Lexer or parser
                                // class?
                                switch (grammarType) {
                                    case LEXER:
                                        if (!JavaClassHelper.isExtendingANTLRLexer(javaSuperClass)
                                                && semanticErrorRequired) {
                                            String key = "antlr.error.super.class.invalid";
                                            String displayName = "super class found "
                                                    + "but is not extending ANTLR Lexer,"
                                                    + "or LexerInterpreter or XPathLexer";
                                            String description = displayName;
                                            addError(key, targetStart, targetEnd, displayName, description);
                                        }
                                        break;
                                    case PARSER:
                                    case COMBINED:
                                        if (!JavaClassHelper.isExtendingANTLRParser(javaSuperClass)
                                                && semanticErrorRequired) {
                                            String key = "antlr.error.super.class.invalid";
                                            String displayName = "super class found but is not extending ANTLR Parser";
                                            String description = displayName;
                                            addError(key, targetStart, targetEnd, displayName, description);
                                        }
                                        break;
                                    default:
                                }
                            }
                        }
                    }
                }
            }
        }
//        System.out.println("ANTLRv4SemanticParser:exitSuperClassSpec(SuperClassSpecContext) : end");
    }

    @Override
    public void exitChannelsSpec(ChannelsSpecContext ctx) {
        switch (grammarType) {
            case PARSER:
            case COMBINED:
                if (semanticErrorRequired) {
                    String key = "antlr.error.channels.in.non.lexer.grammar";
                    Token channelsToken = ctx.CHANNELS().getSymbol();
                    int startOffset = channelsToken.getStartIndex();
                    int endOffset = channelsToken.getStopIndex() + 1;
                    String displayName = "Only a lexer grammar may have a channels statement";
                    String description = displayName;
                    addError(key, startOffset, endOffset, displayName, description);
                }
                break;
        }
    }

    @Override
    public void exitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
        // We add the id of the rule to the set of id
        // it enables to check that each parser rule reference has a correspondent
        // parser rule definition
        ParserRuleDeclarationContext decl = ctx.parserRuleDeclaration();
        ParserRuleIdentifierContext pric = decl.parserRuleIdentifier();
        if (pric != null) {
            TerminalNode pridTN = pric.PARSER_RULE_ID();
            if (pridTN != null) {
                Token token = pridTN.getSymbol();
                if (token != null) {
                    String parserRuleId = token.getText();
                    int startOffset = token.getStartIndex();
                    int endOffset = token.getStopIndex() + 1;
                    switch (grammarType) {
                        case LEXER: {
                            if (semanticErrorRequired) {
                                String key
                                        = "antlr.error.parserRule.lexer.grammar.can.only."
                                        + "contain.lexer.rule.declarations";
                                String displayName
                                        = "a lexer grammar can only contain lexer rule"
                                        + " declarations";
                                String description = displayName + "\n"
                                        + "rule " + parserRuleId + " is a parser rule";
                                addError(key, startOffset, endOffset, displayName, description);
                            }
                            break;
                        }
                        case COMBINED:
                        case PARSER: {
                            if (firstParserRuleDeclaration == null) {
                                firstParserRuleDeclaration = parserRuleId;
                            }
                            break;
                        }
                        default:
                    }
                    String nm = parserRuleId;
                    RuleDeclaration newRule
                            = new RuleDeclaration(PARSER,
                                    nm, decl.parserRuleIdentifier().getStart().getStartIndex(),
                                    decl.parserRuleIdentifier().getStop().getStopIndex() + 1,
                                    ctx.getStart().getStartIndex(),
                                    ctx.getStop().getStopIndex() + 1);
                    parserRuleDeclarations.put(nm, newRule);
                    lastParserRule = newRule;
                }
            }
        }
    }

    @Override
    public void exitParserRuleLabeledAlternative(ParserRuleLabeledAlternativeContext ctx) {
        IdentifierContext idc = ctx.identifier();
        if (idc != null) {
            TerminalNode idTN = idc.ID();
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String altnvLabel = labelToken.getText();
                    if (lastParserRule != null) {
                        RuleDeclaration namedAlternative = lastParserRule.addNamedAlternative(altnvLabel, labelToken.getStartIndex(), labelToken.getStopIndex() + 1);
                        namedAlternatives.put(altnvLabel, namedAlternative);
                    }
//                    namedVariantRuleDeclarations.put(altnvLabel, new RuleDeclaration())
                    if (!altnvLabel.equals("<missing ID>")) {
                        // We test if current parser rule has already an
                        // alternative with the same label
                        if (alternatives.contains(altnvLabel)
                                && semanticErrorRequired) {
                            String key = "antlr.error.parser.rule"
                                    + ".alternatives.must.have.different"
                                    + ".labels";
                            int startOffset = labelToken.getStartIndex();
                            int endOffset = labelToken.getStopIndex() + 1;
                            String displayName = "A previous alternative has"
                                    + " already taken this label";
                            String description = displayName;
                            addError(key, startOffset, endOffset, displayName, description);
                        } else {
                            alternatives.add(altnvLabel);
                        }
                    }
                }
            }
        }
        elementLabels.clear();
    }

    @Override
    public void exitLabeledParserRuleElement(LabeledParserRuleElementContext ctx) {
        IdentifierContext idc = ctx.identifier();
        if (idc != null) {
            TerminalNode idTN = idc.ID();
            if (idTN != null) {
                Token labelToken = idTN.getSymbol();
                if (labelToken != null) {
                    String id = labelToken.getText();
                    if (!id.equals("<missing ID>")) {
                        if (elementLabels.contains(id)
                                && semanticErrorRequired) {
                            String key = "antlr.error.parser.rule"
                                    + ".element.must.have.different.labels";
                            int startOffset = labelToken.getStartIndex();
                            int endOffset = labelToken.getStopIndex() + 1;
                            String displayName = "A previous element has"
                                    + " already taken this label";
                            String description = displayName;
                            addError(key, startOffset, endOffset, displayName, description);
                        } else {
                            elementLabels.add(id);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void exitParserRuleReference(ParserRuleReferenceContext ctx) {
        ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
        if (pric != null) {
            TerminalNode pridTN = pric.PARSER_RULE_ID();
            if (pridTN != null) {
                Token parserRuleRefIdToken = pridTN.getSymbol();
                if (parserRuleRefIdToken != null) {
                    String parserRuleRefId = parserRuleRefIdToken.getText();
                    int startOffset = parserRuleRefIdToken.getStartIndex();
                    int endOffset = parserRuleRefIdToken.getStopIndex() + 1;
                    RuleReference ruleRef = new RuleReference(PARSER, parserRuleRefId, startOffset, endOffset);
                    if (!parserRuleReferences.containsKey(parserRuleRefId)) {
                        parserRuleReferences.put(parserRuleRefId, ruleRef);
                    }
                    addParserRuleReference(parserRuleRefId, ruleRef);
                }
            }
        }
    }

    /**
     * Is called from a lexer or parser rule when its definition contains a
     * string literal or a lexer rule reference.
     *
     * @param ctx
     */
    @Override
    public void exitTerminal(TerminalContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            String id = idToken.getText();
            int startOffset = idToken.getStartIndex();
            int endOffset = idToken.getStopIndex() + 1;
            RuleReference ruleRef = new RuleReference(LEXER, id, startOffset, endOffset);
            if (!lexerRuleReferences.containsKey(id)) {
                lexerRuleReferences.put(id, ruleRef);
            }
            addLexerRuleReference(id, ruleRef);
        } else {
            // A parser grammar cannot define implicitly a new token, so if we find
            // a literal and that literal is not defined in a token declaration
            // (through tokens) or an imported token file (through tokenVocab option)
            if (grammarType == GrammarType.PARSER) {
                TerminalNode stringLiteralTN = ctx.STRING_LITERAL();
                if (stringLiteralTN != null) {
                    String lexerLiteral = stringLiteralTN.getText();
                    // We are in a parser grammar and we encountered a string
                    // literal so this literal is necessarily used in a parser rule
                    // definition. It is not possible to define token rules in
                    // a parser grammar and it is not possible to import lexer
                    // grammar so the token associated to that string literal is
                    // necessarily defined in an imported token files.
                    // So we have to check that our lexer literal is defined in one
                    // of imported tokens files.
                    List<String> tokenLiterals = summary.getImportedTokenLiterals();
                    if (!tokenLiterals.contains(lexerLiteral)
                            && semanticErrorRequired) {
                        String key = "antlr.error.parser.grammar.cannot.add"
                                + ".implicitly.lexer.token.through.literal.use";
                        int startOffset = stringLiteralTN.getSymbol().getStartIndex();
                        int endOffset = stringLiteralTN.getSymbol().getStopIndex() + 1;
                        String displayName = "A parser grammar cannot add"
                                + " implicitly a lexer token through a literal use.";
                        String description = displayName + "\n"
                                + "literal in fault : " + lexerLiteral;
                        addError(key, startOffset, endOffset, displayName, description);
                    }
                }
            }
        }
    }

    @Override
    public void exitTokenRuleDeclaration(TokenRuleDeclarationContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            int startOffset = idToken.getStartIndex();
            int endOffset = idToken.getStopIndex() + 1;
            String lexerRuleId = idToken.getText();
            TerminalNode ident = ctx.TOKEN_ID();
            if (ident != null) { // broken source?
                tokenRuleDeclarations.put(lexerRuleId,
                        new RuleDeclaration(LEXER,
                                lexerRuleId, ident.getSymbol().getStartIndex(),
                                ident.getSymbol().getStopIndex() + 1,
                                ctx.getStart().getStartIndex(),
                                ctx.getStop().getStopIndex() + 1));
            }
            if (grammarType == GrammarType.PARSER
                    && semanticErrorRequired) {
                String key = "antlr.error.parser.grammar.can.only."
                        + "contain.parser.rule.declaration";
                String displayName = "a parser grammar can only contain"
                        + "parser rule declaration";
                String description = displayName + "\n"
                        + "rule " + lexerRuleId + " is a lexer rule";
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    @Override
    public void exitFragmentRuleDeclaration(FragmentRuleDeclarationContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN != null) {
            Token idToken = idTN.getSymbol();
            if (idToken != null) {
                String fragmentRuleId = idToken.getText();
                if (!fragmentRuleId.equals("<missing TOKEN_ID>")) {
                    int startOffset = idToken.getStartIndex();
                    int endOffset = idToken.getStopIndex() + 1;
                    if (grammarType == GrammarType.PARSER
                            && semanticErrorRequired) {
                        String key = "antlr.error.parser.grammar.can.only."
                                + "contain.parser.rule.declaration";
                        String displayName = "a parser grammar can only contain"
                                + "parser rule declaration";
                        String description = displayName + "\n"
                                + "rule " + fragmentRuleId + " is a lexer rule";
                        addError(key, startOffset, endOffset, displayName, description);
                    } else {
                        fragmentRuleDeclarations.put(fragmentRuleId,
                                new RuleDeclaration(FRAGMENT,
                                        fragmentRuleId, idToken.getStartIndex(),
                                        idToken.getStopIndex() + 1,
                                        ctx.getStart().getStartIndex(),
                                        ctx.getStop().getStopIndex() + 1));
                    }
                }
            }
        }
    }

    @Override
    public void exitLexComPushMode(LexComPushModeContext ctx) {
        IdentifierContext ic = ctx.identifier();
        if (ic != null) {
            TerminalNode idTN = ic.ID();
            if (idTN != null) {
                Token modeToken = idTN.getSymbol();
                if (modeToken != null) {
                    String modeId = modeToken.getText();
                    if (!modeId.equals("<missing ID>")) {
                        Integer value = numberOfRulesPassingInAMode.get(modeId);
                        if (value == null) {
                            value = 0;
                        }
                        numberOfRulesPassingInAMode.put(modeId, ++value);
                        List<String> modes = summary.getModes();
                        if (!modes.contains(modeId)
                                && semanticErrorRequired) {
                            String key = "antlr.error.mode.does.not.exit";
                            int startOffset = modeToken.getStartIndex();
                            int endOffset = modeToken.getStopIndex() + 1;
                            String displayName = "mode " + modeId + " does not exist";
                            String description = displayName;
                            addError(key, startOffset, endOffset, displayName, description);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void exitLexComMode(LexComModeContext ctx) {
        IdentifierContext ic = ctx.identifier();
        if (ic != null) {
            TerminalNode idTN = ic.ID();
            if (idTN != null) {
                Token modeToken = idTN.getSymbol();
                if (modeToken != null) {
                    String modeId = modeToken.getText();
                    if (!modeId.equals("<missing ID>")) {
                        Integer value = numberOfRulesPassingInAMode.get(modeId);
                        if (value == null) {
                            value = 0;
                        }
                        numberOfRulesPassingInAMode.put(modeId, ++value);
                    }
                }
            }
        }
    }

    @Override
    public void exitLexComChannel(LexComChannelContext ctx) {
        IdentifierContext ic = ctx.identifier();
        if (ic != null) {
            TerminalNode idTN = ic.ID();
            if (idTN != null) {
                Token channelToken = idTN.getSymbol();
                if (channelToken != null) {
                    String channelId = channelToken.getText();
                    if (!channelId.equals("<missing ID>")) {
                        List<String> channels = summary.getChannels();
                        if (!channels.contains(channelId)
                                && semanticErrorRequired) {
                            String key = "antlr.error.parser.channel.lexer."
                                    + "command.must.reference.a.declared.channel";
                            String displayName = "a channel lexer command must"
                                    + " reference a declared channel";
                            String description = displayName;
                            int startOffset = channelToken.getStartIndex();
                            int endOffset = channelToken.getStopIndex() + 1;
                            addError(key, startOffset, endOffset, displayName, description);
                        }
                        usedChannels.add(channelId);
                    }
                }
            }
        }
    }

    @Override
    public void exitLexerCommand(LexerCommandContext ctx) {
        TerminalNode typeTN = ctx.LEXCOM_TYPE();
        if (typeTN != null) {
            TerminalNode tokenIdTN = ctx.TOKEN_ID();
            if (tokenIdTN != null) {
                Token tokenIdToken = tokenIdTN.getSymbol();
                if (tokenIdToken != null) {
                    String tokenId = tokenIdToken.getText();
                    if (!tokenId.equals("<missing TOKEN_ID>")) {
                        // We check if this token id is defined in a lexer rule
                        List<String> tokenRuleIds = summary.getTokenRuleIds();
                        boolean error;
                        if (tokenRuleIds.contains(tokenId)) {
                            error = false;
                        } else {
                            // There is no token rule corresponding to our token id
                            // we look for it in tokens declaration
                            List<String> tokens = summary.getTokens();
                            if (tokens.contains(tokenId)) {
                                error = false;
                            } else {
                                // There is no token in tokens statement corresponding
                                // to our token so we look for it in imported grammars
                                // and imported token files
                                List<String> importedTokens
                                        = summary.getImportedTokenIds();
                                if (importedTokens.contains(tokenId)) {
                                    error = false;
                                } else {
                                    error = true;
                                }
                            }
                        }
                        if (error && semanticErrorRequired) {
                            String key = "antlr.error.parser.type.lexer."
                                    + "command.must.reference.a.defined.token";
                            String displayName = "a type lexer command must"
                                    + " reference a defined token";
                            String description = displayName;
                            int startOffset = tokenIdToken.getStartIndex();
                            int endOffset = tokenIdToken.getStartIndex() + 1;
                            addError(key, startOffset, endOffset, displayName, description);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void exitModeDec(ModeDecContext ctx) {
        if (grammarType != GrammarType.LEXER
                && semanticErrorRequired) {
            String key = "antlr.error.mode.only.lexer.grammar.can.contain.mode.specifications";
            int startOffset = ctx.MODE().getSymbol().getStartIndex();
            int endOffset = ctx.MODE().getSymbol().getStopIndex() + 1;
            String displayName = "Only lexer grammar can contain mode specifications";
            String description = displayName;
            addError(key, startOffset, endOffset, displayName, description);
        }
    }

    public void check() {
        if (semanticErrorRequired) {
            checkGrammarName();
            checkImportedGrammars();
            checkImportedTokenFiles();
            checkThereIsNoForbiddenTokenID();
            switch (grammarType) {
                case LEXER:
                    checkThereisNoDuplicateMode();
                    checkThereisAtLeastOneLexerCommandPassingInEachMode();
                    checkThereisAtLeastOneLexerRuleInEachMode();

                    checkThereisNoDuplicateChannel();
                    checkAllDeclaredChannelsAreUsed();

                    checkThereIsNoForbiddenFragmentId();
                    checkThereIsNoDuplicateLexerRuleId();
                    checkFragmentRuleReferences();
                    break;
                case PARSER:

                    checkThereIsNoDuplicateParserRuleIds();
                    checkThereIsNoForbiddenParserRuleId();
                    checkRuleReferences();
                    checkAllDeclaredParserRulesAreUsed();
                    break;
                case COMBINED:
                    checkThereIsNoForbiddenFragmentId();
                    checkThereIsNoDuplicateLexerRuleId();

                    checkThereIsNoDuplicateParserRuleIds();
                    checkThereIsNoForbiddenParserRuleId();
                    checkRuleReferences();
                    checkAllDeclaredParserRulesAreUsed();
                    break;
                default:
            }
            checkThereIsAtLeastARule();
        }

    }

    protected void checkGrammarName() {
        String grammarName = summary.getGrammarName();
        Optional<String> grammarNameFromFile = this.grammarNameFromFile();
        boolean mismatch = (grammarName != null && grammarNameFromFile.isPresent()
                && !grammarName.equalsIgnoreCase(grammarNameFromFile.get()))
                || grammarName == null;
        // Will be null if empty file
        if (mismatch) {
            int startOffset = summary.getGrammarIdStartOffset();
            int endOffset = summary.getGrammarIdEndOffset();
            String key = "antlr.error.grammar.must.be.equal."
                    + "to.grammar.file.name";
            String displayName = "grammar name must be equal "
                    + "to grammar file name (without extension)";
            String description = displayName;
            addError(key, startOffset, endOffset, displayName, description);
        }
    }

    private Optional<Path> resolveGrammarFile(String name) {
        Optional<Path> parent = this.grammarFileParent();
        if (parent.isPresent()) {
            Path sibling = parent.get().resolve(name);
            if (Files.exists(sibling)) {
                return Optional.of(sibling);
            }
        }
        Optional<Path> importDir = this.importDir();
        if (importDir.isPresent()) {
            Path result = importDir.get().resolve(name);
            if (Files.exists(result)) {
                return Optional.of(result);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private String grammarNameString() {
        Optional<String> imp = grammarNameFromFile();
        return imp.isPresent() ? imp.get() : "<no grammar name from file>";
    }

    private String fileParentString() {
        Optional<Path> imp = grammarFileParent();
        return imp.isPresent() ? imp.get().toString() : "<no file>";
    }

    private String importPath() {
        Optional<Path> imp = importDir();
        return imp.isPresent() ? imp.get().toString() : "<no import dir>";
    }

    public Optional<Path> importDir() {
        return IMPORT.getPath(project(), grammarFilePath);
    }

    protected void checkImportedGrammars() {
        List<String> importedGrammars = summary.getImportedGrammars();

        for (String importedGrammar : importedGrammars) {
            String importedGrammarFileName = importedGrammar + ".g4";
            Optional<Path> imported = resolveGrammarFile(importedGrammarFileName);
            /*
            // We look for imported grammar from the same directory as current
            // grammar file.
            // We build the grammar file path as a local path from that directory
            Path importedGrammarFilePath = Paths.get(parsedGrammarDirectory,
                    importedGrammarFileName);
            File importedGrammarFile = importedGrammarFilePath.toFile();
            if (!importedGrammarFile.exists()) {
                // We didn't find the file in that directory so now we look for it
                // in import directory if import dir is different from parsed
                // grammar directory
                File importDir = ProjectHelper.getANTLRImportDir(project, grammarFilePath);
                // import directory may be null if we use that plugin in a project
                // type not supported: it must not lead to an error!
                if (importDir != null) {
                    String importDirString = importDir.getPath();
                    // If the parsed grammar is already in import directory, it is
                    // useless to test if file is in import directory
                    if (!importDirString.equals(parsedGrammarDirectory)) {
                        importedGrammarFilePath = Paths.get(importDirString,
                                importedGrammarFileName);
                        importedGrammarFile = importedGrammarFilePath.toFile();
                    } else {
                        importedGrammarFilePath = null;
                        importedGrammarFile = null;
                    }
//                } else {
                    // Project type is not supported, so we are unable to find
                    // where file might be
//                    importedGrammarFilePath = null;
//                    importedGrammarFile = null;
                }
            }
             */
            Map<String, Integer> importedGrammarIdStartOffsets
                    = summary.getImportedGrammarIdStartOffsets();
            Map<String, Integer> importedGrammarIdEndOffsets
                    = summary.getImportedGrammarIdEndOffsets();
            int startOffset = importedGrammarIdStartOffsets.get(importedGrammar);
            int endOffset = importedGrammarIdEndOffsets.get(importedGrammar);
            // importedGrammarFile may be null if the project type is not managed!
            if (!imported.isPresent()) {
                String key = "antlr.error.import.grammar.not.found";
                String displayName = "Unable to import the grammar file "
                        + importedGrammarFileName + " in the directory of "
                        + fileParentString() + " or in ANTLR import directory "
                        + importPath();
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            } else {
                // The imported file exists!
                // We must check if its type is compatible with the type of current
                // grammar. But we don't have this data that is placed in its
                // summary
                FileObject importedGrammarFO
                        = FileUtil.toFileObject(imported.get().toFile());
                Document importedGrammarDoc
                        = ProjectHelper.getDocument(importedGrammarFO);
                // We recover its summary
                GrammarSummary importedGrammarSummary
                        = importedGrammarDoc == null ? null
                                : (GrammarSummary) importedGrammarDoc.getProperty(GrammarSummary.class);
                Optional<Project> prj = project();

                if (importedGrammarSummary == null && prj.isPresent() && importedGrammarDoc != null) {
                    // No summary is associated with this doc. It just means the
                    // document has been loaded from disk into memory by our
                    // previous getDocument() statement
                    // So we try to load it from disk
                    importedGrammarSummary = GrammarSummary.load(prj.get(),
                            imported.get().toString());
                    // If that grammar has never been edited and it is the first
                    // time, we parse the current grammar, then there is no summary
                    // on disk. So we have to collect it
                    if (importedGrammarSummary == null) {
                        parseGrammarFile(importedGrammarDoc,
                                imported.get());
                        // Now there should have a summary associated to the document
                        // So we try again to recover it
                        importedGrammarSummary
                                = (GrammarSummary) importedGrammarDoc.getProperty(GrammarSummary.class);
                    }
                }
                // At this step, summary must be non null except if an exception
                // occurred in parseGrammarFile() method
                if (summary != null && importedGrammarSummary != null) {
                    GrammarType importedGrammarType
                            = importedGrammarSummary.getGrammarType();
                    switch (grammarType) {
                        case LEXER:
                            if (importedGrammarType != GrammarType.LEXER) {
                                String key = "antlr.error.import.lexer.grammar"
                                        + ".can.only.import.lexer.grammars";
                                String displayName
                                        = "A lexer grammar can only include lexer "
                                        + "grammars";
                                String description = displayName + "grammar "
                                        + grammarNameString() + " is a lexer grammar."
                                        + "grammar " + importedGrammar + " is a "
                                        + importedGrammarType + " grammar";
                                addError(key, startOffset, endOffset, displayName, description);
                            }
                            break;
                        case PARSER:
                            if ((importedGrammarType != GrammarType.PARSER)) {
                                String key = "antlr.error.import.parser.grammar"
                                        + ".can.only.import.parser.grammars";
                                String displayName
                                        = "A parser grammar can only import parser"
                                        + " grammars";
                                String description = displayName + "grammar "
                                        + this.grammarNameFromFile() + " is a parser grammar"
                                        + "grammar " + importedGrammar + " is a "
                                        + importedGrammarType + " grammar";
                                addError(key, startOffset, endOffset, displayName, description);
                            }
                            break;
                        case COMBINED:
                            // The below error is nonsense, at least in 4.7 -
                            // you can import - it is basically just an include;
                            // however, those individual grammars will look like
                            // they have errors, since the top level has to import
                            // the dependencies.  So, it ain't pretty,
                            // but you can do it.  Changed severity to INFO.

                            // According to Terence's book, p. 261:
                            // "Combined grammars can import combined grammars
                            // except (for the time being, ones using lexical modes)"
                            if (importedGrammarType == GrammarType.COMBINED) {
                                String key = "antlr.error.import.combined."
                                        + "grammar.can.only.import.parser.and.lexer"
                                        + ".grammars";
                                String displayName = "A combined grammar can only"
                                        + " include parser and lexer grammars";
                                String description = displayName + "grammar "
                                        + grammarNameString() + " is a combined grammar"
                                        + "grammar " + importedGrammar + " is a "
                                        + importedGrammarType + " grammar";
                                addError(Severity.INFO,
                                        key, startOffset, endOffset, displayName, description);
                            }
                            break;
                        case UNDEFINED:
                            break;
                    }
                }
            }
        }
    }

    public void addError(Severity severity, String key, int startOffset, int endOffset, String displayName, String description) {
        ParsingError semanticError = new ParsingError(grammarFilePath,
                severity,
                key,
                startOffset,
                endOffset,
                displayName,
                description);
        semanticErrors.add(semanticError);
    }

    public void addError(String key, int startOffset, int endOffset, String displayName, String description) {
        addError(Severity.ERROR, key, startOffset, endOffset, displayName, description);
    }

    /**
     * We check that imported token files exist
     */
    protected void checkImportedTokenFiles() {
        if (!grammarFilePath.isPresent()) {
            return;
        }
        Optional<Path> antlrSrcDir = SOURCE.getPath(project(), grammarFilePath);

        Optional<Path> par = this.grammarFileParent();
        if (!antlrSrcDir.isPresent() || !par.isPresent()) {
            return;
        }
        Path parsedGrammarDirPath = par.get();
        Path parsedGrammarDirRelativePath
                = antlrSrcDir.get().relativize(parsedGrammarDirPath);

        String parsedGrammarDirRelativePathString
                = parsedGrammarDirRelativePath.toString();
//        System.out.println("parsed grammar dir relative path=" +
//                           parsedGrammarDirRelativePath         );
        Optional<Path> importDir = IMPORT.getPath(project(), grammarFilePath);

        Optional<Path> destDir = OUTPUT.getPath(project(), grammarFilePath);

        List<String> importedTokenFileNames = summary.getImportedTokenFiles();
        Map<String, Integer> importedTokenFileStartOffsets
                = summary.getImportedTokenFileIdStartOffsets();
        Map<String, Integer> importedTokenFileEndOffsets
                = summary.getImportedTokenFileIdEndOffsets();
        for (String importedTokenFileName : importedTokenFileNames) {
            String importedTokenFileWithExt = importedTokenFileName + ".tokens";
            // We look for the token file in the same directory as its importing
            // grammar
            Path importedTokenFilePath = par.get().resolve(importedTokenFileWithExt);
            boolean found = false;
//            System.out.println("We look for in: " + importedTokenFilePath);
            if (!Files.exists(importedTokenFilePath)) {
                System.out.println("Token file path nonexistent: " + importedTokenFilePath
                        + " parsedGrammarDirectory " + par.get()
                        + " antlrImportDir " + importDir
                        + " importedTokenFileWithExt " + importedTokenFileWithExt
                        + " importedTokenFileName " + importedTokenFileName);

                if (grammarFilePath.isPresent()) {
                    importedTokenFilePath = grammarFilePath.get().getParent().resolve(
                            importedTokenFileWithExt);
                }
                if (Files.exists(importedTokenFilePath)) {
                    found = true;
                } else if (importDir.isPresent() && !importDir.get().equals(par.get())) {
                    importedTokenFilePath = Paths.get(importDir.get().toString(),
                            importedTokenFileWithExt);
//                    System.out.println("We look for in: " + importedTokenFilePath);
                    if (!Files.exists(importedTokenFilePath)) {
//                        System.out.println("File not found in import directory");
                        // We look for the token file in destination directory
                        // appended with local importing grammar directory
                        importedTokenFilePath
                                = destDir.get().resolve(parsedGrammarDirRelativePathString);
//                                Paths.get(antlrDestinationDir,
//                                parsedGrammaDirRelativePathString,
//                                importedTokenFileWithExt);
//                        System.out.println("We look for in: " + importedTokenFilePath);
                        if (!Files.exists(importedTokenFilePath)) {
                            found = false;
                            importedTokenFilePath = null;
                        } else {
                            found = true;
                        }
                    }
                } else {
                    found = true;
                }
                if (!found || importedTokenFilePath == null) {
                    // We look for the token file in destination directory
                    // appended with local importing grammar directory
                    importedTokenFilePath
                            = destDir.get().resolve(parsedGrammarDirRelativePathString)
                                    .resolve(importedTokenFileWithExt);
                    if (!Files.exists(importedTokenFilePath)) {
                        found = false;
                        importedTokenFilePath = null;
                    } else {
                        found = true;
                    }
                }
            } else {
                found = true;
            }
            if (!found) {
                int startOffset
                        = importedTokenFileStartOffsets.get(importedTokenFileName);
                int endOffset
                        = importedTokenFileEndOffsets.get(importedTokenFileName) + 1;
                String key = "antlr.error.imported.token.file.does.not.exist";
                String displayName
                        = "Unable to find imported token file '"
                        + importedTokenFileName
                        + "' in the same directory as current grammar"
                        + " or in import directory or in destination"
                        + " directory (in same relative subdir as "
                        + "current grammar)";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    private void parseGrammarFile(Document grammarDoc,
            Path grammarFilePath) {
//        System.out.println("ANTLRv4SemanticParser : grammarFileName=" +
//                           grammarFileName);
        // We does not need to generate hyperlinks for that imported grammar file
        // Its hyperlinks will be generated when user will open it (if he does it)
        try {
            if (grammarDoc == null) {
//                System.err.println("GRAMMAR DOC FOR " + grammarFilePath + " is null");
                return;
            }
            String contentToBeParsed = grammarDoc.getText(0, grammarDoc.getLength());
            try (
                    Reader sr = new StringReader(contentToBeParsed);) {
                ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromReader(sr));
                lexer.removeErrorListeners();

                CommonTokenStream tokens = new CommonTokenStream(lexer);
                ANTLRv4Parser parser = new ANTLRv4Parser(tokens);
                parser.removeErrorListeners(); // remove ConsoleErrorListener
                Collector collector = new Collector(grammarDoc, Optional.of(grammarFilePath));
                parser.addParseListener(collector);
                parser.grammarFile();
            }
        } catch (IOException
                | RecognitionException
                | BadLocationException ex) {
            // Impossible to have BadLocationException with parameter used with
            // getText().
            // A recognitionException is still possible if lexer or parser had a
            // problem.
            // Idem for IOException because we have already everything in memory.
            // So there is nothing to do
            ex.printStackTrace();
        }
    }

    protected void checkThereisNoDuplicateMode() {
        List<String> modes = summary.getChannels();
        ListIterator<String> modeIt = modes.listIterator();
        while (modeIt.hasNext()) {
            String channelId = modeIt.next();
            int currentPosition = modeIt.nextIndex() - 1;
            int otherChannelOffsetWithSameId = modes.lastIndexOf(channelId);
            if (otherChannelOffsetWithSameId != currentPosition) {
                if (semanticErrorRequired) {
                    Map<String, Integer> channelStartOffsets
                            = summary.getChannelStartOffsets();
                    Map<String, Integer> channelEndOffsets
                            = summary.getChannelEndOffsets();
                    int startOffset = channelStartOffsets.get(channelId);
                    int endOffset = channelEndOffsets.get(channelId);
                    String key = "antlr.error.channels."
                            + "there.may.not.be.two."
                            + "channels.with.same.id";
                    String displayName
                            = "A channel called '" + channelId
                            + "' is already defined";
                    String description = displayName;
                    addError(key, startOffset, endOffset, displayName, description);
                }
            }
        }
    }

    // Normally, DEFAULT_MODE="DEFAULT_MODE"
    protected static final String DEFAULT_MODE = ANTLRv4Lexer.modeNames[0];

    protected void checkThereisAtLeastOneLexerCommandPassingInEachMode() {
        // We scan all modes
        List<String> modes = summary.getModes();
        Map<String, Integer> modeStartOffsets = summary.getModeStartOffsets();
        Map<String, Integer> modeEndOffsets = summary.getModeEndOffsets();
        for (String modeId : modes) {
            if (!modeId.equals(DEFAULT_MODE)) {
                if (numberOfRulesPassingInAMode.get(modeId) == null) {
                    String key = "antlr.warning.semantic.at.least.one.token"
                            + ".rule.must.pass.in.mode";
                    int startOffset = modeStartOffsets.get(modeId);
                    int endOffset = modeEndOffsets.get(modeId);
                    String displayName = "At least one rule from another mode "
                            + "must pass (mode or pushMode lexer "
                            + "command) in tthat mode";
                    String description = displayName;
                    addError(
                            Severity.WARNING,
                            key,
                            startOffset,
                            endOffset,
                            displayName,
                            description);
                }
            }
        }
    }

    protected void checkThereisAtLeastOneLexerRuleInEachMode() {
//        System.out.println("ANTLRv4SemanticParser:checkThereisAtLeastOneLexerRuleInEachMode() : begin");
        List<String> modes = summary.getModes();
        Map<String, List<String>> tokenRuleIdsOfMode
                = summary.getTokenRuleIdsOfMode();
        for (String mode : modes) {
            List<String> tokenRuleIdsOfCurrentMode
                    = tokenRuleIdsOfMode.get(mode);
            if (tokenRuleIdsOfCurrentMode == null) {
                String key = "antlr.warning.semantic.at.least.one.token.rule."
                        + "must.be.declared.per.mode";
//                System.out.println("no rule in mode=" + mode);
                Map<String, Integer> modeStartOffsets = summary.getModeStartOffsets();
                Map<String, Integer> modeEndOffsets = summary.getModeEndOffsets();
                int startOffset = modeStartOffsets.get(mode);
                int endOffset = modeEndOffsets.get(mode);
                String displayName = "At least one token rule must be  "
                        + "defined in each mode";
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        startOffset,
                        endOffset,
                        displayName,
                        description);
            }
        }
//        System.out.println("ANTLRv4SemanticParser:checkThereisAtLeastOneLexerRuleInEachMode() : end");
    }

    protected void checkThereisNoDuplicateChannel() {
        List<String> channels = summary.getChannels();
        ListIterator<String> channelIt = channels.listIterator();
        while (channelIt.hasNext()) {
            String channelId = channelIt.next();
            int currentPosition = channelIt.nextIndex() - 1;
            int otherChannelOffsetWithSameId = channels.lastIndexOf(channelId);
            if (otherChannelOffsetWithSameId != currentPosition) {
                Map<String, Integer> channelStartOffsets
                        = summary.getChannelStartOffsets();
                Map<String, Integer> channelEndOffsets
                        = summary.getChannelEndOffsets();
                int startOffset = channelStartOffsets.get(channelId);
                int endOffset = channelEndOffsets.get(channelId);
                String key = "antlr.error.channels.there.may.not.be.two."
                        + "channels.with.same.id";
                String displayName = "A channel called '" + channelId
                        + "' is already defined";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    protected void checkAllDeclaredChannelsAreUsed() {
        List<String> channels = summary.getChannels();
        Map<String, Integer> channelStartOffsets = summary.getChannelStartOffsets();
        Map<String, Integer> channelEndOffsets = summary.getChannelEndOffsets();
        for (String channel : channels) {
            if (!usedChannels.contains(channel)) {
                String key = "antlr.warning.semantic.unused.channel";
                int startOffset = channelStartOffsets.get(channel);
                int endOffset = channelEndOffsets.get(channel);
                String displayName = "channel " + channel + " is unused";
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        startOffset,
                        endOffset,
                        displayName,
                        description);
            }
        }
    }

    private static final String EOF = "EOF";
    private static final List<String> FORBIDDEN_TOKEN_IDS
            = Arrays.asList(new String[]{EOF});

    public void checkThereIsNoForbiddenTokenID() {
        // We scan all token declared in tokens block
        List<String> tokenIds = summary.getTokens();
        for (String tokenId : tokenIds) {
            if (FORBIDDEN_TOKEN_IDS.contains(tokenId)) {
                String key = "antlr.error.token.id.forbiden.value";
                int startOffset = summary.getTokenOffsets().get(tokenId);
                int endOffset = startOffset + tokenId.length();
                String displayName = "You cannot use '" + tokenId
                        + "' for identifying a token\n";
                if (tokenId.equals(EOF)) {
                    displayName
                            += "It is a reserved fragment id identifying the end of file";
                }
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }

        // We scan all token rules
        List<String> tokenRuleIds = summary.getTokenRuleIds();
        for (String tokenRuleId : tokenRuleIds) {
            if (FORBIDDEN_TOKEN_IDS.contains(tokenRuleId)) {
                String key = "antlr.error.token.id.forbiden.value";
                int startOffset
                        = summary.getTokenRuleIdStartOffsets().get(tokenRuleId);
                int endOffset
                        = summary.getTokenRuleIdEndOffsets().get(tokenRuleId) + 1;
                String displayName = "You cannot use '" + tokenRuleId
                        + "' for identifying a token\n";
                if (tokenRuleId.equals(EOF)) {
                    displayName
                            += "It is a reserved fragment id identifying the end of file";
                }
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    /**
     * If two token rules have the same id, the case is processed upward in
     * Collector class. It is the same for two fragment rules having the same
     * id. So the last case not processed is the case where a token rule has the
     * same id as a fragment rule.
     */
    protected void checkThereIsNoDuplicateLexerRuleId() {
        List<String> tokenRuleIds = summary.getTokenRuleIds();
        Map<String, Integer> tokenRuleIdStartOffsets
                = summary.getTokenRuleIdStartOffsets();
        Map<String, Integer> tokenRuleIdEndOffsets
                = summary.getTokenRuleIdEndOffsets();

        List<String> fragmentIds = summary.getFragmentRuleIds();
        Map<String, Integer> fragmentRuleIdStartOffsets
                = summary.getFragmentRuleIdStartOffsets();
        Map<String, Integer> fragmentRuleIdEndOffsets
                = summary.getFragmentRuleIdEndOffsets();
        for (String tokenRuleId : tokenRuleIds) {
            if (fragmentIds.contains(tokenRuleId)) {
                String key = "antlr.error.semantic.id.already.used";
                // We determine which of the two declaration is the last one
                // This one will be marked in error.
                int tokenRuleStartOffset = tokenRuleIdStartOffsets.get(tokenRuleId);
                int fragmentRuleStartOffset = fragmentRuleIdStartOffsets.get(tokenRuleId);
                int startOffset, endOffset;
                String displayName;
                if (tokenRuleStartOffset < fragmentRuleStartOffset) {
                    // The lexer rule in error will be the fragment rule
                    startOffset = fragmentRuleStartOffset;
                    endOffset = fragmentRuleIdEndOffsets.get(tokenRuleId) + 1;
                    displayName = "this fragment rule uses an id already"
                            + " used by a token rule";
                } else {
                    // The lexer rule in error will be the token rule
                    startOffset = tokenRuleStartOffset;
                    endOffset = tokenRuleIdEndOffsets.get(tokenRuleId);
                    displayName = "this token rule uses an id already"
                            + " used by a fragment rule";
                }
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    protected void checkThereIsNoForbiddenFragmentId() {
        List<String> fragmentRuleIds = summary.getFragmentRuleIds();
        for (String fragmentRuleId : fragmentRuleIds) {
            if (EOF.equals(fragmentRuleId)) {
                String key = "antlr.error.fragment.id.forbiden.value";
                int startOffset
                        = summary.getFragmentRuleIdStartOffsets().get(fragmentRuleId);
                int endOffset
                        = summary.getFragmentRuleIdEndOffsets().get(fragmentRuleId) + 1;
                String displayName = "You cannot use '" + fragmentRuleId
                        + "' for identifying a fragment\n"
                        + "It is a reserved fragment id identifying the end of file";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    protected void checkFragmentRuleReferences() {
        List<String> fragmentRuleIds = summary.getFragmentRuleIds();
        Set<String> lexerRuleReferenceIds = lexerRuleReferences.keySet();
        for (String lexerRuleReferenceId : lexerRuleReferenceIds) {
            // We check this lexer reference points to an existing fragment rule
            if (!fragmentRuleIds.contains(lexerRuleReferenceId)) {
                if (!lexerRuleReferenceId.equals(EOF)) {
                    RuleReference lexerRuleReference = lexerRuleReferences.get(lexerRuleReferenceId);
                    String key = "antlr.error.semantic.unable.to.find.fragment.rule";
                    int startOffset = lexerRuleReference.getStartOffset();
                    int endOffset = lexerRuleReference.getEndOffset();
                    String displayName = "unable to find a fragment rule called "
                            + lexerRuleReferenceId;
                    String description = displayName;
                    addError(key, startOffset, endOffset, displayName, description);
                }
            }
        }
    }

    /**
     * We check that each parser reference used has a correspondent parser rule
     * definition.
     *
     * For doing that we must wait that all grammar source file has been
     * semantically parsed (the parser rule declaration can be located before
     * the parser rule declaration where the parser rule reference appears or
     * after or even it can refer to the parser rule where the parser rule
     * refernece appears).
     */
    protected void checkRuleReferences() {
        // We check parser rule references
        Set<String> parserRuleReferenceIds = parserRuleReferences.keySet();
        for (String parserRuleReferenceId : parserRuleReferenceIds) {
            boolean hasReference = allParserRuleIds.contains(parserRuleReferenceId);
            if (!hasReference) {
//                System.out.println("RULE " + parserRuleReferenceId + " is not in " + allParserRuleIds);
                String key = "antlr.error.parserRule.rule.reference.has.no"
                        + ".correspondent.declaration";
                RuleReference parserRuleReference = parserRuleReferences.get(parserRuleReferenceId);
                int startOffset = parserRuleReference.getStartOffset();
                int endOffset = parserRuleReference.getEndOffset();
                String displayName = "The rule reference "
                        + parserRuleReferenceId
                        + " has no correspondent declaration";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
        // We check lexer rule references that is token and fragment rule references
        List<String> localTokenRuleIds = summary.getTokenRuleIds();
        List<String> localFragmentRuleIds = summary.getFragmentRuleIds();
        List<String> localTokenIds = summary.getTokens();
        List<String> importedTokenRuleIds = summary.getImportedTokenIds();
        List<String> importedFragmentRuleIds
                = summary.getImportedFragmentRuleIds();
        List<String> allLexerIds = new ArrayList<>();
        allLexerIds.addAll(localTokenRuleIds);
        allLexerIds.addAll(localFragmentRuleIds);
        allLexerIds.addAll(localTokenIds);
        allLexerIds.addAll(importedTokenRuleIds);
        allLexerIds.addAll(importedFragmentRuleIds);
        Set<String> lexerReferenceIds = lexerRuleReferences.keySet();
        for (String lexerReferenceId : lexerReferenceIds) {
            if (!"EOF".equals(lexerReferenceId) && !allLexerIds.contains(lexerReferenceId)) {
                // If the rule reference references a lexer rule (token or fragment rule)
                // then a simple warning if it is not found
                String key = "antlr.warning.lexerRule.implicit.definition.of"
                        + ".token";
                RuleReference lexerReference
                        = lexerRuleReferences.get(lexerReferenceId);
                int startOffset = lexerReference.getStartOffset();
                int endOffset = lexerReference.getEndOffset();
                String displayName = "implicit definition of token "
                        + lexerReferenceId;
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        startOffset,
                        endOffset,
                        displayName,
                        description);
            }
        }
    }

    protected void checkThereIsNoDuplicateParserRuleIds() {
        // We scan only local parser rules...
        for (String parserRuleId : parserRuleIds) {
            // ... But we look for its position in the set of all parser rules
            // (local and imported ones)...
            int firstIndex = allParserRuleIds.indexOf(parserRuleId);
            // ... and we compare it with the last occurrence of this id in the
            // same set
            int lastIndex = allParserRuleIds.lastIndexOf(parserRuleId);
            if (firstIndex != lastIndex) {
                String key = "antlr.error.parserRule.duplicate.rule.id";
                // If there is a duplicate, then the start and end offset for
                // parserRuleId point to the last occurrence of parserRuleId
                // declaration
                int startOffset
                        = summary.getParserRuleIdStartOffsets().get(parserRuleId);
                int endOffset
                        = summary.getParserRuleIdEndOffsets().get(parserRuleId) + 1;
                String displayName = "The parser rule declaration "
                        + parserRuleId + " already exists";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
        }
    }

    private static final List<String> FORBIDDEN_PARSER_RULE_IDS
            = Arrays.asList(new String[]{"rule", "parserRule"});
    private static final List<String> NOT_RECOMMENDED_PARSER_RULE_IDS
            = Arrays.asList(new String[]{"interpreterRule", "arrayPrediction", "emptyPrediction",
        "semantic", "singletonPrediction", "prediction"});

    protected void checkThereIsNoForbiddenParserRuleId() {
        // We scan only local parser rules...
        for (String parserRuleId : parserRuleIds) {
            if (FORBIDDEN_PARSER_RULE_IDS.contains(parserRuleId)) {
                String key = "antlr.error.parserRule.id.forbiden.value";
                // If there is a duplicate, then the start and end offset for
                // parserRuleId point to the last occurrence of parserRuleId
                // declaration
                int startOffset
                        = summary.getParserRuleIdStartOffsets().get(parserRuleId);
                int endOffset
                        = summary.getParserRuleIdEndOffsets().get(parserRuleId) + 1;
                String displayName = "You cannot use '" + parserRuleId
                        + "' for identifying a parser rule";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            }
            if (NOT_RECOMMENDED_PARSER_RULE_IDS.contains(parserRuleId)) {
                String key = "antlr.warning.parserRule.id.not.recommended.value";
                // If there is a duplicate, then the start and end offset for
                // parserRuleId point to the last occurrence of parserRuleId
                // declaration
                int startOffset
                        = summary.getParserRuleIdStartOffsets().get(parserRuleId);
                int endOffset
                        = summary.getParserRuleIdEndOffsets().get(parserRuleId) + 1;
                char firstChar = parserRuleId.charAt(0);
                String generatedClassName = parserRuleId.replaceFirst(String.valueOf(firstChar),
                        String.valueOf(Character.toUpperCase(firstChar)));
                generatedClassName += "Context";
                String displayName = "Dangerous choice of id: '"
                        + parserRuleId + "'!\n"
                        + "It will lead to the creation of a class called '"
                        + generatedClassName + "' that is member of ANTLR framework";
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        startOffset,
                        endOffset,
                        displayName,
                        description);
            }
        }
    }

    /**
     * We check that each parser rule declared in that file is used in that file
     * except the first one which is the entry point. But there may be several
     * entry points so we generate only a warning.
     */
    protected void checkAllDeclaredParserRulesAreUsed() {
        for (String parserRuleId : parserRuleIds) {
            if (!parserRuleReferences.containsKey(parserRuleId)
                    && !parserRuleId.equals(firstParserRuleDeclaration)) {
                String key = "antlr.warning.parserRule.declaration.used.nowhere";
                int startOffset = summary.getParserRuleIdStartOffsets().get(parserRuleId);
                int endOffset = summary.getParserRuleIdEndOffsets().get(parserRuleId);
                String displayName = "The rule declaration " + parserRuleId
                        + " is used nowhere";
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        startOffset,
                        endOffset,
                        displayName,
                        description);
            }
        }
    }

    public List<EbnfElement> ebnfRanges() {
        return EbnfElement.coalesce(ebnfs);
    }

    private void addEbnf(ParserRuleContext repeated, EbnfSuffixContext suffix) {
        if (suffix == null || repeated == null) {
            return;
        }
        String ebnfString = suffix.getText();
        if (!ebnfString.isEmpty()) {
            boolean isAtLeastOne = suffix.PLUS() != null;
            boolean isGreedy = suffix.QUESTION() == null || suffix.QUESTION().isEmpty();
            int start = repeated.getStart().getStartIndex();
            int end = suffix.getStop().getStopIndex() + 1;
            boolean isQuestionMark = "?".equals(ebnfString);
            ebnfs.add(new EbnfElement(start, end, isAtLeastOne, isGreedy, isQuestionMark));
        }
    }

    @Override
    public void exitEbnf(ANTLRv4Parser.EbnfContext ctx) {
        addEbnf(ctx.block(), ctx.ebnfSuffix());
    }

    @Override
    public void exitParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx) {
        if (ctx.parserRuleAtom() != null) {
            addEbnf(ctx.parserRuleAtom(), ctx.ebnfSuffix());
        } else if (ctx.labeledParserRuleElement() != null) {
            addEbnf(ctx.labeledParserRuleElement(), ctx.ebnfSuffix());
        }
    }

    @Override
    public void exitLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx) {
        if (ctx.lexerRuleAtom() != null) {
            addEbnf(ctx.lexerRuleAtom(), ctx.ebnfSuffix());
        } else if (ctx.lexerRuleElementBlock() != null) {
            addEbnf(ctx.lexerRuleElementBlock(), ctx.ebnfSuffix());
        }
    }

    public List<BlockElement> blocks() {
        return blocks;
    }

    @Override
    public void enterBlock(ANTLRv4Parser.BlockContext ctx) {
        blockDepth++;
    }

    @Override
    public void exitBlock(ANTLRv4Parser.BlockContext ctx) {
        blocks.add(new BlockElement(ctx.getStart().getStopIndex() + 1,
                ctx.getStop().getStartIndex(), blockDepth));
        blockDepth--;
    }

    protected void checkThereIsAtLeastARule() {
        boolean error = false;
        switch (grammarType) {
            case LEXER:
                // We build the list of token rules (defined locally or imported)
                List<String> localTokenRuleIds = summary.getTokenRuleIds();
                List<String> importedTokenRuleIds = summary.getImportedTokenIds();
                // We must have at least one token rule declared locally or imported
                if (localTokenRuleIds.isEmpty()
                        && importedTokenRuleIds.isEmpty()) {
                    error = summary.getFragmentRuleIds().isEmpty();
                }
                break;
            case PARSER:
            case COMBINED:
                // We build the list of parser rules (defined locally or imported)
                List<String> localParserRuleIds = summary.getParserRuleIds();
                List<String> importedParserRuleIds = summary.getImportedParserRuleIds();
                // We must have at least one token rule declared locally or imported
                if (localParserRuleIds.isEmpty()
                        && importedParserRuleIds.isEmpty()) {
                    error = summary.getFragmentRuleIds().isEmpty();
                }
                break;
            default:
        }
        if (error) {
            String key = "antlr.error.semantic.global";
            int startOffset = summary.getGrammarIdStartOffset();
            int endOffset = summary.getGrammarIdEndOffset();
            String displayName = "A grammar file must have at least one rule";
            String description = "A lexer grammar file must have at least one"
                    + "token declaration rule\n"
                    + "A parser grammar file must have at least one"
                    + " parser rule\n"
                    + "a combined grammar file must have at least"
                    + "one perser rule and one lexer rule (defined"
                    + " in the file or imported)";
            addError(key, startOffset, endOffset, displayName, description);
        }
    }
}
