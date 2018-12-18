package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.BitSet;

/**
 *
 * @author Tim Boudreau
 */
interface IndexAddressable<T extends IndexAddressable.IndexAddressableItem> extends Indexed<T> {

    T at(int position);

    boolean isChildType(IndexAddressableItem item);

    static <TI extends IndexAddressable.IndexAddressableItem, 
                    RI extends IndexAddressable.IndexAddressableItem,
                    T extends IndexAddressable<TI>, R extends IndexAddressable<RI>>
            BitSetHeteroObjectGraph<TI, RI, T, R>
            crossReference(T a, R other) {
        int mySize = a.size();
        int otherSize = other.size();
        int treeSize = mySize + otherSize;
        IntIntFunction foreignItemOffset = o -> {
            return mySize + o;
        };
        BitSet[] sets = new BitSet[treeSize];
        BitSet[] reverseSets = new BitSet[treeSize];
        for (int i = 0; i < treeSize; i++) {
            sets[i] = new BitSet(treeSize);
            reverseSets[i] = new BitSet(treeSize);
        }
        BiIntConsumer setOne = (contained, container) -> {
            BitSet outbound = sets[contained];
            outbound.set(container);
            BitSet inbound = reverseSets[container];
            inbound.set(contained);
        };
        for (int i = 0; i < mySize; i++) {
            TI item = a.forIndex(i);
            RI foreignItem = other.at(item.start());
            if (foreignItem != null && item.contains(foreignItem)) {
                System.out.println("cross ref " + item + " -> " + foreignItem);
                int foreignOffset = foreignItemOffset.apply(foreignItem.index());
                setOne.set(foreignItem.index(), foreignOffset);
            } else {
                System.out.println("A: no match " + item + " and " + foreignItem);
                if (item != null && foreignItem != null) {
                    System.out.println("CONTAINS: " + item.contains(foreignItem) + " for " + item.start() + ":" + item.end() + " contains " + foreignItem.start() + ":" + foreignItem.end());
                } else if (foreignItem == null) {
                    System.out.println("No item at " + item.start() + " in " + other);
                }
            }
        }
        for (int i = mySize; i < treeSize; i++) {
            int localOffset = i - mySize;
            RI foreignItem = other.forIndex(localOffset);
            TI item = a.at(foreignItem.start());
            if (item != null && foreignItem.contains(item)) {
                int foreignOffset = foreignItemOffset.apply(foreignItem.index());
                setOne.set(foreignOffset, item.index());
            } else {
                System.out.println("B: no match " + item + " and " + foreignItem);
                if (item != null && foreignItem != null) {
                    System.out.println("CONTAINS: " + item.contains(foreignItem) + " for " + item.start() + ":" + item.end() + " contains " + foreignItem.start() + ":" + foreignItem.end());
                }
            }
        }

        BitSetTree tree = new BitSetTree(sets, reverseSets);
        System.out.println("CR TREE " + tree);
        return new BitSetHeteroObjectGraph<>(tree, a, other);
    }

    @SuppressWarnings("unchecked")
    default <RI extends IndexAddressableItem, R extends IndexAddressable<RI>> BitSetHeteroObjectGraph<T, RI, ?, R> crossReference(R other) {
        return crossReference(this, other);
    }

    interface IntIntFunction {

        int apply(int v);
    }

    interface BiIntConsumer {

        void set(int container, int containedBy);
    }

    public interface IndexAddressableItem extends Comparable<IndexAddressableItem> {

        /**
         * Get the start offset, inclusive.
         *
         * @return The start offset
         */
        int start();

        /**
         * Get the end offset, exclusive.
         *
         * @return 23
         */
        int end();

        /**
         * Get the final position which is within this NamedSemanticRegions -
         * equals to end()-1.
         *
         * @return
         */
        default int stop() {
            return end() - 1;
        }

        /**
         * Get the index of this region within the NamedSemanticRegions which
         * owns it. The meaning of this number is unspecified other than that if
         * you pass this number to fetch a region from the parent by index, you
         * will get an item which equals() this one.
         *
         * @return
         */
        int index();

        /**
         * Get the length of this region - end() - start().
         *
         * @return The length
         */
        default int length() {
            return end() - start();
        }

        /**
         * Determine if this region contains a given position - equivalent to
         * <code>pos &gt;= start() && pos &lt; end()</code>.
         *
         * @param pos A position
         * @return true if it is contained
         */
        default boolean containsPosition(int pos) {
            return pos >= start() && pos < end();
        }

        default boolean contains(int start, int end) {
            return containsPosition(start) && end >= start && end <= end();
        }

        default boolean contains(IndexAddressableItem item) {
            return contains(item.start(), item.end());
        }

        @Override
        public default int compareTo(IndexAddressableItem o) {
            int as = start();
            int bs = o.start();
            int result = as > bs ? 1 : as == bs ? 0 : -1;
            if (result == 0) {
                int ae = end();
                int be = end();
                result = ae > be ? 1 : ae == be ? 0 : -1;
            }
            return result;
        }
    }
}
