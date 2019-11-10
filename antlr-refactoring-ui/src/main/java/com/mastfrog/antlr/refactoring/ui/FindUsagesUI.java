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

import com.mastfrog.abstractions.Named;
import com.mastfrog.range.IntRange;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.refactoring.common.FileObjectHolder;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.modules.refactoring.api.WhereUsedQuery;
import org.netbeans.modules.refactoring.spi.ui.CustomRefactoringPanel;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ProxyLookup;

/**
 *
 * @author Tim Boudreau
 */
@Messages({
    "# {0} - category",
    "# {1} - name",
    "# {2} - file",
    "find_usages=<html>Usages of <b>{0}</b> <i>{1}</i> in {2}"
})
public class FindUsagesUI extends BaseUI<WhereUsedQuery> {

    public FindUsagesUI(Lookup lookup) {
        super(new WhereUsedQuery(lookup));
    }

    private Lookup lookup() {
        return refactoring == null ? Lookup.EMPTY
                : new ProxyLookup(refactoring.getRefactoringSource(), refactoring.getContext());
    }

    static String findNameOfThingRefactored(Lookup lookup) {
        String result = lookup.lookup(String.class);
        if (result != null && result.length() < 40) {
            return result;
        }
        IntRange<?> range = lookup.lookup(IntRange.class);
        if (range != null && range instanceof Named) {
            return ((Named) range).name();
        }
        Named named = lookup.lookup(Named.class);
        if (named != null) {
            return named.name();
        }
        return "(unknown)";
    }

    static String findRefactoringFileName(Lookup lookup) {
        FileObject file = lookup.lookup(FileObject.class);
        if (file != null) {
            return file.getNameExt();
        } else {
            FileObjectHolder hld = lookup.lookup(FileObjectHolder.class);
            if (hld != null) {
                return hld.get().getNameExt();
            }
        }
        return "(unknown)";
    }

    static String findCategoryOfThingRefactored(Lookup lookup) {
        IntRange<?> range = lookup.lookup(IntRange.class);
        if (range != null && range instanceof NamedSemanticRegion<?>) {
            return Localizers.displayName(((NamedSemanticRegion<?>) range).kind());
        }
        ExtractionKey<?> key = lookup.lookup(ExtractionKey.class);
        String result = "(unknown)";
        if (key != null) {
            Class<?> type = key.type();
            result = Localizers.displayName(type);
            if (type.getSimpleName().equals(result)) {
                result = Localizers.displayName(key);
            }
        }
        return result;
    }

    @Override
    public String getName() {
        Lookup lookup = lookup();
        return Bundle.find_usages(findCategoryOfThingRefactored(lookup),
                findNameOfThingRefactored(lookup),
                findRefactoringFileName(lookup));
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
