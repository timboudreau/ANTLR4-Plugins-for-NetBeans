/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.extraction.nb.api;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.GrammarSourceImplementation;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.editor.BaseDocument;
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
            } else if (src instanceof Document) {
                return type.cast(Source.create((Document) src).createSnapshot());
            } else {
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
            if (fo != null) {
                Path p = FileUtil.toFile(fo).toPath();
                return type.cast(p);
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
        } else if (type == Document.class || type == BaseDocument.class || type == StyledDocument.class) {
            FileObject fo = toFileObject();
            if (fo != null) {
                try {
                    DataObject dob = DataObject.find(fo);
                    EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                    if (ck != null) {
                        Document doc = ck.getDocument();
                        if (doc != null && type.isInstance(doc)) {
                            return type.cast(doc);
                        }
                    }
                } catch (DataObjectNotFoundException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        } else if (String.class == type) {
            Path p = lookup(Path.class);
            return type.cast(p == null ? name() : p.toAbsolutePath().toString());
        }
        return null;
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
