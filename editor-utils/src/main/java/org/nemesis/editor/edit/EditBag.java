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
package org.nemesis.editor.edit;

import com.mastfrog.abstractions.Wrapper;
import com.mastfrog.function.TriConsumer;
import org.nemesis.editor.position.PositionRange;
import org.nemesis.editor.position.PositionFactory;
import com.mastfrog.range.IntRange;
import com.mastfrog.range.RangeRelation;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.ops.DocumentOperator;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.editor.BaseDocument;
import org.openide.text.NbDocument;
import static org.nemesis.editor.position.PositionFactory.forDocument;

/**
 * A collection of edits to a document which can be applied in a single shot
 * inside a DocumentOperation that takes care of any locking, cursor
 * repositioning or similar.
 *
 * @author Tim Boudreau
 */
public final class EditBag {

    private final List<EditBagEntry> entries = new ArrayList<>();
    private final StyledDocument doc;
    private final PositionFactory positionFactory;
    private int seqs;

    @SuppressWarnings("LeakingThisInConstructor")
    public EditBag(@NonNull StyledDocument doc, @NonNull Applier applier) {
        this.doc = doc;
        this.positionFactory = forDocument(doc);
        applier.setSet(this);
    }

    public StyledDocument document() {
        return doc;
    }

    public boolean isSingleEdit() {
        return entries.size() == 1;
    }

    public void visitChanges(TriConsumer<ChangeKind, Position, Position> c) {
        Collections.sort(entries);
        for (EditBagEntry entry : entries) {
            c.apply(entry.kind(), entry.startPosition(), entry.endPosition());
        }
    }

