/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.live.language.ambig;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Int;
import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.collections.IntIntMap;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.collections.IntSet;
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
class Grid {

    private final int[][] cells;
    private int rowSize;
    private final IntIntMap greatestColumnForItem;
    private final IntMap<IntSet> reverseIndex;
    private boolean needReindex = true;

    public Grid(int rowSize, int[][] cells) {
        this.cells = cells;
        this.rowSize = rowSize;
        greatestColumnForItem = IntIntMap.create(rowSize);
        reverseIndex = IntMap.create(cells.length, true, IntSet::create);
//        init();
    }

    void shuffle(Random rnd) {
        ArrayUtils.shuffle(rnd, cells);
    }

    void sort() {
        Arrays.sort(cells, (rowA, rowB) -> {
            int max = Math.min(rowA.length, rowB.length);
            int lastA = -1;
            int lastB = -1;
            for (int i = 0; i < max; i++) {
                int result = rowB[i] - rowA[i];
                if (result != 0) {
                    return result;
                }
                if (rowA[i] != -1) {
                    lastA = i;
                }
                if (rowB[i] != -1) {
                    lastB = i;
                }
            }
            return Integer.compare(lastB, lastA);
        });
    }

    int[][] cells() {
        return cells;
    }

    int rowSize() {
        return rowSize;
    }

    public void tune() {
        while (optimizeLoop()) {
            System.out.println("loop: \n" + this);
        }
    }

    public String toString() {
        return toString(Integer::toString);
    }

    private IntSet distinctValues() {
        return distinctValues(rowSize, cells);
    }

    private static IntSet distinctValues(int rowSize, int[][] cells) {
        IntSet result = IntSet.arrayBased(rowSize * rowSize);
        for (int i = 0; i < cells.length; i++) {
            for (int j = 0; j < rowSize; j++) {
                result.add(cells[i][j]);
            }
        }
        return result;
    }

    private static String pad(String val, int to) {
        while (val.length() < to) {
            val = " " + val;
        }
        return val;
    }

    public String toString(IntFunction<String> i) {
        return toString(cells, rowSize, i);
    }

