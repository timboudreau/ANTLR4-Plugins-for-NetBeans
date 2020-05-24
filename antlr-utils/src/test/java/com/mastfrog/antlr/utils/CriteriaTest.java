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
package com.mastfrog.antlr.utils;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class CriteriaTest {

    @Test
    public void testSortAndDedup() {
        int[] val = testSortAndDedup(new int[0]);
        assertArrayEquals(new int[0], val);
        val = testSortAndDedup(100);
        assertEquals(1, val.length, Arrays.toString(val));
        assertArrayEquals(new int[]{100}, val, Arrays.toString(val));
        val = testSortAndDedup(100, 100, 100);
        assertEquals(1, val.length, Arrays.toString(val));
        assertArrayEquals(new int[]{100}, val, Arrays.toString(val));
        val = testSortAndDedup(100, 101, 100, 101);
        assertEquals(2, val.length, Arrays.toString(val));
        assertArrayEquals(new int[]{100, 101}, val, Arrays.toString(val));
        val = testSortAndDedup(9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, val, Arrays.toString(val));
        val = testSortAndDedup(9, 8, 7, 6, 5, 4, 3, 2, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, val, Arrays.toString(val));
        val = testSortAndDedup(10, 10, 0, 0, 0, 0, 0, 5, 0, 0, 0);
        assertArrayEquals(new int[]{0, 5, 10}, val, Arrays.toString(val));
    }

    private int[] testSortAndDedup(int... arr) {
        int[] tst = Criteria.sortAndDedup(arr);
        assertNoDuplicates(tst);
        assertSorted(tst);
        return tst;
    }

    private void assertSorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            int prev = arr[i - 1];
            int curr = arr[i];
            assertTrue(curr > prev, "Not sorted at " + i + " " + Arrays.toString(arr));
        }
    }

    private void assertNoDuplicates(int[] arr) {
        Set<Integer> s = new TreeSet<>();
        for (int i = 0; i < arr.length; i++) {
            s.add(arr[i]);
        }
        assertEquals(s.size(), arr.length, "Size mismatch with set - should have "
                + s + " but got " + Arrays.toString(arr));
    }

}
