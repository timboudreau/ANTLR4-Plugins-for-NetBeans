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

package com.mastfrog.antlr.refactoring.ui;

import javax.swing.event.ChangeListener;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - the name",
    "find_usages=Find usages - {0}"
})
public class FindUsagesUI extends BaseUI<WhereUsedQuery> {

    private final String what;

    public FindUsagesUI(Lookup lookup) {
        super(new WhereUsedQuery(lookup));
        this.what = "huh?";
    }

    @Override
    public String getName() {
        return Bundle.find_usages(what);
    }

    @Override
    public String getDescription() {
        return getName();
    }

    @Override
    public boolean isQuery() {
        return true;
    }

    @Override
    public CustomRefactoringPanel getPanel(ChangeListener cl) {
        return null;
    }
}
