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
package org.nemesis.editor.utils;

import com.mastfrog.function.state.Obj;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoableEdit;
import org.netbeans.editor.BaseDocument;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;

/**
 *
 * @author Tim Boudreau
 */
public final class DocumentOperationContext {

    private static final int TOP = Integer.MAX_VALUE - 1000;
    private static final int BOTTOM = Integer.MIN_VALUE + 1000;

    private final List<RunEntry> operations = new CopyOnWriteArrayList<>();
    private volatile int items;
    private final Set<LockedDocument> lockedDocuments = new HashSet<>();
    private final Set<String> entered = new HashSet<>();
    private final StyledDocument document;
    private final List<UndoableEdit> pendingEdits = new ArrayList<>(5);
    private boolean inWriteLock;
    private final BiConsumer<Document, UndoableEdit> editSender;

    DocumentOperationContext(StyledDocument document, BiConsumer<Document, UndoableEdit> editSender) {
        this.document = document;
        this.editSender = editSender;
    }

    public StyledDocument document() {
        return document;
    }

    private boolean canFlushEdits() {
        return inWriteLock || lockedDocuments.contains(new LockedDocument(LockedDocument.MODE_RUN_ATOMIC, 0, document));
    }

    public DocumentOperationContext sendUndoableEdit(UndoableEdit edit) {
        if (canFlushEdits()) {
            editSender.accept(document, edit);
        } else {
            pendingEdits.add(edit);
        }
        return this;
    }

    private void onEnterAtomicLock() {
        flushPendingEdits();
    }

    void flushPendingEdits() {
        if (document instanceof BaseDocument) {
            BaseDocument bd = (BaseDocument) document;
            if (!bd.isAtomicLock()) {
                NbDocument.runAtomic(document, () -> {
                    doFlushPendingEdits();
                });
                return;
            }
        }
        // if not BaseDocument or locked, may fail
        doFlushPendingEdits();
    }

    private void doFlushPendingEdits() {
        if (!pendingEdits.isEmpty()) {
            for (Iterator<UndoableEdit> it = pendingEdits.iterator(); it.hasNext();) {
                UndoableEdit edit = it.next();
                editSender.accept(document, edit);
                it.remove();
            }
        }
    }

    synchronized boolean markEntered(String id) {
        return entered.add(id);
    }

    synchronized boolean enterExit(String id, BadLocationRunnable run) throws BadLocationException {
        if (!entered.contains(id)) {
            entered.add(id);
            try {
                run.run();
            } finally {
                entered.remove(id);
            }
        }
        return false;
    }

    synchronized boolean ifNotEntered(String id, BadLocationRunnable run) throws BadLocationException {
        if (!entered.contains(id)) {
            entered.add(id);
            run.run();
            return true;
        }
        return false;
    }

    synchronized boolean wasEntered(String id) {
        return entered.contains(id);
    }

