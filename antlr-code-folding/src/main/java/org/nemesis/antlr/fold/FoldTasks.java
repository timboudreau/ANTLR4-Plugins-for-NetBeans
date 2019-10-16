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
package org.nemesis.antlr.fold;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import static org.nemesis.antlr.fold.FoldUtils.documentFor;
import static org.nemesis.antlr.fold.FoldUtils.fileObjectForDocument;
import static org.nemesis.antlr.fold.FoldUtils.opStringSupplier;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
final class FoldTasks {

    static final Logger LOG = Logger.getLogger(FoldTasks.class.getName());
    private static final int GC_INTERVAL = 5;

    private final Map<DocOrFileKey, FoldTask> taskForKey = new HashMap<>();
    private int gcCountdown;
    private static final FoldTasks INSTANCE = new FoldTasks();

    public static FoldTasks getDefault() {
        return INSTANCE;
    }

    public FoldTask forSnapshot(Snapshot snapshot, boolean create) {
        DocOrFileKey key = keyFor(snapshot);
        FoldTask result = null;
        if (key != null) {
            result = taskForKey.get(key);
            if (result == null && create) {
                result = newFoldTask(key);
                LOG.log(Level.FINE, "Created FoldTask {0} for key {1} for "
                        + "snap {2}", new Object[]{result, key, snapshot});
                taskForKey.put(key, result);
            } else {
                if (result != null) {
                    LOG.log(Level.FINEST, "Existing FoldTask {0} for key {1} for "
                            + "snap {2}", new Object[]{result, key, snapshot});
                } else {
                    LOG.log(Level.FINEST, "Not creating FoldTask for {0}", key);
                }
            }
        }
//        maybeGc(result);
        return result;
    }

    public FoldTask forOperation(FoldOperation op, boolean create) {
        DocOrFileKey key = keyFor(op);
        FoldTask result = null;
        if (key != null) {
            result = taskForKey.get(key);
            if (result == null && create) {
                result = newFoldTask(key);
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "Created FoldTask {0} for key {1} for "
                            + "op {2}", new Object[]{result, key, opStringSupplier(op)});
                } else {
                    LOG.log(Level.FINEST, "Existing FoldTask {0} for key {1} for "
                            + "snap {2}", new Object[]{result, key, opStringSupplier(op)});
                }
                taskForKey.put(key, result);
            }
        }
//        maybeGc(result);
        return result;
    }

    private FoldTask newFoldTask(DocOrFileKey key) {
        return new FoldTask(key.toString());
    }

    private void maybeGc(FoldTask newlyCreated) {
        if (gcCountdown++ % GC_INTERVAL == 0) {
            gc(newlyCreated);
        }
    }

    private void gc(FoldTask newlyCreated) {
        try {
            LOG.log(Level.FINER, "FoldTasks.gc with {0} tasks", taskForKey.size());
            for (Iterator<Map.Entry<DocOrFileKey, FoldTask>> it = taskForKey.entrySet().iterator(); it.hasNext();) {
                Map.Entry<DocOrFileKey, FoldTask> e = it.next();
                if (e.getValue() == newlyCreated) {
                    continue;
                }
                DocOrFileKey key = e.getKey();
                if (key.isDead() /* || e.getValue().isEmpty() */) {
                    LOG.log(Level.FINEST, "  Dead key or fold task for {0}", key);
                    it.remove();
                }
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private static DocOrFileKey keyFor(FoldOperation op) {
        Document doc = documentFor(op);
        if (doc != null) {
            FileObject file = fileObjectForDocument(doc);
            return new DocOrFileKey(doc, file);
        } else {
            LOG.log(Level.INFO, "Could not find a document for {0}", opStringSupplier(op));
        }
        return null;
    }

    private static DocOrFileKey keyFor(Snapshot snapshot) {
        Source src = snapshot.getSource();
        return new DocOrFileKey(src.getDocument(true), src.getFileObject());
    }
}
