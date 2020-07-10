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

import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class ArrayUtilTest {

    static String[] SET_1 = {"aim", "airplane", "babies", "differences", "dirigibles",
        "diggings", "dogs", "drunks", "she", "sheep", "shelter", "shone", "shoulder", "sugar", "zebra"};

    static String[] SET_2 = {"amoeba", "babies", "dogs", "differences", "dirigibles",
        "diggings", "drunks", "she", "sheep", "shone", "shoulder"};

    static String[] SET_3 = {"amoeba", "babies", "dogs", "differences", "dirigibles",
        "diggings", "drunks", "she", "sheep", "shone", "shoulder", "zebra", "zipline"};

    @Test
    public void testPrefixSearch() {
        assertPrefixes(SET_1, "d", "differences", "diggings", "dirigibles", "dogs", "drunks");
        assertPrefixes(SET_1, "di", "differences", "diggings", "dirigibles");
        assertPrefixes(SET_1, "dr", "drunks");
        assertPrefixes(SET_1, "du");
        assertPrefixes(SET_1, "do", "dogs");
        assertPrefixes(SET_1, "a", "aim", "airplane");
        assertPrefixes(SET_1, "ai", "aim", "airplane");
        assertPrefixes(SET_1, "aim", "aim");
        assertPrefixes(SET_1, "air", "airplane");
        assertPrefixes(SET_1, "s", "she", "sheep", "shelter", "shone", "shoulder", "sugar");
        assertPrefixes(SET_1, "sh", "she", "sheep", "shelter", "shone", "shoulder");
        assertPrefixes(SET_1, "she", "she", "sheep", "shelter");
        assertPrefixes(SET_1, "sho", "shone", "shoulder");
        assertPrefixes(SET_1, "su", "sugar");
        assertPrefixes(SET_1, "zip");
        assertPrefixes(SET_1, "z", "zebra");
        assertPrefixes(SET_1, "ze", "zebra");
        assertPrefixes(SET_1, "zebra", "zebra");

        assertPrefixes(SET_2, "a", "amoeba");
        assertPrefixes(SET_2, "am", "amoeba");
        assertPrefixes(SET_2, "amo", "amoeba");
        assertPrefixes(SET_2, "amoe", "amoeba");
        assertPrefixes(SET_2, "amoeb", "amoeba");
        assertPrefixes(SET_2, "amoeba", "amoeba");

        assertPrefixes(SET_3, "z", "zebra", "zipline");
        assertPrefixes(SET_3, "ze", "zebra");
        assertPrefixes(SET_3, "zi", "zipline");

        assertPrefixes(SET_1, "", SET_1);

        assertPrefixes(new String[0], "wookie");
    }

    private void assertPrefixes(String[] items, String prefix, String... expected) {
        System.out.println("\n------------------------\n" + prefix + " in " + Strings.join(',', items));
        Arrays.sort(items);
        List<String> exp = Arrays.asList(expected);
        List<String> got = new ArrayList<>();
        ArrayUtil.prefixBinarySearch(items, prefix, ix -> {
            got.add(items[ix]);
        });
        assertEquals("Searching for '" + prefix + "' in " + items.length
                + " items: '" + Strings.join(',', items), exp, got);
    }

}
