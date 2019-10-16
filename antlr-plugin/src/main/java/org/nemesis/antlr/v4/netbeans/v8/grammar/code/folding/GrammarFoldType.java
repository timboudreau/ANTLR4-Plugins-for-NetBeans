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
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.folding;

import org.nemesis.antlr.common.extractiontypes.FoldableRegion;
import org.netbeans.api.editor.fold.FoldTemplate;
import org.netbeans.api.editor.fold.FoldType;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Frédéric Yvon Vinet
 */
@Messages({"comment=Comment", "action=Action", "rule=Rule"})
public class GrammarFoldType {
    private static final String FOLDED_COMMENT = "/*...*/";
    public static final FoldType COMMENT_FOLD_TYPE = FoldType.create
                   ("comment"           ,
                    Bundle.comment()           ,
                    new FoldTemplate
                        (2             , // length of the guarded starting token
                         2             , // length of the guarded end token
                         FOLDED_COMMENT));
    private static final String FOLDED_ACTION = "{...}";
    public static final FoldType ACTION_FOLD_TYPE = FoldType.create
                   ("action"            ,
                    Bundle.action()            ,
                    new FoldTemplate
                        (1             , // length of the guarded starting token
                         1             , // length of the guarded end token
                         FOLDED_ACTION));
    private static final String FOLDED_RULE = "<rule>";
    public static final FoldType RULE_FOLD_TYPE = FoldType.create
                   ("rule"            ,
                    Bundle.rule()            ,
                    new FoldTemplate
                        (0             , // length of the guarded starting token
                         0             , // length of the guarded end token
                         FOLDED_RULE));

    public static FoldType forFoldableRegion(FoldableRegion region) {
        switch (region.kind) {
            case ACTION:
                return ACTION_FOLD_TYPE;
            case COMMENT:
            case DOC_COMMENT:
                return COMMENT_FOLD_TYPE;
            case RULE:
                return RULE_FOLD_TYPE;
            default:
                throw new AssertionError(region.kind);
        }
    }
}