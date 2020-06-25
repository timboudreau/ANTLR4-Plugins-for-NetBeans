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

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.extraction;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.findFileObject;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.findPositionBounds;
import static org.nemesis.antlr.refactoring.AbstractRefactoringContext.inParsingContext;
import org.nemesis.antlr.refactoring.common.Refactorability;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;
import org.openide.util.Lookup;

/**
 * Plugin factory for Antlr (extraction) based refactorings; populate it in the
 * consumer passed to the constructor, and register in the default lookup. This
 * needs to be registered as <i>both</i> RefactoringPluginFactory and
 * Refactorability, e.g.
 * <pre>
 * &#064;ServiceProviders({
 *     &#064;ServiceProvider(service = RefactoringPluginFactory.class, position = 41),
 *     &#064;ServiceProvider(service = Refactorability.class, position = 41)
 * })
 * </pre>
 * Assuming an instance of AntlrRefactoringPluginFactory is installed in some
 * module, other modules can register additional refactorings by registering
 * CustomRefactoring instances in the mime lookup for the correct mime type.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrRefactoringPluginFactory implements RefactoringPluginFactory, Refactorability {

    private final Set<RefactoringPluginGenerator<?>> entries = new LinkedHashSet<>();
    private final String mimeType;
    private final Logger LOG = Logger.getLogger(getClass().getName());

    /**
     * Create a new plugin factory.
     *
     * @param mimeType The mime type
     * @param toPopulate A consumer which you implement and pass to the super
     * constructor, which will at some point be called if refactorings are
     * needed.
     */
    protected AntlrRefactoringPluginFactory(String mimeType, Consumer<RefactoringsBuilder> toPopulate) {
        if (AbstractRefactoringContext.debugLog) {
            LOG.setLevel(Level.ALL);
        }
        this.mimeType = notNull("mimeType", mimeType);
        RefactoringsBuilder bldr = new RefactoringsBuilder(mimeType);
        notNull("toPopulate", toPopulate).accept(bldr);
        entries.addAll(bldr.generators());
    }

    /**
     * Implementation of Refactorability, used by the refactoring API to avoid
     * popping up a refactoring dialog on tokens where it won't be able to do
     * anything.
     *
     * @param type The AbstractRefactoring type (it will always be a subtype of
     * that, but does not specify it to avoid entangling instant rename with the
     * refactoring API).
     * @param file The file
     * @param lookup A lookup
     * @return True if the current element might be able to be refactored using
     * some plugin this factory can create
     */
    public final boolean canRefactor(Class<?> type, FileObject file, Lookup lookup) {
        boolean result = mimeType.equals(file.getMIMEType());
        if (result) {
            for (RefactoringPluginGenerator<?> gen : entries) {
                if (gen.matches(type)) {
                    System.out.println("HAVE A GENERATOR " + gen + " matching " + type.getSimpleName());
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private AntlrRefactoringPluginFactory(String mimeType, RefactoringPluginGenerator<?>... entries) {
        this.mimeType = notNull("mimeType", mimeType);
        this.entries.addAll(Arrays.asList(entries));
    }

    private List<RefactoringPluginGenerator<?>> find(AbstractRefactoring refactoring) {
        List<RefactoringPluginGenerator<?>> result = new ArrayList<>(entries.size());
        entries.stream().filter(e -> e.matches(refactoring)).forEachOrdered((e) -> {
            result.add(e);
        });
        return result;
    }

    boolean isSupported(AbstractRefactoring refactoring) {
        if (entries.stream().anyMatch(ref -> ref.matches(refactoring))) {
            return true;
        }
        return false;
    }

    private void ifLoggable(Level level, Runnable r) {
        if (LOG.isLoggable(level)) {
            r.run();
        }
    }

    @Override
    public final RefactoringPlugin createInstance(AbstractRefactoring refactoring) {
        if (!isSupported(refactoring)) {
            ifLoggable(Level.FINEST, () -> {
                logFinest("{0} does not support {1}",
                        this, refactoring);
            });
            // Not a refactoring we have a generator for
            return null;
        }
        return inParsingContext(() -> { // cache parses
            FileObject fo = findFileObject(refactoring);
            if (fo == null) {
                return null;
            }
            if (!mimeType.equals(fo.getMIMEType())) {
                ifLoggable(Level.FINEST, () -> {
                    logFinest("{0} has nothing for {1} of {2} for {3}",
                            this, fo, fo.getMIMEType(), refactoring);
                });
                // Wrong mime type
                return null;
            }
            Extraction extraction = extraction(fo);
            if (extraction == null) {
                // Likely an exception thrown in parsing
                logWarn("Null extraction for {0} from {1}",
                        refactoring.getRefactoringSource(), fo);
                return null;
            }
            PositionBounds bounds = findPositionBounds(refactoring);
            if (bounds != null) {
                for (RefactoringPluginGenerator<?> entry : find(refactoring)) {
                    RefactoringPlugin plugin = entry.accept(refactoring, extraction, bounds);
                    if (plugin != null) {
                        logFine("Use factory {0} for {1}", entry, plugin);
                        return plugin;
                    }
                }
            } else {
                logWarn(
                        "{0} could not find a PositionBounds via {1} in {2} for {3}",
                        this, refactoring.getRefactoringSource(),
                        fo, refactoring);
            }
            return null;
        });
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('(').append(mimeType).append(' ').append(entries.size()).append(" entries: ")
                .append(Strings.join(", ", entries)).append(')');
        return sb.toString();
    }

    private void logFinest(String s, Object... args) {
        LOG.log(Level.FINEST, s, args);
    }

    private void logFine(String s, Object... args) {
        LOG.log(Level.FINE, s, args);
    }

    private void logWarn(String s, Object... args) {
        LOG.log(Level.WARNING, s, args);
    }
}
