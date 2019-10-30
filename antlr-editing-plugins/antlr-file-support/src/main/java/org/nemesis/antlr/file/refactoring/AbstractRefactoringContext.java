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
import com.mastfrog.function.throwing.ThrowingBiFunction;
import com.mastfrog.range.IntRange;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringElementImplementation;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Lookup;

/**
 * Base class for refactoring-related types which provides thread-local caching
 * for extractions.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractRefactoringContext {

    private static boolean cacheDisabled = Boolean.getBoolean("AbstractRefactoringContext.noCache");

    private static final ThreadLocal<CacheContext> CTX = ThreadLocal.withInitial(CacheContext::new);

    static Extraction extraction(FileObject fo) {
        try {
            // Try to use the document where possible - it
            // may be modified
            return parse(fo);
        } catch (Exception ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
            return null;
        }
    }

    static PositionBounds findPositionBounds(AbstractRefactoring refactoring) {
        final Lookup lookup = refactoring.getRefactoringSource();
        PositionBounds result = lookup.lookup(PositionBounds.class);
        if (result == null) {
            JTextComponent comp = lookup.lookup(JTextComponent.class);
            CloneableEditorSupport supp = lookup.lookup(CloneableEditorSupport.class);
            if (comp == null) {
                if (supp != null) {
                    JEditorPane[] panes = supp.getOpenedPanes();
                    if (panes != null && panes.length > 0) {
                        comp = panes[0];
                    }
                }
            }
            if (comp != null && supp != null) {
                int a = comp.getSelectionStart();
                int b = comp.getSelectionEnd();
                if (a >= 0 && b >= 0) {
                    int start = Math.min(a, b);
                    int end = Math.max(a, b);
                    PositionRef startPos = supp.createPositionRef(start, Position.Bias.Forward);
                    PositionRef endPos = supp.createPositionRef(end, Position.Bias.Backward);
                    result = new PositionBounds(startPos, endPos);
                }
            }
        }
        return result;
    }

    static FileObject findFileObject(AbstractRefactoring refactoring) {
        final Lookup lookup = refactoring.getRefactoringSource();
        FileObject fo = lookup.lookup(FileObject.class);
        if (fo == null) {
            DataObject ob = lookup.lookup(DataObject.class);
            if (ob != null) {
                fo = ob.getPrimaryFile();
            } else {
                return null;
            }
        }
        return fo;
    }

    public static Stringifier<Named> namedStringifier() {
        return NamedStringifier.INSTANCE;
    }

    private static final class CacheContext {

        private int entryCount;
        private final Map<FileObject, Extraction> foMap = new HashMap<>();
        private final Map<Document, Extraction> docMap = new IdentityHashMap<>();

        public <T> T enter(BiFunction<Map<FileObject, Extraction>, Map<Document, Extraction>, T> c) {
            entryCount++;
            try {
                return c.apply(foMap, docMap);
            } finally {
                entryCount--;
            }
        }

        boolean isActive() {
            return entryCount > 0;
        }

        void clear() {
            foMap.clear();
            docMap.clear();
        }
    }

    private static CacheContext context() {
        if (cacheDisabled) {
            return new CacheContext();
        }
        return CTX.get();
    }

    static <T> T inParsingContext(Supplier<T> run) {
        CacheContext ctx = context();
        boolean isNew = !ctx.isActive();
        try {
            if (isNew) {
                return ctx.enter((a, b) -> {
                    return NbAntlrUtils.withPostProcessingDisabled(() -> {
                        return run.get();
                    });
                });
            } else {
                return ctx.enter((a, b) -> {
                    return run.get();
                });
            }
        } finally {
            if (isNew) {
                ctx.clear();
                if (!cacheDisabled) {
                    CTX.remove();
                }
            }
        }
    }

    static <T> T inParsingContext(ThrowingBiFunction<Map<FileObject, Extraction>, Map<Document, Extraction>, T> c) throws Exception {
        CacheContext ctx = CTX.get();
        boolean wasNew = !ctx.isActive();
        try {
            if (wasNew) {
                return ctx.enter((a, b) -> {
                    return NbAntlrUtils.withPostProcessingDisabled(() -> {
                        try {
                            return c.apply(a, b);
                        } catch (Exception ex1) {
                            return Exceptions.chuck(ex1);
                        }
                    });
                });
            } else {
                return ctx.enter((a, b) -> {
                    try {
                        return c.apply(a, b);
                    } catch (Exception ex1) {
                        return Exceptions.chuck(ex1);
                    }
                });
            }
        } finally {
            if (wasNew) {
                ctx.clear();
                CTX.remove();
            }
        }
    }

    /**
     * Parse a file for its extraction. Use this method in preference to parsing
     * directly with ParserManager, as it caches results, and extractions may be
     * needed for a file or document more than once during analysis. This method
     * will prefer the open Document for a file if there is one, so
     * modifications are accounted for.
     *
     * @param fo The file
     * @return An extraction or null
     * @throws Exception If something goes wrong
     */
    protected static Extraction parse(FileObject fo) throws Exception {
        return inParsingContext((foMap, docMap) -> {
            Document doc = null;
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck != null) {
                    doc = ck.getDocument();
                    if (docMap.containsKey(doc)) {
                        return docMap.get(doc);
                    }
                }
            } catch (DataObjectNotFoundException ex) {
                return Exceptions.chuck(ex);
            }
            if (doc != null) {
                return parse(doc);
            } else {
                Extraction result = NbAntlrUtils.parseImmediately(fo);
                foMap.put(fo, result);
                return result;
            }
        });
    }

    /**
     * Parse a document for its extraction. Use this method in preference to
     * parsing directly with ParserManager, as it caches results, and
     * extractions may be needed for a file or document more than once during
     * analysis. This method will prefer the open Document for a file if there
     * is one, so modifications are accounted for.
     *
     * @param fo The file
     * @return An extraction or null
     * @throws Exception If something goes wrong
     */
    protected static Extraction parse(Document doc) throws Exception {
        return inParsingContext((foMap, docMap) -> {
            Extraction result = docMap.get(doc);
            if (result == null) {
                result = NbAntlrUtils.parseImmediately(doc);
                if (result != null) {
                    docMap.put(doc, result);
                }
                FileObject fo = NbEditorUtilities.getFileObject(doc);
                if (fo != null) {
                    foMap.put(fo, result);
                }
            }
            return result;
        });
    }

    protected static Lookup lookupOf(FileObject file) {
        try {
            return DataObject.find(file).getLookup();
        } catch (DataObjectNotFoundException ex) {
            org.openide.util.Exceptions.printStackTrace(ex);
            return Lookup.EMPTY;
        }
    }

    protected static PositionBounds createPosition(FileObject file, IntRange range) {
        CloneableEditorSupport supp = lookupOf(file).lookup(CloneableEditorSupport.class);
        if (supp != null) {
            PositionRef startRef = supp.createPositionRef(range.start(), Position.Bias.Forward);
            PositionRef endRef = supp.createPositionRef(range.end(), Position.Bias.Backward);
            return new PositionBounds(startRef, endRef);
        }
        return null;
    }

    protected static RefactoringElementImplementation createUsage(FileObject file, IntRange range, String of, String name) {
        return new Usage(file, range, of, name);
    }
}
