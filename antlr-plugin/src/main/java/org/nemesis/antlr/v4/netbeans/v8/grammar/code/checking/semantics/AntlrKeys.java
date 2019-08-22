/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.Set;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.common.AntlrConstants.ICON_PATH;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.fold.AntlrFoldsRegistration;
import org.nemesis.antlr.fold.FoldTypeName;
import org.nemesis.antlr.fold.FoldTypeSpec;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.FileType;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.ParserControl;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.SyntaxInfo;
import org.nemesis.antlr.spi.language.highlighting.Coloration;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Lexer;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser.*;
import static org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.AntlrKeys.ANTLR_SAMPLE;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.GrammarType;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
@AntlrLanguageRegistration(name = "Antlr", mimeType = ANTLR_MIME_TYPE, lexer = ANTLRv4Lexer.class,
        parser = @ParserControl(type = ANTLRv4Parser.class, entryPointRule = ANTLRv4Parser.RULE_grammarFile),
        file = @FileType(extension = "g4", multiview = true, iconBase = ICON_PATH, hooks = AntlrDataObjectHooks.class),
        syntax = @SyntaxInfo(
                whitespaceTokens = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                    HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                    OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS}
        ),
        sample = ANTLR_SAMPLE,
        lineCommentPrefix = "//",
        categories = {
            @TokenCategory(name = "delimiter", tokenIds = {COMMA, SEMI, OR}, colors = @Coloration(fg = {255, 255, 0, 255})),
            @TokenCategory(name = "symbol", tokenIds = {
        STAR, COLONCOLON, LBRACE, LT, GT, RARROW, RBRACE, RIGHT, AT, SHARP, NOT, COLON, ASSIGN,
        QUESTION, TOKDEC_LBRACE, PLUS_ASSIGN, RPAREN, LPAREN, LEFT, PLUS, DOLLAR,
        PARDEC_LBRACE, HEADER_END, HEADER_P_START, FRAGDEC_LBRACE, BEGIN_ARGUMENT, DOT,
        PARDEC_BEGIN_ARGUMENT, END_ACTION, BEGIN_ACTION, END_ARGUMENT
    }, colors = @Coloration(fg = {128, 164, 90, 255})),
            @TokenCategory(name = "whitespace", tokenIds = {
        PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
        HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
        OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS
    }, colors = @Coloration(fg = {128, 164, 90, 255})),
            @TokenCategory(name = "keywords", tokenIds = {
        INT, OPTIONS, TOKENS, CHANNELS, IMPORT, CATCH, FINALLY, MODE, THROWS,
        ASSOC, FAIL, RETURNS, LEXER, PARSER, GRAMMAR, INIT, LOCALS, HEADER,
        MEMBERS, TOKEN_VOCAB, LEXER_CHAR_SET, HEADER_IMPORT, HEADER_PACKAGE,
        FRAGMENT, AFTER, SUPER_CLASS, LEXCOM_CHANNEL, LEXCOM_MODE, LEXCOM_SKIP,
        IMPORT_UNTERMINATED, LEXCOM_TYPE, LEXCOM_MORE, LANGUAGE, TOKEN_LABEL_TYPE,
        LEXCOM_PUSHMODE, LEXCOM_POPMODE, HDR_IMPRT_STATIC
    }, colors = @Coloration(fg = {164, 90, 128, 255})),
            @TokenCategory(name = "identifier", tokenIds = {
        ID, PARSER_RULE_ID, PARDEC_ID, FRAGDEC_ID, TOKEN_OR_PARSER_RULE_ID,
        TOKDEC_ID, TOKEN_ID, TYPE_TOKEN_ID, ID_UNTERMINATED, TOK_ID
    }, colors = @Coloration(fg = {164, 128, 90, 255}, bold = true)),
            @TokenCategory(name = "literal", tokenIds = {
        STRING_LITERAL, UNTERMINATED_STRING_LITERAL,}, colors = @Coloration(fg = {128, 164, 90, 255})),
            @TokenCategory(name = "comment", tokenIds = {
        LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
        FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
        HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
        HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
        HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
        IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
        LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
        PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
        TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
        TYPE_LINE_COMMENT}, colors = @Coloration(fg = {128, 128, 128, 255}, italic = true))
        }
)
public class AntlrKeys {