    @SuppressWarnings("ThrowableResultIgnored")
    synchronized void enterExitAtomicLock(StyledDocument doc, BadLocationRunnable run) throws BadLocationException {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_RUN_ATOMIC, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            try {
                Obj<BadLocationException> ex = Obj.create();
                NbDocument.runAtomic(doc, () -> {
                    try {
                        run.run();
                        onEnterAtomicLock();
                    } catch (BadLocationException ble) {
                        ex.set(ble);
                    }
                });
                if (ex.isSet()) {
                    throw ex.get();
                }
            } finally {
                lockedDocuments.remove(ld);
            }
        } else {
            run.run();
        }
    }

    synchronized void enterExitWriteLock(Document doc, BadLocationRunnable run) throws BadLocationException {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_WRITE_LOCK, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            try {
                run.run();
            } finally {
                lockedDocuments.remove(ld);
            }
        } else {
            run.run();
        }
    }

    synchronized boolean enterAtomicLockIfNotAlreadyLockedAndDoNotRemove(Document doc, BadLocationRunnable documentAtomicLocker) throws BadLocationException {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_RUN_ATOMIC, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            documentAtomicLocker.run();
            onEnterAtomicLock();
            return true;
        }
        return false;
    }

    synchronized <T, E extends Exception> T enterAtomicLockIfNotAlreadyLocked(Document doc, AtomicLockFunction<T, E> documentAtomicLocker) throws BadLocationException, E {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_RUN_ATOMIC, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            try {
                return documentAtomicLocker.apply(true, this::onEnterAtomicLock);
            } finally {
                lockedDocuments.remove(ld);
            }
        } else {
            return documentAtomicLocker.apply(false, this::onEnterAtomicLock);
        }
    }

    synchronized <T, E extends Exception> T enterReadLockIfNotAlreadyLocked(Document doc, AtomicLockFunction<T, E> documentAtomicLocker) throws BadLocationException, E {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_READ_LOCK, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            try {
                return documentAtomicLocker.apply(true, DocumentOperationContext::doNothing);
            } finally {
                lockedDocuments.remove(ld);
            }
        } else {
            return documentAtomicLocker.apply(false, DocumentOperationContext::doNothing);
        }
    }

    static void doNothing() {
        // do nothing
    }

    synchronized <T, E extends Exception> T enterWriteLockIfNotAlreadyLocked(Document doc, AtomicLockFunction<T, E> run) throws BadLocationException, E {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_WRITE_LOCK, lockedDocuments.size(), doc);
        if (!lockedDocuments.contains(ld)) {
            lockedDocuments.add(ld);
            return run.apply(true, DocumentOperationContext::doNothing);
        }
        return run.apply(false, DocumentOperationContext::doNothing);
    }

    synchronized boolean exitAtomicLockIfLocked(Document doc, BadLocationRunnable atomicLockReleaser) throws BadLocationException {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_RUN_ATOMIC, lockedDocuments.size(), doc);
        if (lockedDocuments.contains(ld)) {
            try {
                onEnterAtomicLock();
                atomicLockReleaser.run();
            } finally {
                lockedDocuments.remove(ld);
            }
            return true;
        }
        return false;
    }

    synchronized boolean exitWriteLockIfLocked(Document doc, BadLocationRunnable writeLockReleaser) throws BadLocationException {
        LockedDocument ld = new LockedDocument(LockedDocument.MODE_WRITE_LOCK, lockedDocuments.size(), doc);
        if (lockedDocuments.contains(ld)) {
            writeLockReleaser.run();
            lockedDocuments.remove(ld);
            return true;
        }
        return false;
    }

    synchronized void documentReadLocked(Document doc) {
        lockedDocuments.add(new LockedDocument(true, lockedDocuments.size(), doc));
    }

    synchronized void documentWriteLocked(Document doc) {
        lockedDocuments.add(new LockedDocument(false, lockedDocuments.size(), doc));
    }

    synchronized void documentReadUnlocked(Document doc) {
        lockedDocuments.remove(new LockedDocument(true, lockedDocuments.size(), doc));
    }

    synchronized void documentWriteUnlocked(Document doc) {
        lockedDocuments.remove(new LockedDocument(false, lockedDocuments.size(), doc));
    }

    void ensureAllDocumentsUnlocked() {
        try {
            // Ensure unlock in reverse lock order:
            for (LockedDocument item : new TreeSet<>(lockedDocuments)) {
                try {
                    // Read locks are all document.render() - they cannot avoid
                    // getting unlocked
                    if (item.doc instanceof BaseDocument) {
                        BaseDocument bd = (BaseDocument) item.doc;
                        if (!item.isRunAtomic()) {
//                            bd.extWriteUnlock();
                        } else {
//                            bd.atomicUnlock();
                        }
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        } finally {
            lockedDocuments.clear();
        }
    }

    /**
     * Add a runnable to be run in reverse order it was added on the event queue
     * after background operations have completed. The runnable will be run
     * regardless of the completion status of any other runnables added.
     *
     * @param run A runnable
     * @return this
     */
    public DocumentOperationContext add(Runnable run) {
        operations.add(new RunEntry(run, items++));
        return this;
    }

    /**
     * Add a runnable to be runPostOperationsOnEventQueue on the event queue
     * after background operations have completed, and  <i>after</i> all other
     * post-operations except ones subsequently added using this method. The
     * runnable will be run regardless of the completion status of any other
     * runnables added.
     *
     * @param run A runnable
     * @return this
     */
    public DocumentOperationContext addLast(Runnable run) {
        int ix = BOTTOM - ++items;
        operations.add(new RunEntry(run, ix));
        return this;
    }

    /**
     * Add a runnable to be runPostOperationsOnEventQueue on the event queue
     * after background operations have completed, and  <i>before</i> all other
     * post-operations and any post-operations subsequently added using this
     * method. The runnable will be run regardless of the completion status of
     * any other runnables added.
     *
     * @param run A runnable
     * @return this
     */
    public DocumentOperationContext addFirst(Runnable run) {
        int ix = TOP + ++items;
        operations.add(new RunEntry(run, ix));
        return this;
    }

    void runPostOperationsOnEventQueue(Logger logger) {
        if (operations.isEmpty()) {
            logger.log(Level.FINEST, "No post operations to run");
            return;
        }
        List<RunEntry> ops = new ArrayList<>(operations);
        Collections.sort(ops);
        Collections.reverse(ops);
        operations.clear();
        Mutex.EVENT.readAccess(new Runner(logger, ops));
    }

    class Runner implements Runnable {

        private final Logger logger;
        private final List<RunEntry> ops;

        public Runner(Logger logger, List<RunEntry> ops) {
            this.logger = logger;
            this.ops = ops;
        }

        @Override
        public void run() {
            for (RunEntry run : ops) {
                try {
                    logger.log(Level.FINER, "Run post {0}", run);
                    run.run.run();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, run.toString(), ex);
                }
            }
            if (!operations.isEmpty()) {
                // Get us out of this cycle of the event queue
                EventQueue.invokeLater(() -> {
                    runPostOperationsOnEventQueue(logger);
                });
            } else {
                ensureAllDocumentsUnlocked();
            }
        }
    }

    private static class RunEntry implements Comparable<RunEntry> {

        private final Runnable run;
        private final int index;

        public RunEntry(Runnable run, int index) {
            this.run = run;
            this.index = index;
        }

        @Override
        public int compareTo(RunEntry o) {
            return Integer.compare(index, o.index);
        }

        public String toString() {
            return index + ". " + run;
        }
    }

    private static class LockedDocument implements Comparable<LockedDocument> {

        private final int index;
        private final Document doc;
        private final byte mode;
        static final int MODE_RUN_ATOMIC = 0;
        static final int MODE_WRITE_LOCK = 1;
        static final int MODE_READ_LOCK = 2;

        public LockedDocument(boolean read, int index, Document doc) {
            this(read ? 0 : 1, index, doc);
        }

        public LockedDocument(int mode, int index, Document doc) {
            this.mode = (byte) mode;
            this.index = index;
            this.doc = doc;
        }

        boolean isRunAtomic() {
            return mode == 0;
        }

        boolean isWriteLock() {
            return mode == 1;
        }

        boolean isReadLock() {
            return mode == 2;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + (mode * 47);
            hash = 97 * hash + System.identityHashCode(doc);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (!(obj instanceof LockedDocument)) {
                return false;
            }
            final LockedDocument other = (LockedDocument) obj;
            if (this.mode != other.mode) {
                return false;
            }
            return this.doc == other.doc;
        }

        @Override
        public int compareTo(LockedDocument o) {
            return -Integer.compare(index, o.index);
        }

        @Override
        public String toString() {
            return (mode == 0 ? "read-" : mode == 1 ? "write-" : "render-") + index + ". " + System.identityHashCode(doc) + " " + doc;
        }
    }
}
