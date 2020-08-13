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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.nemesis.data.IndexAddressable;
import org.nemesis.data.IndexAddressable.IndexAddressableItem;

/**
 * Duplicate-region tracking via hashing; a ContentsChecksums allows you to find
 * those regions which are duplicates according to the hashing function used.
 * Note we are using 64-bit hashes - collisions are unlikely but possible, so
 * check that the tokens you collected really match before, say, suggesting to
 * consolidate two matching regions based on what you find here.
 *
 * @author Tim Boudreau
 */
public abstract class ContentsChecksums<I extends IndexAddressable.IndexAddressableItem> {

//    @HotSpotIntrinsicCandidate // JDK9
    ContentsChecksums() {
    }

    public static Builder builder() {
        return builder(20);
    }

    public static Builder builder(int expectedSize) {
        return new Builder(expectedSize);
    }

    @SuppressWarnings("unchecked")
    public static <I extends IndexAddressableItem> ContentsChecksums<I> empty() {
        return (ContentsChecksums<I>) EmptyContentsChecksums.INSTANCE;
    }

    /**
     * Returns true if there are no sums.
     *
     * @return true if there are no sums
     */
    public abstract boolean isEmpty();

    /**
     * Returns true if this collection has sums, but none of them are
     * duplicates.
     *
     * @return True if there are duplicates
     */
    public abstract boolean hasDuplicates();

    /**
     * Visit all items which have the pssed sum.
     *
     * @param sum A sum
     * @param c A consumer
     */
    public abstract void visitItemsWithSum(long sum, IntConsumer c);

    /**
     * Visit all groups of regions which have the same sum - the consumer will
     * be passed a list of sets of regions where each list will contain at least
     * two distinct regions.
     *
     * @param c A consumer
     */
    public abstract void visitRegionGroups(Consumer<? super List<? extends I>> c);

    /**
     * Visit all groups of regions which have the same sum - the consumer will
     * be passed a list of sets of regions where each list will contain at least
     * two distinct regions.
     *
     * @param c A consumer
     */
    public abstract void visitRegionGroupsThrowing(ThrowingConsumer<? super List<? extends I>> c) throws Exception;

    /**
     * Visit all regions that have the same sum as the passed region (which must
     * be a child of the originating regions passed to the constructor).
     *
     * @param region A region
     * @param c A consumer
     */
    public abstract void visitRegionsWithSameSum(I region, Consumer<I> c);

    /**
     * Get the sum of one region, or 0 if not summed.
     *
     * @param region A region
     * @return A sum
     */
    public abstract long sum(I region);

    /**
     * Looks up the smallest region in the collection which contains the passed
     * position, and if one is present <i>and it has duplicates according to the
     * hashing function</i> visit all of those duplicates.
     *
     * @param pos A position within the originating document or thing from which
     * this regions was created
     * @param c A consumer
     * @return The number of items visited
     */
    public abstract int visitItemsWithDuplicatesAt(int pos, Consumer<? super I> c);

    /**
     * The number of sums in this collection.
     *
     * @return The size
     */
    public abstract int size();

    private static final class EmptyContentsChecksums extends ContentsChecksums<IndexAddressableItem> {

        static final EmptyContentsChecksums INSTANCE = new EmptyContentsChecksums();

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean hasDuplicates() {
            return false;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void visitItemsWithSum(long sum, IntConsumer c) {
            // do nothing
        }

        @Override
        public void visitRegionGroups(Consumer<? super List<? extends IndexAddressableItem>> c) {
            // do nothing
        }

        @Override
        public void visitRegionsWithSameSum(IndexAddressableItem region, Consumer<IndexAddressableItem> c) {
            c.accept(region);
        }

        @Override
        public long sum(IndexAddressableItem region) {
            return 0L;
        }

        @Override
        public String toString() {
            return "empty-checksums";
        }

        @Override
        public int visitItemsWithDuplicatesAt(int pos, Consumer<? super IndexAddressableItem> c) {
            return 0;
        }

        @Override
        public void visitRegionGroupsThrowing(ThrowingConsumer<? super List<? extends IndexAddressableItem>> c) throws Exception {
            // do nothing
        }
    }

    public static final class Builder {

        private long[] values;
        private int size = 0;
        private final int expectedSize;

        Builder(int expectedSize) {
            this.expectedSize = Math.max(20, expectedSize);
            values = new long[expectedSize];
        }

        private void maybeGrow() {
            if (size + 1 > values.length) {
                values = Arrays.copyOf(values, size + Math.max(40, expectedSize / 2));
            }
        }

        public void add(long sum) {
            maybeGrow();
            values[size++] = sum;
        }

        /**
         * Build a new checksums instance. The passed regions <i>must</i>
         * have <i>exactly</i> the same size() as the number of calls to
         * <code>add()</code> on this builder prior to calling
         * <code>build()</code>.
         *
         * @param <C> The collection type
         * @param <I> The item type
         * @param regions The collection
         * @return A checksums
         */
        public <C extends IndexAddressable<I>, I extends IndexAddressable.IndexAddressableItem>
                ContentsChecksums<I> build(C regions) {
            if (size == 0) {
                return ContentsChecksums.empty();
            }
            long[] vals = Arrays.copyOf(values, size);
            assert size == regions.size() : "Regions size mismatch: "
                    + regions.size() + " / " + size + " for " + regions;
            return new ContentsChecksumsImpl<>(vals, regions);
        }
    }
}
