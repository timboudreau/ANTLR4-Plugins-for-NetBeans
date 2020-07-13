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

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.editor.BaseDocument;
import org.openide.text.NbDocument;

/**
 *
 * @author Tim Boudreau
 */
public final class EditBag {

    private final List<Entry> entries = new ArrayList<>();
    private final StyledDocument doc;

    @SuppressWarnings("LeakingThisInConstructor")
    public EditBag(@NonNull StyledDocument doc, @NonNull Applier applier) {
        this.doc = doc;
        applier.setSet(this);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        if (entries.isEmpty()) {
            sb.append("<empty>");
        } else {
            sb.append("entries:").append(entries.size());
            for (Entry e : entries) {
                sb.append("\n  ").append(e);
            }
        }
        return sb.append(")").toString();
    }

    void applyToStringBuilder(StringBuilder sb) throws Exception { // for tests
        entries.sort(Comparator.<Entry>naturalOrder().reversed());
        for (Entry e : entries) {
            e.apply(sb);
        }
    }

    public static final class Applier {

        private EditBag set;
        private volatile boolean applied;
        private final DocumentOperator op;

        public Applier(DocumentOperator op) {
            this.op = op;
        }

        public Applier() {
            this(DocumentOperator.NON_JUMP_REENTRANT_UPDATE_DOCUMENT);
        }

        void setSet(EditBag set) {
            if (this.set != null) {
                throw new IllegalStateException("Can only be used for one "
                        + EditBag.class.getSimpleName());
            }
            this.set = set;
        }

        public void apply(DocumentOperator op) throws Exception {
            if (set == null) {
                throw new IllegalStateException("Not associated with a "
                        + EditBag.class.getSimpleName());
            }
            boolean app = applied;
            if (app) {
                throw new IllegalStateException("Already applied");
            }
            applied = true;
            set.apply(op);
        }
    }

    void apply(DocumentOperator op) throws Exception {
        op.runOp(doc, () -> {
            entries.sort(Comparator.<Entry>naturalOrder().reversed());
            for (Entry e : entries) {
                e.accept(doc);
            }
            entries.clear();
        });
    }

    public EditBag delete(int pos, int length) throws BadLocationException {
        return delete(NbDocument.createPosition(doc, pos, Position.Bias.Forward),
                NbDocument.createPosition(doc, pos + length, Position.Bias.Backward));
    }

    public EditBag deleteCoordinates(int start, int end) throws BadLocationException {
        return delete(NbDocument.createPosition(doc, start, Position.Bias.Forward),
                NbDocument.createPosition(doc, end, Position.Bias.Backward));
    }

    public EditBag replaceCoordinate(int start, int end, BadLocationSupplier<String, RuntimeException> supp) throws BadLocationException {
        Position st = NbDocument.createPosition(doc, start, Position.Bias.Forward);
        Position en = NbDocument.createPosition(doc, end, Position.Bias.Backward);
        entries.add(new ReplacementEntry(st, en, supp));
        return this;
    }

    public EditBag replace(int start, int length, String txt) throws BadLocationException {
        return replace(start, length, () -> txt);
    }

    public EditBag replace(int start, int length, BadLocationSupplier<String, RuntimeException> supp) throws BadLocationException {
        Position st = NbDocument.createPosition(doc, start, Position.Bias.Forward);
        Position en = NbDocument.createPosition(doc, start + length, Position.Bias.Backward);
        entries.add(new ReplacementEntry(st, en, supp));
        return this;
    }

    public EditBag replace(Position a, Position b, String txt) {
        return replace(a, b, () -> txt);
    }

    public EditBag replace(Position a, Position b, BadLocationSupplier<String, RuntimeException> supp) {
        return sorted(a, b, (start, end) -> {
            entries.add(new ReplacementEntry(start, end, supp));
        });
    }

    private EditBag sorted(Position a, Position b, BiConsumer<Position, Position> firstLast) {
        int oa = a.getOffset();
        int ob = b.getOffset();
        if (oa == ob) {
            return this;
        }
        Position start;
        Position end;
        if (ob > oa) {
            start = a;
            end = b;
        } else {
            start = b;
            end = a;
        }
        firstLast.accept(start, end);
        return this;
    }

    public EditBag delete(Position a, Position b) {
        return sorted(a, b, (start, end) -> {
            entries.add(new DeletionEntry(start, end));
        });
    }

    public EditBag insert(int pos, String insertionText) throws BadLocationException {
        return insert(pos, () -> insertionText);
    }

