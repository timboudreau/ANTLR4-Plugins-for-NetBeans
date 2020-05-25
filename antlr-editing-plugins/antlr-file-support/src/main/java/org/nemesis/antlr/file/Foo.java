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

import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.Boilerplate;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.DelimiterPair;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.Elision;
import com.mastfrog.editor.features.annotations.EditorFeaturesRegistration.LinePosition;
import org.nemesis.antlr.ANTLRv4Lexer;
import static org.nemesis.antlr.ANTLRv4Lexer.BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.LPAREN;
import static org.nemesis.antlr.ANTLRv4Lexer.PARSER_RULE_ID;
import static org.nemesis.antlr.ANTLRv4Lexer.RPAREN;
import static org.nemesis.antlr.ANTLRv4Lexer.STRING_LITERAL;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKEN_ID;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.spi.language.keybindings.ActionBinding;
import org.nemesis.antlr.spi.language.keybindings.ActionBindings;
import org.nemesis.antlr.spi.language.keybindings.BuiltInAction;
import org.nemesis.antlr.spi.language.keybindings.Key;
import org.nemesis.antlr.spi.language.keybindings.KeyModifiers;
import org.nemesis.antlr.spi.language.keybindings.Keybinding;

/**
 *
 * @author Tim Boudreau
 */
