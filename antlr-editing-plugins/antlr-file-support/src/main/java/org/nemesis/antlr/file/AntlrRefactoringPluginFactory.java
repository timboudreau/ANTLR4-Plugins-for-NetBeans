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
package org.nemesis.antlr.file;

import com.mastfrog.function.TriFunction;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import com.mastfrog.util.collections.CollectionUtils;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.text.Document;
import javax.swing.text.Position;
import javax.swing.undo.CannotUndoException;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.nemesis.antlr.common.extractiontypes.ImportKinds;
import org.nemesis.antlr.file.impl.GrammarDeclaration;
import org.nemesis.antlr.project.Folders;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.charfilter.CharFilter;
import org.nemesis.charfilter.CharPredicates;
import org.nemesis.data.named.NamedSemanticRegion;
import org.nemesis.data.named.NamedSemanticRegions;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.SingletonEncounters;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.extraction.attribution.ImportFinder;
import org.nemesis.source.api.GrammarSource;
import org.netbeans.api.queries.FileEncodingQuery;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.RenameRefactoring;
import org.netbeans.modules.refactoring.spi.ModificationResult;
import org.netbeans.modules.refactoring.spi.RefactoringCommit;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.netbeans.modules.refactoring.spi.SimpleRefactoringElementImplementation;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.CloneableEditorSupport;
import org.openide.text.PositionBounds;
import org.openide.text.PositionRef;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = RefactoringPluginFactory.class, position = 1000)
public class AntlrRefactoringPluginFactory implements RefactoringPluginFactory {

    private final Set<RefactoringEntry<?>> entries = new HashSet<>();

    public AntlrRefactoringPluginFactory() {
        this(new RefactoringEntry<>(RenameRefactoring.class,
                AntlrRefactoringPluginFactory::handleRenameRefactoring));
    }

    private AntlrRefactoringPluginFactory(RefactoringEntry<?>... entries) {
        this.entries.addAll(Arrays.asList(entries));
    }

    static class RefactoringEntry<R extends AbstractRefactoring> {

        private final Class<R> type;
        private final TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> test;

        public RefactoringEntry(Class<R> type, TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> test) {
            this.type = type;
            this.test = test;
        }

        public RefactoringPlugin accept(AbstractRefactoring refactoring, Extraction extraction, PositionBounds pos) {
            if (type.isInstance(refactoring)) {
                return test.apply(type.cast(refactoring), extraction, pos);
            }
            return null;
        }

        boolean matches(AbstractRefactoring ref) {
            return type.isInstance(ref);
        }
    }

    private List<RefactoringEntry<?>> find(AbstractRefactoring refactoring) {
        List<RefactoringEntry<?>> result = new ArrayList<>();
        for (RefactoringEntry<?> e : entries) {
            if (e.matches(refactoring)) {
                result.add(e);
            }
        }
        return result;
    }

    protected boolean isSupported(AbstractRefactoring refactoring) {
        for (RefactoringEntry<?> ref : entries) {
            if (ref.matches(refactoring)) {
                return true;
            }
        }
        return false;
    }