    public static String toString(int[][] cells, int rowSize, IntFunction<String> i) {
        StringBuilder sb = new StringBuilder();
        IntMap<String> im = IntMap.create();
        Int max = Int.create();
        distinctValues(rowSize, cells).forEachInt(v -> {
            String result = i.apply(v);
            max.max(result.length());
            max.max(Integer.toString(v).length());
            im.put(v, result);
        });
        for (int j = 0; j < rowSize; j++) {
//            sb.append(j).append(". ").append(pad(" ", max.getAsInt() + 1));
            sb.append(pad(Integer.toString(j), max.getAsInt() + 3));
        }
        sb.append('\n');
        for (int row = 0; row < cells.length; row++) {
            sb.append(row).append(": ");
            for (int col = 0; col < rowSize; col++) {
                int val = cells[row][col];
                String sv = pad(im.get(val), max.getAsInt());
                if (col == 0) {
                    sb.append("| ");
                } else {
                    sb.append(" | ");
                }
                sb.append(sv);
                if (col == rowSize - 1) {
                    sb.append(" |");
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    int[][] matchMatrix() {
        int[][] result = new int[cells.length][rowSize];
        for (int row = 1; row < cells.length; row++) {
            int[] prevRowData = cells[row - 1];
            int[] currRowData = cells[row];
            for (int col = 0; col < rowSize - 1; col++) {
                int curr = prevRowData[col];
                int nxt = currRowData[col + 1];
                if (curr == nxt && curr != -1) {
                    result[row - 1][col]++;
                    for (int i = row - 2; i >= 0; i--) {
                        int[] aboveRow = cells[i];
                        if (aboveRow[col] == curr) {
                            result[i][col]++;
                        }
                    }
                    if (row - 2 >= 0 && col > 0) {
                        for (int precedingRow = row - 2, precedingColumn = col - 1; precedingRow >= 0 && precedingColumn >= 0; precedingRow--, precedingColumn--) {
                            int[] aboveData = cells[precedingRow];
                            if (aboveData[precedingColumn] > 0) {
                                result[precedingRow][precedingColumn]++;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }
        for (int row = cells.length - 1; row > 0; row--) {
            int[] currRowData = cells[row];
            int[] prevRowData = cells[row - 1];
            for (int col = 0; col < rowSize - 1; col++) {
                int curr = currRowData[col];
                int prev = prevRowData[col + 1];
                if (curr == prev && curr != -1) {
                    int amt = result[row - 1][col + 1];
                    result[row][col] = amt + 1;
                    if (row + 1 < cells.length) {
                        for (int r = row + 1; r < cells.length; r++) {
                            int nextRowValue = cells[r][col];
                            if (nextRowValue == curr) {
                                result[r][col]++;
                            }
                        }
                        if (col + 1 < rowSize) {
                            for (int subsequentRow = row + 1, subsequentColumn = col + 1, ix = 1; subsequentRow < cells.length && subsequentColumn < rowSize; subsequentRow++, subsequentColumn++, ix++) {
                                if (result[subsequentRow][subsequentColumn] > 0) {
                                    result[subsequentRow][subsequentColumn] += amt + ix;
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    void sortByColumn(int column) {
        Arrays.sort(cells, (rA, rB) -> {
            return Integer.compare(rB[column], rB[column]);
        });
    }

    boolean applyMatrix(int[][] matrix) {
        int[] sums = new int[cells.length];
        int max = 0;
        for (int row = 0; row < matrix.length; row++) {
            int[] mxRow = matrix[row];
            int sum = 0;
            int lastValue = 0;
            for (int col = 0; col < mxRow.length; col++) {
                sum += mxRow[col];
                if (cells[row][col] != -1) {
                    lastValue = col;
                }
            }
            max = Math.max(max, mxRow.length + sum);
        }
        if (max == 0) {
            return false;
        }
        int oldRowSize = rowSize;
        grow(max + 1);
        System.out.println("GROWN: " + this);
        for (int row = 0; row < matrix.length; row++) {
            int[] rowData = cells[row];
            for (int col = oldRowSize - 1; col >= 0; col--) {
                int shiftBy = matrix[row][col];
                if (shiftBy != 0) {
                    int runLength = (oldRowSize - col) + 1;
                    int target = col + shiftBy;
                    System.arraycopy(rowData, col, rowData, target, runLength);
                    try {
                        Arrays.fill(rowData, col, target, -1);
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Bad matrix?\n" + toString(matrix, oldRowSize, Integer::toString));
                    }
                }
            }
        }
        needReindex = true;
        return true;
    }

    void trim() {
        int maxPosition = 0;
        int minPosition = 0;
        for (int col = 0; col < rowSize; col++) {
            boolean empty = true;
            for (int row = 0; row < cells.length; row++) {
                if (cells[row][col] != -1) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                minPosition++;
            } else {
                break;
            }
        }
        for (int row = 0; row < cells.length; row++) {
            for (int col = rowSize - 1; col >= 0; col--) {
                int val = cells[row][col];
                if (val != -1) {
                    maxPosition = Math.max(maxPosition, col);
                }
            }
        }
        int newSize = (maxPosition + 1) - minPosition;
        if (minPosition > 0) {
            for (int i = 0; i < cells.length; i++) {
                System.arraycopy(cells[i], minPosition, cells[i], 0, cells[i].length - minPosition);
            }
        }
        System.out.println("REIS " + newSize);
        for (int i = 0; i < cells.length; i++) {
            cells[i] = Arrays.copyOf(cells[i], newSize);
        }
        rowSize = newSize;
        IntSet empties = emptyColumns();
        System.out.println("PRUNE EMPTY " + empties);
        if (!empties.isEmpty()) {
            for (int i = 0; i < cells.length; i++) {
                int[] nue = new int[cells[i].length - empties.size()];
                Arrays.fill(nue, -1);
                for (int j = 0, cursor = 0; j < cells[i].length; j++) {
                    if (!empties.contains(j)) {
                        nue[cursor++] = cells[i][j];
                    }
                }
                cells[i] = nue;
            }
        }
        rowSize = newSize - empties.size();
    }

    boolean align() {
        IntSet vals = distinctValues();
        int[][] mx = new int[cells.length][rowSize];
        reindex();
        IntSet changedRows = IntSet.bitSetBased(rowSize);
        vals.forEachInt(val -> {
            int ix = greatestColumnForItem.indexOf(val);
            if (ix >= 0) {
                int col = greatestColumnForItem.valueAt(ix);
                for (int row = 0; row < cells.length; row++) {
                    if (changedRows.contains(row)) {
                        continue;
                    }
                    int index = indexOf(row, val);
                    if (index >= 0 && index < col) {
                        mx[row][index] += col - index;
                        changedRows.add(row);
                    }
                }
            }
        });
        if (!changedRows.isEmpty()) {
            System.out.println("WILL APPLY:\n" + toString(cells, rowSize, Integer::toString));
            boolean result = applyMatrix(mx);
            if (result) {
                alignInners();
                alignStarts();
                shiftConflicts();
//                makeOrphansAdjacaent();
                trim();
                applyMatrix(matchMatrix());
                trim();
            }
            return result;
        }
        return false;
    }

    boolean shiftConflicts() {
        int[][] mx = new int[cells.length][rowSize];
        IntMap<IntSet> rowsForValue = IntMap.create(cells.length, true, () -> IntSet.bitSetBased(rowSize));

        IntSet vals = IntSet.bitSetBased(rowSize);
        Bool any = Bool.create();
        int[] nums = new int[cells.length];
        for (int i = 0; i < nums.length; i++) {
            nums[i] = i;
        }
        for (int col = 0; col < rowSize; col++) {
            rowsForValue.clear();
            vals.clear();
            for (int row = 0; row < cells.length; row++) {
                int v = cells[row][col];
                if (v != -1) {
                    vals.add(v);
                    rowsForValue.get(v).add(row);
                }
            }
            if (vals.size() > 1) {
                Int offset = Int.of(1);
//                rowsForValue.removeIf(is -> is.size() <= 1);
                int c = col;
                rowsForValue.forEachPair((int val, IntSet rows) -> {
                    if (rows.isEmpty()) {
                        return;
                    }
                    int off = offset.getAsInt();
                    rows.forEachInt(row -> {
                        mx[row][c] += off;
                        any.set();
                    });
                    if (c < rowSize - 1) {
                        IntSet unused = IntSet.of(nums);
                        unused.removeAll(rows);
                        unused.forEachInt(u -> {
                            mx[u][c + 1] += off + 1;
                        });
                    }
                    offset.increment();
                });
            }
        }
        any.ifTrue(() -> {
            applyMatrix(mx);
        });
        return any.getAsBoolean();
    }

    IntSet orphanColumns(int row) {
        IntSet result = null;
        for (int col = rowSize - 1; col >= 0; col--) {
            int itemCount = 0;
            for (int testRow = 0; testRow < cells.length; testRow++) {
                if (cells[testRow][col] != -1) {
                    if (testRow != row) {
                        itemCount = 1000;
                        break;
                    }
                    itemCount++;
                }
            }
            if (itemCount == 1) {
                if (result == null) {
                    result = IntSet.bitSetBased(rowSize);
                }
                result.add(col);
            }
        }
        return result == null ? IntSet.EMPTY : result;
    }

    boolean makeOrphansAdjacaent() {
        Bool result = Bool.create();
        int maxAdjusted = -1;
        for (int row = 0; row < cells.length; row++) {
            IntSet all = orphanColumns(row);
            if (!all.isEmpty()) {
                int r = row;
                System.out.println("Orphans in " + r + ": " + all);
                if (all.first() > maxAdjusted) {
                    for (int j = 0; j < all.size(); j++) {
                        int col = all.valueAt(j);
                        System.out.println("  col " + col + " @ " + j);
                        int target = r;
                        for (int i = col; i >= 0; i--) {
                            int ic = columnItemCount(i);
                            if (ic <= 1) {
                                target = i;
                            } else {
                                break;
                            }
                        }
                        if (target < r) {
                            System.out.println("swap " + target + " / " + r);
                            swapColumns(target, r);
                            result.set();
                            maxAdjusted = target;
                        }
                    };
                }
                if (result.getAsBoolean()) {
                    System.out.println("bk on " + row + " ma " + maxAdjusted);
                    break;
                }
            }
        }
        return result.getAsBoolean();
    }

    boolean coalesceAdjacent() {
        boolean result = false;
        for (int i = 1; i < rowSize; i++) {
            int svA = singleValue(i - 1);
            int svB = singleValue(i);
            if (svA != -1 && svA == svB) {
                for (int row = 0; row < cells.length; row++) {
                    int valB = cells[row][i];
                    cells[row][i - 1] = valB;
                    cells[row][i] = -1;
                    result = true;
                }
            }
        }
        return result;
    }

    int singleValue(int column) {
        int result = -1;
        int v = -1;
        for (int row = 0; row < cells.length; row++) {
            int val = cells[row][column];
            if (val == -1) {
                continue;
            }
            if (v == -1) {
                v = val;
            } else if (v != val) {
                v = -1;
                break;
            }
        }
        return v;
    }

    boolean xmakeOrphansAdjacaent() {
        // XXX DOES NOT WORK
        boolean result = false;
        int tr = -1;
        for (int col = rowSize - 1; col > 0; col--) {
            int itemCount = 0;
            int targetRow = -1;
            for (int row = 0; row < cells.length; row++) {
                if (cells[row][col] != -1) {
                    itemCount++;
                    targetRow = row;
                }
            }
            if (itemCount == 1 && cells[targetRow][col - 1] == -1) {
                if (tr == -1) {
                    tr = targetRow;
                } else if (targetRow != tr) {
                    continue;
                }
                int leastColumn = col - 1;
                for (int prevColumn = col - 2; prevColumn >= 0; prevColumn--) {
                    if (cells[targetRow][prevColumn] != -1) {
                        break;
                    } else {
                        leastColumn = prevColumn;
                    }
                }
                if (leastColumn != col) {
                    swapColumns(col, leastColumn);
                    result = true;
                }
            }
        }
        return result;
    }

    private int columnItemCount(int col) {
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i][col] != -1) {
                count++;
            }
        }
        return count;
    }

    private void swapColumns(int ca, int cb) {
        if (ca == cb) {
            return;
        }
        needReindex = true;
        for (int i = 0; i < cells.length; i++) {
            int hold = cells[i][ca];
            cells[i][ca] = cells[i][cb];
            cells[i][cb] = hold;
        }
    }

    boolean alignInners() {
        reindex();
        boolean changed = false;
        for (int row = 0; row < cells.length; row++) {
            int lastValueCell = cells[row][rowSize - 1] == -1 ? rowSize : -1;
            for (int i = rowSize - 1; i >= 0; i--) {
                if (cells[row][i] != -1) {
                    lastValueCell = i;
                }
            }
            for (int col = lastValueCell - 1; col >= 0; col--) {
                int v = cells[row][col];
                if (v != -1) {
                    int max = greatestColumnForItem.get(v);
                    if (max >= 0 && col < max && max < lastValueCell) {
                        cells[row][col] = -1;
                        cells[row][max] = v;
                        changed = true;
                    }
                    lastValueCell = col;
                }
            }
        }
        if (changed) {
            needReindex = true;
        }
        return changed;
    }

    boolean alignStarts() {
        int[][] mx = new int[cells.length][rowSize];
        boolean any = false;
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < rowSize; col++) {
                int val = cells[row][col];
                if (val != -1) {
                    reindex();
                    int g = greatestColumnForItem.get(val);
                    if (g > col) {
                        mx[row][col] = g - col;
                        any = true;
                    }
                    break;
                }
            }
        }
        if (any) {
            applyMatrix(mx);
        }
        return any;
    }

    private boolean optimizeLoop() {
        IntSet empty = emptyColumns();
        if (empty.isEmpty()) {
            return false;
        }
        int first = empty.first();
        boolean backHalf = first > rowSize / 2;
        boolean result = findDownRightDiagonals((aR, aC, bR, bC) -> {
            shiftRight(aR, aC, lastOccupiedColumn(aC));
            return false;
        });

        int last = empty.last();
        return result;
    }

    interface IntQuadPredicate {

        boolean test(int firstRow, int firstColumn, int secondRow, int secondColumn);
    }

    private boolean findDownRightDiagonals(IntQuadPredicate f) {
        for (int i = 1; i < cells.length; i++) {
            int[] prevRow = cells[i - 1];
            int[] currRow = cells[i];
            for (int j = 0; j < rowSize - 1; j++) {
                int val = prevRow[j];
                int downright = currRow[j + 1];
                if (val == downright) {
                    boolean result = f.test(i - 1, j, i, j + 1);
                    if (!result) {
                        return !result;
                    }
                }
            }
        }
        return false;
    }

    private boolean allSameValue(int column) {
        int target = -1;
        for (int i = 0; i < cells.length; i++) {
            int[] rowdata = cells[i];
            int val = rowdata[column];
            if (target == -1) {
                if (val != -1) {
                    target = val;
                }
            } else {
                if (val == -1) {
                    continue;
                } else if (val != target) {
                    return false;
                }
            }
        }
        return true;
    }

    private IntSet emptyColumns() {
        IntSet result = IntSet.bitSetBased(rowSize);
        for (int col = 0; col < rowSize; col++) {
            boolean empty = true;
            for (int row = 0; row < cells.length; row++) {
                if (cells[row][col] != -1) {
                    empty = false;
                    break;
                }
            }
            if (empty) {
                result.add(col);
            }
        }
        return result;
    }

    private boolean withEmptyColumns(Predicate<boolean[]> c) {
        boolean[] emptyColumns = new boolean[rowSize];
        Arrays.fill(emptyColumns, true);
        boolean anyEmpty = false;
        for (int column = 0; column < rowSize; column++) {
            boolean currEmpty = true;
            for (int row = 0; row < cells.length; row++) {
                if (cells[row][column] != -1) {
                    currEmpty = false;
                    break;
                }
            }
            if (currEmpty) {
                emptyColumns[column] = true;
                anyEmpty = true;
            }
        }
        if (anyEmpty) {
            return c.test(emptyColumns);
        }
        return anyEmpty;
    }

    private void init() {
        System.out.println("BEFORE:\n" + toString());
        Arrays.sort(cells, (aArr, bArr) -> {
            for (int i = 0; i < Math.min(aArr.length, bArr.length); i++) {
                int result = Integer.compare(aArr[i], bArr[i]);
                if (result != 0) {
                    int lengthCompare = -Integer.compare(aArr.length, bArr.length);
                    if (lengthCompare != 0) {
                        return lengthCompare;
                    }
                    return result;
                }
            }
            return -Integer.compare(aArr.length, bArr.length);
        });
        for (int row = 0; row < cells.length; row++) {
            int last = lastOccupiedColumn(row);
            if (last > 1 && last < rowSize - 1) {
                int[] rowData = cells[row];
                int mid = last / 2;
                int runLength = (last - mid) + 1;
                int target = rowData.length - runLength;
//                shiftRight(rowData, mid, target);
                System.arraycopy(rowData, mid, rowData, target, runLength);
                Arrays.fill(rowData, mid, (rowData.length - mid) - 1, -1);
            }
        }
        System.out.println("AFTER:\n" + this);
    }

    public static int levenshteinDistance(
            int[] a,
            int[] b) {
        int[][] distance = new int[a.length + 1][b.length + 1];

        for (int i = 0; i <= a.length; i++) {
            distance[i][0] = i;
        }
        for (int j = 1; j <= b.length; j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                distance[i][j] = minimum(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1] + ((a[i - 1] == b[j - 1]) ? 0 : 1));
            }
        }

        return distance[a.length][b.length];
    }

    private static int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    private void reindex() {
        if (needReindex) {
            needReindex = false;
            for (int row = 0; row < cells.length; row++) {
                for (int column = 0; column < rowSize; column++) {
                    int value = cells[row][column];
                    if (value != -1) {
                        int newmax = Math.max(column, greatestColumnForItem.getAsInt(value, -1));
                        greatestColumnForItem.put(value, newmax);
                        reverseIndex.get(row).add(value);
                    }
                }
            }
        }
    }

    public void set(int row, int column, int value) {
        cells[column][row] = value;
        int newmax = Math.max(column, greatestColumnForItem.getAsInt(value, -1));
        greatestColumnForItem.put(value, newmax);
        reverseIndex.get(row).add(value);
    }

    boolean containsHeterogenous(int column) {
        IntSet s = IntSet.create(cells.length);
        visitColumn(column, s::add);
        return s.size() > 1;
    }

    private void grow(int targetWidth) {
        if (targetWidth > rowSize) {
            int newWidth = Math.max(targetWidth, rowSize * 2);
            for (int i = 0; i < cells.length; i++) {
                cells[i] = Arrays.copyOf(cells[i], newWidth);
                Arrays.fill(cells[i], rowSize, newWidth, -1);
            }
            rowSize = newWidth;
        }
    }

    int visitRow(int row, IntConsumer c) {
        int count = 0;
        int[] rowContents = cells[row];
        for (int i = 0; i < rowSize; i++) {
            int item = rowContents[i];
            if (item != -1) {
                c.accept(item);
                count++;
            }
        }
        return count;
    }

    int visitColumn(int column, IntConsumer c) {
        int count = 0;
        for (int i = 0; i < cells.length; i++) {
            int[] rowContents = cells[i];
            int item = rowContents[column];
            if (item != -1) {
                c.accept(item);
                count++;
            }
        }
        return count;
    }

    int lastIndexOf(int row, int value) {
        int[] rowData = cells[row];
        for (int i = rowSize - 1; i >= 0; i--) {
            if (rowData[i] == value) {
                return i;
            }
        }
        return -1;
    }

    int indexOf(int row, int value) {
        int[] rowdata = cells[row];
        for (int i = 0; i < rowdata.length; i++) {
            if (rowdata[i] == value) {
                return i;
            }
        }
        return -1;
    }

    void shiftLeft(int row, int from, int to) {
        needReindex = true;
        int[] rowData = cells[row];
        int len = from - to;
        for (int i = 0; i < len; i++) {
            if (rowData[i] != -1) {
                throw new IllegalArgumentException("Shift left row " + row + " from " + from + " to " + to + " would clobber " + rowData[i] + " at " + i);
            }
        }
        shiftLeft(rowData, from, to);
    }

    void shiftRight(int row, int from, int to) {
        int len = to - from;
        if (from + len > rowSize) {
            grow(rowSize + len);
        }
        needReindex = true;
        int[] rowData = cells[row];
        shiftRight(rowData, from, to);
    }

    private static void shiftRight(int[] row, int from, int to) {
        if (from == to) {
            return;
        }
//        int runLength = (to - from) + 1;
//        System.arraycopy(row, from, row, to, runLength);
//        Arrays.fill(row, from, from + ((to - from) - 1), -1);

        int len = to - from;
        System.out.println("SHIFT " + len + " items from " + from + " to " + to
                + " end point will be " + (to + len));
        System.arraycopy(row, from, row, to, len);
        for (int i = from; i < to; i++) {
            row[i] = -1;
        }
    }

    private static void shiftLeft(int[] row, int from, int to) {
        if (from == to) {
            return;
        }
        int len = from - to;
        System.arraycopy(row, from, row, to, len);
        Arrays.fill(row, to, to + len, -1);
    }

    private int lastOccupiedColumn(int row) {
        int[] rowData = cells[row];
        for (int i = rowSize - 1; i >= 0; i--) {
            if (rowData[i] != -1) {
                return i;
            }
        }
        return -1;
    }

    private int size(int row) {
        int[] data = cells[row];
        int result = 0;
        for (int i = 0; i < rowSize; i++) {
            if (data[i] != -1) {
                result++;
            }
        }
        return result;
    }

}
