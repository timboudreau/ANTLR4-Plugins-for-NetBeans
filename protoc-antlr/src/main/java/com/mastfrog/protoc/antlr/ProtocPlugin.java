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
package com.mastfrog.protoc.antlr;

import com.mastfrog.protoc.antlr.Protobuf3Parser.EnumDefinitionContext;
import com.mastfrog.protoc.antlr.Protobuf3Parser.ProtoContext;
import com.mastfrog.util.strings.Strings;
import java.util.List;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.navigator.SimpleNavigatorRegistration;
import org.nemesis.antlr.spi.language.AntlrLanguageRegistration;
import org.nemesis.antlr.spi.language.highlighting.Coloration;
import org.nemesis.antlr.spi.language.highlighting.ColoringCategory;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightRefreshTrigger;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlightZOrder;
import org.nemesis.antlr.spi.language.highlighting.semantic.HighlighterKeyRegistration;
import org.nemesis.extraction.ExtractionRegistration;
import org.nemesis.extraction.ExtractorBuilder;
import org.nemesis.extraction.NamedRegionData;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.extraction.key.NamedRegionKey;

/**
 *
 * @author Tim Boudreau
 */
@AntlrLanguageRegistration(
        name = "Protobuf",
        lexer = Protobuf3Lexer.class,
        mimeType = "text/x-protobuf",
        sample = ProtocPlugin.SAMPLE,
        file = @AntlrLanguageRegistration.FileType(extension = "proto", multiview = false,
                iconBase = "protobuf.png" /* , hooks = YaslDataObjectHooks.class */),
        parser = @AntlrLanguageRegistration.ParserControl(type = Protobuf3Parser.class, entryPointRule = Protobuf3Parser.RULE_proto,
                generateSyntaxTreeNavigatorPanel = true, helper = ProtocHelper.class),
        lineCommentPrefix = "//",
        localizingBundle = "com.mastfrog.protoc.antlr.Bundle",
        genericCodeCompletion = @AntlrLanguageRegistration.CodeCompletion(
                ignoreTokens = {
                    Protobuf3Lexer.COMMA, Protobuf3Lexer.WS
                }),
        syntax = @AntlrLanguageRegistration.SyntaxInfo(whitespaceTokens = Protobuf3Lexer.WS,
                commentTokens = {Protobuf3Lexer.COMMENT, Protobuf3Lexer.LINE_COMMENT},
                bracketSkipTokens = {
                    Protobuf3Lexer.WS, Protobuf3Lexer.COMMENT, Protobuf3Lexer.LINE_COMMENT
                }
        ),
        categories = {
            @TokenCategory(name = "identifier",
                    tokenIds = {
                        Protobuf3Lexer.Ident
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "identifier",
                        fg = {67, 67, 156}, bold = true),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "identifier",
                        fg = {40, 40, 160})
                    }),
            @TokenCategory(name = "operator",
                    tokenIds = {
                        Protobuf3Lexer.ASSIGN, Protobuf3Lexer.MINUS,
                        Protobuf3Lexer.PLUS
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "operator",
                        fg = {178, 130, 100}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "operator",
                        fg = {40, 40, 160})
                    }),
            @TokenCategory(name = "separator",
                    tokenIds = {
                        Protobuf3Lexer.DOT, Protobuf3Lexer.COMMA, Protobuf3Lexer.LBRACE,
                        Protobuf3Lexer.LBRACK, Protobuf3Lexer.LCHEVR, Protobuf3Lexer.RBRACE,
                        Protobuf3Lexer.RBRACK, Protobuf3Lexer.RCHEVR, Protobuf3Lexer.SEMI
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "operator",
                        fg = {178, 167, 163}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "operator",
                        fg = {40, 80, 160})
                    }),
            @TokenCategory(name = "literal",
                    tokenIds = {
                        Protobuf3Lexer.BoolLit, Protobuf3Lexer.IntLit,
                        Protobuf3Lexer.FloatLit, Protobuf3Lexer.StrLit,},
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "string",
                        fg = {129, 123, 26}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "string",
                        fg = {40, 40, 160})
                    }),
            @TokenCategory(name = "comment",
                    tokenIds = {
                        Protobuf3Lexer.LINE_COMMENT, Protobuf3Lexer.COMMENT
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "comment",
                        fg = {180, 180, 180}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "comment", fg = {0,
                    0, 0})
                    }),
            @TokenCategory(name = "types",
                    tokenIds = {
                        Protobuf3Lexer.BOOL, Protobuf3Lexer.BYTES, Protobuf3Lexer.DOUBLE,
                        Protobuf3Lexer.FLOAT, Protobuf3Lexer.FIXED32, Protobuf3Lexer.FIXED64,
                        Protobuf3Lexer.INT32, Protobuf3Lexer.INT64, Protobuf3Lexer.SINT32,
                        Protobuf3Lexer.SINT64, Protobuf3Lexer.SFIXED32, Protobuf3Lexer.SFIXED64,
                        Protobuf3Lexer.ENUM, Protobuf3Lexer.ONEOF, Protobuf3Lexer.MAP
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "keyword",
                        fg = {71, 97, 145}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "keyword",
                        fg = {40, 160, 89})
                    }),
            @TokenCategory(name = "keywords",
                    tokenIds = {
                        Protobuf3Lexer.IMPORT, Protobuf3Lexer.MESSAGE, Protobuf3Lexer.TO,
                        Protobuf3Lexer.WEAK, Protobuf3Lexer.SYNTAX, Protobuf3Lexer.SERVICE,
                        Protobuf3Lexer.OPTION, Protobuf3Lexer.REPEATED, Protobuf3Lexer.RESERVED,
                        Protobuf3Lexer.PUBLIC, Protobuf3Lexer.PACKAGE
                    },
                    colors = {
                        @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "keyword",
                        fg = {145, 71, 97}),
                        @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "keyword",
                        fg = {89, 40, 160})
                    }),}
)
public class ProtocPlugin {

