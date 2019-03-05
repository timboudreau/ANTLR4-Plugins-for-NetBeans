package org.nemesis.antlr.spi.language;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.filesystems.FileObject;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
public final class SyntaxError implements Comparable<SyntaxError> {

    private final Optional<Token> token;
    private final int startOffset;
    private final int endOffset;
    private final String message;
    private final RecognitionException originalException;
    private static final Logger LOG = Logger.getLogger(SyntaxError.class.getName());

    SyntaxError(Optional<Token> token, int startOffset, int endOffset, String message, RecognitionException originalException) {
        this.token = token;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.message = message;
        this.originalException = originalException;
    }

    ErrorDescription toErrorDescription(Snapshot snapshot, NbLexerAdapter<?, ?> adapter, NbParserHelper helper) {
        if (helper != null) {
            ErrorDescription result = helper.convertError(snapshot, this);
            if (result != null) {
                return result;
            }
        }
        LazyFixList fixes = originalException != null && originalException.getOffendingState() != -1
                ? new Fixes(adapter, snapshot) : NO_FIXES;

        FileObject fo = snapshot == null ? null : snapshot.getSource().getFileObject();
        if (fo != null) {
            return forFileObject(fo, fixes);
        }
        Document doc = snapshot == null ? null : snapshot.getSource().getDocument(false);
        if (doc != null) {
            return forDocument(doc, fixes);
        }
        return null;
    }

    private int start() {
        return startOffset < 0 ? 0 : startOffset;
    }

    private int end() {
        return endOffset < start() ? start() + 1 : endOffset;
    }

    private ErrorDescription forFileObject(FileObject fo, LazyFixList fixes) {
        // Antlr can give us errors from -1 to 0
        return ErrorDescriptionFactory.createErrorDescription(Severity.ERROR,
                message, fixes, fo, start(),
                end());
    }

    private ErrorDescription forDocument(Document doc, LazyFixList fixes) {
        // Antlr can give us errors from -1 to 0
        return ErrorDescriptionFactory.createErrorDescription(Severity.ERROR,
                message, fixes, doc, new SimplePosition(start()), new SimplePosition(end()));
    }

    private static final RequestProcessor FIX_COMPUTATION
            = new RequestProcessor("antlr-fix-computation", 2);

    private class Fixes implements LazyFixList, Runnable {

        private final PropertyChangeSupport supp = new PropertyChangeSupport(this);
        private final NbLexerAdapter<?, ?> adapter;
        private final Snapshot snapshot;
        private final AtomicBoolean submitted = new AtomicBoolean();

        public Fixes(NbLexerAdapter<?, ?> adapter, Snapshot snapshot) {
            this.adapter = adapter;
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            computeFixes();
        }

        void maybeStart() {
            if (!_containsFixes()) {
                submitted.set(true);
                updateFixes(Collections.emptyList());
            } else if (submitted.compareAndSet(false, true)) {
                FIX_COMPUTATION.submit(this);
            }
        }

        private boolean errored;

        private boolean _containsFixes() {
            synchronized (this) {
                if (fixes != null) {
                    return !fixes.isEmpty();
                }
            }
            if (errored) {
                return false;
            }
            boolean result = startOffset > 0 && endOffset > 0;
            if (result) {
                try {
                    // bug - ? - in antlr
                    result &= originalException != null && originalException.getExpectedTokens().size() > 0;
                } catch (IllegalArgumentException ex) {
                    errored = true;
                    LOG.log(Level.FINEST, "Exception computing expected "
                            + "tokens for " + originalException
                            + " state "
                            + originalException.getOffendingState(), ex);

                }
            }

            return result;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            supp.addPropertyChangeListener(l);
            maybeStart();
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            supp.removePropertyChangeListener(l);
            maybeStart();
        }

        @Override
        public boolean probablyContainsFixes() {
            boolean result = _containsFixes();
            if (result) {
                maybeStart();
            }
            return result;
        }

        private List<Fix> fixes;

        @Override
        public synchronized List<Fix> getFixes() {
            if (fixes == null) {
                computeFixes();
            }
            return fixes;
        }

        @Override
        public boolean isComputed() {
            return fixes != null;
        }