    public PositionRange bounds() throws BadLocationException {
        if (isEmpty()) {
            return positionFactory.range(0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        Position minPos = null;
        Position maxPos = null;
        for (EditBagEntry e : entries) {
            int start = e.start();
            int end = e.end();
            if (start < min) {
                minPos = e.startPosition();
                min = start;
            }
            if (end > max) {
                maxPos = e.endPosition();
                max = end;
            }
            min = Math.min(e.start(), min);
            max = Math.max(e.end(), max);
        }
        return PositionRange.create(minPos, maxPos, doc);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private int nextSeq() {
        return seqs++;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('(');
        if (entries.isEmpty()) {
            sb.append("<empty>");
        } else {
            sb.append("entries:").append(entries.size());
            for (EditBagEntry e : entries) {
                sb.append("\n  ").append(e);
            }
        }
        return sb.append(")").toString();
    }

    /**
     * For tests that need to ensure document modifications do what they are
     * supposed to, it can be useful to compare with the same set of operations
     * applied to a string builder.
     *
     * @param sb
     * @throws Exception
     */
    public void applyToStringBuilder(StringBuilder sb) throws Exception { // for tests
        entries.sort(Comparator.<EditBagEntry>naturalOrder().reversed());
        for (EditBagEntry e : entries) {
            e.apply(sb);
        }
    }

    void apply(DocumentOperator op) throws Exception {
        if (isEmpty()) {
            return;
        }
        op.runOp(doc, () -> {
            // Hmm, entries will sort in reverse order

            // we probably want to sort by end position
            // and sort ones with a greater start position and
            // the same end
//            entries.sort(Comparator.<Entry>naturalOrder().reversed());
            entries.sort(Comparator.naturalOrder());

            for (EditBagEntry e : entries) {
                e.accept(doc);
            }
//            entries.clear();
        });
    }

    private static int entrySort(EditBagEntry a, EditBagEntry b) {
        if (true) {
            int result = -Integer.compare(a.end(), b.end());
            if (result == 0) {
                result = Integer.compare(a.start(), b.start());
            }
            if (result == 0) {
                result = Integer.compare(a.sequence(), b.sequence());
            }
            return result;
        }
        if (a.isDeletion() && !b.isDeletion()) {
            return -1;
        } else if (b.isDeletion() && !a.isDeletion()) {
            return 1;
        }
        // Sort by end position,
        RangeRelation rel = a.relationTo(b);
        switch (rel) {
            case EQUAL:
                return Integer.compare(a.sequence(), b.sequence());
            case AFTER:
                return -1;
            case BEFORE:
                return 1;
            case CONTAINED:
                return -1;
            case CONTAINS:
                return 1;
            case STRADDLES_END:
                return -1;
            case STRADDLES_START:
                return 1;
            default:
                throw new AssertionError(rel);
        }
    }

    public EditBag delete(int pos, int length) throws BadLocationException, BadLocationException, BadLocationException {
        return delete(positionFactory.createPosition(pos, Position.Bias.Forward),
                positionFactory.createPosition(pos + length, Position.Bias.Backward));
    }

    public EditBag deleteCoordinates(int start, int end) throws BadLocationException {
        return delete(positionFactory.createPosition(start, Position.Bias.Forward),
                NbDocument.createPosition(doc, end, Position.Bias.Backward));
    }

    public EditBag replaceCoordinates(int start, int end, Supplier<String> text) throws BadLocationException {
        entries.add(new ReplacementEntry(positionFactory.range(start,
                Position.Bias.Forward, end, Position.Bias.Backward),
                notNull("text", text), nextSeq()));
        return this;
    }

    public EditBag replace(int start, int length, String txt) throws BadLocationException {
        return replace(start, length, StringSupplier.of(txt));
    }

    public EditBag replace(int start, int length, Supplier<String> supp) throws BadLocationException {
        entries.add(new ReplacementEntry(positionFactory.range(start, Position.Bias.Forward, start + length, Position.Bias.Backward), supp, nextSeq()));
        return this;
    }

    public EditBag replace(Position a, Position b, String txt) {
        return replace(PositionRange.create(notNull("a", a),
                notNull("b", b), doc), StringSupplier.of(txt));
    }

    public EditBag replace(IntRange<? extends IntRange> range, String txt) throws BadLocationException {
        return replace(notNull("range", range), StringSupplier.of(notNull("txt", txt)));
    }

    public EditBag replace(IntRange<? extends IntRange> range, Supplier<String> supp) throws BadLocationException {
        PositionRange pr = positionFactory.range(notNull("range", range));
        return replace(pr, supp);
    }

    public EditBag replace(PositionRange range, String txt) {
        return replace(notNull("range", range), StringSupplier.of(notNull("txt", txt)));
    }

    public EditBag replace(PositionRange range, Supplier<String> supp) {
        notNull("range", range);
        assert range.document() == document();
        entries.add(new ReplacementEntry(range, supp, nextSeq()));
        return this;
    }

    public EditBag replace(Position a, Position b, Supplier<String> supp) {
        return replace(PositionRange.create(notNull("a", a), notNull("b", b), doc), supp);
    }

    public EditBag delete(IntRange<? extends IntRange<?>> range) throws BadLocationException {
        return delete(positionFactory.range(notNull("range", range)));
    }

    public EditBag delete(Iterable<? extends PositionRange> ranges) {
        for (PositionRange r : notNull("ranges", ranges)) {
            delete(r);
        }
        return this;
    }

    public EditBag delete(PositionRange range) {
        notNull("range", range);
        assert range.document() == document();
        entries.add(new DeletionEntry(range, nextSeq()));
        return this;
    }

    public EditBag delete(Position a, Position b) {
        PositionRange range = PositionRange.create(notNull("a", a),
                notNull("b", b), doc);
        entries.add(new DeletionEntry(range, nextSeq()));
        return this;
    }

    public EditBag insert(int pos, String insertionText) throws BadLocationException {
        return insert(pos, StringSupplier.of(notNull("insertionText",
                insertionText)));
    }

    public EditBag insert(int pos, Supplier<String> insertionText) throws BadLocationException {
        Position p = positionFactory.createPosition(pos, Position.Bias.Forward);
        return insert(p, insertionText);
    }

    public EditBag insert(int pos, Position.Bias bias, String insertionText) throws BadLocationException {
        return insert(pos, bias, StringSupplier.of(notNull("insertionText", insertionText)));
    }

    public EditBag insert(int pos, Position.Bias bias, Supplier<String> insertionText) throws BadLocationException {
        Position p = positionFactory.createPosition(pos, notNull("bias", bias));
        return insert(p, insertionText);
    }

    public EditBag insert(Position pos, String insertionText) {
        return insert(pos, StringSupplier.of(insertionText));
    }

    public EditBag insert(Position pos, Supplier<String> insertionText) {
        entries.add(new InsertionEntry(positionFactory, notNull("pos", pos),
                notNull("insertionText", insertionText), nextSeq()));
        return this;
    }

    public EditBag modify(IntRange<? extends IntRange> range, DocumentModifier run) throws BadLocationException {
        return modify(positionFactory.range(notNull("range", range)), notNull("run", run));
    }

    public EditBag modify(PositionRange range, DocumentModifier run) {
        assert range.document() == document();
        entries.add(new AdhocStartEndEntry(notNull("range",range),
                notNull("run", run), nextSeq()));
        return this;
    }

    public EditBag modify(Position requiredStart, DocumentModifier run) {
        entries.add(new AdhocEntry(notNull("requiredStart", requiredStart),
                notNull("run", run), nextSeq()));
        return this;
    }

    /**
     * Add an adhoc modification operation; note that if both a start and end
     * are passed, and the end is less than the start, the values will be
     * reversed at the time the modifier is called.
     *
     * @param requiredStart The start position, used for sequencing operations
     * to minimize bookkeeping
     * @param optionalEnd An optional end position - this is a good idea
     * @param run A thing that will applyToDocument the document somehow, or
     * wants to interact with it while locked in whatever manner the
     * DocumentOperator that drives it does
     * @return this
     */
    public EditBag modify(Position requiredStart, Position optionalEnd, DocumentModifier run) {
        if (optionalEnd != null) {
            PositionRange range = PositionRange.create(notNull("requiredStart", 
                    requiredStart), optionalEnd, doc);
            entries.add(new AdhocStartEndEntry(range, run, nextSeq()));
            return this;
        } else {
            return modify(requiredStart, run);
        }
    }

    private static final class AdhocEntry implements EditBagEntry {

        final Position position;
        final DocumentModifier run;
        final int seq;

        AdhocEntry(Position requiredStart, DocumentModifier run, int seq) {
            this.position = requiredStart;
            this.run = run;
            this.seq = seq;
        }

        public int sequence() {
            return seq;
        }

        @Override
        public void apply(StringBuilder sb) throws Exception {
            run.applyToStringBuilder(position.getOffset(), -1, sb);
        }

        @Override
        public void accept(StyledDocument obj) throws BadLocationException {
            run.applyToDocument(position.getOffset(), -1, obj);
        }

        @Override
        public int start() {
            return position.getOffset();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Position startPosition() {
            return position;
        }

        @Override
        public Position endPosition() {
            return position;
        }

        @Override
        public ChangeKind kind() {
            return ChangeKind.CUSTOM;
        }
    }

    private static final class AdhocStartEndEntry implements EditBagEntry {

        final DocumentModifier run;
        final int seq;
        final PositionRange range;

        AdhocStartEndEntry(PositionRange range, DocumentModifier run, int seq) {
            this.range = range;
            this.run = run;
            this.seq = seq;
        }

        public int sequence() {
            return seq;
        }

        @Override
        public void apply(StringBuilder sb) throws Exception {
            run.applyToStringBuilder(range.start(), range.end(), sb);
        }

        @Override
        public void accept(StyledDocument obj) throws BadLocationException {
            run.applyToDocument(range.start(), range.end(), obj);
        }

        @Override
        public int start() {
            return range.start();
        }

        @Override
        public int size() {
            return range.size();
        }

        @Override
        public int end() {
            return range.end();
        }

        @Override
        public Position startPosition() {
            return range.startPosition();
        }

        @Override
        public Position endPosition() {
            return range.endPosition();
        }

        @Override
        public ChangeKind kind() {
            return ChangeKind.CUSTOM;
        }
    }

    private static final class InsertionEntry implements EditBagEntry {

        final PositionFactory positions;

        final Position start;

        final Supplier<String> insertionText;
        final int seq;

        public InsertionEntry(PositionFactory positions, Position start, Supplier<String> insertionText, int seq) {
            this.positions = positions;
            this.start = start;
            this.insertionText = insertionText;
            this.seq = seq;
        }

        @Override
        public int sequence() {
            return seq;
        }

        @Override
        public int start() {
            return start.getOffset();
        }

        @Override
        public int size() {
            return insertionText.get().length();
        }

        @Override
        public Position startPosition() {
            return start;
        }

        @Override
        public Position endPosition() {
            int len = insertionText.get().length();
            try {
                return positions.createPosition(start() + len, Position.Bias.Backward);
            } catch (BadLocationException ex) {
                return Exceptions.chuck(ex);
            }
        }

        @Override
        public String toString() {
            String s = insertionText.get();
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

        @Override
        public ChangeKind kind() {
            return ChangeKind.INSERTION;
        }
    }

    private static final class DeletionEntry implements EditBagEntry {

        final PositionRange range;
        final int seq;

        public DeletionEntry(PositionRange range, int seq) {
            this.range = range;
            this.seq = seq;
        }

        @Override
        public int sequence() {
            return seq;
        }

        @Override
        public boolean isDeletion() {
            return true;
        }

        @Override
        public Position startPosition() {
            return range.startPosition();
        }

        @Override
        public Position endPosition() {
            return range.endPosition();
        }

        @Override
        public int start() {
            return range.start();
        }

        @Override
        public int size() {
            return range.size();
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
            int end = end();
            int start = start();
            if (end > start) {
                sb.delete(start, end);
            }
        }

        @Override
        public ChangeKind kind() {
            return ChangeKind.DELETION;
        }
    }

    private static final class ReplacementEntry implements EditBagEntry {

        final PositionRange range;
        final Supplier<String> replacementText;
        final int seq;

        public ReplacementEntry(PositionRange range,
                Supplier<String> replacementText, int seq) {
            this.range = range;
            this.replacementText = replacementText;
            this.seq = seq;
        }

        @Override
        public int sequence() {
            return seq;
        }

        @Override
        public Position startPosition() {
            return range.startPosition();
        }

        @Override
        public Position endPosition() {
            return range.endPosition();
        }

        @Override
        public int start() {
            return range.start();
        }

        @Override
        public int size() {
            return range.size();
        }

        public String toString() {
            String s = replacementText.get();
            StringBuilder sb = new StringBuilder("Replace ")
                    .append(s.length()).append(" characters at ")
                    .append(start()).append(':').append(end()).append(" (")
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

        @Override
        public ChangeKind kind() {
            return ChangeKind.REPLACEMENT;
        }
    }

    static final class StringSupplier implements Supplier<String>, Wrapper<String> {

        // For loggability
        private final String text;

        public static Supplier<String> of(String txt) {
            return new StringSupplier(txt);
        }

        public StringSupplier(String text) {
            this.text = text;
        }

        @Override
        public String get() {
            return text;
        }

        public String toString() {
            return text;
        }

        @Override
        public String wrapped() {
            return text;
        }
    }
}
