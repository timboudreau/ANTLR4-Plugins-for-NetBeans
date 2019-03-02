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
package org.nemesis.extraction.nb;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.source.api.GrammarSource;
import org.nemesis.source.spi.RelativeResolverImplementation;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 *
 * @author Tim Boudreau
 */
public class AntlrDocumentRelativeResolverImplementation extends RelativeResolverImplementation<Document> {

    public AntlrDocumentRelativeResolverImplementation() {
        super(Document.class);
    }

    @Override
    public Optional<Document> resolve(Document relativeTo, String name) {
        FileObject fo = NbEditorUtilities.getFileObject(relativeTo);
        if (fo != null) {
            GrammarSource<FileObject> fogs = GrammarSource.find(fo, fo.getMIMEType());
            if (fogs != null) {
                GrammarSource<?> gs = fogs.resolveImport(name);
                if (gs != null) {
                    try {
                        Object src = gs.source();
                        if (src instanceof Document) {
                            return Optional.of((Document) src);
                        } else if (src instanceof FileObject) {
                            FileObject n = (FileObject) src;
                            DataObject dob = DataObject.find(n);
                            EditorCookie ck = dob.getLookup().lookup(EditorCookie.class);
                            if (ck != null) {
                                Document d = ck.getDocument();
                                if (d == null) {
                                    d = ck.openDocument();
                                }
                                if (d != null) {
                                    return Optional.of(d);
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(AntlrDocumentRelativeResolverImplementation.class.getName()).log(Level.SEVERE, "Exception resolving " + name, ex);
                    }
                }
            }
//            Optional<FileObject> neighbor = new AntlrFileObjectRelativeResolver().resolve(fo, name);
//            if (neighbor.isPresent()) {
//                try {
//                    DataObject dob = DataObject.find(neighbor.get());
//                    EditorCookie ck = dob.getCookie(EditorCookie.class);
//                    if (ck != null) {
//                        Document d = ck.getDocument();
//                        if (d == null) {
//                            d = ck.openDocument();
//                        }
//                        if (d != null) {
//                            return Optional.of(d);
//                        }
//                    }
//                } catch (IOException ex) {
//                    Logger.getLogger(AntlrDocumentRelativeResolverImplementation.class.getName()).log(Level.SEVERE, "Exception resolving " + name, ex);
//                }
//            }
        }
        return Optional.empty();
    }
}
