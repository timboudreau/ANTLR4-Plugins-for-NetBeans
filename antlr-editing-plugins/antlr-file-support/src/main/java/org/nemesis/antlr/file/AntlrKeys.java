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

import org.nemesis.antlr.file.impl.ColorKeyFromRegionReference;
import org.nemesis.antlr.common.extractiontypes.GrammarType;
import org.nemesis.antlr.file.impl.AntlrDataObjectHooks;
import java.util.Set;
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
import org.nemesis.antlr.fold.AntlrFoldsRegistration;
import org.nemesis.antlr.fold.FoldTypeName;
import org.nemesis.antlr.fold.FoldTypeSpec;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.FileType;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.ParserControl;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.SyntaxInfo;
import org.nemesis.antlr.spi.language.Goto;
import org.nemesis.antlr.spi.language.Imports;
import org.nemesis.antlr.spi.language.InplaceRename;
import org.nemesis.antlr.spi.language.ReferenceableFromImports;
import org.nemesis.antlr.spi.language.highlighting.Coloration;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.ALL_POPULAR_THEMES;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.POPULAR_BRIGHT_THEMES;
import static org.nemesis.antlr.spi.language.highlighting.Coloration.POPULAR_DARK_THEMES;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightZOrder;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistrations;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;

/**
 *
 * @author Tim Boudreau
 */