    @AntlrFoldsRegistration(mimeType = "text/x-g4", foldSpec = @FoldTypeSpec(name = "rules", guardedStart = 3,
            guardedEnd = 3, displayText = "rules"))
    public static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);
    public static final NamedRegionKey<ImportKinds> IMPORTS = NamedRegionKey.create("imports", ImportKinds.class);

    @HighlighterKeyRegistration(mimeType = "text/g4",
            colors = @ColoringCategory(name = "alternatives", colors = @Coloration(
                    themes = {"NetBeans", "NetBeans55", "NetBeans_Solarized_Dark", "BlueTheme"},
                    bg = {255, 255, 242}
            )))
    public static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("labels", RuleTypes.class);

    @AntlrFoldsRegistration(mimeType = "text/x-g4", foldSpec = @FoldTypeSpec(name = "header", guardedStart = 3,
            guardedEnd = 3, displayText = "header"))
    public static final RegionsKey<HeaderMatter> HEADER_MATTER = RegionsKey.create(HeaderMatter.class, "headerMatter");
    public static final NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionKey.create("ruleNames", RuleTypes.class);
    public static final NameReferenceSetKey<RuleTypes> RULE_NAME_REFERENCES = RULE_NAMES.createReferenceKey("ruleRefs");
    public static final SingletonKey<GrammarType> GRAMMAR_TYPE = SingletonKey.create(GrammarType.class);

//    @HighlighterKeyRegistration(mimeType = "text/x-g4", colors = @ColoringCategory(name = "ebnfs", colors = @Coloration(
//            themes = {"NetBeans", "NetBeans55", "NetBeans_Solarized_Dark", "BlueTheme"},
//            bg = {255, 255, 242}
//    )))
    public static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");

    @AntlrFoldsRegistration(mimeType = "text/x-g4", foldSpec = @FoldTypeSpec(name = "block", guardedStart = 3,
            guardedEnd = 3, displayText = "block"))
    public static final RegionsKey<Void> BLOCKS = RegionsKey.create(Void.class, "blocks");

    @AntlrFoldsRegistration(mimeType = ANTLR_MIME_TYPE, foldType = FoldTypeName.MEMBER)
    public static final RegionsKey<FoldableRegion> FOLDABLES = RegionsKey.create(FoldableRegion.class, "folds");

    static final String ANTLR_SAMPLE = "grammar Timestamps;\n"
            + "\n"
            + "timestampDecl : \n"
            + "    ( def? ':' ts=Timestamp constraints) #IsoTimestamp\n"
            + "    |(def? ':' amt=digits) #IntTimestamp \n"
            + "    |(def? ':' digits) #FooTimestamp;\n"
            + "\n"
            + "constraints: (min=min | max=max | req=req)*;\n"
            + "\n"
            + "max : 'max' value=timestampLiteral;\n"
            + "min : Min value=timestampLiteral;\n"
            + "req : 'required';\n"
            + "def  : 'default'? '=' def=timestampLiteral;\n"
            + "timestampLiteral : Timestamp;\n"
            + "\n"
            + "digits: DIGIT (DIGIT | '_')*;\n"
            + "\n"
            + "Timestamp : Datestamp 'T' Time;\n"
            + "Datestamp : FOUR_DIGITS '-' TWO_DIGITS '-' TWO_DIGITS ;\n"
            + "Time : TWO_DIGITS ':' TWO_DIGITS ':' TWO_DIGITS TS_FRACTION? TS_OFFSET;\n"
            + "\n"
            + "Min : 'min';\n"
            + "\n"
            + "fragment FOUR_DIGITS : DIGIT DIGIT DIGIT DIGIT;\n"
            + "fragment TWO_DIGITS : DIGIT DIGIT;\n"
            + "fragment TS_FRACTION : '.' DIGIT+;\n"
            + "fragment TS_OFFSET\n"
            + "    : 'Z' | TS_NUM_OFFSET;\n"
            + "fragment TS_NUM_OFFSET\n"
            + "    : ( '+' | '-' ) DIGIT DIGIT ':' DIGIT DIGIT;\n"
            + "fragment DIGIT: [0-9];\n";
}
