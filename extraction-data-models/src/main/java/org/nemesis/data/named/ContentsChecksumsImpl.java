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
package org.nemesis.data.named;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.graph.BitSetUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.nemesis.data.IndexAddressable;

/**
 *
 * @author Tim Boudreau
 */
final class ContentsChecksumsImpl<C extends IndexAddressable<I>, I extends IndexAddressable.IndexAddressableItem> extends ContentsChecksums<I> {

    private final long[] values;
    private final C regions;
    private DuplicateIndex index;

    ContentsChecksumsImpl(long[] values, C regions) {
        this.values = values;
        this.regions = regions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(11 + (values.length * 5));
        sb.append("Checksums(");
        for (int i = 0; i < values.length; i++) {
            sb.append(i).append(':').append(values[i]);
            if (i != values.length - 1) {
                sb.append(',');
            }
        }
//        sb.append(" index:").append(duplicateIndex()); // XXX deleteme
        return sb.append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof ContentsChecksumsImpl) {
            return Arrays.equals(((ContentsChecksumsImpl) o).values, values);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public int visitItemsWithDuplicatesAt(int pos, Consumer<? super I> c) {
        I item = regions.at(pos);
        if (item != null) {
            long sum = values[item.index()];
            DuplicateIndex ix = duplicateIndex();
            if (!ix.isEmpty()) {
                int[] result = new int[1];
                ix.visitMatches(sum, i -> {
                    c.accept(regions.forIndex(i));
                    result[0]++;
                });
                return result[0];
            }
        }
        return 0;
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isEmpty() {
        return values.length == 0;
    }

    @Override
    public boolean hasDuplicates() {
        return !duplicateIndex().isEmpty();
    }

    public long sum(int index) {
        return index >= 0 && index < values.length ? values[index] : 0;
    }

    public int[] itemsWithSum(long sum) {
        return duplicateIndex().matches(sum);
    }

    @Override
    public void visitItemsWithSum(long sum, IntConsumer c) {
        index.visitMatches(sum, c);
    }

    @Override
    public void visitRegionGroups(Consumer<? super List<? extends I>> c) {
        DuplicateIndex dupIx = duplicateIndex();
        for (int i = 0; i < dupIx.size(); i++) {
            BitSet bits = dupIx.bits[i];
            List<I> list = new ArrayList<>(bits.cardinality());
            BitSetUtils.forEach(bits, ix -> {
                list.add(regions.forIndex(ix));
            });
            c.accept(list);
        }
    }
    @Override
    public void visitRegionGroupsThrowing(ThrowingConsumer<? super List<? extends I>> c) throws Exception {
        DuplicateIndex dupIx = duplicateIndex();
        for (int i = 0; i < dupIx.size(); i++) {
            BitSet bits = dupIx.bits[i];
            List<I> list = new ArrayList<>(bits.cardinality());
            BitSetUtils.forEach(bits, ix -> {
                list.add(regions.forIndex(ix));
            });
            c.accept(list);
        }
    }

    @Override
    public void visitRegionsWithSameSum(I region, Consumer<I> c) {
        if (regions.isChildType(region)) {
            long sum = sum(region);
            if (sum != Long.MIN_VALUE) {
                duplicateIndex().visitMatches(sum, index -> {
                    c.accept(regions.forIndex(index));
                });
            }
        }
    }

    @Override
    public long sum(I region) {
        if (regions.isChildType(region)) {
            int ix;
            if (region instanceof NamedSemanticRegionReference<?>) {
                ix = ((NamedSemanticRegionReference<?>) region).referencing().index();
            } else {
                ix = region.index();
            }
            return sum(ix);
        }
        return Long.MIN_VALUE;
    }

    private DuplicateIndex duplicateIndex() {
        return index == null ? index = new DuplicateIndex() : index;
    }

    private class DuplicateIndex {

        final long[] sorted;
        private final BitSet[] bits;

        DuplicateIndex() {
            // XXX need a bi-sort ala IntMap.
            Map<Long, BitSet> m = new HashMap<>();
            for (int i = 0; i < values.length; i++) {
                BitSet e = m.get(values[i]);
                if (e == null) {
                    e = new BitSet(Math.max(2, values.length / 3));
                    m.put(values[i], e);
                } else {
                    e.set(i);
                }
            }
            List<Map.Entry<Long, BitSet>> all = new LinkedList<>(m.entrySet());
            Collections.sort(all, (a, b) -> {
                return a.getKey().compareTo(b.getKey());
            });
            for (Iterator<Map.Entry<Long, BitSet>> it = all.iterator(); it.hasNext();) {
                if (it.next().getValue().cardinality() < 2) {
                    it.remove();
                }
            }
            sorted = new long[all.size()];
            bits = new BitSet[all.size()];
            for (int i = 0; i < bits.length; i++) {
                Map.Entry<Long, BitSet> e  = all.get(i);
                sorted[i] = e.getKey();
                bits[i] = e.getValue();
                assert bits[i] != null : "Null value in " + all;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sorted.length; i++) {
                sb.append("{").append(i).append(": ").append(bits[i]).append("}");
            }
            return sb.toString();
        }

        public int size() {
            return sorted.length;
        }

        public boolean isEmpty() {
            return sorted.length == 0;
        }

        public void visitIndices(IntConsumer allConsumer) {
            for (BitSet bs : bits) {
                BitSetUtils.forEach(bs, allConsumer);
            }
        }

        void visitMatches(long l, IntConsumer c) {
            int ix = Arrays.binarySearch(sorted, l);
            if (ix >= 0) {
                BitSetUtils.forEach(bits[ix], c);
            }
        }

        int[] matches(long l) {
            int ix = Arrays.binarySearch(sorted, l);
            if (ix >= 0) {
                int[] result = new int[bits[ix].cardinality()];
                int[] curr = new int[0];
                BitSetUtils.forEach(bits[ix], (int index) -> {
                    result[curr[0]++] = index;
                });
                return result;
            } else {
                return new int[0];
            }
        }
    }
}
