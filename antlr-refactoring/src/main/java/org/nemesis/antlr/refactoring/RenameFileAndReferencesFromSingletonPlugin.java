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

import com.mastfrog.abstractions.Named;
import com.mastfrog.abstractions.Stringifier;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.text.BadLocationException;
import org.nemesis.antlr.refactoring.usages.ImportersFinder;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.key.ExtractionKey;
import org.nemesis.extraction.key.NamedRegionKey;
import org.nemesis.extraction.key.SingletonKey;
import org.nemesis.localizers.api.Localizers;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.openide.filesystems.FileObject;
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
        this.stringifier = NamedStringifier.generic(); // null ok
        this.filter = filter == null ? CharFilter.ALL : filter; // null ok
        refactoring.getContext().add(key);
        refactoring.getContext().add(decl);
        refactoring.getContext().add(decl.get());
        refactoring.getContext().add(stringifier == null ? NamedStringifier.generic() : stringifier);
        refactoring.getContext().add(this.filter);
    }

    @Override
    protected Object[] getLookupContents() {
        return stringifier == null ? new Object[]{key, filter}
                : new Object[]{key, filter, stringifier};
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "("
                + refactoring + " " + file.getNameExt()
                + " " + key + " " + decl + " filter " + filter + ")";
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
                    logWarn("No stringifier provided and "
                            + "{0} does not implement Named. Using toString()"
                            + "for {1}",
                            obj == null ? "<nulltype>" : obj.getClass().getName(),
                            obj);
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
            logFine("Prepare {0}", this);
            Problem p  = collectImports(this::isCancelled, file, bag,
                    refactoring, null);
            if (isCancelled() || (p != null && p.isFatal())) {
                logFine("Cancelled {0}", this);
                return p;
            }
            // do the rename last, so as not to interfere with anything
            // trying to read it
            bag.add(refactoring, new RenameFile(refactoring::getNewName, file));
            return p;
        });
    }

    Problem collectImports(BooleanSupplier cancelled, FileObject file,
            RefactoringElementsBag bag,
            RenameRefactoring refactoring,
            NamedRegionKey<?> importKey) {
        ImportersFinder finder = ImportersFinder.forFile(file);

        List<RefactoringElementImplementation> result = new ArrayList<>(20);

        return finder.usagesOf(this::isCancelled, file, importKey,
                (IntRange<? extends IntRange> range, String originalName,
                        FileObject fo, ExtractionKey<?> key,
                        Extraction ignored) -> {
                    // Ensure we don't hold a reference to parse contents
                    // by copying to a plain IntRange from a NamedSemanticRegion or
                    // similar
                    IntRange<? extends IntRange> ir = Range.ofCoordinates(
                            range.start(), range.end());
                    logFinest("Replace {0} with {1} in {2} at {3} for {4} {5}",
                            originalName,
                            refactoring.getNewName(),
                            fo.getNameExt(),
                            ir,
                            range,
                            Localizers.displayName(key));
                    Problem problem = null;
                    try {
                        ReplaceRanges.create(key, fo, 
                                Collections.singletonList(range),
                                originalName,
                                refactoring.getNewName(),
                                result::add);
                    } catch (IOException | BadLocationException ex) {
                        log(ex);
                        problem = toProblem(ex);
                    }
                    return problem;
                });
    }
}