    public EditBag insert(int pos, BadLocationSupplier<String, RuntimeException> insertionText) throws BadLocationException {
        Position p = NbDocument.createPosition(doc, pos, Position.Bias.Forward);
        return insert(p, insertionText);
    }

    public EditBag insert(Position pos, BadLocationSupplier<String, RuntimeException> insertionText) {
        entries.add(new InsertionEntry(pos, insertionText));
        return this;
    }

    interface Entry extends BadLocationConsumer<StyledDocument>, IntRange<Entry> {

        // These methods are used in range coalescing - we don't need them here
        @Override
        default Entry newRange(int start, int size) {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        default Entry newRange(long start, long size) {
            throw new UnsupportedOperationException("Not supported.");
        }

        // For tests
        void apply(StringBuilder sb) throws Exception;

        @Override
        default int compareTo(Range<?> o) {
//            if (this instanceof DeletionEntry && !(o instanceof DeletionEntry)) {
//                return 1;
//            } else if (o instanceof DeletionEntry && !(this instanceof DeletionEntry)){
//                return -1;
//            }
            return IntRange.super.compareTo(o);
        }
    }

    private static final class InsertionEntry implements Entry {

        private final Position start;

        private final BadLocationSupplier<String, RuntimeException> insertionText;

        public InsertionEntry(Position start, BadLocationSupplier<String, RuntimeException> insertionText) {
            this.start = start;
            this.insertionText = insertionText;
        }

        @Override
        public int start() {
            return start.getOffset();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public String toString() {
            String s = insertionText.toStringGet();
            StringBuilder sb = new StringBuilder("Insert ")
                    .append(s.length()).append(" characters at ")
                    .append(start()).append(" (")
                    .append(size()).append(") '");
            Strings.escape(s, s.length(), Escaper.CONTROL_CHARACTERS, sb);
            return sb.append("')").toString();
        }

        @Override
        public void accept(@NonNull StyledDocument doc) throws BadLocationException {
            doc.insertString(start.getOffset(), insertionText.get(), null);
        }

        @Override
        public void apply(StringBuilder sb) throws BadLocationException {
            // for tests
            sb.insert(start(), insertionText.get());
        }
    }

    private static final class DeletionEntry implements Entry {

        private final Position start;
        private final Position end;

        public DeletionEntry(Position start, Position end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public int start() {
            return start.getOffset();
        }

        @Override
        public int size() {
            return end.getOffset() - start.getOffset();
        }

        @Override
        public void accept(@NonNull StyledDocument doc) throws BadLocationException {
            int startPoint = start();
            int length = size();
            if (length > 0) {
                doc.remove(startPoint, length);
            }
        }

        public String toString() {
            return new StringBuilder("Delete ")
                    .append(size()).append(" characters at ")
                    .append(start()).append(':').append(end()).toString();
        }

        @Override
        public void apply(StringBuilder sb) {
            sb.delete(start(), end());
        }
    }

    private static final class ReplacementEntry implements Entry {

        private final Position start;
        private final Position end;
        private final BadLocationSupplier<String, RuntimeException> replacementText;

        public ReplacementEntry(Position start, Position end, BadLocationSupplier<String, RuntimeException> replacementText) {
            this.start = start;
            this.end = end;
            this.replacementText = replacementText;
        }

        @Override
        public int start() {
            return start.getOffset();
        }

        @Override
        public int size() {
            return end.getOffset() - start.getOffset();
        }

        public String toString() {
            String s = replacementText.toStringGet();
            StringBuilder sb = new StringBuilder("Replace ")
                    .append(s.length()).append(" characters at ")
                    .append(start()).append(end()).append(" (")
                    .append(size()).append("): '");
            Strings.escape(s, s.length(), Escaper.CONTROL_CHARACTERS, sb);
            return sb.append("')").toString();
        }

        @Override
        public void accept(@NonNull StyledDocument doc) throws BadLocationException {
            int startPoint = start();
            int length = size();
            if (doc instanceof BaseDocument) {
                BaseDocument bd = (BaseDocument) doc;
                bd.replace(startPoint, length, replacementText.get(), null);
            } else if (length > 0) {
                doc.remove(startPoint, length);
                doc.insertString(startPoint, replacementText.get(), null);
            }
        }

        @Override
        public void apply(StringBuilder sb) throws BadLocationException {
            // for tests
            sb.replace(start(), end(), replacementText.get());
        }
    }
}
