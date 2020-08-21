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

import org.nemesis.antlr.file.impl.AntlrDataObjectHooks;
import java.util.Set;
import javax.swing.JOptionPane;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.ANTLRv4Parser;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_altList;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_block;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_channelsSpec;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_ebnf;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_ebnfSuffix;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_fragmentRuleIdentifier;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_fragmentRuleSpec;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_labeledParserRuleElement;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexComMode;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexComPushMode;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexerRuleAtom;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexerRuleElement;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexerRuleElements;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_modeDec;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_parserRuleAlternative;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_parserRuleAtom;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_parserRuleIdentifier;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_parserRuleReference;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_parserRuleSpec;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_tokenRuleIdentifier;
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexerCommand;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_lexerCommands;
import static org.nemesis.antlr.ANTLRv4Parser.RULE_tokenRuleSpec;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.common.AntlrConstants.ICON_PATH;
import org.nemesis.antlr.common.extractiontypes.EbnfProperty;
import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.nemesis.antlr.common.extractiontypes.HeaderMatter;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.common.extractiontypes.LexerModes;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.antlrrefactoring.InlineRefactoringPlugin;
import org.nemesis.antlr.file.impl.ColorKeyFromRegionReference;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.fold.AntlrFoldsRegistration;
import org.nemesis.antlr.fold.FoldTypeName;
import org.nemesis.antlr.fold.FoldTypeSpec;
import org.nemesis.antlr.instantrename.annotations.InplaceRename;
import org.nemesis.antlr.refactoring.CustomRefactoringRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.CodeCompletion.RuleSubstitutions;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.CodeCompletion.SupplementaryTokenCompletion;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.FileType;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.ParserControl;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration.SyntaxInfo;
import org.nemesis.antlr.spi.language.Goto;
import org.nemesis.antlr.spi.language.Imports;
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
import org.nemesis.antlr.spi.language.keybindings.Key;
import org.nemesis.antlr.spi.language.keybindings.KeyModifiers;
import org.nemesis.antlr.spi.language.keybindings.Keybinding;
import org.nemesis.antlr.spi.language.keybindings.Keybindings;
import org.nemesis.charfilter.CharPredicates;
import org.nemesis.charfilter.anno.CharFilterSpec;
import org.nemesis.charfilter.anno.CharPredicateSpec;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.RegionsKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
@AntlrLanguageRegistration(name = "Antlr", mimeType = "text/x-g4", lexer = ANTLRv4Lexer.class,
        useImplicitLanguageNameFromLexerName = true,
        parser = @ParserControl(
                type = ANTLRv4Parser.class,
                generateSyntaxTreeNavigatorPanel = true,
                generateExtractionDebugNavigatorPanel = true,
                defaultErrorHighlightingEnabled = false,
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
                //                ignoreTokens = {
                //                    LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
                //                    FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
                //                    HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
                //                    HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
                //                    HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
                //                    IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
                //                    LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
                //                    PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
                //                    TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
                //                    TYPE_LINE_COMMENT},
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
                    TYPE_LINE_COMMENT, LBRACE, RBRACE, LPAREN, RPAREN},
                preferredRules = {
                    RULE_ebnfSuffix,
                    //                    RULE_identifier,
                    RULE_parserRuleIdentifier,
                    RULE_fragmentRuleIdentifier,
                    RULE_tokenRuleIdentifier,
                    RULE_block,
                    RULE_parserRuleAtom,
                    RULE_lexerRuleAtom,
                    RULE_lexerRuleElement,
                    RULE_labeledParserRuleElement,
                    RULE_altList,
                    RULE_lexerCommand,
                    RULE_lexerCommands,
                    RULE_lexComPushMode,
                    RULE_lexComMode,
                    RULE_ebnf,
                    RULE_parserRuleSpec, // right after colon
                    RULE_tokenRuleSpec, // right after colon
                    RULE_fragmentRuleSpec, // right after colon
                    RULE_channelsSpec,
                    RULE_lexerRuleElements,
                    RULE_ebnf,
                    RULE_modeDec,
                    RULE_parserRuleAlternative,
                    RULE_parserRuleReference,},
                tokenCompletions = {
                    @SupplementaryTokenCompletion(
                            tokenId = LEXCOM_MORE,
                            text = "more"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = LEXCOM_MODE,
                            text = "mode"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = LEXCOM_PUSHMODE,
                            text = "pushMode()"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = LEXCOM_POPMODE,
                            text = "popMode"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = LEXCOM_TYPE,
                            text = "type()"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = FRAGMENT,
                            text = "fragment"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = OR,
                            text = "|"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = ANTLRv4Lexer.RARROW,
                            text = "->"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = BEGIN_ACTION,
                            text = "{}"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = STRING_LITERAL,
                            text = "''"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = END_ACTION,
                            text = "}\n"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = SEMI,
                            text = ";"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = DOT,
                            text = "."
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = SHARP,
                            text = "#"
                    ),
                    @SupplementaryTokenCompletion(
                            tokenId = NOT,
                            text = "~"
                    )
                },
                ruleSubstitutions = {
                    @RuleSubstitutions(
                            complete = RULE_parserRuleReference,
                            withCompletionsOf = RULE_parserRuleIdentifier
                    ),
                    @RuleSubstitutions(
                            complete = RULE_parserRuleAtom,
                            withCompletionsOf = RULE_parserRuleIdentifier
                    ),
                    @RuleSubstitutions(
                            complete = RULE_lexerRuleAtom,
                            withCompletionsOf = RULE_tokenRuleIdentifier
                    ),
                    @RuleSubstitutions(
                            complete = RULE_labeledParserRuleElement,
                            withCompletionsOf = RULE_parserRuleIdentifier
                    )
                }
        ),
        localizingBundle = "org.nemesis.antlr.file.res.Bundle",
        sample = "AntlrSample.g4",
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
                        //                        fg = {255, 200, 200},
                        bg = {222, 90, 90, 32},
                        waveUnderline = {255, 0, 0},
                        themes = {
                            POPULAR_BRIGHT_THEMES
                        }),
                @Coloration(
                        derivedFrom = "errors",
                        //                        fg = {255, 200, 200},
                        bg = {171, 63, 63, 32},
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
                                fg = {164, 110, 164},
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {108, 212, 105},
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "whitespace",
                    tokenIds = {
                        PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
                        HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
                        OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS
                    },
                    colors = @Coloration(
                            //                            fg = {128, 164, 90, 255},
                            derivedFrom = "whitespace",
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "keyword",
                    tokenIds = {
                        INT, OPTIONS, TOKENS, CHANNELS, IMPORT, CATCH, FINALLY, MODE, THROWS,
                        ASSOC, FAIL, RETURNS, LEXER, PARSER, GRAMMAR, INIT, LOCALS, HEADER,
                        MEMBERS, TOKEN_VOCAB, HEADER_IMPORT, HEADER_PACKAGE,
                        FRAGMENT, AFTER, SUPER_CLASS, LEXCOM_CHANNEL, LEXCOM_MODE, LEXCOM_SKIP,
                        LEXCOM_TYPE, LEXCOM_MORE, LANGUAGE, TOKEN_LABEL_TYPE,
                        LEXCOM_PUSHMODE, LEXCOM_POPMODE, HDR_IMPRT_STATIC, TYPE_TOKEN_ID
                    }, colors = {
                @Coloration(
                        derivedFrom = "keyword",
                        //                        fg = {168, 58, 132},
                        themes = POPULAR_BRIGHT_THEMES),
                @Coloration(
                        derivedFrom = "keyword",
                        //                        fg = {196, 184, 159},
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
                                derivedFrom = "identifier",
                                //                                fg = {20, 190, 190},
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                derivedFrom = "identifier",
                                //                                fg = {148, 209, 204},
                                themes = POPULAR_DARK_THEMES)
                    }),

            @TokenCategory(name = "token_or_parser",
                    tokenIds = TOKEN_OR_PARSER_RULE_ID,
                    colors = @Coloration(
                            fg = {100, 128, 200, 255},
                            italic = true,
                            derivedFrom = "identifier",
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "varName",
                    tokenIds = PARDEC_ID,
                    colors = {
                        @Coloration(fg = {80, 190, 80},
                        derivedFrom = "identifier"
                        )
                    }),

            @TokenCategory(name = "token-id",
                    tokenIds = TOKEN_ID,
                    colors = {
                        @Coloration(
                                fg = {100, 100, 164},
                                bold = false,
                                derivedFrom = "identifier",
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {204, 204, 254},
                                bold = false,
                                derivedFrom = "identifier",
                                themes = POPULAR_DARK_THEMES),}),

            @TokenCategory(name = "tok-id",
                    tokenIds = TOK_ID,
                    colors = @Coloration(
                            bold = true,
                            derivedFrom = "token-id",
                            themes = ALL_POPULAR_THEMES)),

            @TokenCategory(name = "fragment",
                    tokenIds = FRAGDEC_ID,
                    colors = {
                        @Coloration(
                                fg = {12, 188, 0},
                                italic = false,
                                bold = false,
                                derivedFrom = "identifier",
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {120, 232, 120},
                                italic = false,
                                derivedFrom = "identifier",
                                bold = false,
                                themes = POPULAR_DARK_THEMES),}),

            @TokenCategory(name = "parser-rule",
                    tokenIds = PARSER_RULE_ID,
                    colors = {
                        @Coloration(
                                fg = {206, 86, 86},
                                bold = false,
                                italic = false,
                                derivedFrom = "identifier",
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {250, 250, 178},
                                bold = false,
                                italic = false,
                                derivedFrom = "identifier",
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
                                derivedFrom = "literal",
                                themes = POPULAR_BRIGHT_THEMES),
                        @Coloration(
                                fg = {255, 170, 158},
                                bold = true,
                                derivedFrom = "literal",
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
            @TokenCategory(name = "unused",
                    tokenIds = {},
                    colors = {
                        @Coloration(
                                derivedFrom = "mod-unused",
                                waveUnderline = {0, 0, 0},
                                themes = {
                                    POPULAR_BRIGHT_THEMES
                                }),
                        @Coloration(
                                derivedFrom = "mod-unused",
                                waveUnderline = {140, 140, 140},
                                themes = {
                                    POPULAR_DARK_THEMES
                                })
                    }
            ),
            @TokenCategory(name = "warning",
                    tokenIds = {},
                    colors = {
                        @Coloration(
                                derivedFrom = "warning",
                                //                                fg = {255, 255, 200},
                                bg = {212, 212, 100, 32},
                                waveUnderline = {210, 120, 0},
                                themes = {
                                    POPULAR_BRIGHT_THEMES
                                }),
                        @Coloration(
                                derivedFrom = "warning",
                                //                                fg = {200, 200, 200},
                                bg = {171, 171, 63, 32},
                                waveUnderline = {210, 120, 0},
                                themes = {
                                    POPULAR_DARK_THEMES
                                })
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
        },
        embeddedLanguages = {
            @AntlrLanguageRegistration.Embedding(mimeType = "text/x-java", tokens = {ANTLRv4Lexer.ACTION_CONTENT,
        ANTLRv4Lexer.BEGIN_ACTION, ANTLRv4Lexer.END_ACTION})
        }
)
public class AntlrKeys {

    @Localize(displayName = "Rule Body")
    public static final NamedRegionKey<RuleTypes> RULE_BOUNDS = NamedRegionKey.create("ruleBounds", RuleTypes.class);

    @Imports(mimeType = ANTLR_MIME_TYPE)
    @Localize(displayName = "Import")
    public static final NamedRegionKey<ImportKinds> IMPORTS = NamedRegionKey.create("imports", ImportKinds.class);

    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE, order = -10000, positionInZOrder = -1000,
            zOrder = HighlightZOrder.SHOW_OFF_RACK, fixedSize = false,
            colors = @ColoringCategory(name = "alternatives",
                    colors = {
                        @Coloration(
                                themes = POPULAR_BRIGHT_THEMES,
                                derivedFrom = "identifier",
                                fg = {180, 140, 70},
                                bold = true
                        ),
                        @Coloration(
                                themes = POPULAR_DARK_THEMES,
                                derivedFrom = "identifier",
                                fg = {242, 183, 84},
                                bold = true
                        )}))
    public static final NamedRegionKey<RuleTypes> NAMED_ALTERNATIVES = NamedRegionKey.create("alternatives", RuleTypes.class);

    @AntlrFoldsRegistration(mimeType = ANTLR_MIME_TYPE, foldSpec = @FoldTypeSpec(name = "header", guardedStart = 3,
            guardedEnd = 3, displayText = "header"))
    @Localize(displayName = "Header Element")
    public static final RegionsKey<HeaderMatter> HEADER_MATTER = RegionsKey.create(HeaderMatter.class, "headerMatter");

//    @SimpleNavigatorRegistration(displayName = "Rules", order = 1, mimeType = MIME_TYPE,
//            appearance = AntlrNavigatorAppearance.class)
    @ReferenceableFromImports(mimeType = ANTLR_MIME_TYPE)
    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE,
            positionInZOrder = 502, fixedSize = true, coloringName = "token-id")
    @Localize(displayName = "Rule")
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
                                    derivedFrom = "mark-occurrences"
                            //                                    bg = {220, 220, 190}
                            ),
                            @Coloration(
                                    themes = POPULAR_DARK_THEMES,
                                    derivedFrom = "mark-occurrences"
                            //                                    bg = {55, 50, 50}
                            )})
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
    @InplaceRename(mimeType = ANTLR_MIME_TYPE,
            filter = @CharFilterSpec(
                    initialCharacter = @CharPredicateSpec(
                            include = CharPredicates.JAVA_IDENTIFIER_START
                    ),
                    subsequentCharacters = @CharPredicateSpec(
                            include = CharPredicates.JAVA_IDENTIFIER_PART
                    )
            )
    )
    @Localize(displayName = "Rule Reference")
    @CustomRefactoringRegistration(actionPosition = 101, description = "Inline a rule and remove it",
            name = "Inline Rule", keybinding = "OS-N", mimeType = ANTLR_MIME_TYPE,
            enabledOnTokens = {PARSER_RULE_ID, TOKEN_ID, FRAGDEC_ID, ID},
            plugin = InlineRefactoringPlugin.class,
            lexer = ANTLRv4Lexer.class,
            publicRefactoringPluginClass = true
    //            ,ui = InlineRefactoringUI.class
    //            ,refactoring = InlineRuleRefactoring.InlineRule.class
    )
    public static final NameReferenceSetKey<RuleTypes> RULE_NAME_REFERENCES = RULE_NAMES.createReferenceKey("ruleRefs");

    @Localize(displayName = "Grammar Type")
    @InplaceRename(mimeType = ANTLR_MIME_TYPE,
            filter = @CharFilterSpec(
                    initialCharacter = @CharPredicateSpec(
                            include = {CharPredicates.FILE_NAME_SAFE, CharPredicates.JAVA_IDENTIFIER_START},
                            logicallyOr = false
                    ),
                    subsequentCharacters = @CharPredicateSpec(
                            include = {CharPredicates.FILE_NAME_SAFE, CharPredicates.JAVA_IDENTIFIER_PART},
                            logicallyOr = false
                    )
            ),
            useRefactoringApi = true
    )
    public static final SingletonKey<GrammarDeclaration> GRAMMAR_TYPE = SingletonKey.create(GrammarDeclaration.class);

    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE, colors = @ColoringCategory(name = "ebnfs",
            colors = {
                @Coloration(
                        themes = POPULAR_BRIGHT_THEMES,
                        italic = true /*,
                        bg = {249, 249, 220}*/
                ),
                @Coloration(
                        themes = POPULAR_DARK_THEMES,
                        italic = true/*,
                        bg = {58, 52, 47}*/
                )
            }))
    @Localize(displayName = "Repeating Elements")
    public static final RegionsKey<Set<EbnfProperty>> EBNFS = RegionsKey.create(Set.class, "ebnfs");

