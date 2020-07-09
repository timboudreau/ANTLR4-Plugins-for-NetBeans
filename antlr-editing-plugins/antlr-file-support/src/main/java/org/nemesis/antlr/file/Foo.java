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
import static org.nemesis.antlr.ANTLRv4Lexer.*;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;

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
                    name = "Ignore a : typed right after another :",
                    onKeyTyped = ':',
                    category = "Elisions",
                    whenNotIn = {STRING_LITERAL, ACTION_CONTENT, LEXER_CHAR_SET}),
            @Elision(
                    backward = false,
                    name = "Skip extract :'s",
                    onKeyTyped = ':',
                    category = "Elisions",
                    whenNotIn = {STRING_LITERAL, ACTION_CONTENT, LEXER_CHAR_SET}),
            @Elision(
                    backward = true,
                    name = "Elide ] after [",
                    onKeyTyped = ']',
                    category="Elisions",
                    whenNotIn = {STRING_LITERAL, ACTION_CONTENT},
                    description = "Elide ] after ["
            )
        },
        deleteMatchingDelimiter = {
            @DelimiterPair(
                    category = "Delimiters",
                    openingToken = LPAREN,
                    closingToken = RPAREN,
                    name = "Delete matching paren if empty",
                    ignoring = {WS, PARDEC_WS},
                    description = "Delete Matching Delimiters"),
            @DelimiterPair(
                    category = "Delimiters",
                    openingToken = LEXER_CHAR_SET,
                    closingToken = LEXER_CHAR_SET,
                    name = "Delete matching [ if empty",
                    ignoring = {WS, PARDEC_WS, LEXER_CHAR_SET},
                    description = "Delete Matching Delimiters")
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
                    whenCurrentTokenNot = {STRING_LITERAL, LINE_COMMENT, BLOCK_COMMENT, LPAREN, ACTION_CONTENT, LBRACE, LEXER_CHAR_SET, COLON},
                    whenFollowedByPattern = "<| SEMI {PARSER_RULE_ID TOKEN_ID} ! COLON ? PARDEC_WS ID_WS IMPORT_WS CHN_WS FRAGDEC_WS HDR_IMPRT_WS HDR_PCKG_WS HEADER_P_WS HEADER_WS LEXCOM_WS OPT_WS PARDEC_OPT_WS TOK_WS TOKDEC_WS TYPE_WS WS LINE_COMMENT BLOCK_COMMENT CHN_BLOCK_COMMENT FRAGDEC_LINE_COMMENT CHN_LINE_COMMENT DOC_COMMENT HDR_IMPRT_LINE_COMMENT HDR_PCKG_LINE_COMMENT HEADER_BLOCK_COMMENT HEADER_LINE_COMMENT HEADER_P_BLOCK_COMMENT HEADER_P_LINE_COMMENT ID_BLOCK_COMMENT ID_LINE_COMMENT IMPORT_BLOCK_COMMENT IMPORT_LINE_COMMENT LEXCOM_BLOCK_COMMENT LEXCOM_LINE_COMMENT OPT_BLOCK_COMMENT OPT_LINE_COMMENT PARDEC_LINE_COMMENT PARDEC_BLOCK_COMMENT PARDEC_OPT_LINE_COMMENT PARDEC_OPT_BLOCK_COMMENT TOK_BLOCK_COMMENT TOK_LINE_COMMENT TYPE_LINE_COMMENT"
            ),
            @Boilerplate(
                    category = "Boilerplate",
                    description = "Insert a matching ] when [ typed",
                    linePosition = LinePosition.ANY,
                    name = "Insert ] for [",
                    onChar = '[',
                    inserting = "[^]",
                    whenPrecedingToken = {COLON, PARDEC_WS, TOKDEC_WS,
                        LEXER_CHAR_SET,
                        ID, FRAGDEC_ID,
                        TOKEN_ID, STRING_LITERAL,
                        NOT, LPAREN
                    },
                    whenCurrentTokenNot = {ACTION_CONTENT, STRING_LITERAL, LINE_COMMENT, BLOCK_COMMENT, LBRACE,
                        PARDEC_LINE_COMMENT,
                        CHN_BLOCK_COMMENT,
                        CHN_LINE_COMMENT,
                        PARDEC_BLOCK_COMMENT
                    }
            ),
            @Boilerplate(
                    category = "Boilerplate",
                    name = "Insert a matching ) after (",
                    onChar = '(',
                    whenPrecedingToken = {ANTLRv4Lexer.COLON,
                        PARSER_RULE_ID, TOKEN_ID,
                        TOKDEC_WS, PARDEC_WS,
                        FRAGDEC_WS,
                        END_ACTION},
                    inserting = "(^)",
                    whenCurrentTokenNot = {
                        LEXER_CHAR_SET,
                        STRING_LITERAL,
                        ACTION_CONTENT,
                        ID,
                        PARSER_RULE_ID,
                        TOKEN_ID,
                        LINE_COMMENT,
                        BLOCK_COMMENT,
                        STRING_LITERAL,
                        PARDEC_LINE_COMMENT,
                        CHN_BLOCK_COMMENT,
                        CHN_LINE_COMMENT,
                        PARDEC_BLOCK_COMMENT
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
//@ActionBindings(mimeType = "text/x-g4", bindings = {
//    @ActionBinding(action = BuiltInAction.ToggleComment, bindings = {
//        @Keybinding(modifiers = KeyModifiers.CTRL_OR_COMMAND, key = Key.SLASH)
//    })
//})
public class Foo {
/*
    @Keybindings(mimeType = ANTLR_MIME_TYPE, description = "Dump the editor kit", displayName = "Dump Editor Kit", name = "dumpEditorKit",
            popup = true, menuPath = "Edit", keybindings = @Keybinding(key = Key.EIGHT, modifiers = KeyModifiers.CTRL_OR_COMMAND))
    public static void dumpEditorKit(JTextComponent comp) {
        EditorKit kit = ((JEditorPane) comp).getEditorKit();
        StringBuilder sb = new StringBuilder("Editor Kit: ").append(kit.getClass().getName()).append('\n');
        if (kit instanceof BaseKit) {
            BaseKit bk = (BaseKit) kit;
            sb.append(bk.getContentType()).append("\n\n");
            MultiKeymap map = bk.getKeymap();
            sb.append("KeymapClass\t" + map.getClass().getName()).append("\n");
            Action[] actions = map.getBoundActions();
            KeyStroke[] keys = map.getBoundKeyStrokes();
            sb.append(actions.length).append(" actions ").append(keys.length).append(" keys").append('\n');
            sb.append("Locally Defined:\n");
            for (int i = 0; i < keys.length; i++) {
                KeyStroke key = keys[i];
                if (map.isLocallyDefined(key)) {
                    Action a = map.getAction(key);
                    sb.append('\t').append(i).append(keyStrokeToString(key))
                            .append('\t').append(a.getValue(Action.NAME)).append(" (").append(a.getClass().getName()).append(")\n");
                }
            }
            sb.append("\nInherited: \n");
            for (int i = 0; i < keys.length; i++) {
                KeyStroke key = keys[i];
                if (!map.isLocallyDefined(key)) {
                    Action a = map.getAction(key);
                    sb.append('\t').append(i).append(keyStrokeToString(key))
                            .append('\t').append(a.getValue(Action.NAME)).append(" (").append(a.getClass().getName()).append(")\n");
                }
            }
            Action tk = bk.getActionByName("toggle-comment");
            sb.append("\nToggle Comment Action:\t").append(tk);
        } else {
            sb.append("Not a BaseKit: ").append(kit.getClass().getName()).append("\t").append(kit);
        }

        TopComponent tc = new TopComponent();
        tc.setLayout(new BorderLayout());
        JTextArea a = new JTextArea(sb.toString());
        a.setEditable(false);
        tc.add(new JScrollPane(a), BorderLayout.CENTER);
        tc.setDisplayName("Editor Kit");
        tc.open();
        tc.requestActive();
    }
    */
}
