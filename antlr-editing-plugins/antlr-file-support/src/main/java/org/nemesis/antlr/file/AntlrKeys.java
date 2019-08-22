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
package org.nemesis.antlr.file;

import java.awt.Color;
import org.nemesis.antlr.file.impl.AntlrDataObjectHooks;
import java.util.Set;
import java.util.function.Function;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.common.AntlrConstants.ICON_PATH;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import static org.nemesis.antlr.file.AntlrKeys.ANTLR_SAMPLE;
import org.nemesis.antlr.file.impl.AntlrNavigatorAppearance;
import org.nemesis.antlr.fold.AntlrFoldsRegistration;
import org.nemesis.antlr.fold.FoldTypeName;
import org.nemesis.antlr.fold.FoldTypeSpec;
import org.nemesis.antlr.navigator.SimpleNavigatorRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.FileType;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.ParserControl;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.SyntaxInfo;
import org.nemesis.antlr.spi.language.Goto;
import org.nemesis.antlr.spi.language.highlighting.Coloration;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightZOrder;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistrations;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
@AntlrLanguageRegistration(name = "Antlr", mimeType = ANTLR_MIME_TYPE, lexer = ANTLRv4Lexer.class,
        parser = @ParserControl(type = ANTLRv4Parser.class, generateSyntaxTreeNavigatorPanel = true,
                entryPointRule = ANTLRv4Parser.RULE_grammarFile),
        file = @FileType(extension = "g4", multiview = true, iconBase = ICON_PATH, hooks = AntlrDataObjectHooks.class),
        syntax = @SyntaxInfo(
                whitespaceTokens = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                    HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                    OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS}, commentTokens = {
                    LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
                    FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
                    HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
                    HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
                    HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
                    IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
                    LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
                    PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
                    TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
                    TYPE_LINE_COMMENT}
        ),
        genericCodeCompletion = @AntlrLanguageRegistration.CodeCompletion(
                ignoreTokens = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                    HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                    OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS,
                    LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
                    FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
                    HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
                    HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
                    HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
                    IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
                    LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
                    PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
                    TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
                    TYPE_LINE_COMMENT}),
        localizingBundle = "org.nemesis.antlr.file.Bundle",
        sample = ANTLR_SAMPLE,
        lineCommentPrefix = "//",
        categories = {
            @TokenCategory(name = "whatevs", parserRuleIds = { ANTLRv4Parser.RULE_grammarSpec, ANTLRv4Parser.RULE_headerAction,
                        ANTLRv4Parser.RULE_actionBlock},
                    colors = @Coloration(
                            bg = {0, 0, 0},
                            fg = {255, 220, 255}
                    )
            ),

            @TokenCategory(name = "errors", tokenIds = {
        ID_UNTERMINATED, UNTERMINATED_ARGUMENT, HEADER_P_UNTERMINATED, ERRCHAR,
        HDR_PCKG_UNTERMINATED, UNTERMINATED_ACTION, UNTERMINATED_HEADER,
        UNTERMINATED_STRING_LITERAL, OPT_UNTERMINATED, CHN_UNTERMINATED, TOKDEC_UNTERMINATED,
        FRAGDEC_UNTERMINATED, HDR_IMPRT_UNTERMINATED, TOK_UNTERMINATED, PARDEC_UNTERMINATED,
        LEXCOM_UNTERMINATED, TYPE_UNTERMINATED, LEXER_CHAR_SET_UNTERMINATED,
        PARDEC_OPT_UNTERMINATED
    }, colors = @Coloration(fg = {255, 200, 200}, bg = {222, 90, 90})),
            @TokenCategory(name = "delimiter", tokenIds = {COMMA, SEMI, OR}, colors = @Coloration(fg = {255, 255, 0, 255})),
            @TokenCategory(name = "symbol", tokenIds = {
        STAR, COLONCOLON, LBRACE, LT, GT, RARROW, RBRACE, RIGHT, AT, SHARP, NOT, COLON, ASSIGN,
        QUESTION, TOKDEC_LBRACE, PLUS_ASSIGN, RPAREN, LPAREN, LEFT, PLUS, DOLLAR,
        PARDEC_LBRACE, HEADER_END, HEADER_P_START, FRAGDEC_LBRACE, BEGIN_ARGUMENT, DOT,
        PARDEC_BEGIN_ARGUMENT, END_ACTION, BEGIN_ACTION, END_ARGUMENT, RANGE
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
    }, colors = @Coloration(fg = {200, 90, 164, 255})),
            @TokenCategory(name = "identifier", tokenIds = {
        //        ID,
        /* TOK_ID, TOKEN_ID, */TYPE_TOKEN_ID
    }, colors = @Coloration(fg = {164, 90, 128, 255})),
            @TokenCategory(name = "token_or_parser", tokenIds = TOKEN_OR_PARSER_RULE_ID,
                    colors = @Coloration(fg = {100, 128, 200, 255}, italic = true)),
            @TokenCategory(name = "varName", tokenIds = PARDEC_ID,
                    colors = @Coloration(fg = {80, 255, 80}, italic = true)),
            @TokenCategory(name = "genericId", tokenIds = ID,
                    colors = @Coloration(fg = {255, 210, 80}, italic = true)),
            @TokenCategory(name = "tokid", tokenIds = {TOKEN_ID},
                    colors = @Coloration(fg = {164, 164, 90, 255}, bold = false, bg = {220, 220, 255})),
            @TokenCategory(name = "tokenid", tokenIds = {TOK_ID},
                    colors = @Coloration(fg = {164, 0, 0}, bold = false, bg = {220, 220, 255})),
            @TokenCategory(name = "fragment", tokenIds = FRAGDEC_ID,
                    colors = @Coloration(fg = {164, 128, 90, 255}, italic = true)),
            @TokenCategory(name = "parser-rule", tokenIds = PARSER_RULE_ID,
                    colors = @Coloration(fg = {164, 128, 90, 255}, bold = true)),
            @TokenCategory(name = "token-rule", tokenIds = TOKDEC_ID,
                    colors = @Coloration(fg = {164, 164, 90, 255}, bold = false, bg = {220, 220, 255})),
            @TokenCategory(name = "literal", tokenIds = {
        STRING_LITERAL}, colors = @Coloration(fg = {128, 164, 90, 255})),
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
        TYPE_LINE_COMMENT}, colors = @Coloration(fg = {128, 128, 128, 255}))
        }
)
public class AntlrKeys {

