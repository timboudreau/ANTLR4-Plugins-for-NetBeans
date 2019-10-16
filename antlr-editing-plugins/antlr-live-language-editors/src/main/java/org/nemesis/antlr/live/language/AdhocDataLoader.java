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
package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataLoaderPool;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.nemesis.adhoc.mime.types.AdhocMimeTypes;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = DataLoader.class, position = 20000)
public class AdhocDataLoader extends DataLoader implements BiConsumer<String, String> {

    private static final RequestProcessor TYPE_CHANGE_THREAD_POOL
            = new RequestProcessor("AdhocDataLoader-type-changer", 1);

    public AdhocDataLoader() {
        super("org.nemesis.antlr.live.language.AdhocDataObject");
        AdhocMimeTypes.listenForRegistrations(this); // weak reference, no leak
    }

    @Override
    protected void addNotify() {
        super.addNotify();
    }

    @Override
    @Messages("antlrFiles=Adhoc Files Mapped To Antlr Grammars")
    protected String defaultDisplayName() {
        return Bundle.antlrFiles();
    }

    @Override
    protected String actionsContext() {
        return "Loaders/text/plain/Actions";
    }

    @Override
    protected DataObject handleFindDataObject(FileObject fo, RecognizedFiles rf) throws IOException {
        if (isAdhocFile(fo)) {
            rf.markRecognized(fo);
            return new AdhocDataObject(fo, this);
        }
        return null;
    }

    private boolean isAdhocFile(FileObject fo) {
        boolean result = !"g4".equals(fo.getExt())
                && (AdhocMimeTypes.isAdhocMimeType(fo.getMIMEType())
                || AdhocMimeTypes.isAdhocMimeTypeFileExtension(fo.getExt())
                || AdhocMimeTypes.isRegisteredExtension(fo.getExt()));
        return result;
    }

    @Override
    public void accept(String ext, String mimetype) {
        DynamicLanguages.ensureRegistered(mimetype);
        FileUtil.setMIMEType(ext, mimetype);
        EventQueue.invokeLater(new MimeTypeUpdaterForFilesAndDataObjects(ext, mimetype));
    }

    final class MimeTypeUpdaterForFilesAndDataObjects implements Runnable {

        private final String ext;
        private final String mimeType;
        private final Map<FileObject, DataObject> files = Collections.synchronizedMap(new HashMap<>());
        private final Set<FileObject> toReopen = new HashSet<>();

        MimeTypeUpdaterForFilesAndDataObjects(String ext, String mimeType) {
            this.ext = ext;
            this.mimeType = mimeType;
        }

        @SuppressWarnings("unchecked")
        void populateByReflection() {
            /* DataObjectPool.POOL.getActiveDataObjects();
  Iterator<Item>
  Item -> obj (Reference<DataObject>)
             */
            try {
                Class<?> dataObjectPool = Class.forName("org.openide.loaders.DataObjectPool", true, Lookup.getDefault().lookup(ClassLoader.class));
                Field poolField = dataObjectPool.getDeclaredField("POOL");
                poolField.setAccessible(true);
                Method getActiveDataObjects = dataObjectPool.getDeclaredMethod("getActiveDataObjects");
                getActiveDataObjects.setAccessible(true);
                Class<?> itemType = Class.forName("org.openide.loaders.DataObjectPool$Item");
                Field objField = itemType.getDeclaredField("obj");
                objField.setAccessible(true);
                Object pool = poolField.get(null);
                if (pool == null) {
                    return;
                }
                Iterator<?> items = (Iterator<?>) getActiveDataObjects.invoke(pool);
                int count = 0;
                while (items.hasNext()) {
                    count++;
                    Reference<DataObject> dobRef = (Reference<DataObject>) objField.get(items.next());
                    DataObject dob = dobRef.get();
                    if (dob != null && dob.isValid() && ext.equals(dob.getPrimaryFile().getExt())) {
                        files.put(dob.getPrimaryFile(), dob);
                    }
                }
//                Iterator<?> items = mth.invoke(mth, args)
            } catch (Exception | Error e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            // A bunch of thread jumping here:  We must use the EDT to ask
            // for the open editor panes;  we must NOT use the EDT to call
            // DataObject.find(); hence the choreography
            if (EventQueue.isDispatchThread()) {
                // Find any open editors over files that are currenly open -
                // replacing the data loader will cause them to be closed
                for (JTextComponent c : EditorRegistry.componentList()) {
                    TopComponent tc = (TopComponent) SwingUtilities.getAncestorOfClass(TopComponent.class, c);
                    if (tc != null) {
                        DataObject dob = tc.getLookup().lookup(DataObject.class);
                        if (dob != null) {
                            FileObject fo = dob.getPrimaryFile();
                            if (ext.equals(fo.getExt())) {
                                toReopen.add(fo);
                                files.put(fo, dob);
                            }
                        }
                    }
                }
                // Now find any other dataobjects that exist in the system via
                // hackery
                TYPE_CHANGE_THREAD_POOL.submit(this);
            } else {
                populateByReflection();
                for (Map.Entry<FileObject, DataObject> e : files.entrySet()) {
                    try {
                        DataLoaderPool.setPreferredLoader(e.getKey(), AdhocDataLoader.this);
                        e.getValue().setValid(false);
                    } catch (IOException | PropertyVetoException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
                if (!toReopen.isEmpty()) {
                    TYPE_CHANGE_THREAD_POOL.post(new Runnable() {
                        final Set<OpenCookie> reoopeners = new HashSet<>();

                        @Override
                        public void run() {
                            if (!EventQueue.isDispatchThread()) {
                                // Now find OpenCookies for all the files to reopen
                                for (FileObject dob : toReopen) {
                                    try {
                                        DataObject toOpen = DataObject.find(dob);
                                        OpenCookie opener = toOpen.getLookup().lookup(OpenCookie.class);
                                        if (opener != null) {
                                            reoopeners.add(opener);
                                        }
                                    } catch (DataObjectNotFoundException ex) {
                                        Exceptions.printStackTrace(ex);
                                    }
                                }
                                if (!reoopeners.isEmpty()) {
                                    EventQueue.invokeLater(this);
                                }
                            } else {
                                // And on EDT, open them
                                for (OpenCookie ck : reoopeners) {
                                    ck.open();
                                }
                            }
                        }
                    }, 150);
                }
            }
        }
    }
}
