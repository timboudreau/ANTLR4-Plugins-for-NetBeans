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
import org.nemesis.antlr.file.AntlrKeys;
import org.nemesis.antlr.refactoring.AntlrRefactoringPluginFactory;
import org.nemesis.antlr.refactoring.common.Refactorability;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.charfilter.CharPredicates;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProviders({
    @ServiceProvider(service = RefactoringPluginFactory.class, position = 41),
    @ServiceProvider(service = Refactorability.class, position = 41)
})
public final class AntlrLanguageRefactoringPluginFactory extends AntlrRefactoringPluginFactory {

    public AntlrLanguageRefactoringPluginFactory() {
        super(ANTLR_MIME_TYPE, bldr -> {
            bldr.rename()
                    .renaming(AntlrKeys.GRAMMAR_TYPE)
                    .withNameFilter(
                            CharFilter.of(
                                    CharPredicates.JAVA_IDENTIFIER_START,
                                    CharPredicates.JAVA_IDENTIFIER_PART
                            ))
                    .build()
                    .usages().finding(AntlrKeys.GRAMMAR_TYPE)
                    .usages().finding(AntlrKeys.RULE_NAME_REFERENCES)
                    .usages().finding(AntlrKeys.RULE_NAME_REFERENCES.referencing())
                    .rename().renaming(AntlrKeys.RULE_NAME_REFERENCES)
                    .withCharFilter(
                            CharFilter.of(
                                    CharPredicates.JAVA_IDENTIFIER_START,
                                    CharPredicates.JAVA_IDENTIFIER_PART
                            ));

        });
    }
}
