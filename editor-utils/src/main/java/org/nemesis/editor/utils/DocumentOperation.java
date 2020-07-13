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

import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.utils.DocumentOperator.BuiltInDocumentOperations;

/**
 * Performs operations on a single document. Single-use. In general, do not call
 * when holding any foreign locks unless you are very, very sure that nothing
 * that might respond to changes made in the document could acquire them
 * out-of-order. When in doubt, use a new requets processor thread or
 * EventQueue.invokeLater().
 *
 * @param <T> The return type of the operator that will be passed in.
 * @param <E> An exception type thrown by the operation
 */
public final class DocumentOperation<T, E extends Exception> {

    private final DocumentOperator.BleFunction<T, E> runner;
    private String names;

    DocumentOperation(StyledDocument doc, Set<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> props) {
//        this(DocumentOperator.runner(doc, props.toArray(new DocumentOperator.BuiltInDocumentOperations[props.size()])));
        this(DocumentOperator.runner(doc, sorted(props)));
        names = Strings.join(":", props);
    }

    static List<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> sorted(Set<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> props) {
        boolean allProps = true;
        // The price of allowing ad-hoc functions in here, not just instances of BuiltInDocumentOperations -
        // We MUST sort the ones that are BuiltInDocumentOperations instances into their defined order, so we
        // acquire locks before we call things that require the locks to be locked, and do
        // it all in the right order.
        //
        // So, since we can have random functions included in the list, we DO want to
        // preserve the run-order of THOSE relative to whatever they run after, and sort
        // those groups so if there are two in a row, that ordering is preserved, but
        // otherwise they run in the order of whatever BuiltInDocumentOperations was added next
        //
        // First test if we have the simple case - all BuiltInDocumentOperations intsnaces, in which
        // case we don't have to jump through hoops to sort
        for (Function<StyledDocument, DocumentPreAndPostProcessor> item : props) {
            if (!(item instanceof BuiltInDocumentOperations)) {
                allProps = false;
                break;
            }
        }
        if (allProps) {
            // Simple case, sort by enum order and done
            List<BuiltInDocumentOperations> l = new ArrayList<>();
            for (Function<StyledDocument, DocumentPreAndPostProcessor> item : props) {
                l.add((BuiltInDocumentOperations) item);
            }
            Collections.sort(l);
            return l;
        }
        // Keep the original sort order for the two-in-a-row case
        List<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> originalSort = new ArrayList<>(props);

        // Map the immediately subsequent BuiltInDocumentOperations instance to any rnadom function instances
        // that precede it
        Map<Function<StyledDocument, DocumentPreAndPostProcessor>, BuiltInDocumentOperations> preceders = new HashMap<>(props.size());
        // A pool of items we have seen that need to be mapped to the next props
        List<Function<StyledDocument, DocumentPreAndPostProcessor>> current = new ArrayList<>(props.size());

        List<SortWrapper> stubs = new ArrayList<>(props.size());
        for (Function<StyledDocument, DocumentPreAndPostProcessor> item : props) {
            if (item instanceof BuiltInDocumentOperations) {
                BuiltInDocumentOperations p = (BuiltInDocumentOperations) item;
                for (Function<StyledDocument, DocumentPreAndPostProcessor> prec : current) {
                    preceders.put(prec, p);
                }
                current.clear();
            } else {
                current.add(item);
            }
            // The list "current" will wind up containing any trailing function instances
            // that are not followed by ANY props instance
            SortWrapper stub = new SortWrapper(item, preceders, current, originalSort);
            stubs.add(stub);
        }
        // Sort to get our final order
        Collections.sort(stubs);
        // Pull the now-sorted instances back out of the sorted wrappers
        List<Function<StyledDocument, DocumentPreAndPostProcessor>> result = new ArrayList<>(props);
        for (SortWrapper stub : stubs) {
            result.add(stub.target);
        }
        return result;
    }

    /**
     * Wrapper for functions which will use natural sort for those that are
     * BuiltInDocumentOperations instances, and sort the others in the sequence
     * they were added relative to the next.
     */
    private static class SortWrapper implements Comparable<SortWrapper> {

        private final Function<StyledDocument, DocumentPreAndPostProcessor> target;
        private final Map<Function<StyledDocument, DocumentPreAndPostProcessor>, BuiltInDocumentOperations> propFollowingNonPropItems;
        private final List<Function<StyledDocument, DocumentPreAndPostProcessor>> tail;
        private final List<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> originalSort;

