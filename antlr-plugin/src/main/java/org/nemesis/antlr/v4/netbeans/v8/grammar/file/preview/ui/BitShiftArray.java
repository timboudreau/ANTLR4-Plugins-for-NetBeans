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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.preview.ui;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Like a bitset, but with it's principal task being to be able to
 * bit-rotate some or all bits in an array of longs.  Used to generate
 * the cartesian product of all possible combinations of a list of rules which
 * CulpritFinder could omit from a generated grammar to see if they are
 * causing problems when parsing a particular example text.
 *
 * @author Tim Boudreau
 */
final class BitShiftArray {

    private final long[] bits;
    private final long endMask;
    private final int sizeInBits;
    int shiftCount;
    private static long LAST_BIT = 1L << Long.SIZE - 1;

    public BitShiftArray(int sizeInBits, long[] initial) {
        this.sizeInBits = sizeInBits;
        int remainder = sizeInBits % Long.SIZE;
        this.bits = initial;
        long mask = 0;
        for (int i = remainder; i < Long.SIZE; i++) {
            mask |= 1L << i;
        }
        endMask = mask;
    }

    public BitShiftArray(int sizeInBits) {
        this(sizeInBits, initArray(sizeInBits));
    }

    public int sizeInBits() {
        return sizeInBits;
    }

    public void clear() {
        Arrays.fill(bits, 0L);
    }

    public int arraySize() {
        return bits.length;
    }

    public BitShiftArray or(BitShiftArray other) {
        long[] nue = new long[Math.min(bits.length, other.bits.length)];
        for (int i = 0; i < nue.length; i++) {
            long val = bits[i];
            val |= other.bits[i];
            nue[i] = val;
        }
        return new BitShiftArray(Math.min(sizeInBits, other.sizeInBits), nue);
    }

    public BitShiftArray copy() {
        return new BitShiftArray(sizeInBits, Arrays.copyOf(bits, bits.length));
    }

    public boolean intersects(BitShiftArray other) {
        int len = Math.min(bits.length, other.bits.length);
        for (int i = 0; i < len; i++) {
            long a = bits[i];
            long b = other.bits[i];
            if ((a & b) != 0) {
                return true;
            }
        }
        return false;
    }

    public static BitShiftArray findIntersector(Collection<BitShiftArray> all) {
        if (all.size() == 1) {
            return null;
        }
        int minArray = Integer.MAX_VALUE;
        for (BitShiftArray b : all) {
            minArray = Math.min(minArray, b.bits.length);
        }
        long[] l = new long[minArray];
        for (int i = 0; i < l.length; i++) {
            for (BitShiftArray b : all) {
                if ((b.bits[i] & l[i]) != 0) {
                    return b;
                }
                l[i] |= b.bits[i];
            }
        }
        return null;
    }

    public static BitShiftArray orAll(BitShiftArray... all) {
        if (all.length == 1) {
            return all[0];
        }
        int minBits = Integer.MAX_VALUE;
        int minArray = Integer.MAX_VALUE;
        for (BitShiftArray b : all) {
            minBits = Math.min(minBits, b.sizeInBits);
            minArray = Math.min(minArray, b.bits.length);
        }
        long[] l = new long[minArray];
        for (int i = 0; i < l.length; i++) {
            for (BitShiftArray b : all) {
                l[i] |= b.bits[i];
            }
        }
        return new BitShiftArray(minBits, l);
    }

    public static BitShiftArray orAll(Collection<BitShiftArray> all) {
        if (all.size() == 1) {
            return all.iterator().next();
        }
        int minBits = Integer.MAX_VALUE;
        int minArray = Integer.MAX_VALUE;
        for (BitShiftArray b : all) {
            minBits = Math.min(minBits, b.sizeInBits);
            minArray = Math.min(minArray, b.bits.length);
        }
        long[] l = new long[minArray];
        for (int i = 0; i < l.length; i++) {
            for (BitShiftArray b : all) {
                l[i] |= b.bits[i];
            }
        }
        return new BitShiftArray(minBits, l);
    }

    public long firstLong() {
        return bits.length == 0 ? 0 : bits[0];
    }

    private static long[] initArray(int sizeInBits) {
        int remainder = sizeInBits % Long.SIZE;
        int count = (sizeInBits / Long.SIZE) + (remainder > 0 ? 1 : 0);
        long[] result = new long[count];
        result[0] = 1;
        return result;
    }