        private void updateFixes(List<Fix> fixes) {
            List<Fix> old;
            synchronized (this) {
                old = fixes;
                this.fixes = fixes;
            }
            supp.firePropertyChange(PROP_FIXES, old, fixes);
            supp.firePropertyChange(PROP_COMPUTED, false, true);
        }

        private void computeFixes() {
            if (!probablyContainsFixes()) {
                updateFixes(fixes);
                return;
            }
            IntervalSet is = originalException.getExpectedTokens();
            int max = is.size();
            List<InsertFix> computedFixes = new ArrayList<>(is.size());
            for (int i = 0; i < max; i++) {
                int tokenId = is.get(i);
                String literalName = adapter.vocabulary().getLiteralName(tokenId);
                if (literalName != null) {
                    computedFixes.add(new InsertFix(literalName));
                }
            }
            LOG.log(Level.FINER, "Computed fixes in {0} for {1}: {2}",
                    new Object[]{snapshot, SyntaxError.this, computedFixes});
            updateFixes(Collections.unmodifiableList(computedFixes));
        }

        private class InsertFix implements Fix, Comparable<InsertFix> {

            private final String literalName;

            public InsertFix(String literalName) {
                this.literalName = literalName;
            }

            @Override
            public String getText() {
                return "Insert '" + literalName + "'";
            }

            @Override
            public ChangeInfo implement() throws Exception {
                FileObject fo = snapshot.getSource().getFileObject();
                Document doc = snapshot.getSource().getDocument(true);
                doc.insertString(startOffset, literalName, null);
                int end = Math.max(end(), start() + literalName.length());
                ChangeInfo info = fo == null
                        ? new ChangeInfo(new SimplePosition(start()), new SimplePosition(end))
                        : new ChangeInfo(fo, new SimplePosition(start()), new SimplePosition(end));
                return info;
            }

            @Override
            public String toString() {
                return getText();
            }

            @Override
            public int compareTo(InsertFix o) {
                return literalName.compareToIgnoreCase(o.literalName);
            }
        }
    }

    static final LazyFixList NO_FIXES = new NoFixes();

    static final class NoFixes implements LazyFixList {

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            // do nothing
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            // do nothing
        }

        @Override
        public boolean probablyContainsFixes() {
            return false;
        }

        @Override
        public List<Fix> getFixes() {
            return Collections.emptyList();
        }

        @Override
        public boolean isComputed() {
            return true;
        }

        @Override
        public String toString() {
            return "<no-fixes>";
        }

    }

    private static final class SimplePosition implements Position {

        private final int pos;

        public SimplePosition(int pos) {
            this.pos = pos;
        }

        @Override
        public int getOffset() {
            return pos;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SimplePosition && ((SimplePosition) o).pos == pos;
        }

        @Override
        public int hashCode() {
            return 371 * pos;
        }

        @Override
        public String toString() {
            return Integer.toString(pos);
        }
    }

    public Optional<Token> token() {
        return token;
    }

    public int startOffset() {
        return startOffset;
    }

    public int endOffset() {
        return endOffset;
    }

    public String message() {
        return message;
    }

    public RecognitionException originalException() {
        return originalException;
    }

    @Override
    public String toString() {
        return "SyntaxError{" + "token=" + token + ", startOffset=" + startOffset + ", endOffset=" + endOffset + ", message=" + message + ", originalException=" + originalException + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + (token.isPresent() ? token.get().getType() : 0);
        hash = 61 * hash + this.startOffset;
        hash = 61 * hash + this.endOffset;
        hash = 61 * hash + Objects.hashCode(this.message);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SyntaxError other = (SyntaxError) obj;
        if (this.startOffset != other.startOffset) {
            return false;
        }
        if (token.isPresent() != other.token.isPresent()) {
            return false;
        }
        if (this.endOffset != other.endOffset) {
            return false;
        }
        if (!Objects.equals(this.message, other.message)) {
            return false;
        }
        return !(token.isPresent() && Objects.equals(token.get(), other.token.get()));
    }

    @Override
    public int compareTo(SyntaxError o) {
        int result = startOffset == o.startOffset ? 0 : startOffset > o.startOffset ? 1 : -1;
        if (result == 0) {
            result = endOffset == o.endOffset ? 0 : endOffset < o.endOffset ? 1 : -1;
        }
        return result;
    }
}
