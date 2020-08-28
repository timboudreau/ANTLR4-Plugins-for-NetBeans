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
package org.nemesis.extraction.nb.api;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;

/**
 * Extension to GrammarSource which supports resolving a FileObject. The default
 * implementation of lookupImpl() can resolve FileObject, File, Path,
 * BaseDocument, StyledDocument, Snapshot, Source and Project.
 *
 * @author Tim Boudreau
 */
public abstract class AbstractFileObjectGrammarSourceImplementation<T> extends GrammarSourceImplementation<T> {

    private Reference<Document> docRef;

    protected AbstractFileObjectGrammarSourceImplementation(Class<T> type) {
        super(type);
    }

    /**
     * Convert this to a FileObject.
     *
     * @return A FileObject or null
     */
    public abstract FileObject toFileObject();

    @Override
    protected <R> R lookupImpl(Class<R> type) {
        T src = source();
        if (type == Snapshot.class) {
            if (type.isInstance(src)) {
                return type.cast(src);
            } else if (src instanceof Source) {
                return type.cast(((Source) src).createSnapshot());
            } else if (src instanceof Document) {
                return type.cast(Source.create((Document) src).createSnapshot());
            } else {
                Document doc = lookupImpl(Document.class);
                if (doc != null) {
                    return type.cast(Source.create(doc).createSnapshot());
                }
                FileObject fo = toFileObject();
                if (fo != null) {
                    return type.cast(Source.create(fo).createSnapshot());
                }
            }
        } else if (type == Source.class) {
            if (type.isInstance(src)) {
                return type.cast(src);
            } else if (src instanceof Document) {
                return type.cast(Source.create((Document) src));
            } else {
                Document doc = lookupImpl(Document.class);
                if (doc != null) {
                    return type.cast(Source.create(doc));
                }
                FileObject fo = toFileObject();
                if (fo != null) {
                    return type.cast(Source.create(fo));
                }
            }
        } else if (type == FileObject.class) {
            FileObject fo = toFileObject();
            if (fo != null) {
                return type.cast(toFileObject());
            }
        } else if (type == Path.class) {
            FileObject fo = toFileObject();
            if (fo != null) { // MemoryFileSystem, etc.
                File f = FileUtil.toFile(fo);
                if (f == null) {
                    return type.cast(f.toPath());
                }
            }
        } else if (type == File.class) {
            FileObject fo = toFileObject();
            if (fo != null) {
                File f = FileUtil.toFile(fo);
                if (f != null) {
                    return type.cast(f);
                }
            }
        } else if (type == Project.class) {
            FileObject fo = toFileObject();
            if (fo != null) {
                return type.cast(FileOwnerQuery.getOwner(fo));
            }
        } else if (type == Document.class || type == BaseDocument.class || type == StyledDocument.class || type == AbstractDocument.class) {
            Document doc = document();
            if (doc != null && type.isInstance(doc)) {
                return type.cast(doc);
            }
        } else if (DataObject.class == type) {
            FileObject fo = toFileObject();
            try {
                return type.cast(DataObject.find(fo));
            } catch (DataObjectNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        } else if (String.class == type) {
            Path p = lookup(Path.class);
            return type.cast(p == null ? name() : p.toAbsolutePath().toString());
        }
        return null;
    }

    protected Document document() {
        if (docRef != null) {
            Document doc = docRef.get();
            if (doc != null) {
                return doc;
            }
        }
        FileObject fo = toFileObject();
        if (fo != null) {
            try {
                DataObject dob = DataObject.find(fo);
                EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                if (ck != null) {
                    return ck.getDocument();
                }
            } catch (DataObjectNotFoundException ex) {
                Logger.getLogger(AbstractFileObjectGrammarSourceImplementation.class.getName())
                        .log(Level.WARNING, "File for " + fo + " disappeared", ex);
            }
        }
        return null;
    }

    @Override
    public String name() {
        return toFileObject().getName();
    }

    @Override
    public long lastModified() throws IOException {
        Document doc = document();
        if (doc != null) {
            return DocumentUtilities.getDocumentTimestamp(doc);
        }
        return toFileObject().lastModified().getTime();
    }

    public static FileObject fileObjectFor(GrammarSource<?> src) {
        Optional<FileObject> fo = src.lookup(FileObject.class);
        return fo.isPresent() ? fo.get() : null;
    }

    @Override
    public String computeId() {
        FileObject fo = lookup(FileObject.class);
        if (fo != null) {
            return hashString(fo.toURI().toString());
        }
        return null;
    }
}
