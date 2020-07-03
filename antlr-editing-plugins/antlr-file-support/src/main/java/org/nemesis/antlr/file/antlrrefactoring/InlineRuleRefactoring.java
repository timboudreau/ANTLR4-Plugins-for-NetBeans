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
package org.nemesis.antlr.file.antlrrefactoring;

import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.file.antlrrefactoring.InlineRuleRefactoring.InlineRule;
import org.nemesis.antlr.refactoring.CustomRefactoring;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.openide.text.PositionBounds;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
@MimeRegistration(mimeType = ANTLR_MIME_TYPE, position = 100, service = CustomRefactoring.class)
public class InlineRuleRefactoring extends CustomRefactoring<InlineRule> {

    public InlineRuleRefactoring() {
        super(InlineRule.class);
    }

    @Override
    public InlineRefactoringPlugin apply(InlineRule refactoring, Extraction extraction, PositionBounds bounds) {
        return new InlineRefactoringPlugin(refactoring, extraction, bounds);
    }

    public static class InlineRule extends AbstractRefactoring {

        public InlineRule(Lookup refactoringSource, Object... contents) {
            this(contents.length == 0 ? refactoringSource
                    : new ProxyLookup(refactoringSource, Lookups.fixed(contents)));
        }

        public InlineRule(Lookup refactoringSource) {
            super(refactoringSource);
            getContext().add(RULE_NAME_REFERENCES);
        }
    }
}
