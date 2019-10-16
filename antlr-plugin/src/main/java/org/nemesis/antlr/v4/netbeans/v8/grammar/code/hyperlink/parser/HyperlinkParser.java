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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink.parser;

import java.io.File;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.antlr.v4.runtime.Token;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.AntlrFolders;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink.Hyperlink;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink.HyperlinkCategory;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.hyperlink.Hyperlinks;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ClassIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.DelegateGrammarContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.GrammarIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.IdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComChannelContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComModeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexComPushModeContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.LexerCommandContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleIdentifierContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.ParserRuleReferenceContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.SuperClassSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TerminalContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenLabelTypeSpecContext;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.TokenVocabSpecContext;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrExtractor.MISSING_ID;

import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarSummary;

import org.nemesis.antlr.v4.netbeans.v8.project.helper.ProjectHelper;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.api.ParsingBag;
import org.netbeans.api.project.Project;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class HyperlinkParser extends ANTLRv4BaseListener {
    private final GrammarSummary summary;
    private final Hyperlinks     links;
    
    
    private final List<String>                 javaImports;
    
 // Regroups local and imported:
 // - token rule ids,
 // - token declared in tokens block,
 // - token imported from tokens file,
    
 // Regroups local and imported :
 // - token rule ids,
 // - token declared in tokens block,
 // - token imported from tokens file,
 // - fragment rule ids.
    private final List<String>                 allTokenIds;
    private final Map<String, Integer>         allTokenIdOffsets;
    private final Map<String, String>          allTokenIdFilePaths;
    
    private final List<String>                 allLexerIds;
    private final Map<String, Integer>         allLexerIdOffsets;
    private final Map<String, String>          allLexerIdFilePaths;
    
    private final List<String>                 allTokenLiterals;
    private final Map<String, Integer>         allTokenLiteralOffsets;
    private final Map<String, String>          allTokenLiteralFilePaths;
    
    private final List<String>                 allParserRuleIds;
    private final Map<String, Integer>         allParserRuleIdStartOffsets;
    private final Map<String, Integer>         allParserRuleIdEndOffsets;
    private final Map<String, String>          allParserRuleIdFilePaths;
    private final ParsingBag bag;
    
    public HyperlinkParser
            (ParsingBag  bag) {
        assert null != bag : "bag null";
        this.bag = bag;
        this.summary = bag.get(GrammarSummary.class);
        assert summary != null : "summary not present in parsing bag";
        this.links = new Hyperlinks();
        // If doc has a previous hyperlinks then it is overwritten
        bag.put(Hyperlinks.class, links);

     // We recover the FileObject of the current document
        
     // We will need to know in which project we are for being able to determine
     // the project type (ant-based or Maven-based)
        this.javaImports = summary.getJavaImports();
        
        this.allTokenIds = new ArrayList<>();
        this.allTokenIds.addAll(summary.getTokens());
        this.allTokenIds.addAll(summary.getTokenRuleIds());
        this.allTokenIds.addAll(summary.getImportedTokenIds());
        
        this.allTokenIdOffsets = new HashMap<>();
        this.allTokenIdOffsets.putAll(summary.getTokenOffsets());
        this.allTokenIdOffsets.putAll(summary.getTokenRuleIdStartOffsets());
        this.allTokenIdOffsets.putAll(summary.getImportedTokenIdOffsets());
        
        this.allTokenIdFilePaths = new HashMap<>();
        String sourceFilePathString = summary.getSourceFilePath().toString();
        for (String lexerId : summary.getTokens()) {
            allTokenIdFilePaths.put(lexerId, sourceFilePathString);
        }
        for (String lexerRuleId : summary.getTokenRuleIds()) {
            allTokenIdFilePaths.put(lexerRuleId, sourceFilePathString);
        }
        this.allTokenIdFilePaths.putAll(summary.getImportedTokenFilePaths());
        
        this.allLexerIds = new ArrayList<>();
        this.allLexerIds.addAll(allTokenIds);
        this.allLexerIds.addAll(summary.getFragmentRuleIds());
        this.allLexerIds.addAll(summary.getImportedFragmentRuleIds());
        
        this.allLexerIdOffsets = new HashMap<>();
        this.allLexerIdOffsets.putAll(allTokenIdOffsets);
        this.allLexerIdOffsets.putAll(summary.getFragmentRuleIdStartOffsets());
        this.allLexerIdOffsets.putAll(summary.getImportedFragmentRuleIdOffsets());
        
        this.allLexerIdFilePaths = new HashMap<>();
        this.allLexerIdFilePaths.putAll(allTokenIdFilePaths);
        for (String fragmentRuleId : summary.getFragmentRuleIds()) {
            allLexerIdFilePaths.put(fragmentRuleId, sourceFilePathString);
        }
        this.allLexerIdFilePaths.putAll(summary.getImportedFragmentFilePaths());
        
        this.allTokenLiterals = new ArrayList<>();
        this.allTokenLiterals.addAll(summary.getImportedTokenLiterals());
        
        this.allTokenLiteralOffsets = new HashMap<>();
        this.allTokenLiteralOffsets.putAll(summary.getImportedTokenLiteralOffsets());
        
        this.allTokenLiteralFilePaths = new HashMap<>();
        this.allTokenLiteralFilePaths.putAll(summary.getImportedTokenLiteralFilePaths());
        
        this.allParserRuleIds = new ArrayList<>();
        this.allParserRuleIds.addAll(summary.getParserRuleIds());
        this.allParserRuleIds.addAll(summary.getImportedParserRuleIds());
        
        this.allParserRuleIdStartOffsets = new HashMap<>();
        this.allParserRuleIdStartOffsets.putAll
                                        (summary.getParserRuleIdStartOffsets());
        this.allParserRuleIdStartOffsets.putAll
                                (summary.getImportedParserRuleIdStartOffsets());
        
        this.allParserRuleIdEndOffsets = new HashMap<>();
        this.allParserRuleIdEndOffsets.putAll
                                          (summary.getParserRuleIdEndOffsets());
        this.allParserRuleIdEndOffsets.putAll
                                  (summary.getImportedParserRuleIdEndOffsets());
        
        this.allParserRuleIdFilePaths = new HashMap<>();
        for (String parserRuleId : summary.getParserRuleIds()) {
            allParserRuleIdFilePaths.put(parserRuleId, sourceFilePathString);
        }
        this.allParserRuleIdFilePaths.putAll(summary.getImportedParserRuleFilePaths());
    }
    
    
 /**
  * Called at the end of an import statement.
  * 
  * @param ctx 
  */
    @Override
    public void exitDelegateGrammar(DelegateGrammarContext ctx) {
        GrammarIdentifierContext gic = ctx.grammarIdentifier();
        if (gic != null) {
            IdentifierContext ic = gic.identifier();
        
            Token token = ic.ID().getSymbol();
            int start = token.getStartIndex();
            int end   = token.getStopIndex() + 1;
            String targetWord = token.getText();
        
            if (!targetWord.equals("<missing ID>")) {
                String importedGrammarName = ic.getText();

                GrammarSource<?> importedSource = bag.source().resolveImport(importedGrammarName);
                if (importedSource == null) {
//                    System.out.println("NO docFO in HyperlinkParser " + this);
                    return;
                }
                Optional<Path> importedGrammarFilePathOpt = importedSource.lookup(Path.class);
                if (!importedGrammarFilePathOpt.isPresent()) {
                    return;
                }
                Path importedGrammarFilePath = importedGrammarFilePathOpt.get();
                File importedGrammarFile = importedGrammarFilePath.toFile();
                Hyperlink link = new Hyperlink(start                    ,
                                               end                      , 
                                               HyperlinkCategory.GRAMMAR,
                                               importedGrammarFile      ,
                                               targetWord               ,
                                               0                        );
                links.addLink(link);
            }
        }
    }
    
    
 /**
  * Called at the end of a superClass option declaration
  * @param ctx 
  */
    @Override
    public void exitSuperClassSpec(SuperClassSpecContext ctx) {
        Optional<Path> sourceFolder = AntlrFolders.SOURCE.getPath(bag.source().lookup(Project.class), bag.source().lookup(Path.class));

        Optional<Project> projectOpt = bag.source().lookup(Project.class);
        if (!projectOpt.isPresent()) {
            return;
        }
        Project project = projectOpt.get();

        // XXX it is very clear that the original version of this code never
        // worked and never was tested - fullQualifiedImportedTokenClass.replace(".", "/") was
        // never going to work as intended ("." will replace the first character in the string
        // and nothing more).  This code also will not hyperlink correctly if the
        // file is in a JAR on the classpath.  Perhaps we should delete all of it.

        
//        System.out.println("HyperlinkParser:exitSuperClassSpec() : begin");
     // A class identifier may contain a package name so identifier returns
     // a list of identifiers
        ClassIdentifierContext cic = ctx.classIdentifier();
        if (cic != null) {
            if (!MISSING_ID.equals(cic.identifier(0).getText())) {
                int targetStart = cic.getStart().getStartIndex();
                int targetEnd   = cic.getStop().getStopIndex() + 1;
//                System.out.println("- target start =" + targetStart);
//                System.out.println("  target end   =" + targetEnd);
                String fullQualifiedSuperClass = getClassNameWithPackage(cic);
            
                int start = fullQualifiedSuperClass.lastIndexOf(".");
             // Next statement is valid even if start = -1
                String targetWord = fullQualifiedSuperClass.substring(start + 1);
                String superClassPackage;
                if (start == -1)
                    superClassPackage = "";
                else
                    superClassPackage = fullQualifiedSuperClass.substring(0, start);

//                System.out.println("- target word  =" + targetWord);
//                System.out.println("  package name =" + superClassPackage);
        
             // There is a link to fullQualifiedSuperClass transformed in directory
                String superClassFilePathString = fullQualifiedSuperClass.replace
                                                                     ('.', '/');
                superClassFilePathString += ".java";
//                System.out.println("- super class file path string = " +
//                                   superClassFilePathString             );
        
             // The first place where non generated Java source may be is:
             // - in src.dir (mandatory property) concatenated with its potential
             //   package transformed in directory structure if the project is 
             //   ant-based,
             // - in src/main/java concatenated with its potential package transformed
             //   in directory structure if project is Maven-based.
                String srcDirPathString = 
                              ProjectHelper.getJavaSourceDir(project).getPath();
                if (srcDirPathString != null) {
                    Path superClassPath = Paths.get(srcDirPathString        ,
                                                    superClassFilePathString);
                 // We add the hyperlink
                    File superClassFile = superClassPath.toFile();
                    Hyperlink link = new Hyperlink(targetStart           ,
                                                   targetEnd             ,
                                                   HyperlinkCategory.JAVA,
                                                   superClassFile        ,
                                                   targetWord            ,
                                                   0                     );
                    links.addLink(link);

                 // If no package has been specified, it is also possible that package
                 // is defined in a Java import statement. If it is the case, then this
                 // declaration nullify the previous one.
                    if ("".equals(superClassPackage)) {
                        Iterator<String> it2 = javaImports.iterator();
                        String fullQualifiedImportedClassName;
                        String importedClassName;
                        boolean found = false;
                        while (it2.hasNext() && !found) {
                            fullQualifiedImportedClassName = it2.next();
                            start = fullQualifiedImportedClassName.lastIndexOf(".");
                            importedClassName = fullQualifiedImportedClassName.substring(start + 1);
                         // here classNameWithPackage is just a class name because 
                         // superClassPackageName is empty
//                            System.out.println("- importedClassName=" + importedClassName);
//                            System.out.println("  targetWord=" + targetWord);
                            if (importedClassName.equals(targetWord)) {
                                found = true;
                                superClassFilePathString =
                                          fullQualifiedImportedClassName.replace
                                                                     ('.', '/');
                                superClassFilePathString += ".java";
//                                System.out.println("- super Class File Path String=" + superClassFilePathString);
                                superClassPath = Paths.get
                                                     (srcDirPathString        ,
                                                      superClassFilePathString);
                             // We add the hyperlink
                                superClassFile = superClassPath.toFile();
                             // A package has been found so there is no doubt the only
                             // possible link is the next qualified link so we remove
                             // the previous link
                                links.removeLink(link);
                                link = new Hyperlink(targetStart           ,
                                                     targetEnd             ,
                                                     HyperlinkCategory.JAVA,
                                                     superClassFile        ,
                                                     targetWord            ,
                                                     0                     );
                                links.addLink(link);
                            }
                        }
                    }
                }
            }
        }
//        System.out.println("HyperlinkParser:exitSuperClassSpec() : end");
    }

 /**
  * Called at the end of a tokenVocab option declaration
  * @param ctx 
  */
    @Override
    public void exitTokenVocabSpec(TokenVocabSpecContext ctx) {
        ANTLRv4Parser.IdentifierContext ic = ctx.identifier();
        if (ic != null) {
            TerminalNode idTN = ic.ID();
            if (idTN != null) {
                Token token = idTN.getSymbol();
                if (token != null) {
                    int targetStart = token.getStartIndex();
                    int targetEnd   = token.getStopIndex() + 1;
                    String targetWord = token.getText();
                    if (!targetWord.equals(MISSING_ID)) {

                        Optional<Path> existingTokensFile = ProjectHelper
                                .findTokensFile(targetWord, bag.source());
                        if (existingTokensFile.isPresent()) {
                            File tokenClassFile = existingTokensFile.get().toFile();
                            Hyperlink link = new Hyperlink
                                          (targetStart             ,
                                           targetEnd               ,
                                           HyperlinkCategory.TOKENS,
                                           tokenClassFile          ,
                                           targetWord              ,
                                           0                       );
                            links.addLink(link);
                        }
                    }
                }
            }
        }
    }
    
    
 /**
  * Called at the end of a TokenLabelType option declaration.
  * @param ctx
  */
    @Override
    public void exitTokenLabelTypeSpec(TokenLabelTypeSpecContext ctx) {
     // A class identifier may contain a package name so identifier returns
     // a list of identifiers
        ClassIdentifierContext cic = ctx.classIdentifier();
        if (cic != null) {
            if (cic.identifier(0).ID() != null) {
                if (cic.identifier(0).ID().getSymbol() != null) {
                    if (!cic.identifier(0).ID().getSymbol().getText().equals(MISSING_ID)) {
                        int targetStart = cic.getStart().getStartIndex();
                        int targetEnd   = cic.getStop().getStopIndex() + 1;
                        String fullQualifiedTokenClass = getClassNameWithPackage(cic);
                     // The first place where Java source may be is:
                     // - in antlr.generator.dest.dir (mandatory property) concatenated with
                     //   its potential package transformed in directory structure if the project is
                     //   ant-based,
                     // - in target/generated-sources/antlr4 concatenated with its potential
                     //   package transformed in directory structure if project is Maven-based.

                        Optional<Path> sourceFile = ProjectHelper.getJavaSourceFile(MISSING_ID, bag.source());
                        if (sourceFile.isPresent()){
                            int start = fullQualifiedTokenClass.lastIndexOf(".");
                            // Next statement is valid even if start = -1
                            String targetWord = fullQualifiedTokenClass.substring(start + 1);
                            Hyperlink link = new Hyperlink(targetStart           ,
                                               targetEnd             ,
                                               HyperlinkCategory.JAVA,
                                               sourceFile.get().toFile()        ,
                                               targetWord            ,
                                               0                     );
                            links.addLink(link);
                        }
                    }
                }
            }
        }
    }

    
 /**
  * Called at the end of a parser rule reference use
  * @param ctx 
  */
    @Override
    public void exitParserRuleReference(ParserRuleReferenceContext ctx) {
        ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
        if (pric != null) {
            TerminalNode pridTN = pric.PARSER_RULE_ID();
            if (pridTN != null) {
                Token id = pridTN.getSymbol();
                int linkStart = id.getStartIndex();
                int linkEnd   = id.getStopIndex() + 1;
                String targetWord = id.getText();
                
                Integer targetOffset = allParserRuleIdStartOffsets.get(targetWord);
                if (targetOffset != null) {
                    String targetFilePathString =
                                       allParserRuleIdFilePaths.get(targetWord);
/*
                    System.out.println
                              ("- HyperlinkParser::exitParserRuleReference : "+
                               "filePath=" + targetFilePathString +
                               " offset=" + targetOffset +
                               " rule ID=" + targetWord);
*/
                    Hyperlink link = new Hyperlink
                                            (linkStart                     ,
                                             linkEnd                       ,
                                             HyperlinkCategory.RULE_REF    ,
                                             new File(targetFilePathString),
                                             targetWord                    ,
                                             targetOffset                  );
                    links.addLink(link);
                }
            }
        }
    }
    
    
 /**
  * Called at the end of a terminal (token or fragment reference) (in a parser 
  * rule or in a lexer rule).
  * 
  * @param ctx 
  */
    @Override
    public void exitTerminal(TerminalContext ctx) {
//        System.out.println("HyperLinkParser:exitTerminal() : begin");
        TerminalNode idTN = ctx.TOKEN_ID();
        TerminalNode stringLiteralTN = ctx.STRING_LITERAL();
        if (idTN != null) {
            Token lexerIdToken = idTN.getSymbol();
            int lexerIdStart = lexerIdToken.getStartIndex();
            int lexerIdEnd   = lexerIdToken.getStopIndex() + 1;
            String lexerId = lexerIdToken.getText();
//            System.out.println("- grammar name=" + summary.getGrammarName());
//            System.out.println("- lexer reference encountered: " + lexerId);
         // We look for lexerId in the list of all accessible lexer ids
            if (allLexerIds.contains(lexerId)) {
                Path filePath = Paths.get(allLexerIdFilePaths.get(lexerId));
                int targetOffset = allLexerIdOffsets.get(lexerId);
                Hyperlink link = new Hyperlink(lexerIdStart              ,
                                               lexerIdEnd                ,
                                               HyperlinkCategory.RULE_REF,
                                               filePath.toFile()         ,
                                               lexerId                   ,
                                               targetOffset              );
                links.addLink(link);
            }
        } else if (stringLiteralTN != null) {
            Token stringLiteralToken = stringLiteralTN.getSymbol();
            int stringLiteralStart = stringLiteralToken.getStartIndex();
            int stringLiteralEnd   = stringLiteralToken.getStopIndex() + 1;
            String stringLiteral = stringLiteralToken.getText();
            if (allTokenLiterals.contains(stringLiteral)) {
                Path filePath = Paths.get
                                  (allTokenLiteralFilePaths.get(stringLiteral));
                int targetOffset = allTokenLiteralOffsets.get(stringLiteral);
                Hyperlink link = new Hyperlink(stringLiteralStart        ,
                                               stringLiteralEnd          ,
                                               HyperlinkCategory.RULE_REF,
                                               filePath.toFile()         ,
                                               stringLiteral             ,
                                               targetOffset              );
                links.addLink(link);
            }
        }
        
//        System.out.println("HyperLinkParser:exitTerminal() : end");
    }
    
    
    @Override
    public void exitLexerCommand(LexerCommandContext ctx) {
//        System.out.println("HyperlinkParser:exitLexerCommand(LexerCommandContext) : begin");
        TerminalNode typeCommand = ctx.LEXCOM_TYPE();
        if (typeCommand != null) {
            TerminalNode tokenIdTN = ctx.TOKEN_ID();
            if (tokenIdTN != null) {
                Token tokenIdToken = tokenIdTN.getSymbol();
                if (tokenIdToken != null) {
                    String tokenId = tokenIdToken.getText();
                    if (!tokenId.equals("<missing TOKEN_ID>")) {
                        Integer destOffset = allTokenIdOffsets.get(tokenId);
                        if (destOffset != null) {
                            int tokenStart = tokenIdToken.getStartIndex();
                            int tokenEnd   = tokenIdToken.getStopIndex() + 1;
                            Path filePath = Paths.get
                                             (allTokenIdFilePaths.get(tokenId));
                            Hyperlink link = new Hyperlink
                                              (tokenStart              ,
                                               tokenEnd                ,
                                               HyperlinkCategory.TOKENS,
                                               filePath.toFile()       ,
                                               tokenId                 ,
                                               destOffset              );
                            links.addLink(link);
                        }
                    }
                }
            }
        }
//        System.out.println("HyperlinkParser:exitLexerCommand(LexerCommandContext) : end");
    }
    
    
    @Override
    public void exitLexComMode(LexComModeContext ctx) {
        IdentifierContext ic = ctx.identifier();
        if (ic != null) {
            TerminalNode idTN = ic.ID();
            if (idTN != null) {
                Token modeIdToken = idTN.getSymbol();
                if (modeIdToken != null) {
                    String modeId = modeIdToken.getText();
                    if (!modeId.equals("<missing ID>")) {
                     // we look for offset of mode statement declaring to
                     // modeId
                        Map<String, Integer> modeStartOffsets = summary.getModeStartOffsets();
                        Integer offset = modeStartOffsets.get(modeId);
                        if (offset != null) {
                            int tokenStart = modeIdToken.getStartIndex();
                            int tokenEnd   = modeIdToken.getStopIndex() + 1;
                            Optional<Path> filePath = summary.getSourceFilePath();
                            if (filePath.isPresent()) {
//                            System.out.println("- HyperlinkParser: filePath=" + filePath + " index=" + targetOffset);
                                Hyperlink link = new Hyperlink
                                              (tokenStart                ,
                                               tokenEnd                  ,
                                               HyperlinkCategory.MODE_REF,
                                               filePath.get().toFile()         ,
                                               modeId                    ,
                                               offset                    );
                                links.addLink(link);
                            }
                        }
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
                Token modeIdToken = idTN.getSymbol();
                if (modeIdToken != null) {
                    String modeId = modeIdToken.getText();
                    if (!modeId.equals("<missing ID>")) {
                     // we look for offset of mode statement declaring to
                     // modeId
                        Map<String, Integer> modeStartOffsets = summary.getModeStartOffsets();
                        Integer offset = modeStartOffsets.get(modeId);
                        if (offset != null) {
                            int tokenStart = modeIdToken.getStartIndex();
                            int tokenEnd   = modeIdToken.getStopIndex() + 1;
                            Optional<Path> filePath = summary.getSourceFilePath();
//                            System.out.println("- HyperlinkParser: filePath=" + filePath + " index=" + targetOffset);
                            if (filePath.isPresent()) {
                                Hyperlink link = new Hyperlink
                                              (tokenStart                ,
                                               tokenEnd                  ,
                                               HyperlinkCategory.MODE_REF,
                                               filePath.get().toFile()   ,
                                               modeId                    ,
                                               offset                    );
                                links.addLink(link);
                            }
                        }
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
                Token channelIdToken = idTN.getSymbol();
                if (channelIdToken != null) {
                    String channelId = channelIdToken.getText();
                    if (!channelId.equals("<missing ID>")) {
                     // we look for offset of mode statement declaring to
                     // modeId
                        Map<String, Integer> channelOffsets = summary.getChannelStartOffsets();
                        Integer offset = channelOffsets.get(channelId);
                        if (offset != null) {
                            int tokenStart = channelIdToken.getStartIndex();
                            int tokenEnd   = channelIdToken.getStopIndex() + 1;
                            Optional<Path> filePath = summary.getSourceFilePath();
                            if (filePath.isPresent()) {
//                            System.out.println("- HyperlinkParser: filePath=" + filePath + " index=" + targetOffset);
                                Hyperlink link = new Hyperlink
                                                  (tokenStart                   ,
                                                   tokenEnd                     ,
                                                   HyperlinkCategory.CHANNEL_REF,
                                                   filePath.get().toFile()      ,
                                                   channelId                    ,
                                                   offset                       );
                                links.addLink(link);
                            }
                        }
                    }
                }
            }
        }
    }
    
    
    private String getClassNameWithPackage(ClassIdentifierContext ctx) {
        List<IdentifierContext> ics = ctx.identifier();
        Iterator<IdentifierContext> it = ics.iterator();
        IdentifierContext ic;
        Token token;
     // A word may be a package name or a class name if it is the last
     // ic of ics
        String word;
        StringBuilder classNameWithPackage = new StringBuilder();
        while (it.hasNext()) {
            ic = it.next();
            token = ic.ID().getSymbol();
            word = token.getText();
            if ("".equals(classNameWithPackage.toString()))
                classNameWithPackage.append(word);
            else {
                classNameWithPackage.append(".");
                classNameWithPackage.append(word);
            }
        }
        return classNameWithPackage.toString();
    }
}