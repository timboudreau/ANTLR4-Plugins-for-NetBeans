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
package org.nemesis.antlr.file.editor.ext.impl;

import com.mastfrog.util.collections.ArrayUtils;
import org.nemesis.antlr.ANTLRv4Lexer;
import org.nemesis.antlr.file.editor.ext.EditorFeatures;
import static org.nemesis.antlr.ANTLRv4Lexer.BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.CHN_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.CHN_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.CHN_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.DOC_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.FRAGDEC_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.FRAGDEC_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.HDR_IMPRT_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HDR_IMPRT_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.HDR_PCKG_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HDR_PCKG_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_P_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_P_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_P_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.HEADER_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.ID_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.ID_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.ID_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.IMPORT_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.IMPORT_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.IMPORT_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.LEXCOM_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.LEXCOM_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.LEXCOM_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.OPT_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.OPT_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.OPT_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_OPT_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_OPT_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_OPT_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.PARDEC_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.TOKDEC_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.TOK_BLOCK_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.TOK_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.TOK_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.TYPE_LINE_COMMENT;
import static org.nemesis.antlr.ANTLRv4Lexer.TYPE_WS;
import static org.nemesis.antlr.ANTLRv4Lexer.WS;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlrformatting.api.Criteria;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.netbeans.spi.options.OptionsPanelController;

/**
 *
 * @author Tim Boudreau
 */
public class TestImpl extends EditorFeatures {

    private static final int[] WHITESPACE_TOKEN_IDS = {PARDEC_WS, ID_WS, IMPORT_WS, CHN_WS, FRAGDEC_WS,
        HDR_IMPRT_WS, HDR_PCKG_WS, HEADER_P_WS, HEADER_WS, LEXCOM_WS,
        OPT_WS, PARDEC_OPT_WS, TOK_WS, TOKDEC_WS, TYPE_WS, WS};
    private static final int[] COMMENT_TOKEN_IDS = {
        LINE_COMMENT, BLOCK_COMMENT, CHN_BLOCK_COMMENT,
        FRAGDEC_LINE_COMMENT, CHN_LINE_COMMENT, DOC_COMMENT,
        HDR_IMPRT_LINE_COMMENT, HDR_PCKG_LINE_COMMENT,
        HEADER_BLOCK_COMMENT, HEADER_LINE_COMMENT, HEADER_P_BLOCK_COMMENT,
        HEADER_P_LINE_COMMENT, ID_BLOCK_COMMENT, ID_LINE_COMMENT,
        IMPORT_BLOCK_COMMENT, IMPORT_LINE_COMMENT, LEXCOM_BLOCK_COMMENT,
        LEXCOM_LINE_COMMENT, OPT_BLOCK_COMMENT, OPT_LINE_COMMENT, PARDEC_LINE_COMMENT,
        PARDEC_BLOCK_COMMENT, PARDEC_OPT_LINE_COMMENT, PARDEC_OPT_BLOCK_COMMENT,
        TOK_BLOCK_COMMENT, TOK_LINE_COMMENT,
        TYPE_LINE_COMMENT};

    private static final int[] IGNORABLE_TOKEN_IDS;

    static {
        IGNORABLE_TOKEN_IDS = ArrayUtils.concatenate(WHITESPACE_TOKEN_IDS,
                COMMENT_TOKEN_IDS);
    }

    private static final Criteria CRIT = Criteria.forVocabulary(ANTLRv4Lexer.VOCABULARY);
    static final TestImpl INSTANCE = new TestImpl();

    private TestImpl() {
        super(ANTLR_MIME_TYPE, b -> {
            b.insertBoilerplate(" : ^;").onlyWhenAtLineEnd()
                    .whenPrecedingToken(CRIT.anyOf(ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID))
                    .whenPrecededByPattern(
                            CRIT.matching(ANTLRv4Lexer.SEMI),
                            CRIT.anyOf(ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID)
                    )
                    .orDocumentStartOrEnd().ignoring(CRIT.anyOf(IGNORABLE_TOKEN_IDS))
                    .stoppingOn(CRIT.matching(ANTLRv4Lexer.COLON))
                    .setName("Insert colon after rule name")
                    .setDescription("Causes a : and ; to automatically be inserted "
                            + "when you type a new rule name on a blank line, and places "
                            + "the caret before the ;")
                    .whenKeyTyped(' ');

            b.insertBoilerplate("(^)")
                    .whenPrecedingToken(CRIT.anyOf(ANTLRv4Lexer.COLON,
                            ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID,
                            ANTLRv4Lexer.TOKDEC_WS, ANTLRv4Lexer.PARDEC_WS,
                            ANTLRv4Lexer.FRAGDEC_WS
                    ))
                    .setName("Insert closing ) after (")
                    .whenKeyTyped('(');

            b.insertBoilerplate("{^}")
                    .whenPrecedingToken(CRIT.anyOf(ANTLRv4Lexer.COLON,
                            ANTLRv4Lexer.PARSER_RULE_ID, ANTLRv4Lexer.TOKEN_ID,
                            ANTLRv4Lexer.TOKDEC_WS, ANTLRv4Lexer.PARDEC_WS,
                            ANTLRv4Lexer.LEXCOM_WS, ANTLRv4Lexer.PARDEC_OPT_WS,
                            ANTLRv4Lexer.FRAGDEC_WS
                    ))
                    .setName("Insert closing { after }")
                    .whenKeyTyped('{');

        });
    }

    @MimeRegistration(mimeType = ANTLR_MIME_TYPE,
            service = TypedTextInterceptor.Factory.class,
            position = 10)
    public static TypedTextInterceptor.Factory typingFactoryRegistration() {
        return INSTANCE.typingFactory();
    }

    @MimeRegistration(mimeType = ANTLR_MIME_TYPE, position = 171, service = EditorFeatures.class)
    public static EditorFeatures instance() {
        return INSTANCE;
    }

    @OptionsPanelController.SubRegistration(
            location = "Editor",
            displayName = "#AdvancedOption_DisplayName_AntlrOptions",
            keywords = "#AdvancedOption_Keywords_AntlrOptions",
            keywordsCategory = "Editor/AntlrOptions"
    )
    @org.openide.util.NbBundle.Messages({
        "AdvancedOption_DisplayName_AntlrOptions=Antlr",
        "AdvancedOption_Keywords_AntlrOptions=antlr"
    })
    public static OptionsPanelController optionsPanelRegistration() {
        return INSTANCE.optionsPanelController();
    }
}