@EditorFeaturesRegistration(
        mimeType = ANTLR_MIME_TYPE,
        order = 10,
        lexer = ANTLRv4Lexer.class,
        languageDisplayName = "Antlr",
        elideTypedChars = {
            @Elision(
                    backward = true,
                    name = "Skip extract :'s",
                    onKeyTyped = ':',
                    category = "Stuff",
                    whenNotIn = STRING_LITERAL),
            @Elision(
                    backward = false,
                    name = "Skip extract :'s",
                    onKeyTyped = ':',
                    category = "Stuff",
                    whenNotIn = STRING_LITERAL)
        },
        deleteMatchingDelimiter = {
            @DelimiterPair(
                    category = "Stuff",
                    openingToken = LPAREN,
                    closingToken = RPAREN,
                    name = "Delete matching paren if empty",
                    ignoring = {ANTLRv4Lexer.WS, ANTLRv4Lexer.PARDEC_WS},
                    description = "Delete stuff")
        },
        insertBoilerplate = {
            @Boilerplate(
                    category = "Boilerplate",
                    description = "Insert : and ; at start of added rule",
                    linePosition = LinePosition.AT_END,
                    name = "Insert :",
                    onChar = ' ',
                    whenPrecedingToken = {TOKEN_ID, PARSER_RULE_ID},
                    inserting = " : ^;",
                    whenCurrentTokenNot = {STRING_LITERAL, LINE_COMMENT, BLOCK_COMMENT},
                    whenFollowedByPattern = "<| SEMI {PARSER_RULE_ID TOKEN_ID} ! COLON ? PARDEC_WS ID_WS IMPORT_WS CHN_WS FRAGDEC_WS HDR_IMPRT_WS HDR_PCKG_WS HEADER_P_WS HEADER_WS LEXCOM_WS OPT_WS PARDEC_OPT_WS TOK_WS TOKDEC_WS TYPE_WS WS LINE_COMMENT BLOCK_COMMENT CHN_BLOCK_COMMENT FRAGDEC_LINE_COMMENT CHN_LINE_COMMENT DOC_COMMENT HDR_IMPRT_LINE_COMMENT HDR_PCKG_LINE_COMMENT HEADER_BLOCK_COMMENT HEADER_LINE_COMMENT HEADER_P_BLOCK_COMMENT HEADER_P_LINE_COMMENT ID_BLOCK_COMMENT ID_LINE_COMMENT IMPORT_BLOCK_COMMENT IMPORT_LINE_COMMENT LEXCOM_BLOCK_COMMENT LEXCOM_LINE_COMMENT OPT_BLOCK_COMMENT OPT_LINE_COMMENT PARDEC_LINE_COMMENT PARDEC_BLOCK_COMMENT PARDEC_OPT_LINE_COMMENT PARDEC_OPT_BLOCK_COMMENT TOK_BLOCK_COMMENT TOK_LINE_COMMENT TYPE_LINE_COMMENT"
            ),
            @Boilerplate(
                    category = "Boilerplate",
                    name = "Insert closing ) after (",
                    onChar = '(',
                    whenPrecedingToken = {ANTLRv4Lexer.COLON,
                        ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID,
                        ANTLRv4Lexer.TOKDEC_WS, ANTLRv4Lexer.PARDEC_WS,
                        ANTLRv4Lexer.FRAGDEC_WS,
                        ANTLRv4Lexer.END_ACTION},
                    inserting = "(^)",
                    whenCurrentTokenNot = {
                        ANTLRv4Lexer.STRING_LITERAL,
                        ANTLRv4Lexer.LINE_COMMENT, ANTLRv4Lexer.BLOCK_COMMENT,
                        ANTLRv4Lexer.STRING_LITERAL,
                        ANTLRv4Lexer.PARDEC_LINE_COMMENT,
                        ANTLRv4Lexer.CHN_BLOCK_COMMENT,
                        ANTLRv4Lexer.CHN_LINE_COMMENT,
                        ANTLRv4Lexer.PARDEC_BLOCK_COMMENT
                    }
            ),
            @Boilerplate(
                    category = "Boilerplate",
                    name = "Insert closing { after }",
                    onChar = '{',
                    whenPrecedingToken = {ANTLRv4Lexer.COLON,
                        ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID,
                        ANTLRv4Lexer.TOKDEC_WS, ANTLRv4Lexer.PARDEC_WS,
                        ANTLRv4Lexer.LEXCOM_WS, ANTLRv4Lexer.PARDEC_OPT_WS,
                        ANTLRv4Lexer.FRAGDEC_WS,
                        ANTLRv4Lexer.TOKENS},
                    inserting = "{^}",
                    whenCurrentTokenNot = {
                        ANTLRv4Lexer.STRING_LITERAL,
                        ANTLRv4Lexer.LINE_COMMENT, ANTLRv4Lexer.BLOCK_COMMENT,
                        ANTLRv4Lexer.STRING_LITERAL,
                        ANTLRv4Lexer.PARDEC_LINE_COMMENT,
                        ANTLRv4Lexer.CHN_BLOCK_COMMENT,
                        ANTLRv4Lexer.CHN_LINE_COMMENT,
                        ANTLRv4Lexer.PARDEC_BLOCK_COMMENT}
            ),
            @Boilerplate(
                    category = "Boilerplate",
                    name = "Insert closing single-quote",
                    onChar = '\'',
                    whenPrecedingToken = {ANTLRv4Lexer.COLON,
                        ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID,
                        ANTLRv4Lexer.TOKDEC_WS, ANTLRv4Lexer.PARDEC_WS,
                        ANTLRv4Lexer.LEXCOM_WS, ANTLRv4Lexer.PARDEC_OPT_WS,
                        ANTLRv4Lexer.FRAGDEC_WS, ANTLRv4Lexer.STRING_LITERAL,
                        ANTLRv4Lexer.PARSER_RULE_ID},
                    inserting = "'^'",
                    whenCurrentTokenNot = {
                        ANTLRv4Lexer.LINE_COMMENT, ANTLRv4Lexer.BLOCK_COMMENT,
                        ANTLRv4Lexer.PARDEC_LINE_COMMENT,
                        ANTLRv4Lexer.CHN_BLOCK_COMMENT,
                        ANTLRv4Lexer.CHN_LINE_COMMENT,
                        ANTLRv4Lexer.PARDEC_BLOCK_COMMENT,
                        ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT,
                        ANTLRv4Lexer.LEXCOM_LINE_COMMENT
                    }
            )
        }
)
@ActionBindings(mimeType = "text/x-g4", bindings = {
    @ActionBinding(action = BuiltInAction.ToggleComment, bindings = {
        @Keybinding(modifiers = KeyModifiers.CTRL_OR_COMMAND, key = Key.SLASH)
    })
})
public class Foo {

}