    public static final String MIME_TYPE = "text/x-g4";

    private static final String[] DARK_THEMES = {"NetBeans_Solarized_Dark", "BlueTheme"};
    private static final String[] BRIGHT_THEMES = new String[] {"NetBeans", "NetBeans55"};

    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "rules", guardedStart = 3,
            guardedEnd = 3, displayText = "rules"))
    public static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);
    public static final NamedRegionKey<ImportKinds> IMPORTS = NamedRegionKey.create("imports", ImportKinds.class);

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, order = 20, positionInZOrder = 900,
            colors = @ColoringCategory(name = "alternatives",
                    colors = {
                        @Coloration(
                                themes = {"NetBeans", "NetBeans55"},
                                bg = {255, 255, 200},
                                bold = true,
                                italic = true
                        ),
                        @Coloration(
                                themes = {"NetBeans_Solarized_Dark", "BlueTheme"},
                                bg = {80, 40, 40},
                                bold = true,
                                italic = true
                        ),}))
    public static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("alternatives", RuleTypes.class);

    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "header", guardedStart = 3,
            guardedEnd = 3, displayText = "header"))
    public static final RegionsKey<HeaderMatter> HEADER_MATTER = RegionsKey.create(HeaderMatter.class, "headerMatter");

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, positionInZOrder = 10, order = 11,
            colors = @ColoringCategory(name = "stuff",
                    colors = @Coloration(bg = {100, 255, 100})))
    public static final RegionsKey<String> STUFF = RegionsKey.create(String.class, "stuff");

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, order = 800,
            positionInZOrder = 2100,
            attributeSetFinder = RuleNameColorFinder.class
    /*
             colors = @ColoringCategory(name = "names",
                    colors = {
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme", "NetBeans", "NetBeans55"}, bold = true)
                    })
     */
    )
    @SimpleNavigatorRegistration(displayName = "Rules", order = 1, mimeType = MIME_TYPE,
            appearance = AntlrNavigatorAppearance.class)
    public static final NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionKey.create("ruleNames", RuleTypes.class);

    @HighlighterKeyRegistrations({
        @HighlighterKeyRegistration(mimeType = MIME_TYPE,
                fixedSize = true,
                trigger = HighlightRefreshTrigger.CARET_MOVED,
                zOrder = HighlightZOrder.SHOW_OFF_RACK,
                order = 1000,
                positionInZOrder = 1000,
                colors = @ColoringCategory(name = "mark-occurrences",
                        colors = @Coloration(
                                themes = {"NetBeans", "NetBeans55", "NetBeans_Solarized_Dark", "BlueTheme"},
                                bg = {220, 220, 190}
                        ))
        ),
        @HighlighterKeyRegistration(mimeType = MIME_TYPE,
                fixedSize = true,
                trigger = HighlightRefreshTrigger.DOCUMENT_CHANGED,
                order = 2000,
                zOrder = HighlightZOrder.SYNTAX_RACK,
                positionInZOrder = 2000,
                colors = @ColoringCategory(name = "refs",
                        colors = @Coloration(
                                themes = {"NetBeans", "NetBeans55", "NetBeans_Solarized_Dark", "BlueTheme"},
                                italic = true
                        ))
        )
    })
    @Goto(mimeType = MIME_TYPE)
    public static final NameReferenceSetKey<RuleTypes> RULE_NAME_REFERENCES = RULE_NAMES.createReferenceKey("ruleRefs");
    public static final SingletonKey<GrammarType> GRAMMAR_TYPE = SingletonKey.create(GrammarType.class);

    public static final class RuleNameColorFinder implements Function<NamedSemanticRegion<RuleTypes>, AttributeSet> {

        private final SimpleAttributeSet none = new SimpleAttributeSet();
        private final SimpleAttributeSet named = new SimpleAttributeSet();
        private final SimpleAttributeSet bold = new SimpleAttributeSet();
        private final SimpleAttributeSet boldItalic = new SimpleAttributeSet();

        public RuleNameColorFinder() {
            bold.addAttribute(StyleConstants.FontConstants.Bold, true);
            boldItalic.addAttribute(StyleConstants.FontConstants.Bold, true);
            boldItalic.addAttribute(StyleConstants.FontConstants.Italic, true);
            named.addAttribute(StyleConstants.ColorConstants.Background, Color.ORANGE);
            named.addAttribute(StyleConstants.ColorConstants.Foreground, Color.BLUE);
        }

        @Override
        public AttributeSet apply(NamedSemanticRegion<RuleTypes> t) {
            System.out.println("COLOR FOR " + t.name() + " " + t.kind());
            switch (t.kind()) {
                case FRAGMENT:
                    return boldItalic;
                case LEXER:
                    return bold;
                case PARSER:
                    return bold;
                case NAMED_ALTERNATIVES:

                default:
                    return none;
            }
        }
    }

    @HighlighterKeyRegistration(mimeType = MIME_TYPE, colors = @ColoringCategory(name = "ebnfs",
            colors = {
                @Coloration(
                        themes = {"NetBeans", "NetBeans55"},
                        bg = {255, 255, 242}
                ),
                @Coloration(
                        themes = {"NetBeans_Solarized_Dark", "BlueTheme"},
                        bg = {52, 52, 100})
            }))
    public static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");

    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "block", guardedStart = 3,
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
