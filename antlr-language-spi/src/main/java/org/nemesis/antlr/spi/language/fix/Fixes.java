/*
 * The MIT License
 *
 * Copyright 2019 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.nemesis.antlr.spi.language.fix;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.data.IndexAddressable;
import org.nemesis.extraction.Extraction;
import org.nemesis.misc.utils.function.ThrowingFunction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 * Convenience class which makes adding error descriptions with hints to a
 * parsed document much more straightforward.
 *
 * @author Tim Boudreau
 */
public final class Fixes {

    static final Logger LOG = Logger.getLogger(Fixes.class.getName());

    static {
        LOG.setLevel(Level.ALL);
    }

    private final Extraction extraction;

    private final ParseResultContents contents;

    /**
     * Create a new Fixes with the passed extraction and parse result contents
     * to feed error descriptions into.
     *
     * @param extraction An extraction
     * @param contents The contents of an AntlrParseResult
     */
    public Fixes(Extraction extraction, ParseResultContents contents) {
        this.extraction = extraction;
        this.contents = contents;
    }

    /**
     * Utility method to return an empty lazy fix list.
     *
     * @return An empty lazy fix list
     */
    public static LazyFixList none() {
        return NoFixes.NO_FIXES;
    }

    /**
     * Ad a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addHint(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        add(null, Severity.HINT, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id The error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addHint(String errorId, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(errorId, Severity.HINT, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.HINT and an id.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addHint(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.HINT, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.HINT.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addHint(IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(null, Severity.HINT, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Ad a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addWarning(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws
            BadLocationException {
        add(null, Severity.WARNING, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addWarning(String errorId, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(errorId, Severity.WARNING, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param id An error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addWarning(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.WARNING, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.WARNING.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addWarning(IndexAddressable.IndexAddressableItem item, String message) throws BadLocationException {
        add(null, Severity.WARNING, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addError(IndexAddressable.IndexAddressableItem item, String message, Consumer<FixConsumer> lazyFixes)
            throws BadLocationException {
        add(null, Severity.ERROR, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR and a callback which will be invoked to
     * collect possible fixes.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @param lazyFixes A consumer which can create 0 or more "fixes" which
     * alter the document in some way
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addError(String id, IndexAddressable.IndexAddressableItem item, String message,
            Consumer<FixConsumer> lazyFixes) throws
            BadLocationException {
        add(id, Severity.ERROR, message, item.start(), item.end(), lazyFixes);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR.
     *
     * @param id the error id
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addError(String errorId, IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(errorId, Severity.ERROR, message, item.start(), item.end(), null);
        return this;
    }

    /**
     * Add a hint with Severity.ERROR
     *
     * @param item The item the fix relates to
     * @param message The message to display in the editor margin
     * @return this
     * @throws BadLocationException if the coordinates supplied are outside the
     * document
     */
    public Fixes addError(IndexAddressable.IndexAddressableItem item, String message) throws
            BadLocationException {
        add(null, Severity.ERROR, message, item.start(), item.end(), null);
        return this;
    }

    private void add(String id, Severity severity, String description, int start, int end,
            Consumer<FixConsumer> lazyFixes) throws BadLocationException {
        LOG.log(Level.FINER, "Add hint {0} {2} ''{3}'' @ {4}:{5} with {6}",
                new Object[]{id, severity, description, start, end, lazyFixes});

        Optional<Document> doc = extraction.source().lookup(Document.class);
        Optional<FileObject> fo = extraction.source().lookup(FileObject.class);
        ErrorDescription desc = null;
        if (doc.isPresent() && doc.get() instanceof BaseDocument) {
            BaseDocument bd = (BaseDocument) doc.get();
            Position startPos = bd.createPosition(start);
            Position endPos = bd.createPosition(end);
            if (lazyFixes == null) {
                if (id == null) {
                    desc = ErrorDescriptionFactory.createErrorDescription(severity, description, Collections
                            .emptyList(),
                            bd, startPos, endPos);
                } else {
                    desc = ErrorDescriptionFactory.createErrorDescription(id, severity, description, description,
                            NoFixes.NO_FIXES,
                            bd, startPos, endPos);

                }
            } else {
                LazyFixList hints = new VeryLazyHintsList(lazyFixes, extraction);
                if (id == null) {
                    desc = ErrorDescriptionFactory.createErrorDescription(severity, description, hints, bd, startPos,
                            endPos);
                } else {
                    desc = ErrorDescriptionFactory
                            .createErrorDescription(id, severity, description, description, hints, bd, startPos,
                                    endPos);
                }
            }
        } else if (fo.isPresent()) {
            FileObject file = fo.get();
            if (lazyFixes == null) {
                if (id == null) {
                    desc = ErrorDescriptionFactory.createErrorDescription(severity, description, file, start, end);
                } else {
                    desc = ErrorDescriptionFactory.createErrorDescription(id, severity, description, description,
                            NoFixes.NO_FIXES, file, start, end);

                }
            } else {
                LazyFixList hints = new VeryLazyHintsList(lazyFixes, extraction);
                if (id == null) {
                    desc = ErrorDescriptionFactory.createErrorDescription(severity, description, hints, file, start,
                            end);
                } else {
                    desc = ErrorDescriptionFactory
                            .createErrorDescription(id, severity, description, description, hints, file, start, end);
                }
            }
        }
        if (desc != null) {
            contents.addErrorDescription(desc);
        } else {
            throw new IllegalStateException("No FileObject or BaseDocument");
        }
    }

    private static final class VeryLazyHintsList implements LazyFixList, Runnable {

