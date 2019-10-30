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

import com.mastfrog.abstractions.Named;
import com.mastfrog.abstractions.Stringifier;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.file.refactoring.usages.ImportersFinder;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.netbeans.modules.refactoring.spi.RefactoringCommit;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages(value = {
    "# {0} - file name",
    "exists={0} already exists",
    "name_unchanged=Name unchanged",
    "invalid_chars=Invalid characters in name",
    "empty_name=Empty name"})
class RenameFileAndReferencesFromSingletonPlugin<T> extends AbstractAntlrRefactoringPlugin<RenameRefactoring> {

    private static final Logger LOG = Logger.getLogger(
            RenameFileAndReferencesFromSingletonPlugin.class.getName());
    private final SingletonEncounters.SingletonEncounter<T> decl;
    private final Stringifier<? super T> stringifier;
    private boolean warned;
    private final SingletonKey<T> key;
    private final CharFilter filter;

    RenameFileAndReferencesFromSingletonPlugin(
            SingletonKey<T> key,
            RenameRefactoring refactoring,
            Extraction extraction,
            FileObject file,
            SingletonEncounters.SingletonEncounter<T> decl,
            Stringifier<? super T> stringifier,
            CharFilter filter) {
        super(refactoring, extraction, file);
        this.decl = notNull("decl", decl);
        this.key = notNull("key", key);
        this.stringifier = stringifier; // null ok
        this.filter = filter == null ? CharFilter.ALL : filter; // null ok
    }

    @Override
    public String toString() {
        return "Rename " + key + " " + decl + " to " + refactoring.getNewName()
                + " with filter " + filter;
    }

    @Override
    public Problem preCheck() {
        if (refactoring.getNewName() != null) {
            return checkParameters();
        }
        return null;
    }

    private String oldName() {
        String name;
        if (stringifier == null) {
            T obj = decl.get();
            if (obj instanceof Named) {
                name = ((Named) obj).name();
            } else {
                if (!warned) {
                    warned = true;
                    LOG.log(Level.WARNING, "No stringifier provided and "
                            + "{0} does not implement Named. Using toString()"
                            + "for {1}",
                            new Object[]{
                                obj == null ? "<nulltype>" : obj.getClass().getName(),
                                obj
                            });
                }
                name = obj == null ? "null" : obj.toString();
            }
        } else {
            name = stringifier.toString(decl.get());
        }
        return name;
    }

    private static Problem fatal(String msg) {
        return new Problem(true, msg);
    }

    @Override
    public Problem checkParameters() {
        String nm = refactoring.getNewName();
        String newFileName = nm + "." + file.getExt();
        return ProblemSupplier.of(super::checkParameters)
                .or(() -> nm == null || nm.isEmpty()
                ? new Problem(true, Bundle.empty_name()) : null)
                .or(() -> oldName().equals(nm) ? fatal(Bundle.name_unchanged()) : null)
                .or(() -> filter.test(nm, false) ? fatal(Bundle.invalid_chars()) : null)
                .or(() -> file.getParent().getFileObject(newFileName) != null ? fatal(Bundle.exists(newFileName)) : null)
                .get();
//                .get();
        /*
        Problem result = super.checkParameters();
        if (result != null) {
            return result;
        }
        String nm = refactoring.getNewName();
        if (nm == null || nm.isEmpty()) {
            return new Problem(true, Bundle.empty_name());
        }
        String name = oldName();
        if (nm.equals(name)) {
            return new Problem(true, Bundle.name_unchanged());
        }
        boolean valid = filter.test(nm, false);
        if (!valid) {
            return new Problem(true, Bundle.invalid_chars());
        }
        String newFileName = nm + "." + file.getExt();
        if (file.getParent().getFileObject(newFileName) != null) {
            return new Problem(true, Bundle.exists(newFileName));
        }
         */
//        return null;
    }

    @Override
    public Problem fastCheckParameters() {
        return preCheck();
    }

    @Override
    public Problem doPrepare(RefactoringElementsBag bag) {
        Problem check = checkParameters();
        if (check != null) {
            return check;
        }
        // Ensure we don't provision hints and similar across a bunch of
        // related files - not needed here, and a reparse will be
        // triggered on rewrite
        return AbstractRefactoringContext.inParsingContext(() -> {
            OneRangeInOneFileChange impl = new OneRangeInOneFileChange(decl,
                    oldName(), refactoring, file, key.name());
            bag.add(refactoring, impl);
            Set<OneRangeInOneFileChange> others
                    = collectImports(file, bag, refactoring, null);
            if (isCancelled()) {
                return null;
            }
            try {
                Set<ModificationResult> results = new HashSet<>(others.size() + 1);
                ModificationResult res = impl.toModificationResult();
                results.add(res);
                for (OneRangeInOneFileChange i : others) {
                    results.add(i.toModificationResult());
                }
                RefactoringCommit commit = new RefactoringCommit(results);
                bag.registerTransaction(commit);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return new Problem(true, ex.toString());
            }
            // do the rename last, so as not to interfere with anything
            // trying to read it
            bag.add(refactoring, new RenameFile(refactoring::getNewName, file));
            return null;
        });
    }

    Set<OneRangeInOneFileChange> collectImports(FileObject file,
            RefactoringElementsBag bag,
            RenameRefactoring refactoring,
            NamedRegionKey<?> importKey) {
        ImportersFinder finder = ImportersFinder.forFile(file);

        Set<OneRangeInOneFileChange> changes = new HashSet<>();
        finder.usagesOf(this::isCancelled, file, importKey, (IntRange range, String originalName,
                FileObject fo, String localizedRegionName,
                Extraction ignored) -> {
            // Ensure we don't hold a reference to parse contents
            // by copying to a plain IntRange from a NamedSemanticRegion or
            // similar
            IntRange ir = Range.ofCoordinates(range.start(), range.end());
            System.out.println("Replace " + originalName + " with " + refactoring.getNewName() + " in " + fo.getNameExt()
                    + " at " + ir
                    + " for " + range + " " + localizedRegionName);
            OneRangeInOneFileChange impl = new OneRangeInOneFileChange(ir,
                    originalName, refactoring, fo, localizedRegionName);
            changes.add(impl);
            bag.add(refactoring, impl);
            if (isCancelled()) {
                return;
            }
        });
        return changes;
    }
}
