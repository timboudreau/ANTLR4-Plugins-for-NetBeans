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

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.filesystems.FileObject;
import org.openide.text.PositionBounds;

/**
 *
 * @author Tim Boudreau
 */
public final class AntlrRefactoringPluginFactory extends AbstractRefactoringContext implements RefactoringPluginFactory {

    private final Set<RefactoringPluginGenerator<?>> entries = new LinkedHashSet<>();
    private final String mimeType;
    private static final Logger LOG = Logger.getLogger(AntlrRefactoringPluginFactory.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    /*
    public AntlrRefactoringPluginFactory() {
        this(ANTLR_MIME_TYPE,
                new RefactoringPluginGenerator<>(RenameRefactoring.class,
                        new StrategyRefactoringFactory(
                                AntlrKeys.GRAMMAR_TYPE,
                                CharFilter.of(CharPredicates.JAVA_IDENTIFIER_START, CharPredicates.JAVA_IDENTIFIER_PART),
                                NamedStringifier.INSTANCE,
                                new RenameFileFromSingletonCreationStrategy()
                        )),
                //                        new AntlrRenameFileRefactoringFactory()),
                new RefactoringPluginGenerator<>(WhereUsedQuery.class,
                        AntlrRefactoringPluginFactory::handleFindUsages)
        );
    }
     */
    AntlrRefactoringPluginFactory(String mimeType, Collection<? extends RefactoringPluginGenerator<?>> entries) {
        this.entries.addAll(entries);
        this.mimeType = mimeType;
    }

    public static RefactoringsBuilder builder(String mimeType) {
        return new RefactoringsBuilder(mimeType);
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

    @Override
    public RefactoringPlugin createInstance(AbstractRefactoring refactoring) {
        return inParsingContext(() -> { // cache parses
            if (!isSupported(refactoring)) {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, "{0} does not support {1}",
                            new Object[]{this, refactoring});
                }
                // Not a refactoring we have a generator for
                return null;
            }
            FileObject fo = findFileObject(refactoring);
            if (!mimeType.equals(fo.getMIMEType())) {
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, "{0} has nothing for {1} of {2} for {3}",
                            new Object[]{this, fo, fo.getMIMEType(), refactoring});
                }
                // Wrong mime type
                return null;
            }
            Extraction extraction = extraction(fo);
            if (extraction == null) {
                // Likely an exception thrown in parsing
                LOG.log(Level.WARNING, "Null extraction for {0} from {1}",
                        new Object[]{refactoring.getRefactoringSource(), fo});
                return null;
            }
            PositionBounds bounds = findPositionBounds(refactoring);
            if (bounds != null) {
                for (RefactoringPluginGenerator<?> entry : find(refactoring)) {
                    RefactoringPlugin plugin = entry.accept(refactoring, extraction, bounds);
                    if (plugin != null) {
                        return plugin;
                    }
                }
            } else {
                LOG.log(Level.WARNING,
                        "{0} could not find a PositionBounds via {1} in {2} for {3}",
                        new Object[]{this, refactoring.getRefactoringSource(),
                            fo, refactoring});
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
}
