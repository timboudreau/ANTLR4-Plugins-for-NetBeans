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
package org.nemesis.data.impl;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import static org.nemesis.data.impl.ArrayUtil.Bias.BACKWARD;
import static org.nemesis.data.impl.ArrayUtil.Bias.FORWARD;
import static org.nemesis.data.impl.ArrayUtil.Bias.NONE;

/**
 * Utilities for manipulating and searching in arrays and paris of start/end
 * arrays which may contain duplicate entries, or where the ends array may be
 * unsorted. Non-API.
 *
 * @author Tim Boudreau
 */
public final class ArrayUtil {

    @SuppressWarnings("unchecked")
    public static <T> T[] genericArray(Class<? super T> type, int size) {
        return (T[]) Array.newInstance(type, size);
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] ofType(T[] array, int newSize) {
        return genericArray((Class<T>) array.getClass().getComponentType(), newSize);
    }

    private static final class ArrayIUO implements IntUnaryOperator {

        private final int[] array;

        ArrayIUO(int[] array) {
            assert array != null;
            this.array = array;
        }

        @Override
        public int applyAsInt(int operand) {
            if (operand < 0 || operand >= array.length) {
                return -1;
            }
            return array[operand];
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.array);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ArrayIUO) {
                return Arrays.equals(((ArrayIUO) o).array, array);
            } else if (o instanceof IntUnaryOperator) {
                IntUnaryOperator iuo = (IntUnaryOperator) o;
                int[] others = new int[array.length];
                for (int i = 0; i < array.length; i++) {
                    others[i] = iuo.applyAsInt(i);
                }
                if (Arrays.equals(others, array)) {
                    return iuo.applyAsInt(array.length) == -1;
                }
            }
            return false;
        }
    }

    public static IntUnaryOperator iuo(int[] items) {
        return new ArrayIUO(items);
    }

    /**
     * Perform a binary search for the offset of a start/end pair which contains
     * the passed position.
     *
     * @param pos The position
     * @param searchFirst The first element to examine
     * @param searchLast The last element to examin
     * @param starts The array of start positions
     * @param ends Provider of end positions (which may be based on string
     * length rather than being an actual array)
     * @param size The size of the array, in the case there are trailing unused
     * entries
     * @return A position, or -1 if the request cannot be satisfied
     */
    public static int rangeBinarySearch(int pos, int searchFirst, int searchLast, int[] starts, IntUnaryOperator ends, int size) {
        return rangeBinarySearch(pos, searchFirst, searchLast, iuo(starts), ends, size);
    }

    public static int rangeBinarySearch(int pos, int searchFirst, int searchLast, IntUnaryOperator starts, IntUnaryOperator ends, int size) {
        int firstStart = starts.applyAsInt(searchFirst);
        int lastEnd = ends.applyAsInt(searchLast);
        int firstEnd = ends.applyAsInt(searchFirst);
        if (pos >= firstStart && pos < firstEnd) {
            return searchFirst;
        } else if (pos < firstStart) {
            return -1;
        } else if (pos == lastEnd - 1) {
            return searchLast;
        } else if (pos > lastEnd) {
            return -1;
        }
        int lastStart = starts.applyAsInt(searchLast);
        if (pos >= lastStart && pos < lastEnd) {
            return searchLast;
        } else if (pos == firstEnd - 1) {
            return searchFirst;
        }
        int mid = searchFirst + ((searchLast - searchFirst) / 2);
        if (mid == searchFirst || mid == searchLast) {
            return -1;
        }
        int midStart = starts.applyAsInt(mid);
        int midEnd = ends.applyAsInt(mid);
        if (pos >= midStart && pos < midEnd) {
            return mid;
        }
        if (pos >= midEnd) {
            return rangeBinarySearch(pos, mid + 1, searchLast - 1, starts, ends, size);
        } else {
            return rangeBinarySearch(pos, searchFirst + 1, mid - 1, starts, ends, size);
        }
    }

    /**
     * Perform a binary search in set of start/end pairs, to locate the index of
     * that pair (if any) where start &lt;= pos and end &gt; pos.
     *
     * @param pos The position
     * @param starts The array of start offsets, sorted
     * @param ends A provider of end positions
     * @param size The utilized size of the array, which may be smaller than the
     * array size
     * @return An integer offset or -1
     */
    public static int rangeBinarySearch(int pos, int[] starts, SizedArrayValueSupplier ends, int size) {
        return rangeBinarySearch(pos, iuo(starts), ends, size);
    }

    public static int rangeBinarySearch(int pos, IntUnaryOperator starts, SizedArrayValueSupplier ends, int size) {
        if (size == 0 || pos < starts.applyAsInt(0) || pos >= ends.get(size - 1)) {
            return -1;
        }
        return rangeBinarySearch(pos, 0, size - 1, starts, ends, size);
    }

    /**
     * Scan forward through an array of start/end pairs to find the last
     * <i>consecutive</i> position which contains the passed position.
     *
     * @param pos The position to seek
     * @param targetAndDepth a 2-element array of the current target position
     * and the current target position's nesting depth, under the terms of
     * SemanticRegions.
     *
     * @param starts The starts array
     * @param ends The ends array
     * @param size The effective size of the arrays for purposes of searching
     */
    public static void subsequentIndexContaining(int pos, int[] targetAndDepth, int[] starts, int[] ends, int size) {
        int result = targetAndDepth[0];
        for (int i = targetAndDepth[0] + 1; i < size; i++) {
            int st = starts[i];
            int en = ends[i];
            if (st > pos && en > pos) {
                break;
            }
            if (st <= pos && en > pos) {
                result = i;
                targetAndDepth[1]++;
            }
        }
        targetAndDepth[0] = result;
    }

    public static int lastOffsetLessThanOrEqualTo(int pos, int[] in, int first, int last, int size) {
        int lval = in[last];
        if (lval <= pos) {
            return last;
        }
        if (first == last) {
            return -1;
        }
        int fval = in[first];
        if (fval <= pos) {
            if (first < size && in[first + 1] > pos) {
                return first;
            }
            int mid = first + ((last - first) / 2);
            if (mid == first || mid == last) {
                return first;
            }
            int m = in[mid];
            if (m <= pos) {
                return lastOffsetLessThanOrEqualTo(pos, in, mid, last - 1, size);
            } else {
                return lastOffsetLessThanOrEqualTo(pos, in, first + 1, mid, size);
            }
        }
        return -1;
    }

    /**
     * In the case of a sorted array which may contain duplicate elements, find
     * the offset of the last element which is less than or equal to the passed
     * position.
     *
     * @param pos The position
     * @param in The array to examine
     * @param size The effective size of the array
     * @return an integer index, or -1 if none
     */
    public static int lastOffsetLessThanOrEqualTo(int pos, int[] in, int size) {
        return lastOffsetLessThanOrEqualTo(pos, in, size, NONE);
    }

    /**
     * In the case of a sorted array which may contain duplicate elements, find
     * the offset of the last element which is less than or equal to the passed
     * position.
     *
     * @param pos The position
     * @param in The array to examine
     * @param size The effective size of the array
     * @param bias Whether to return the first or last matching array offset
     * @return an integer index, or -1 if none
     */
    public static int lastOffsetLessThanOrEqualTo(int pos, int[] in, int size, Bias bias) {
        if (size == 0 || in.length == 0) {
            return -1;
        }
        int last = size - 1;
        int result = lastOffsetLessThanOrEqualTo(pos, in, 0, last, size);
        if (result >= 0) {
            switch (bias) {
                case FORWARD:
                    while (result < size - 1 && in[result + 1] <= pos) {
                        result++;
                    }
                    break;
                case BACKWARD:
                    while (result > 0 && in[result - 1] <= pos) {
                        result--;
                    }
                    break;
                case NONE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError(bias);
            }
        }
        return result;
    }

    /**
     * Perform a binary search (with some minimal linear scans) of a start/end
     * array pair, to find the last offset where the start/end pair for that
     * offset contains the passed position.
     *
     * @param pos The position to look for
     * @param starts An array of start offsets
     * @param ends An array of end offsets
     * @param size The effective size of the arrays
     * @param hasNesting If true, the ends array may be unsorted and have nested
     * items
     * @param firstUnsortedEndsEntry The last index of the ends array which is
     * definitely sorted, so faster binary search operations can be used for
     * queries which definitely pertain only to items below this index
     * @return a 2-element array containing the offset and the nesting depth at
     * that offset, as defined by SemanticRegions
     */
    public static int[] nestingBinarySearch(int pos, int[] starts, int[] ends, int size, boolean hasNesting, int firstUnsortedEndsEntry) {
        return nestingBinarySearch(pos, hasNesting, starts, ends, size, 0, firstUnsortedEndsEntry);
    }

    /**
     * Create an SizedArrayValueSupplier over an array.
     *
     * @param ends An array of end positoins
     * @return A supplier
     */
    public static final SizedArrayValueSupplier arrayEndSupplier(int[] ends) {
        return new Arr(ends);
    }

    private static int[] nestingBinarySearch(int pos, boolean hasNesting, int[] starts, int[] ends, int size, int depth, int firstUnsortedEndsEntry) {
        // Okay, this requires some explaining:
        //  - For many cases, binary search will work, unless it stumbles across an end that comes
        //    before the target position
        //  - In that case, we must do a linear scan UP TO the last entry in the starts array
        //    which could possibly be our start (which will still be far less than iterating
        //    the entire array in most cases
        int target = rangeBinarySearch(pos, starts, arrayEndSupplier(ends), size);
        if (target < 0) {
            // Binary search failed.  If we know we do not have any nesting which
            // interferes with the sort order of the ends array, then we can stop
            // here
            if (hasNesting) {
                // If there is nesting, then the end test in rangeBinarySearch, which
                // expects the ends array to be sorted can miss.
                // In that case, we need to do a linear search, but only of
                // a few items - as soon as we encounter one which starts
                // after the end of the earliest start which is <= pos,
                // we can bail out
                int scanTo = lastOffsetLessThanOrEqualTo(pos, starts, size);
                if (scanTo >= 0 && scanTo >= firstUnsortedEndsEntry - 1) {
                    int scanStop = ends[scanTo];
                    if (pos >= scanTo && pos < scanStop) {
                        target = scanTo;
                    } else {
                        //                        for (int i = firstUnsortedEndsEntry; i < scanTo; i++) {
                        for (int i = 0; i < scanTo; i++) {
                            int st = starts[i];
                            int en = ends[i];
                            if (pos >= st && pos < en) {
                                target = i;
                            }
                        }
                    }
                }
            }
            if (target < 0) {
                return new int[]{-1, -1};
            }
        } else if (target == 0 && firstUnsortedEndsEntry > 1) {
            // If the target is zero, we know zero cannot be the child of any
            // preceding element, so we can return a nesting depth of zero
            // here and be done
            return new int[]{0, 0};
        }
        if (target > 0) {
            // Our target may be the child of another, so scan backwards
            // to find out
            int tgt = target - 1;
            while (tgt >= 0 && tgt >= firstUnsortedEndsEntry - 1) {
                if (ends[tgt] >= ends[target]) {
                    depth++;
                }
                tgt--;
            }
            // XXX return here?  Is the test below needed if this worked?
        }
        int[] result = new int[]{target, depth};
        subsequentIndexContaining(pos, result, starts, ends, size);
        return result;
    }

    public static int firstOffsetGreaterThanOrEqualTo(int pos, int[] in, int first, int last) {
        int fval = in[first];
        if (fval >= pos) {
            // if it is the first value we have come to that is >=, we have it
            return first;
        }
        if (last <= first) {
            return -1;
        }
        int midOffset = first + ((last - first) / 2);
        int lval = in[last];
        if (lval >= pos) {
            if (in[last - 1] < pos) {
                return last;
            }
            int m = in[midOffset];
            if (m >= pos) {
                return firstOffsetGreaterThanOrEqualTo(pos, in, first + 1, midOffset);
            } else {
                return firstOffsetGreaterThanOrEqualTo(pos, in, midOffset, last - 1);
            }
        }
        return -1;
    }

    public static int firstOffsetGreaterThanOrEqualTo(int pos, int[] in, int size) {
        return firstOffsetGreaterThanOrEqualTo(pos, in, size, NONE);
    }

    public static int firstOffsetGreaterThanOrEqualTo(int pos, int[] in, int size, Bias bias) {
        if (size == 0 || in.length == 0) {
            return -1;
        }
        int last = size - 1;
        int result = firstOffsetGreaterThanOrEqualTo(pos, in, 0, last);
        if (result >= 0) {
            switch (bias) {
                case FORWARD:
                    while (result < size - 1 && in[result + 1] >= pos) {
                        result++;
                    }
                    break;
                case BACKWARD:
                    while (result > 0 && in[result - 1] >= pos) {
                        result--;
                    }
                    break;
                case NONE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError(bias);
            }
        }
        return result;
    }

    private ArrayUtil() {
        throw new AssertionError();
    }

    public static MutableEndSupplier createMutableArrayEndSupplier(int[] ends) {
        return new ArrayEndSupplier(ends);
    }

    public static int endSupplierHashCode(SizedArrayValueSupplier es) {
        return endSupplierHashCode(es, es.size());
    }

    public static int endSupplierHashCode(IntUnaryOperator es, int size) {
        int result = 1;
        for (int i = 0; i < size; i++) {
            result = 31 * result + es.applyAsInt(i);
        }
        return result;
    }

    public static enum Bias {
        FORWARD,
        BACKWARD,
        NONE;
    }
}