        private final PropertyChangeSupport supp = new PropertyChangeSupport(this);
        private static final RequestProcessor THREAD_POOL = new RequestProcessor("antlr-lazy-hints", 3, true);
        private final Consumer<FixConsumer> lazyFixes;
        private final Extraction extraction;
        private final AtomicReference<List<Fix>> ref = new AtomicReference<>();
        private volatile boolean computed;
        private volatile Future<?> future;

        public VeryLazyHintsList(Consumer<FixConsumer> lazyFixes, Extraction extraction) {
            this.lazyFixes = lazyFixes;
            this.extraction = extraction;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            LOG.log(Level.FINEST, "Add property change listener to {0}: {1}", new Object[] {this, l});
            supp.addPropertyChangeListener(l);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            LOG.log(Level.FINEST, "Remove property change listener from {0}: {1}", new Object[] {this, l});
            Future<?> fut = this.future;
            supp.removePropertyChangeListener(l);
            if (fut != null && supp.getPropertyChangeListeners().length == 0) {
                LOG.log(Level.FINE, "Cancel hint computation on {0}", this);
                fut.cancel(true);
                ref.set(null);
                future = null;
                computed = false;
            }
        }

        @Override
        public boolean probablyContainsFixes() {
            return true;
//            List<Fix> all = ref.get();
//            return all == null || !computed ? true : !all.isEmpty();
        }

        @Override
        public List<Fix> getFixes() {
            return maybeLaunch();
        }

        @Override
        public boolean isComputed() {
            return computed;
        }

        List<Fix> maybeLaunch() {
            if (ref.compareAndSet(null, Collections.emptyList())) {
                LOG.log(Level.FINER, "Launch computation for {0}", this);
                future = THREAD_POOL.submit(this);
            }
            return ref.get();
        }

        @Override
        public void run() {
            LOG.log(Level.FINE, "Begin fix computation for {0}", this);
            List<Fix> fixen = null;
            if (Thread.interrupted()) {
                LOG.log(Level.FINE, "Skip hint generation for interrupted for {0}", this);
                ref.set(null);
                future = null;
                computed = false;
                return;
            }
            try {
                FixListBuilder flb = new FixListBuilder(extraction);
                lazyFixes.accept(flb);
                fixen = flb.entries();
                LOG.log(Level.FINER, "Computed {0} fixes for {1} in {2}", new Object[]{fixen.size(), this, flb});
                ref.set(fixen);
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "Exception computing fixes", t);
                t.printStackTrace(System.out);
            } finally {
                computed = true;
                future = null;
                LOG.log(Level.FINEST, "Fix computation completed for {0} with {1}", new Object[] {this, fixen});
            }
            supp.firePropertyChange(PROP_COMPUTED, false, true);
            if (fixen.size() > 0) {
                LOG.log(Level.FINEST, "Firing fix change in {0}", this);
                supp.firePropertyChange(PROP_FIXES, Collections.emptyList(), fixen);
            }
        }
    }

    private static final class ChangeConsumerImpl extends DocumentEditBag {

        final ChangeInfo changes = new ChangeInfo();

        ChangeConsumerImpl(Extraction ext) {
            super(ext.source().lookup(FileObject.class), ext.source().lookup(Document.class));
        }

        @Override
        public DocumentEditBag addChange(BaseDocument document, Optional<FileObject> fileMayBeNull, int start, int end)
                throws
                BadLocationException {
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "Add change to {0} at {1}:{2}");
            }
            changes.add(fileMayBeNull.isPresent() ? fileMayBeNull.get() : null,
                    document.createPosition(start), document.createPosition(end));
            return this;
        }
    }

    private static final class FixListBuilder extends FixConsumer {

        final Extraction extraction;
        private List<Fix> entries;

        FixListBuilder(Extraction extraction) {
            this.extraction = extraction;
        }

        List<Fix> entries() {
            return entries == null ? Collections.emptyList() : entries;
        }

        @Override
        public void accept(ThrowingFunction<BaseDocument, String> describer, FixImplementation impl) {
            LOG.log(Level.FINEST, "Add fix {0} impl {1}", new Object[]{describer, impl});
            if (entries == null) {
                entries = new ArrayList<>(7);
            }
            entries.add(new LazyFixListEntry(describer, impl, extraction));
        }

    }

    private static final class LazyFixListEntry implements Fix {

        private final ThrowingFunction<BaseDocument, String> describer;
        private final FixImplementation implementer;
        private String description;
        private ChangeInfo changes;
        private final Extraction ext;
        private volatile boolean errored;

        public LazyFixListEntry(ThrowingFunction<BaseDocument, String> describer, FixImplementation implementer,
                Extraction ext) {
            this.describer = describer;
            this.implementer = implementer;
            this.ext = ext;
        }

        @Override
        public String getText() {
            if (description == null) {
                BaseDocument doc = (BaseDocument) ext.source().lookup(Document.class).get();
                try {
                    description = describer.apply(doc);
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                    ex.printStackTrace(System.out);
                    description = " - (" + ex + ")";
                }
            }
            return description;
        }

        @Override
        public ChangeInfo implement() throws Exception {
            if (errored || changes != null) {
                return changes;
            }
            BaseDocument doc = (BaseDocument) ext.source().lookup(Document.class).get();
            Optional<FileObject> file = ext.source().lookup(FileObject.class);
            ChangeConsumerImpl changer = new ChangeConsumerImpl(ext);
            implementer.implement(doc, file, ext, changer);
            return changer.changes;
        }
    }
}