    public void visitBits(IntConsumer cons) {
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] == 0) {
                continue;
            }
            for (int j = 0; j < Long.SIZE; j++) {
                long mask = 1l << j;
                int index = (i * Long.SIZE) + j;
                if (index > sizeInBits) {
                    break;
                }
                if ((bits[i] & mask) != 0) {
                    cons.accept(index);
                }
            }
        }
    }

    public void pruneList(List<?> list) {
        visitBitsReverseOrder((v) -> {
            Object o = list.remove(v);
        });
    }

    public void visitBitsReverseOrder(IntConsumer cons) {
        for (int i=sizeInBits-1; i >= 0; i--) {
            if (isSet(i)) {
                cons.accept(i);
            }
        }
//        for (int i = bits.length - 1; i <= 0; i--) {
//            if (bits[i] == 0) {
//                continue;
//            }
//            for (int j = Long.SIZE - 1; j >= 0; j--) {
//                long mask = 1l << j;
//                int index = (i * Long.SIZE) + j;
//                if (index > sizeInBits) {
//                    break;
//                }
//                if ((bits[i] & mask) != 0) {
//                    cons.accept(index);
//                }
//            }
//        }
    }

    public void visitBits(IntPredicate cons) {
        all:
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] == 0) {
                continue;
            }
            for (int j = 0; j < Long.SIZE; j++) {
                long mask = 1l << j;
                int index = (i * Long.SIZE) + j;
                if (index > sizeInBits) {
                    break;
                }
                if ((bits[i] & mask) != 0) {
                    boolean stop = !cons.test(index);
                    if (stop) {
                        break all;
                    }
                }
            }
        }
    }

    public int shiftSetBit(int after) {
        for (int i = after; i < sizeInBits; i++) {
            if (isSet(i)) {
                clear(i);
                if (i != sizeInBits - 1) {
                    while (++i < sizeInBits && isSet(i)) {
                        ;
                    }
                    if (i < sizeInBits) {
                        set(i);
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int setLeftMostUnsetBit() {
        int result = -1;
        if (bits[0] == 0L) {
            bits[0] = 1;
            return 0;
        }
        all:
        for (int i = 0; i < bits.length; i++) {
            for (int bit = 0; bit < Long.SIZE; bit++) {
                int index = (i * Long.SIZE) + bit;
                if (index > this.sizeInBits) {
                    break all;
                }
                long mask = 1L << bit;
                if ((bits[i] & mask) == 0) {
                    bits[i] |= mask;
                    result = index;
                    break all;
                }
            }
        }
        return result;
    }

    public int setRightMostUnsetBit() {
        int result = -1;
        int last = bits.length - 1;
        int startingBit = (sizeInBits % Long.SIZE) - 1;
        if (startingBit == 0) {
            startingBit = Long.SIZE - 1;
        }
        all:
        for (int i = last; i >= 0; i--) {
            for (int bit = startingBit; bit >= 0; bit--) {
                int index = (i * Long.SIZE) + bit;
                long mask = 1L << bit;
                if ((bits[i] & mask) == 0) {
                    bits[i] |= mask;
                    result = index;
                    break all;
                }
            }
            startingBit = Long.SIZE - 1;
        }
        return result;
    }

    public int countInterstices() {
        boolean inSet = false;
        int start = firstSetBit();
        int end = lastSetBit();
        if (start == end) {
            return 0;
        }
        int result = 1;
        for (int i = start; i < end; i++) {
            boolean curr = isSet(i);
            if (!curr && inSet) {
                result++;
            }
            inSet = curr;
        }
        return result;
    }

    public int firstSetBit() {
        return firstSetBit(0);
    }

    public int lastSetBit() {
        return lastSetBit(sizeInBits - 1);
    }

    public int lastSetBit(int beforeOrAt) {
        for (int i = beforeOrAt; i >= 0; i--) {
            if (isSet(i)) {
                return i;
            }
        }
        return -1;
    }

    public int firstUnsetBit() {
        return firstUnsetBit(0);
    }

    public int lastUnsetBit() {
        return lastUnsetBit(sizeInBits - 1);
    }

    public int moveFirstBitLeft() {
        return moveBitLeft(0);
    }

    public int moveLastBitRight() {
        return moveBitRight(sizeInBits - 1);
    }

    public int rotateFirstBitLeft() {
        return rotateBitLeft(0);
    }

    public int rotateLastBitRight() {
        return rotateBitRight(sizeInBits - 1);
    }

    public int moveBitLeft(int from) {
        int start = firstSetBit(from);
        if (start >= 0 && start < sizeInBits - 1) {
            int next = firstUnsetBit(start);
            if (next != -1) {
                clear(start);
                set(next);
                return next;
            }
        }
        return -1;
    }

    public int rotateBitLeft(int from) {
        if (from == sizeInBits - 1) {
            from = 0;
        }
        int start = firstSetBit(from);
        if (start >= 0 && start < sizeInBits - 1) {
            int next = firstUnsetBit(start);
            if (next != -1) {
                clear(start);
                set(next);
                return next;
            } else {
                next = firstUnsetBit();
                clear(start);
                set(next);
                return next;
            }
        } else if (start == -1) {
            return rotateFirstBitLeft();
        }
        return -1;
    }

    public int moveBitRight(int from) {
        int start = lastSetBit(from);
        if (start < sizeInBits && start > 0) {
            int next = lastUnsetBit(start);
            if (next != -1) {
                clear(start);
                set(next);
                return next;
            }
        }
        return -1;
    }

    public int rotateBitRight(int from) {
        if (from == 0) {
            from = sizeInBits - 1;
        }
        int start = lastSetBit(from);
        if (start < sizeInBits && start > 0) {
            int next = lastUnsetBit(start);
            if (next != -1) {
                clear(start);
                set(next);
                return next;
            } else {
                next = lastUnsetBit();
                clear(start);
                set(next);
                return next;
            }
        } else if (start == -1) {
            return rotateLastBitRight();
        }
        return -1;
    }

    public int firstSetBit(int atOrAfter) {
        for (int i = atOrAfter; i < sizeInBits; i++) {
            if (isSet(i)) {
                return i;
            }
        }
        return -1;
    }

    public int lastUnsetBit(int beforeOrAt) {
        for (int i = beforeOrAt; i >= 0; i--) {
            if (!isSet(i)) {
                return i;
            }
        }
        return -1;
    }

    public int firstUnsetBit(int atOrAfter) {
        for (int i = atOrAfter; i < sizeInBits; i++) {
            if (!isSet(i)) {
                return i;
            }
        }
        return -1;
    }

    public int setFirstUnsetBitAtOrAfter(int which) {
        int arrayOffset = which / Long.SIZE;
        int bitOffset = which % Long.SIZE;
        int result = -1;
        int bitsStart = bitOffset;
        all:
        for (int i = arrayOffset; i < bits.length; i++) {
            for (int bit = bitsStart; bit < Long.SIZE; bit++) {
                int index = (i * Long.SIZE) + bit;
                if (index > this.sizeInBits) {
                    break all;
                }
                long mask = 1L << bit;
                if ((bits[i] & mask) == 0) {
                    bits[i] |= mask;
                    result = index;
                    break all;
                }
            }
            bitsStart = 0;
        }
        if (result == -1) {
            result = setLeftMostUnsetBit();
        }
        return result;
    }

    public int cardinality() {
        int result = 0;
        for (int i = 0; i < Long.SIZE; i++) {
            long mask = 1L << i;
            for (int j = 0; j < bits.length; j++) {
                if ((bits[j] & mask) != 0) {
                    result++;
                }
            }
        }
        return result;
    }

    public boolean isSet(int bit) {
        int arrayIndex = bit / Long.SIZE;
        int bitOffset = bit % Long.SIZE;
        return (bits[arrayIndex] & (1L << bitOffset)) != 0;
    }

    public void set(int bit) {
        int arrayIndex = bit / Long.SIZE;
        int bitOffset = bit % Long.SIZE;
        bits[arrayIndex] |= 1L << bitOffset;
    }

    public void clear(int bit) {
        int arrayIndex = bit / Long.SIZE;
        int bitOffset = bit % Long.SIZE;
        bits[arrayIndex] &= ~(1L << bitOffset);
    }

    public void boundedShiftLeft(int fromBit) {
        int arrayStart = fromBit / Long.SIZE;
        int bitOffset = fromBit % Long.SIZE;
        int startBit = bitOffset;
        shiftCount++;
        int preserveMask = 0;
        for (int i = 0; i < Long.SIZE; i++) {
            if (i < startBit) {
                preserveMask |= 1L << i;
            }
        }
        boolean maskOne = false;
        for (int i = arrayStart; i < bits.length; i++) {
            long orig = bits[i];
            long nue;
            if (i == arrayStart && bitOffset > 0) {
                nue = (orig << 1L) & ~preserveMask;
                nue |= orig & preserveMask;
            } else {
                nue = orig << 1L;
            }
            if (maskOne) {
                nue |= 1L;
                maskOne = false;
            }
            if (i == bits.length - 1) {
                boolean isMaskedEndBits = (nue & endMask) != 0;
                if (isMaskedEndBits) {
                    maskOne = true;
                    setFirstUnsetBitAtOrAfter(fromBit);
                    nue &= ~endMask;
                }
            } else {
                if ((orig & LAST_BIT) != 0) {
                    maskOne = true;
                }
            }
            bits[i] = nue;
        }
    }

    public void shiftLeft() {
        if (bits.length == 1) {
            long old = bits[0];
            bits[0] = (old << 1L);
            if ((bits[0] & endMask ) != 0) {
                bits[0] |= 1L;
            }
            bits[0] &= ~endMask;
            return;
        }
        shiftCount++;
        boolean maskOne = false;
        for (int i = 0; i < bits.length; i++) {
            long orig = bits[i];
            long nue = orig << 1L;
            if (maskOne) {
                nue |= 1L;
                maskOne = false;
            }
            if (i == bits.length - 1) {
                boolean isMaskedEndBits = (nue & endMask) != 0;
                if (isMaskedEndBits) {
                    maskOne = true;
                    setLeftMostUnsetBit();
                    nue &= ~endMask;
                }
            } else {
                if ((orig & LAST_BIT) != 0) {
                    maskOne = true;
                }
            }
            bits[i] = nue;
        }
    }

    public boolean isLastBitSet() {
        return isSet(sizeInBits-1);
    }

    public void shiftRight() {
        long endArrayBitMask = 1L << (Long.SIZE - (sizeInBits % Long.SIZE)) - 1L;
        if (bits.length == 1) {
            long old = bits[0];
            bits[0] = (old >> 1L);
            if ((old & 1L ) != 0) {
                bits[0] |= endArrayBitMask << 1;
            }
            bits[0] &= ~endMask;
            return;
        }
        shiftCount--;
        boolean maskNext = false;
        int last = bits.length - 1;
        for (int i = last; i >= 0; i--) {
            long lastBitMask = i == last ? endArrayBitMask : LAST_BIT;
            long old = bits[i];
            long nue = (old >> 1L) & ~LAST_BIT;
            boolean hasBitToMove = (1L & old) != 0L;
            if (maskNext) {
                nue |= lastBitMask;
                maskNext = false;
            }
            if (hasBitToMove) {
                if (i == 0) {
                    setRightMostUnsetBit();
                } else {
                    maskNext = true;
                }
            }
            if (i == last) {
                nue &= ~endMask;
            }
            bits[i] = nue;
        }
    }

    public BitSet toBitSet() {
        //            return BitSet.valueOf(bits);
        BitSet set = new BitSet(sizeInBits);
        visitBits((i) -> {
            set.set(i);
        });
        return set;
    }

    public String toString() {
        //            return tos(toBitSet(), sizeInBits);
        StringBuilder sb = new StringBuilder();
        toBinaryString(bits, sizeInBits % Long.SIZE, sb);
        return sb.toString();
    }

    static String tos(BitSet set, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            if (set.get(i)) {
                sb.append('-');
            } else {
                sb.append('0');
            }
        }
        return sb.toString();
    }

    static String toBinaryString(long[] longs, int stopBit) {
        StringBuilder sb = new StringBuilder();
        toBinaryString(longs, stopBit, sb);
        return sb.toString();
    }

    static void toBinaryString(long[] longs, int stopBit, StringBuilder sb) {
        if (stopBit == 0) {
            stopBit = Long.SIZE;
        }
        for (int i = 0; i < longs.length; i++) {
            long l = longs[i];
            if (i != longs.length - 1) {
                toBinaryString(l, sb);
            } else {
                toBinaryString(l, stopBit, sb);
            }
            //                if (i != longs.length - 1) {
            //                    sb.append(' ');
            //                }
        }
    }

    static String toBinaryString(long val) {
        return toBinaryString(val, Long.SIZE);
    }

    static String toBinaryString(long val, int stopBit) {
        StringBuilder sb = new StringBuilder(Long.SIZE);
        toBinaryString(val, stopBit, sb);
        return sb.toString();
    }

    static void toBinaryString(long l, StringBuilder sb) {
        toBinaryString(l, Long.SIZE, sb);
    }

    static void toBinaryString(long l, int stopBit, StringBuilder sb) {
        long mask = 1L;
        for (int i = 0; i < stopBit; i++) {
            if ((l & mask) != 0) {
                sb.append('x');
            } else {
                sb.append('-');
            }
            mask = mask << 1;
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Arrays.hashCode(this.bits);
        hash = 71 * hash + this.sizeInBits;
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
        final BitShiftArray other = (BitShiftArray) obj;
        if (this.sizeInBits != other.sizeInBits) {
            return false;
        }
        if (!Arrays.equals(this.bits, other.bits)) {
            return false;
        }
        return true;
    }

}
