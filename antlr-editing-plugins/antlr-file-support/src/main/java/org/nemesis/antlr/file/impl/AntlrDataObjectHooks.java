/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
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
package org.nemesis.antlr.file.impl;

import java.io.IOException;
import org.nemesis.antlr.spi.language.DataObjectHooks;
import com.mastfrog.function.throwing.io.IORunnable;
import java.util.Iterator;
import org.nemesis.antlr.file.api.GrammarFileDeletionHook;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 *
 * @author Tim Boudreau
 */
@NbBundle.Messages({
    "# {0} - the file name",
    "# {1} - list of registered file extensions",
    "hasRegisteredExtensions=Grammar {0} has the file extension(s) {1} registered to it. "
    + "Unregister those extensions and really delete it?"})
public class AntlrDataObjectHooks implements DataObjectHooks {

    @Override
    public void handleDelete(DataObject on, IORunnable superHandleDelete) throws IOException {
        new HookRunner(on, superHandleDelete, 
            Lookup.getDefault().lookupAll(GrammarFileDeletionHook.class)).run();
        /*
        Set<String> registeredExtensions = registeredExtensions(on);
        if (!registeredExtensions.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String e : registeredExtensions) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e);
            }
            String msg = Bundle.hasRegisteredExtensions(on.getPrimaryFile().getName(), sb);
            throw new UserQuestionException(msg) {
                @Override
                public void confirmed() throws IOException {
                    AdhocMimeTypes.unregisterMimeType(adhocMimeType(on));
                    superHandleDelete.run();
                }
            };
        } else {
            superHandleDelete.run();
        }
         */
    }

    static final class HookRunner implements IORunnable {

        private final DataObject ob;
        private final IORunnable superHandleDelete;
        private final Iterable<? extends GrammarFileDeletionHook> hooks;
        private final Iterator<? extends GrammarFileDeletionHook> iter;

        public HookRunner(DataObject ob, IORunnable superHandleDelete, Iterable<? extends GrammarFileDeletionHook> hooks) {
            this.ob = ob;
            this.superHandleDelete = superHandleDelete;
            this.hooks = hooks;
            iter = hooks.iterator();
        }

        @Override
        public void run() throws IOException {
            if (iter.hasNext()) {
                GrammarFileDeletionHook hook = iter.next();
                hook.onBeforeDeleteGrammar(ob, this);
            } else {
                superHandleDelete.run();
                for (GrammarFileDeletionHook hook : hooks) {
                    hook.onAfterDeleteGrammar(ob);
                }
            }
        }

    }

    /*
    private String adhocMimeType(DataObject on) {
        File file = FileUtil.toFile(on.getPrimaryFile());
        if (file != null) {
            return AdhocMimeTypes.mimeTypeForPath(file.toPath());
        }
        return null;
    }

    private Set<String> registeredExtensions(DataObject on) {
        String mimeType = adhocMimeType(on);
        if (mimeType != null) {
            return AdhocMimeTypes.registeredExtensionsFor(mimeType);
        }
        return Collections.emptySet();
    }
*/
}
