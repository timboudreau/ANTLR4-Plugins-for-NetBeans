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
package org.nemesis.antlr.file.refactoring;

import com.mastfrog.range.IntRange;
import java.io.IOException;
import static org.nemesis.antlr.file.refactoring.AbstractRefactoringContext.createPosition;
import static org.nemesis.antlr.file.refactoring.AbstractRefactoringContext.lookupOf;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
final class OneRangeInOneFileChange extends SimpleRefactoringElementImplementation {

    private final IntRange decl;
    private final String originalName;
    private final RenameRefactoring refactoring;
    private final FileObject fo;
    private final String localizedRegionKindName;

    OneRangeInOneFileChange(IntRange decl, String originalName, RenameRefactoring refactoring, FileObject fo, String localizedRegionKindName) {
        this.decl = decl;
        this.originalName = originalName;
        this.refactoring = refactoring;
        this.fo = fo;
        this.localizedRegionKindName = localizedRegionKindName;
    }

    public ModificationResult toModificationResult() throws IOException {
        LazyModificationSupplier lms = new LazyModificationSupplier(refactoring, decl, fo,
                refactoring::getNewName);
        // Force it to cache - need to sort out some racing between reading
        // and writing files when performing modifications leading to
        // file already locked exceptions
        lms.get();
        return new LazyModificationResult(fo, lms);
    }

    @Override
    public String toString() {
        return "Change '" + originalName + "' of " + localizedRegionKindName
                + " at " + decl;
    }

    @NbBundle.Messages(value = {
        "# {0} - the original name",
        "# {1} - the new name",
        "replace-title=Replace ''{0}'' with ''{1}''"})
    @Override
    public String getText() {
        return Bundle.replace_title(originalName, refactoring.getNewName());
    }

    @NbBundle.Messages(value = {"# {0} - the kind of element", "replace-name=Replace name in {0}"})
    @Override
    public String getDisplayText() {
        return Bundle.replace_name(localizedRegionKindName); // XXX pass this in
    }

    @Override
    public void performChange() {
        // do nothing - we use toModificationResult to handle this
    }

    @Override
    public Lookup getLookup() {
        return lookupOf(fo);
    }

    @Override
    public FileObject getParentFile() {
        return fo;
    }

    @Override
    public PositionBounds getPosition() {
        return createPosition(fo, decl);
    }
}