        private SortWrapper(Function<StyledDocument, DocumentPreAndPostProcessor> item,
                Map<Function<StyledDocument, DocumentPreAndPostProcessor>, BuiltInDocumentOperations> propFollowingNonPropItems,
                List<Function<StyledDocument, DocumentPreAndPostProcessor>> tail,
                List<? extends Function<StyledDocument, DocumentPreAndPostProcessor>> originalSort) {
            this.target = item;
            this.propFollowingNonPropItems = propFollowingNonPropItems;
            this.tail = tail;
            this.originalSort = originalSort;
        }

        private BuiltInDocumentOperations proxyProps() {
            if (target instanceof BuiltInDocumentOperations) {
                return (BuiltInDocumentOperations) target;
            } else if (target instanceof DocumentOperator.PreserveCaret) {
                // This is just PRESERVE_CARET_POSITION but with a custom doodad to compute
                // the revised caret position, so it should sort in the same
                // order as that would
                return BuiltInDocumentOperations.PRESERVE_CARET_POSITION;
            }
            // Return nothing
            return null;
        }

        @Override
        public int compareTo(SortWrapper o) {
            BuiltInDocumentOperations pa = proxyProps();
            BuiltInDocumentOperations pb = o.proxyProps();
            if (pa != null && pb != null) {
                // Two props items - easy
                return pa.compareTo(pb);
            } else if (pa == null && pb == null) {
                // If these are tail items, preserve their original order reading
                // the tail list
                if (tail.contains(target) && tail.contains(o.target)) {
                    return Integer.compare(tail.indexOf(target), tail.indexOf(o.target));
                }

                pa = propFollowingNonPropItems.get(target);
                pb = propFollowingNonPropItems.get(o.target);
                if ((pa != null && pa == pb) || (pa == null && pb == null)) {
                    // Use the full original sort order - no other information,
                    // and these are both tail items
                    return Integer.compare(originalSort.indexOf(target), originalSort.indexOf(o.target));
                } else if (pa != null && pb != null) {
                    return pa.compareTo(pb);
                } else if (pa == null && pb != null) {
                    // We are in the tail, they are not, so we sort down
                    return 1;
                } else {
                    // They are in the tail and we are not, so we sort up
                    return -1;
                }
            } else if (pa == null && pb != null) {
                BuiltInDocumentOperations followingProp = propFollowingNonPropItems.get(target);
                if (followingProp == null) {
                    // we are in the tail and sort down
                    return 1;
                }
                return followingProp.compareTo(pb);
            } else /* if (pa != null && pb == null) */ {
                BuiltInDocumentOperations followingProp = propFollowingNonPropItems.get(o.target);
                if (followingProp == null) {
                    // the other one is in the tail, we are not, so we sort up
                    return -1;
                }
                return pa.compareTo(followingProp);
            }
        }
    }

    private DocumentOperation(DocumentOperator.BleFunction<T, E> runner) {
        this.runner = runner;
    }

    /**
     * Perform the operation, returning any result.
     *
     * @param documentProcessor
     *
     * @return
     *
     * @throws BadLocationException
     * @throws E
     */
    public T operate(DocumentProcessor<T, E> documentProcessor) throws BadLocationException, E {
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName("Doc processor " + names + " (was " + oldName + ")");
        try {
            return runner.apply(documentProcessor);
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }

    /**
     * Run the passed runnable with all the various locks and settings
     * configured; exceptions are logged with level SEVERE which will generate a
     * user notification.
     *
     * @param r A runnable
     */
    public void run(Runnable r) {
        DocumentProcessor<T, E> wrapped = new DocumentProcessor<T, E>() {
            @Override
            public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                r.run();
                return null;
            }

            public String toString() {
                return "Runnable(" + r + ")";
            }
        };
        try {
            operate(wrapped);
        } catch (Exception e) {
            DocumentOperator.LOG.log(Level.SEVERE, r.toString(), e);
        }
    }

    public void runOp(BadLocationRunnable run) {
        DocumentProcessor<T, E> wrapped = new DocumentProcessor<T, E>() {
            @Override
            public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                run.run();
                return null;
            }

            public String toString() {
                return "Runnable(" + run + ")";
            }
        };
        try {
            operate(wrapped);
        } catch (Exception e) {
            DocumentOperator.LOG.log(Level.SEVERE, run.toString(), e);
        }
    }


    public void run(Consumer<DocumentOperationContext> r) {
        DocumentProcessor<T, E> wrapped = new DocumentProcessor<T, E>() {
            @Override
            public T get(DocumentOperationContext ctx) throws E, BadLocationException {
                r.accept(ctx);
                return null;
            }

            public String toString() {
                return "Consumer(" + r + ")";
            }
        };
        try {
            operate(wrapped);
        } catch (Exception e) {
            DocumentOperator.LOG.log(Level.SEVERE, r.toString(), e);
        }
    }
}
