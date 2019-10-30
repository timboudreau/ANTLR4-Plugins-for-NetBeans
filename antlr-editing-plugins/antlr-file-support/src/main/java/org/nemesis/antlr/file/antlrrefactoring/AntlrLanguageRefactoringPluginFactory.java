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
import org.nemesis.antlr.file.refactoring.AntlrRefactoringPluginFactory;
import static org.nemesis.antlr.file.refactoring.AntlrRefactoringPluginFactory.namedStringifier;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.charfilter.CharPredicates;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RefactoringPluginFactory.class, position = 1000)
public final class AntlrLanguageRefactoringPluginFactory implements RefactoringPluginFactory {

    private RefactoringPluginFactory delegate;

    private synchronized RefactoringPluginFactory delegate() {
        if (delegate == null) {
            delegate = AntlrRefactoringPluginFactory.builder(ANTLR_MIME_TYPE)
                    .rename().renaming(AntlrKeys.GRAMMAR_TYPE)
                    .withNameFilter(CharFilter.of(CharPredicates.JAVA_IDENTIFIER_START, CharPredicates.JAVA_IDENTIFIER_PART))
                    .withStringifier(namedStringifier())
                    .build()
                    .usages().finding(AntlrKeys.GRAMMAR_TYPE, namedStringifier())
                    .usages().finding(AntlrKeys.RULE_NAMES)
                    .usages().finding(AntlrKeys.RULE_NAME_REFERENCES)
                    .build();
            System.out.println("Created ANTLR refactorings: \n" + delegate + "\n");
        }
        return delegate;
    }

    @Override
    public RefactoringPlugin createInstance(AbstractRefactoring ar) {
        return delegate().createInstance(ar);
    }

}