    @SimpleNavigatorRegistration(mimeType = "text/x-protobuf", displayName = "#NAV_PANEL", order = 0)
    public static final NamedRegionKey<Kinds> NAMES = NamedRegionKey.create("protocolElements", Kinds.class);
    @HighlighterKeyRegistration(mimeType = "text/x-protobuf",
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
    )
    public static final NameReferenceSetKey<Kinds> REFS = NAMES.createReferenceKey("protocolRefs");

    public static final String SAMPLE = "syntax = \"proto2\";\n"
            + "package tutorial;\n"
            + "option java_package = \"com.mastfrog.protocol.telemetry\";\n"
            + "option java_multiple_files = true;\n"
            + "/**\n"
            + "    A block comment."
            + "*/\n"
            + "enum MessageType {\n"
            + "    UNKNOWN_MESSAGE = 0;\n"
            + "    MEASUREMENT = 1;\n"
            + "    SYSTEM = 2;\n"
            + "}\n";

    public static enum Kinds {
        ENUM,
        MESSAGE
    }

    @ExtractionRegistration(mimeType = "text/x-protobuf", entryPoint = ProtoContext.class)
    static void extract(ExtractorBuilder<? super ProtoContext> bldr) {
        bldr.extractNamedRegionsKeyedTo(Kinds.class).recordingNamePositionUnder(NAMES)
                .whereRuleIs(Protobuf3Parser.EnumDefinitionContext.class)
                .derivingNameFromTerminalNodeWith(Kinds.ENUM, EnumDefinitionContext::ENUM)
                .whereRuleIs(Protobuf3Parser.MessageTypeContext.class)
                .derivingNameWith((Protobuf3Parser.MessageTypeContext t) -> {
                    List<TerminalNode> ids = t.Ident();
                    if (ids == null || ids.isEmpty()) {
                        return null;
                    }
                    int start = ids.get(0).getSymbol().getStartIndex();
                    int end = ids.get(ids.size() - 1).getSymbol().getStopIndex() + 1;
                    return NamedRegionData.create(Strings.join('.', t.Ident()), Kinds.MESSAGE, start, end);
                }).collectingReferencesUnder(REFS)
                .whereReferenceContainingRuleIs(Protobuf3Parser.EnumNameContext.class)
                .whenAncestorRuleOf(Protobuf3Parser.MessageTypeContext.class)
                .derivingReferenceOffsetsFromTerminalNodeWith(Kinds.ENUM, (Protobuf3Parser.EnumNameContext enc) -> {
                    return enc.Ident();
                })
                .whereReferenceContainingRuleIs(Protobuf3Parser.MessageNameContext.class)
                .whenAncestorRuleOf(Protobuf3Parser.MessageTypeContext.class)
                .derivingReferenceOffsetsFromTerminalNodeWith(ProtocPlugin.Kinds.MESSAGE, mnc -> {
                    return mnc.Ident();
                }).finishReferenceCollector().finishNamedRegions();
    }
}