    private Extraction getExtraction(AbstractRefactoring refactoring, FileObject fo) {
        Document doc = null;
        try {
            DataObject dob = DataObject.find(fo);
            EditorCookie ck = dob.getCookie(EditorCookie.class);
            if (ck != null) {
                doc = ck.getDocument();
            }
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
        try {
            Extraction ext;
            if (doc == null) {
                ext = NbAntlrUtils.parseImmediately(fo);
            } else {
                ext = NbAntlrUtils.parseImmediately(doc);
            }
            return ext;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

    @Override
    public RefactoringPlugin createInstance(AbstractRefactoring refactoring) {
        if (!isSupported(refactoring)) {
            return null;
        }
        final Lookup lookup = refactoring.getRefactoringSource();
        FileObject fo = refactoring.getRefactoringSource().lookup(FileObject.class);
        if (fo == null) {
            return null;
        }
        if (!ANTLR_MIME_TYPE.equals(fo.getMIMEType())) {
            return null;
        }
        Extraction ext = getExtraction(refactoring, fo);
        if (ext == null) {
            return null;
        }

//        RefactoringElement element = lookup.lookup(RefactoringElement.class);
        PositionBounds bds = lookup.lookup(PositionBounds.class);
        if (bds != null) {
            for (RefactoringEntry<?> entry : find(refactoring)) {
                RefactoringPlugin plugin = entry.accept(refactoring, ext, bds);
                if (plugin != null) {
                    return plugin;
                }
            }
        }
        return null;
    }

    private static RefactoringPlugin handleRenameRefactoring(RenameRefactoring refactoring, Extraction extraction, PositionBounds bounds) {
        SingletonEncounters<GrammarDeclaration> gtEncounters = extraction.singletons(AntlrKeys.GRAMMAR_TYPE);

        if (gtEncounters.isEmpty()) {
            return null;
        }

        int start = bounds.getBegin().getOffset();
        int end = bounds.getEnd().getOffset();

        IntRange range = Range.ofCoordinates(start, end);
        SingletonEncounter<GrammarDeclaration> item = null;
        loop:
        for (SingletonEncounter<GrammarDeclaration> s : gtEncounters) {
            RangeRelation rel = s.relationTo(range);
            switch (rel) {
                case CONTAINED:
                case CONTAINS:
                case EQUAL:
                    item = s;
                    break loop;
            }
        }
        if (item != null) {
            return new RenameGrammarRefactoringPlugin(refactoring, extraction, extraction.source().lookup(FileObject.class).get(), item);
        }
        return null;
    }

    static abstract class AbstractAntlrRefactoringPlugin<R extends AbstractRefactoring> implements RefactoringPlugin {

        protected final R refactoring;
        protected final Extraction extraction;
        protected final FileObject file;

        AbstractAntlrRefactoringPlugin(R refactoring, Extraction extraction, FileObject file) {
            this.refactoring = refactoring;
            this.extraction = extraction;
            this.file = file;
        }
    }

    @Messages({
        "name_unchanged=Name unchanged",
        "invalid_chars=Invalid characters in name",
        "empty_name=Empty name"
    })
    private static class RenameGrammarRefactoringPlugin extends AbstractAntlrRefactoringPlugin<RenameRefactoring> {

        private final SingletonEncounter<GrammarDeclaration> decl;

        public RenameGrammarRefactoringPlugin(RenameRefactoring refactoring, Extraction extraction, FileObject file, SingletonEncounter<GrammarDeclaration> decl) {
            super(refactoring, extraction, file);
            this.decl = decl;
        }

        @Override
        public Problem preCheck() {
            return null;
        }

        @Override
        public Problem checkParameters() {
            String nm = refactoring.getNewName();
            if (nm == null || nm.isEmpty()) {
                return new Problem(true, Bundle.empty_name());
            }
            if (nm.equals(decl.value().grammarName())) {
                return new Problem(true, Bundle.name_unchanged());
            }
            boolean valid = CharFilter.excluding(
                    CharPredicates.JAVA_IDENTIFIER_START,
                    CharPredicates.JAVA_IDENTIFIER_PART)
                    .test(nm, false);
            if (!valid) {
                return new Problem(true, Bundle.invalid_chars());
            }
            return null;
        }

        @Override
        public Problem fastCheckParameters() {
            return preCheck();
        }

        @Override
        public void cancelRequest() {

        }

        @Override
        public Problem prepare(RefactoringElementsBag reb) {
            Problem check = checkParameters();
            if (check != null) {
                return check;
            }
            Impl impl = new Impl(decl, decl.value().grammarName(), refactoring, file);
            reb.add(refactoring, impl);
            Set<Impl> others = findImporters(reb);
            reb.add(refactoring, new RenameFile(refactoring, file, reb));
            try {
                Set<ModificationResult> results = new HashSet<>(others.size() + 1);
                ModificationResult res = new ModRes(file, impl.getModifiedText());
                results.add(res);
                for (Impl i : others) {
                    results.add(new ModRes(i.fo, i.getModifiedText()));
                }
                RefactoringCommit commit = new RefactoringCommit(results);
                reb.registerTransaction(commit);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                return new Problem(true, ex.toString());
            }
            return null;
        }

        private Set<Impl> findImporters(RefactoringElementsBag bag) {
            Set<Impl> result = new HashSet<>();
            ImportFinder imports = ImportFinder.forMimeType(file.getMIMEType());
            Iterable<FileObject> all = CollectionUtils.concatenate(Folders.ANTLR_GRAMMAR_SOURCES.allFiles(file),
                    Folders.ANTLR_IMPORTS.allFiles(file));
            Set<FileObject> seen = new HashSet<>();
            seen.add(file);
            for (FileObject fo : all) {
                if (seen.contains(fo)) {
                    continue;
                }
                if (fo.getMIMEType().equals(file.getMIMEType())) {
                    try {
                        Extraction ext = NbAntlrUtils.parseImmediately(fo);
                        NamedSemanticRegions<ImportKinds> imported = ext.namedRegions(AntlrKeys.IMPORTS);
                        if (!imported.isEmpty()) {
                            for (GrammarSource<?> src : imports.allImports(ext, CollectionUtils.blackHoleSet())) {
                                Optional<FileObject> importedFile = src.lookup(FileObject.class);
                                if (importedFile.isPresent() && file.equals(importedFile.get())) {
                                    String grammarName = src.name();
                                    NamedSemanticRegion<ImportKinds> region = imported.regionFor(grammarName);
                                    if (region != null) {
                                        Impl impl = new Impl(region, region.name(), refactoring, fo);
                                        result.add(impl);
                                        bag.add(refactoring, impl);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            return result;
        }

        static class ModRes implements ModificationResult {

            private final FileObject file;
            private final String text;

            public ModRes(FileObject file, String text) {
                this.file = file;
                this.text = text;
            }

            @Override
            public String getResultingSource(FileObject fo) throws IOException, IllegalArgumentException {
                if (file.equals(fo)) {
                    return text;
                }
                return null;
            }

            @Override
            public Collection<? extends FileObject> getModifiedFileObjects() {
                return Collections.singleton(file);
            }

            @Override
            public Collection<? extends File> getNewFiles() {
                return Collections.emptySet();
            }

            @Override
            public void commit() throws IOException {
                Charset cs = FileEncodingQuery.getEncoding(file);
                try (OutputStream out = file.getOutputStream()) {
                    out.write(text.getBytes(cs));
                }
            }

        }

        static class Impl extends SimpleRefactoringElementImplementation {

            private final IntRange decl;
            private final String name;
            private final RenameRefactoring refactoring;
            private final FileObject fo;

            public Impl(IntRange decl, String name, RenameRefactoring refactoring, FileObject fo) {
                this.decl = decl;
                this.name = name;
                this.refactoring = refactoring;
                this.fo = fo;
            }

            @Override
            public String getText() {
                return "Replace '" + name + "' with " + refactoring.getNewName() + "'";
            }

            String getModifiedText() throws IOException {
                StringBuilder sb = new StringBuilder(fo.asText());
                sb.delete(decl.start(), decl.end());
                sb.insert(decl.start(), refactoring.getNewName());
                return sb.toString();
            }

            @Override
            public String getDisplayText() {
                return "Replace name in grammar declaration";
            }

            @Override
            public void performChange() {

            }

            @Override
            public Lookup getLookup() {
                return Lookup.EMPTY;
            }

            @Override
            public FileObject getParentFile() {
                return fo;
            }

            @Override
            public PositionBounds getPosition() {
                try {
                    DataObject dob = DataObject.find(fo);
                    CloneableEditorSupport supp = dob.getLookup().lookup(CloneableEditorSupport.class);
                    PositionRef start = supp.createPositionRef(decl.start(), Position.Bias.Forward);
                    PositionRef end = supp.createPositionRef(decl.end(), Position.Bias.Backward);
                    return new PositionBounds(start, end);
                } catch (DataObjectNotFoundException ex) {
                    Exceptions.printStackTrace(ex);
                }
                return null;
            }
        }
    }

    private static class RenameFile extends SimpleRefactoringElementImplementation {

        private final RenameRefactoring refactoring;

        private FileObject fo;

        public RenameFile(RenameRefactoring refactoring, FileObject fo, RefactoringElementsBag bag) {
            this.refactoring = refactoring;
            this.fo = fo;
        }

        @Override
        @Messages({
            "# {0} - original file name",
            "TXT_RenameFile=Rename file {0}",
            "# {0} - grammar file path",
            "TXT_RenameFolder=Rename folder {0}"})
        public String getText() {
            return fo.isFolder() ? Bundle.TXT_RenameFolder(fo.getNameExt())
                    : Bundle.TXT_RenameFile(fo.getNameExt());
        }

        @Override
        public String getDisplayText() {
            return getText();
        }

        private String oldName;

        @Override
        public void performChange() {
            try {
                oldName = fo.getName();
                DataObject.find(fo).rename(refactoring.getNewName());
            } catch (DataObjectNotFoundException ex) {
                throw new IllegalStateException(ex);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void undoChange() {
            try {
                if (!fo.isValid()) {
                    throw new Cannot(fo);
                }
                DataObject.find(fo).rename(oldName);
            } catch (DataObjectNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public FileObject getParentFile() {
            return fo;
        }

        @Override
        public PositionBounds getPosition() {
            return null;
        }
    }

    @Messages({
        "# {0} - the file name",
        "cannot_undo=Cannot undo - file deleted: {0}"
    })
    static final class Cannot extends CannotUndoException {

        private final FileObject fo;

        public Cannot(FileObject fo) {
            this.fo = fo;
        }

        @Override
        public String getMessage() {
            return Bundle.cannot_undo(fo.getNameExt());
        }

    }
}
