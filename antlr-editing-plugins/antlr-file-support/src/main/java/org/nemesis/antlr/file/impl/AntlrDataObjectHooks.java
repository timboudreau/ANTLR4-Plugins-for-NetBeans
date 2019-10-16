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
package org.nemesis.antlr.file.impl;

import java.io.IOException;
import org.nemesis.antlr.spi.language.DataObjectHooks;
import com.mastfrog.function.throwing.io.IORunnable;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.function.Supplier;
import org.nemesis.antlr.file.api.GrammarFileDeletionHook;
import org.nemesis.antlr.project.AntlrConfiguration;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.spi.queries.FileEncodingQueryImplementation;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.InstanceContent;

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

    static final EncImpl encodingQuery = new EncImpl();

    @Override
    public void decorateLookup(DataObject on, InstanceContent content, Supplier<Lookup> superGetLookup) {
        // The ANTLR configuration can specify a different encoding from the
        // project, so ensure it is present
        content.add(new EncImpl());
    }

    static class EncImpl extends FileEncodingQueryImplementation {

        @Override
        public Charset getEncoding(FileObject file) {
            if (!"text/x-g4".equals(file.getMIMEType())) {
                return null;
            }
            Project project = FileOwnerQuery.getOwner(file);
            if (project == null) {
                return null;
            }
            AntlrConfiguration config = AntlrConfiguration.forProject(project);
            if (config != null) {
                return config.encoding();
            }
            return null;
        }
    }

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
