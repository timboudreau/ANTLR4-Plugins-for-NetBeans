package org.nemesis.antlr.v4.netbeans.v8.grammar.file.experimental.nio.lock;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 *
 * @author Tim Boudreau
 */
public class SubLock {

    protected final BitSet locked;
    protected final SubLock parent;
    protected final AtomicReferenceArray<SubLock> children = new AtomicReferenceArray<>(2);

    SubLock(int start, int stop, boolean initiallyLocked, SubLock parent) {
        locked = new BitSet(stop + 1);
        this.parent = parent;
        if (initiallyLocked) {
            for (int i = start; i < stop; i++) {
                locked.set(i);
            }
        }
    }

    public void close() {
        locked.clear();
    }
}
