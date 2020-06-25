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
package org.nemesis.antlr.refactoring;

import com.mastfrog.util.collections.ArrayUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractAntlrRefactoringPlugin<R extends AbstractRefactoring>
        extends AbstractRefactoringContext implements RefactoringPlugin, Lookup.Provider {

    protected final R refactoring;
    protected final Extraction extraction;
    protected final FileObject file;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Lookup lookup;

    AbstractAntlrRefactoringPlugin(R refactoring, Extraction extraction, FileObject file) {
        this.refactoring = notNull("refactoring", refactoring);
        this.extraction = notNull("extraction", extraction);
        this.file = notNull("file", file);
        System.out.println("CREATE A " + getClass().getName() + " for " + refactoring + " and " + file.getNameExt());
    }

    protected Object[] getLookupContents() {
        return null;
    }

    @Override
    public final Lookup getLookup() {
        if (lookup == null) {
            Object[] baseContents = new Object[]{this, extraction, refactoring, file, cancelled};
            Object[] contents = getLookupContents();
            if (contents != null && contents.length != 0) {
                Object[] all = ArrayUtils.concatenateAll(baseContents, contents);
                lookup = Lookups.fixed(all);
            } else {
                lookup = Lookups.fixed(baseContents);
            }
        }
        return lookup;
    }

    protected final boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public final void cancelRequest() {
        cancelled.set(true);
    }

    protected final RefactoringElementImplementation renameFile(RenameRefactoring refactoring, FileObject fo) {
        return renameFile(refactoring::getNewName, fo);
    }

    protected final RefactoringElementImplementation renameFile(Supplier<String> newNameSupplier, FileObject fo) {
        return new RenameFile(newNameSupplier, fo);
    }

    @NbBundle.Messages({
        "# {0} - The file name",
        "invalidFile=File deleted: {0}",
        "# {0} - The file name",
        "cantRead=Not readable: {0}"
    })
    @Override
    public Problem checkParameters() {
        if (!file.isValid()) {
            return createProblem(true, Bundle.invalidFile(file.getNameExt()));
        }
        if (!file.canRead()) {
            return createProblem(true, Bundle.cantRead(file.getNameExt()));
        }
        return null;
    }

    @Override
    public Problem preCheck() {
        new Exception(getClass().getSimpleName() + " preCheck").printStackTrace();
        if (!file.isValid()) {
            return createProblem(true, Bundle.invalidFile(file.getNameExt()));
        }
        if (!file.canRead()) {
            return createProblem(true, Bundle.cantRead(file.getNameExt()));
        }
        return null;
    }

    @Override
    public Problem fastCheckParameters() {
        return null;
    }

    @Override
    public final Problem prepare(RefactoringElementsBag bag) {
        new Exception(getClass().getSimpleName() + ".prepare").printStackTrace();
        return inParsingContext(() -> doPrepare(bag));
    }

    protected abstract Problem doPrepare(RefactoringElementsBag bag);
}
