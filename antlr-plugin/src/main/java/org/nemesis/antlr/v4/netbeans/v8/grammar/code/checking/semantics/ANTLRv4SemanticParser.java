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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.bcel.classfile.JavaClass;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;
import org.nemesis.antlr.v4.netbeans.v8.generic.parsing.ParsingError;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.ANTLRv4GrammarChecker;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.NBANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ChannelsSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ClassIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.DelegateGrammarContext;
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
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.SuperClassSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TerminalContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenRuleDeclarationContext;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarSummary;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.nemesis.antlr.v4.netbeans.v8.project.helper.java.JavaClassHelper;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.SemanticRegions;
import com.mastfrog.graph.StringGraph;
import org.nemesis.data.named.NamedRegionReferenceSet;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.source.api.ParsingBag;
import org.netbeans.api.project.Project;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.netbeans.spi.project.support.ant.PropertyProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class ANTLRv4SemanticParser extends ANTLRv4BaseListener {

    private final GrammarSummary summary;
    private final GrammarType grammarType;

    private final List<String> parserRuleIds = new LinkedList<>();

    private String firstParserRuleDeclaration;
    private String firstImportedParserRule;
    private final Map<String, Integer> numberOfRulesPassingInAMode = new HashMap<>();

    private final List<String> usedChannels = new ArrayList<>();

    private final Set<String> alternatives = new HashSet<>();
    private final Set<String> elementLabels = new HashSet<>();

    private final List<ParsingError> semanticErrors = new LinkedList<>();
    private final List<ParsingError> semanticWarnings = new LinkedList<>();
    private final boolean semanticErrorRequired;
    private final GrammarSource<?> source;

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
        return source.lookup(Path.class);
    }

    private Optional<Path> grammarFileParent() {
        Optional<Path> grammarFilePath = grammarFilePath();
        if (grammarFilePath.isPresent()) {
            return Optional.of(grammarFilePath.get().getParent());
        }
        return Optional.empty();
    }

    private Optional<String> grammarNameFromFile() {
        Optional<Path> grammarFilePath = source.lookup(Path.class);
        if (grammarFilePath.isPresent()) {
            String filename = grammarFilePath.get().getFileName().toString();
            int ix = filename.lastIndexOf('.');
            if (ix > 0) {
                return Optional.of(filename.substring(0, ix));
            }
        }
        return Optional.empty();
    }

    public Optional<Project> project() {
        return source.lookup(Project.class);
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

    public ANTLRv4SemanticParser(GrammarSource<?> src, GrammarSummary summary) {
        assert summary != null;
        this.source = src;
        this.summary = summary;
        grammarType = summary.getGrammarType();
        semanticErrorRequired = checkSemanticErrorRequired();
        parserRuleIds.addAll(summary.getParserRuleIds());
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

    private Extraction extraction;

    public Extraction extraction() {
        return extraction;
    }

    @Override
    public void exitGrammarFile(ANTLRv4Parser.GrammarFileContext ctx) {
        extraction = AntlrExtractor.getDefault().extract(ctx, source);
    }

    public StringGraph ruleTree() {
        return extraction == null ? null : extraction.referenceGraph(AntlrKeys.RULE_NAME_REFERENCES);
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

    /**
     * Is called from a lexer or parser rule when its definition contains a
     * string literal or a lexer rule reference.
     *
     * @param ctx
     */
    @Override
    public void exitTerminal(TerminalContext ctx) {
        TerminalNode idTN = ctx.TOKEN_ID();
        if (idTN == null) {
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
                    break;
                case PARSER:

                    checkThereIsNoDuplicateParserRuleIds();
                    checkThereIsNoForbiddenParserRuleId();
                    checkRuleReferences();
                    checkDuplicateLabels();
                    checkAllDeclaredParserRulesAreUsed();
                    break;
                case COMBINED:
                    checkThereIsNoForbiddenFragmentId();
                    checkThereIsNoDuplicateLexerRuleId();

                    checkThereIsNoDuplicateParserRuleIds();
                    checkThereIsNoForbiddenParserRuleId();
                    checkRuleReferences();
                    checkAllDeclaredParserRulesAreUsed();
                    checkDuplicateLabels();
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

    private Optional<GrammarSource<?>> resolveGrammarFile(String name) {
        return Optional.ofNullable(source.resolveImport(name));
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
        return AntlrFolders.IMPORT.getPath(project(), grammarFilePath());
    }

    protected void checkImportedGrammars() {
        List<String> importedGrammars = summary.getImportedGrammars();

        for (String importedGrammar : importedGrammars) {
            Optional<GrammarSource<?>> imported = resolveGrammarFile(importedGrammar);
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
                        + importedGrammar + " in the directory of "
                        + fileParentString() + " or in ANTLR import directory "
                        + importPath();
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            } else {
                GrammarSource<?> importedSource = imported.get();
                // The imported file exists!
                // We must check if its type is compatible with the type of current
                // grammar. But we don't have this data that is placed in its
                // summary

                ParsingBag bag = ParsingBag.forGrammarSource(importedSource);
                GrammarSummary importedGrammarSummary = bag.get(GrammarSummary.class);
                if (importedGrammarSummary == null) {
//                    NBANTLRv4Parser.parse(bag);
                    Optional<Project> prj = source.lookup(Project.class);
                    if (prj.isPresent()) {
                        importedGrammarSummary = GrammarSummary.load(importedSource);
                    }
                    if (importedGrammarSummary == null) {
                        try {
                            ANTLRv4GrammarChecker parsed = NBANTLRv4Parser.parse(imported.get());
                            importedGrammarSummary = parsed.check();
                        } catch (IOException ioe) {
                            Exceptions.printStackTrace(ioe);
                        }
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
        ParsingError semanticError = new ParsingError(source,
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
        List<String> importedTokenFileNames = summary.getImportedTokenFiles();
        Map<String, Integer> importedTokenFileStartOffsets
                = summary.getImportedTokenFileIdStartOffsets();
        Map<String, Integer> importedTokenFileEndOffsets
                = summary.getImportedTokenFileIdEndOffsets();
        for (String importedTokenFileName : importedTokenFileNames) {
            Optional<Path> tokensFilePath = ProjectHelper.findTokensFile(importedTokenFileName, source);
            if (!tokensFilePath.isPresent()) {
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
        NamedSemanticRegions<RuleTypes> names = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        if (names.contains(EOF)) {
            NamedSemanticRegion<RuleTypes> eofRegion = names.regionFor(EOF);
            String key = "antlr.error.fragment.id.forbiden.value";
            int startOffset
                    = eofRegion.start();
            int endOffset
                    = eofRegion.end();
            String displayName = "You cannot use '" + EOF
                    + "' for identifying a fragment\n"
                    + "It is a reserved fragment id identifying the end of file";
            String description = displayName;
            addError(key, startOffset, endOffset, displayName, description);

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
        Attributions<GrammarSource<?>, NamedSemanticRegions<RuleTypes>, NamedSemanticRegion<RuleTypes>, RuleTypes> resInfo;
        try {
            resInfo = extraction.resolveUnknowns(AntlrKeys.RULE_NAME_REFERENCES, AntlrExtractor.resolver());
        } catch (IOException ioe) {
            Exceptions.printStackTrace(ioe);
            return;
        }
        // XXX we need to track which unknown refs were likely trying to be a parser
        // rule reference
        SemanticRegions<UnknownNameReference> unresolved = resInfo.remainingUnattributed();
        for (SemanticRegion<UnknownNameReference> r : unresolved) {
            UnknownNameReference<?> k = r.key();
            if (EOF.equals(k.name())) {
                continue;
            }
            if (k.expectedKind() == RuleTypes.PARSER) {
                String key = "antlr.error.parserRule.rule.reference.has.no"
                        + ".correspondent.declaration";
                int startOffset = r.start();
                int endOffset = r.end();
                String displayName = "The rule reference "
                        + r.key().name()
                        + " has no correspondent declaration";
                String description = displayName;
                addError(key, startOffset, endOffset, displayName, description);
            } else {
                String key = "antlr.warning.lexerRule.implicit.definition.of"
                        + ".token";
                String displayName = "implicit definition of token "
                        + k.name();
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        r.start(),
                        r.end(),
                        displayName,
                        description);
            }
        }
    }

    protected void checkThereIsNoDuplicateParserRuleIds() {

        Map<String, Set<NamedSemanticRegion<RuleTypes>>> duplicates = extraction.duplicates(AntlrKeys.RULE_NAMES);
        for (Map.Entry<String, Set<NamedSemanticRegion<RuleTypes>>> e : duplicates.entrySet()) {
            for (NamedSemanticRegion<RuleTypes> r : e.getValue()) {

                String key = "antlr.error." + r.kind() + ".duplicate.rule.id";
                String displayName = "The " + r.kind() + " rule declaration "
                        + e.getKey() + " occurs more than once";
                String description = displayName;
                addError(key, r.start(), r.end(), displayName, description);
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
        NamedRegionReferenceSets<RuleTypes> refs = extraction.nameReferences(AntlrKeys.RULE_NAME_REFERENCES);
        if (refs == null || refs.isEmpty()) {
            return;
        }
        NamedSemanticRegions<RuleTypes> names = extraction.namedRegions(AntlrKeys.RULE_NAMES);
        names.collectNames(item -> {
            return item.kind() == RuleTypes.PARSER;
        }).forEach(ruleName -> {
            NamedRegionReferenceSet<RuleTypes> set = refs.references(ruleName);
            if (set == null || set.isEmpty()) {
                NamedSemanticRegion<RuleTypes> reg = names.regionFor(ruleName);
                if (reg == null || reg.ordering() == 0) {
                    return;
                }
                String key = "antlr.warning.parserRule.declaration.used.nowhere";
                String displayName = "The rule declaration " + ruleName
                        + " is used nowhere";
                String description = displayName;
                addError(
                        Severity.WARNING,
                        key,
                        reg.start(),
                        reg.end(),
                        displayName,
                        description);
            };
        });
    }

    protected void checkDuplicateLabels() {
        Map<String, Set<NamedSemanticRegion<RuleTypes>>> duplicates = extraction.duplicates(AntlrKeys.NAMED_ALTERNATIVES);
        if (duplicates != null) {
            for (Map.Entry<String, Set<NamedSemanticRegion<RuleTypes>>> e : duplicates.entrySet()) {
                for (NamedSemanticRegion<RuleTypes> nr : e.getValue()) {
                    addError("antlr.dup.label", nr.start(), nr.end(), "Duplicate label",
                            "The label '" + nr.name() + " occurs more than once");
                }
            }
        }
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
