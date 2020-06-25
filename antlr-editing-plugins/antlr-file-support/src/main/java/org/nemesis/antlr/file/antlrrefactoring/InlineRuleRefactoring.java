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

import com.mastfrog.function.state.Obj;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.RuleTypes;
import org.nemesis.antlr.file.AntlrKeys;
import static org.nemesis.antlr.file.AntlrKeys.RULE_BOUNDS;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAMES;
import static org.nemesis.antlr.file.AntlrKeys.RULE_NAME_REFERENCES;
import org.nemesis.antlr.file.antlrrefactoring.InlineRuleRefactoring.InlineRule;
import org.nemesis.antlr.refactoring.AbstractRefactoringContext;
import org.nemesis.antlr.refactoring.CustomRefactoring;
import org.nemesis.antlr.refactoring.ReplaceRanges;
import org.nemesis.antlr.refactoring.usages.SimpleUsagesFinder;
import org.nemesis.data.SemanticRegion;
import org.nemesis.data.named.NamedRegionReferenceSets;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegionReference;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.AttributedForeignNameReference;
import org.nemesis.extraction.Attributions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.UnknownNameReference;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
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

    static class InlineRule extends AbstractRefactoring {

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