@AntlrLanguageRegistration(name = "Antlr", mimeType = ANTLR_MIME_TYPE, lexer = ANTLRv4Lexer.class,
        parser = @ParserControl(
                type = ANTLRv4Parser.class,
                generateSyntaxTreeNavigatorPanel = true,
                generateExtractionDebugNavigatorPanel = true,
                //                defaultErrorHighlightingEnabled = false,
                entryPointRule = ANTLRv4Parser.RULE_grammarFile),
        file = @FileType(extension = "g4",
                multiview = true,
                iconBase = ICON_PATH,
                hooks = AntlrDataObjectHooks.class),
        syntax = @SyntaxInfo(
                whitespaceTokens = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                    HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                    OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS},
                commentTokens = {
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
            @TokenCategory(name = "errors",
                    tokenIds = {
                        ID_UNTERMINATED, UNTERMINATED_ARGUMENT, HEADER_P_UNTERMINATED, ERRCHAR,
                        HDR_PCKG_UNTERMINATED, UNTERMINATED_ACTION, UNTERMINATED_HEADER,
                        UNTERMINATED_STRING_LITERAL, OPT_UNTERMINATED, CHN_UNTERMINATED, TOKDEC_UNTERMINATED,
                        FRAGDEC_UNTERMINATED, HDR_IMPRT_UNTERMINATED, TOK_UNTERMINATED, PARDEC_UNTERMINATED,
                        LEXCOM_UNTERMINATED, TYPE_UNTERMINATED, LEXER_CHAR_SET_UNTERMINATED,
                        PARDEC_OPT_UNTERMINATED, IMPORT_UNTERMINATED
                    }, colors = {
                @Coloration(
                        derivedFrom = "errors",
                        fg = {255, 200, 200},
                        bg = {222, 90, 90},
                        waveUnderline = {255, 200, 200},
                        themes = {
                            POPULAR_BRIGHT_THEMES
                        }),
                @Coloration(
                        derivedFrom = "errors",
                        fg = {255, 200, 200},
                        bg = {171, 63, 63},
                        waveUnderline = {255, 200, 200},
                        themes = {
                            POPULAR_DARK_THEMES
                        })
            }),

            @TokenCategory(name = "delimiter", tokenIds = {COMMA, SEMI, OR},
                    colors = {
                        @Coloration(
                                fg = {200, 140, 46},
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {255, 255, 255},
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "symbol",
                    tokenIds = {
                        COLONCOLON, LBRACE, LT, GT, RARROW, RBRACE,
                        RIGHT, AT, SHARP, NOT, COLON, ASSIGN,
                        TOKDEC_LBRACE, PLUS_ASSIGN, RPAREN, LPAREN, LEFT, DOLLAR,
                        PARDEC_LBRACE, HEADER_END, HEADER_P_START, FRAGDEC_LBRACE,
                        BEGIN_ARGUMENT, DOT, PARDEC_BEGIN_ARGUMENT, END_ACTION,
                        BEGIN_ACTION, END_ARGUMENT
                    },
                    colors = {
                        @Coloration(
                                fg = {81, 62, 81},
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {255, 255, 153},
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "whitespace",
                    tokenIds = {
                        PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                        HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                        OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS
                    },
                    colors = @Coloration(
                            fg = {128, 164, 90, 255},
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "keywords",
                    tokenIds = {
                        INT, OPTIONS, TOKENS, CHANNELS, IMPORT, CATCH, FINALLY, MODE, THROWS,
                        ASSOC, FAIL, RETURNS, LEXER, PARSER, GRAMMAR, INIT, LOCALS, HEADER,
                        MEMBERS, TOKEN_VOCAB, HEADER_IMPORT, HEADER_PACKAGE,
                        FRAGMENT, AFTER, SUPER_CLASS, LEXCOM_CHANNEL, LEXCOM_MODE, LEXCOM_SKIP,
                        LEXCOM_TYPE, LEXCOM_MORE, LANGUAGE, TOKEN_LABEL_TYPE,
                        LEXCOM_PUSHMODE, LEXCOM_POPMODE, HDR_IMPRT_STATIC, TYPE_TOKEN_ID
                    }, colors = {
                @Coloration(
                        fg = {168, 58, 132},
                        themes = POPULAR_BRIGHT_THEMES),
                @Coloration(
                        fg = {240, 193, 255},
                        themes = POPULAR_DARK_THEMES)
            }),

            @TokenCategory(name = "character-selectors",
                    tokenIds = {LEXER_CHAR_SET, RANGE},
                    colors = {
                        @Coloration(
                                fg = {90, 200, 164},
                                italic = true,
                                bold = true,
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {193, 240, 255},
                                italic = true,
                                bold = true,
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "identifier", tokenIds = ID,
                    colors = {
                        @Coloration(
                                fg = {20, 190, 190},
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {148, 209, 204},
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "token_or_parser",
                    tokenIds = TOKEN_OR_PARSER_RULE_ID,
                    colors = @Coloration(
                            fg = {100, 128, 200, 255},
                            italic = true,
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "varName",
                    tokenIds = PARDEC_ID,
                    colors = {
                        @Coloration(fg = {80, 190, 80})
                    }),

            @TokenCategory(name = "token-id",
                    tokenIds = TOKEN_ID,
                    colors = {
                        @Coloration(
                                fg = {100, 100, 164},
                                bold = false,
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {204, 204, 254},
                                bold = false,
                                themes = POPULAR_DARK_THEMES),}),

            @TokenCategory(name = "tok-id",
                    tokenIds = TOK_ID,
                    colors = @Coloration(
                            bold = true,
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "fragment",
                    tokenIds = FRAGDEC_ID,
                    colors = {
                        @Coloration(
                                fg = {12, 188, 0},
                                italic = false,
                                bold = false,
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {166, 236, 181},
                                italic = false,
                                bold = false,
                                themes = POPULAR_DARK_THEMES),}),

            @TokenCategory(name = "parser-rule",
                    tokenIds = PARSER_RULE_ID,
                    colors = {
                        @Coloration(
                                fg = {206, 86, 86},
                                bold = false,
                                italic = false,
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {250, 250, 178},
                                bold = false,
                                italic = false,
                                themes = POPULAR_DARK_THEMES),}),

            @TokenCategory(name = "token-rule",
                    tokenIds = TOKDEC_ID,
                    colors = {
                        @Coloration(
                                themes = POPULAR_BRIGHT_THEMES,
                                bold = true),
                        @Coloration(
                                themes = POPULAR_DARK_THEMES,
                                bold = true)}),

            @TokenCategory(name = "wildcards",
                    tokenIds = {QUESTION, PLUS, STAR},
                    colors = {
                        @Coloration(
                                themes = POPULAR_BRIGHT_THEMES,
                                bold = true,
                                italic = false,
                                fg = {101, 101, 255}),
                        @Coloration(
                                themes = POPULAR_DARK_THEMES,
                                bold = true,
                                italic = false,
                                fg = {153, 153, 255})}),

            @TokenCategory(name = "literal",
                    tokenIds = STRING_LITERAL,
                    colors = {
                        @Coloration(
                                fg = {128, 164, 90},
                                bold = true,
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {255, 170, 158},
                                bold = true,
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "comment",
                    tokenIds = {
                        LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
                        FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
                        HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
                        HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
                        HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
                        IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
                        LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
                        PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
                        TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
                        TYPE_LINE_COMMENT
                    }, colors = {
                @Coloration(
                        fg = {128, 128, 128},
                        themes = POPULAR_BRIGHT_THEMES),
                @Coloration(
                        fg = {0, 0, 0},
                        themes = POPULAR_DARK_THEMES)
            }),

            // Categories defined here and used for semantic highlighting
            @TokenCategory(name = "fragment-reference",
                    tokenIds = {},
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "fragment",
                            bold = false,
                            italic = false
                    )
            ),

            @TokenCategory(name = "lexer-rule-reference",
                    tokenIds = {},
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "token-id",
                            bold = false,
                            italic = false
                    )
            ),

            @TokenCategory(name = "parser-rule-reference",
                    tokenIds = {},
                    colors = @Coloration(
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "parser-rule",
                            bold = false,
                            italic = false
                    )
            ),

            @TokenCategory(name = "parser-rule-name",
                    tokenIds = {},
                    colors = @Coloration(
                            bold = true,
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "parser-rule")),

            @TokenCategory(name = "lexer-rule-name",
                    tokenIds = {},
                    colors = @Coloration(
                            bold = true,
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "token-id")),

            @TokenCategory(name = "fragment-rule-name",
                    tokenIds = {},
                    colors = @Coloration(
                            bold = true,
                            themes = ALL_POPULAR_THEMES,
                            derivedFrom = "fragment")),

            // Used by inplace rename
            @TokenCategory(name = "synchronized-text-blocks-ext",
                    tokenIds = {},
                    colors = {
                        @Coloration(
                                bold = true,
                                themes = ALL_POPULAR_THEMES,
                                bg = {142, 96, 96},
                                fg = {245, 245, 245}
                        )
                    }
            ),
            @TokenCategory(name = "synchronized-text-blocks-ext-slave",
                    tokenIds = {},
                    colors = {
                        @Coloration(
                                bold = true,
                                themes = ALL_POPULAR_THEMES,
                                bg = {172, 96, 96},
                                fg = {10, 10, 10}
                        )}
            )
        }
)
public class AntlrKeys {

    public static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);

    @Imports(mimeType = ANTLR_MIME_TYPE)
    public static final NamedRegionKey<ImportKinds> IMPORTS = NamedRegionKey.create("imports", ImportKinds.class);

    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE, order = -10000, positionInZOrder = -1000,
            zOrder = HighlightZOrder.SHOW_OFF_RACK, fixedSize = false,
            colors = @ColoringCategory(name = "alternatives",
                    colors = {
                        @Coloration(
                                themes = POPULAR_BRIGHT_THEMES,
                                fg = {235, 171, 120},
                                bold = true
                        ),
                        @Coloration(
                                themes = POPULAR_DARK_THEMES,
                                fg = {242, 183, 89},
                                bold = true
                        )}))
    public static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("alternatives", RuleTypes.class);

    @AntlrFoldsRegistration(mimeType = ANTLR_MIME_TYPE, foldSpec = @FoldTypeSpec(name = "header", guardedStart = 3,
            guardedEnd = 3, displayText = "header"))
    public static final RegionsKey<HeaderMatter> HEADER_MATTER = RegionsKey.create(HeaderMatter.class, "headerMatter");

//    @SimpleNavigatorRegistration(displayName = "Rules", order = 1, mimeType = MIME_TYPE,
//            appearance = AntlrNavigatorAppearance.class)
    @ReferenceableFromImports(mimeType = ANTLR_MIME_TYPE)
    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE,
            positionInZOrder = 502, fixedSize = true, coloringName = "token-id")
    public static final NamedRegionKey<RuleTypes> RULE_NAMES = NamedRegionKey.create("ruleNames", RuleTypes.class);

    @HighlighterKeyRegistrations(value = {
        @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE,
                fixedSize = true,
                trigger = HighlightRefreshTrigger.CARET_MOVED,
                zOrder = HighlightZOrder.SHOW_OFF_RACK,
                order = 1000,
                positionInZOrder = 1000,
                colors = @ColoringCategory(name = "mark-occurrences",
                        colors = {
                            @Coloration(
                                    themes = POPULAR_BRIGHT_THEMES,
                                    bg = {220, 220, 190}
                            ),
                            @Coloration(
                                    themes = POPULAR_DARK_THEMES,
                                    bg = {92, 89, 145})})
        ),
        @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE,
                fixedSize = true,
                trigger = HighlightRefreshTrigger.DOCUMENT_CHANGED,
                order = 2000,
                zOrder = HighlightZOrder.SYNTAX_RACK,
                positionInZOrder = 1990,
                colorFinder = ColorKeyFromRegionReference.class
        )
    })
    @Goto(mimeType = ANTLR_MIME_TYPE)
    @InplaceRename(mimeType=ANTLR_MIME_TYPE)
    public static final NameReferenceSetKey<RuleTypes> RULE_NAME_REFERENCES = RULE_NAMES.createReferenceKey("ruleRefs");
    public static final SingletonKey<GrammarType> GRAMMAR_TYPE = SingletonKey.create(GrammarType.class);

    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE, colors = @ColoringCategory(name = "ebnfs",
            colors = {
                @Coloration(
                        themes = POPULAR_BRIGHT_THEMES,
                        bg = {255, 255, 242, 245}
                ),
                @Coloration(
                        themes = POPULAR_DARK_THEMES,
                        bg = {159, 160, 219, 24})
            }))
    public static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");

//    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "block", guardedStart = 3,
//            guardedEnd = 3, displayText = "block"))
    public static final RegionsKey<Void> BLOCKS = RegionsKey.create(Void.class, "blocks");

    @AntlrFoldsRegistration(mimeType = ANTLR_MIME_TYPE, foldType = FoldTypeName.MEMBER)
    public static final RegionsKey<FoldableRegion> FOLDABLES = RegionsKey.create(FoldableRegion.class, "folds");

    static final String ANTLR_SAMPLE = "grammar Timestamps;\n"
            + "\n"
            + "timestampDecl : \n"
            + "    ( def? ':' ts=Timestamp constraints) #IsoTimestamp\n"
            + "    |(def? ':' amt=digits) #IntTimestamp \n"
            + "    |(def? ':' digits) #FooTimestamp;\n"
            + "\n// line comment\n"
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
            + "Thing : Timestamp~[\\r\\n]*;\n"
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
