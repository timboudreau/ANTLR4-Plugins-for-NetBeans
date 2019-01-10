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
package org.nemesis.antlr.v4.netbeans.v8;

import java.io.File;
import java.nio.file.Path;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.GrammarSource;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.extraction.src.spi.GrammarSourceImplementation;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * Extension to GrammarSource which supports resolving a FileObject.
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
        if (type == FileObject.class) {
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
        }
        return null;
    }

    public static FileObject fileObjectFor(GrammarSource<?> src) {
        return src.lookup(FileObject.class);
    }
}
