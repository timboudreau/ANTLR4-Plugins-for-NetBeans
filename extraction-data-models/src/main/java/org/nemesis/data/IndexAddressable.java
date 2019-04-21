package org.nemesis.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.nemesis.data.graph.hetero.BitSetHeteroObjectGraph;
import org.nemesis.data.graph.IntGraph;
import org.nemesis.data.graph.bits.MutableBits;
import org.nemesis.misc.utils.function.IntBiConsumer;

/**
 * Base interface for collections of that can be indexed and represent a range
 * of characters being parsed. Most subclasses have strict rules about the order
 * elements may be added in.  This is the base interface for several lightweight,
 * highly efficient data stores for information extracted during a parse.
 *
 * @author Tim Boudreau
 */
public interface IndexAddressable<T extends IndexAddressable.IndexAddressableItem> extends Indexed<T> {

    /**
     * Get the element - the most specific in element in the case of nested
     * elements - at this character position.
     *
     * @param position A character position
     * @return An element whose start() &gt;= position and end() &lt; position,
     * or null if no such element exists in this collection
     */
    T at(int position);

    /**
     * Determine if the passed instance of IndexAddressable could have been
     * created by this collection.
     *
     * @param item
     * @return
     */
    boolean isChildType(IndexAddressableItem item);

    /**
     * Determine if this collection is empty.
     *
     * @return true if it is empty
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Cross reference two heterogenous IndexAddressables, and build a graph of
     * container and containee for elements which sit entirely within the bounds
     * of an element in another collection.
     * <p>
     * Note that if two elements have exactly the same bounds, both will be
     * recorded as containing each other.
     * </p>
     *
     * @param <TI> One elememt type
     * @param <RI> The other element type
     * @param <T> One collection type
     * @param <R> The other collection type
     * @param a The first collection
     * @param b The second collection
     * @return A graph
     */
    static <TI extends IndexAddressable.IndexAddressableItem, RI extends IndexAddressable.IndexAddressableItem, T extends IndexAddressable<TI>, R extends IndexAddressable<RI>>
            BitSetHeteroObjectGraph<TI, RI, T, R>
            crossReference(T a, R b) {
        // build a graph from arrays of BitMaps, where:
        // The first a.size() indices represent relationships of items in the
        // first collection
        // The indices from a.size() to a.size() + b.size() represent elements
        // from the second collection
        // We create two BitSet[a.size() + b.size()] with a.size() + b.size()
        // elements arrays - one BitSet for outbound edges, and one BitSet for
        // inbound edges for each element
        // That gives us an integer graph; we then wrap it in a
        // BitSetHeteroObjectGraph which looks up elements in the passed
        // IndexAddressables, and uses Set instances which wrap a BitSet
        // to provide a usable API for interacting with the graph
        int aSize = a.size();
        int bSize = b.size();
        int treeSize = aSize + bSize;
        // A factory to ensure we always compute offsets with the same logic
        IntUnaryOperator foreignItemOffset = o -> {
            return aSize + o;
        };
        // outbound edge sets
        MutableBits[] sets = new MutableBits[treeSize];
        // inbound edge sets
        MutableBits[] reverseSets = new MutableBits[treeSize];
        for (int i = 0; i < treeSize; i++) {
            sets[i] = MutableBits.create(treeSize);
            reverseSets[i] = MutableBits.create(treeSize);
        }
        // Takes care of setting the relationship in both BitSet arrays
        IntBiConsumer setOne = (contained, container) -> {
            MutableBits outbound = sets[contained];
            outbound.set(container);
            MutableBits inbound = reverseSets[container];
            inbound.set(contained);
        };
        // Iterate the first collection's items
        for (int i = 0; i < aSize; i++) {
            TI item = a.forIndex(i);
            RI foreignItem = b.at(item.start());
            // Check for either containing the other
            if (foreignItem != null && item.contains(foreignItem)) {
//                System.out.println("cross ref " + item + " -> " + foreignItem);
                int foreignOffset = foreignItemOffset.applyAsInt(foreignItem.index());
                setOne.accept(foreignItem.index(), foreignOffset);
            } else if (foreignItem != null && foreignItem.contains(item)) {
                int foreignOffset = foreignItemOffset.applyAsInt(foreignItem.index());
                setOne.accept(foreignOffset, foreignItem.index());
            }
        }
        // Iterate the second collection's items
        for (int i = aSize; i < treeSize; i++) {
            // XXX could build a BitSet of those indices already tested by
            // the preceding loop and only test those unset
            int localOffset = i - aSize;
            RI foreignItem = b.forIndex(localOffset);
            TI item = a.at(foreignItem.start());
            if (item != null && foreignItem.contains(item)) {
                int foreignOffset = foreignItemOffset.applyAsInt(foreignItem.index());
                setOne.accept(foreignOffset, item.index());
            } else if (item != null && item.contains(foreignItem)) {
                int foreignOffset = foreignItemOffset.applyAsInt(foreignItem.index());
                setOne.accept(item.index(), foreignOffset);
            }
        }
        IntGraph tree = IntGraph.create(sets, reverseSets);
        BitSetHeteroObjectGraph<TI, RI, T, R> result = BitSetHeteroObjectGraph.create(tree, a, b);
        return result;
    }

    /**
     * Cross reference this collection with another, building a graph of
     * elements in one which contain elements in the other.
     *
     * @param <RI>
     * @param <R>
     * @param other The other collection
     * @return A graph
     */
    @SuppressWarnings("unchecked")
    default <RI extends IndexAddressableItem, R extends IndexAddressable<RI>> BitSetHeteroObjectGraph<T, RI, ?, R> crossReference(R other) {
        return crossReference(this, other);
    }


    public interface NamedIndexAddressable<T extends IndexAddressableItem & Named> extends IndexAddressable<T> {

        Iterator<T> byPositionIterator();

        default Iterator<T> byNameIterator() {
            // Inefficient, and types which implement an index should
            // override
            List<T> objs = new ArrayList<>(size());
            Iterator<T> it = byPositionIterator();
            while (it.hasNext()) {
                objs.add(it.next());
            }
            Collections.sort(objs, (a, b) -> {
                return a.name().compareTo(b.name());
            });
            return objs.iterator();
        }
    }

    /**
     * One item in an index addressable collection.
     */
    public interface IndexAddressableItem extends Comparable<IndexAddressableItem> {

        /**
         * Determine if this item occurs after (start() &gt;= item.end())
         * another item.
         *
         * @param item Another item
         * @return
         */
        default boolean isAfter(IndexAddressableItem item) {
            return start() >= item.end();
        }

        /**
         * Determine if this item occurs before (end() &lt;= item.start())
         * another item.
         *
         * @param item
         * @return
         */
        default boolean isBefore(IndexAddressableItem item) {
            return end() <= item.start();
        }

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

        /**
         * Determine if this region contains the passed region.
         *
         * @param start
         * @param end
         * @return
         */
        default boolean contains(int start, int end) {
            assert end > start : "Start <= end: " + start + ":" + end;
            return containsPosition(start) && end >= start && end <= end();
        }

        /**
         * Determine if the bounds of the passed item are equal to or contained
         * within the bounds of this one.
         *
         * @param item An item which may be from another collection than this
         * one's owner
         * @return true if it is contained or has equal bounds
         */
        default boolean contains(IndexAddressableItem item) {
            return contains(item.start(), item.end());
        }

        default boolean offsetsEqual(IndexAddressableItem item) {
            return item.start() == start() && item.end() == end();
        }

        /**
         * Sorts items by their start and end position.
         *
         * @param o Another object
         * @return the sort
         */
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