//    @AntlrFoldsRegistration(mimeType = MIME_TYPE, foldSpec = @FoldTypeSpec(name = "block", guardedStart = 3,
//            guardedEnd = 3, displayText = "block"))
    @Localize(displayName = "Parenthesized Blocks")
    public static final RegionsKey<Void> BLOCKS = RegionsKey.create(Void.class, "blocks");

    @AntlrFoldsRegistration(mimeType = ANTLR_MIME_TYPE, foldType = FoldTypeName.MEMBER)
    @Localize(displayName = "Code Folds")
    public static final RegionsKey<FoldableRegion> FOLDABLES = RegionsKey.create(FoldableRegion.class, "folds");

    @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE, colors = @ColoringCategory(name = "mode",
            colors = {
                @Coloration(
                        themes = POPULAR_BRIGHT_THEMES,
                        fg = {180, 40, 40},
                        bold = true,
                        derivedFrom = "identifier",
                        italic = true
                ),
                @Coloration(
                        themes = POPULAR_DARK_THEMES,
                        fg = {140, 141, 225},
                        derivedFrom = "identifier",
                        bold = true,
                        italic = true
                )
            }))
    public static final NamedRegionKey<LexerModes> MODES = NamedRegionKey.create("lexer-modes", LexerModes.class);

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
                                    //                                    bg = {220, 220, 190},
                                    derivedFrom = "mark-occurrences"
                            ),
                            @Coloration(
                                    themes = POPULAR_DARK_THEMES,
                                    //                                    bg = {50, 50, 50},
                                    derivedFrom = "mark-occurrences"
                            )})
        ),
        @HighlighterKeyRegistration(mimeType = ANTLR_MIME_TYPE,
                fixedSize = true,
                trigger = HighlightRefreshTrigger.DOCUMENT_CHANGED,
                order = 2000,
                zOrder = HighlightZOrder.SYNTAX_RACK,
                positionInZOrder = 1990,
                coloringName = "mode"
        )
    })

    @Goto(mimeType = ANTLR_MIME_TYPE)
    @InplaceRename(mimeType = ANTLR_MIME_TYPE,
            filter = @CharFilterSpec(
                    initialCharacter = @CharPredicateSpec(
                            include = CharPredicates.JAVA_IDENTIFIER_START
                    ),
                    subsequentCharacters = @CharPredicateSpec(
                            include = CharPredicates.JAVA_IDENTIFIER_PART
                    )
            )
    )
    @Localize(displayName = "Lexer Mode References")
    public static final NameReferenceSetKey<LexerModes> MODE_REFS = MODES.createReferenceKey("mode-refs");

    @Keybindings(description = "Whatever", displayName = "Whatever", menuPath = "Edit",
            name = "whatevs", mimeType = "text/x-g4", popup = true,
            keybindings = @Keybinding(key = Key.SEMICOLON, modifiers = KeyModifiers.CTRL_OR_COMMAND))
    public static void fooz() {
        JOptionPane.showMessageDialog(null, "Hello world");
    }
}
