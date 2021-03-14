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
package org.nemesis.data;

import com.mastfrog.abstractions.list.IndexedResolvable;
import com.mastfrog.util.path.UnixPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import static java.nio.charset.StandardCharsets.UTF_16;
import org.nemesis.data.impl.ArrayUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.data.SemanticRegions.SemanticRegionsBuilder;
import org.nemesis.jfs.JFS;
import org.nemesis.jfs.JFSClassLoader;
import org.nemesis.jfs.JFSCoordinates;
import org.nemesis.jfs.javac.CompileResult;
import org.nemesis.jfs.javac.JFSCompileBuilder;
import org.nemesis.jfs.javac.JavacOptions;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticRegionsTest {

    @Test
    public void testNearest() throws Exception {
        /*
word=Hello =0:5@0^0
word=this =6:10@1^0
word=world =11:16@2^0
         */
        SemanticRegions.SemanticRegionsBuilder<String> bldr = SemanticRegions.builder(String.class);
        bldr.add("Hello", 0, 5);
        bldr.add("this", 6, 10);
        bldr.add("world", 11, 16);
        SemanticRegions<String> r = bldr.build();

        List<String> seen = new ArrayList<>();
        assertEquals("Wrong key for " + 5, "this", r.nearestTo(5).key());
        for (int i = 0; i < 16; i++) {
            SemanticRegion<String> reg = r.nearestTo(i);
            System.out.println(i + ". nearest " + reg);
            if (!seen.contains(reg.key())) {
                seen.add(reg.key());
            }
        }
        assertEquals(Arrays.asList("Hello", "this", "world"), seen);
    }

    @Test
    public void testNearestWithSpacing() throws Exception {
        SemanticRegions.SemanticRegionsBuilder<String> bldr = SemanticRegions.builder(String.class);
        bldr.add("Hello", 2, 7);
        bldr.add("this", 9, 13);
        bldr.add("world", 15, 20);
        SemanticRegions<String> r = bldr.build();

        List<String> seen = new ArrayList<>();
        assertEquals("Wrong key for " + 6, "Hello", r.nearestTo(6).key());
        assertEquals("Wrong key for " + 5, "Hello", r.nearestTo(5).key());
        assertEquals("Wrong key for " + 8, "this", r.nearestTo(8).key());
        for (int i = 0; i < 20; i++) {
            SemanticRegion<String> reg = r.nearestTo(i);
            if (!seen.contains(reg.key())) {
                seen.add(reg.key());
            }
        }
        assertEquals(Arrays.asList("Hello", "this", "world"), seen);
    }

    private static SemanticRegions<String> collectBetweenRegions() {
        SemanticRegions.SemanticRegionsBuilder<String> bldr = SemanticRegions.builder(String.class);
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                int start = i * 10;
                if (i == 4) {
                    bldr.add("40:100", 40, 100);
                }
                bldr.add(start + ":" + (start + 12), start, start + 12);
                for (int j = start; j < start + 10; j++) {
                    String val = Integer.toString(j);
                    if (j == start + 5) {
                        bldr.add(j + ":" + (j + 5), j, j + 5);
                    }
                    bldr.add(val, j, j + 1);
                }
            }
        }
        return bldr.build();
    }

    @Test
    public void testCodeGeneration() throws IOException, Throwable {
        // Unusual to generate code, compile it and run it in memory
        // in a test, but since we can, why not?
        JFS jfs = JFS.builder().build();
        SemanticRegions<String> ranges = collectBetweenRegions();
        StringBuilder source = new StringBuilder("package foo.bar;\n\n")
                .append("import ").append(SemanticRegions.class.getName()).append(";\n")
                .append("import ").append(SemanticRegionsBuilder.class.getName().replace('$', '.')).append(";\n\n")
                .append("public class Stuff {\n    public static SemanticRegions<String> generate() {\n");
        source.append(ranges.toCode());
        source.append("        return regions;\n    }\n}\n");
        System.out.println("SOURCE:\n" + source);
        jfs.create(UnixPath.get("foo", "bar", "Stuff.java"), SOURCE_PATH, source.toString());
        CompileResult result;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PrintWriter writer = new PrintWriter(out, true, UTF_16);
            result = new JFSCompileBuilder(jfs).addSourceLocation(SOURCE_PATH)
                    .sourceLevel(8).targetLevel(8).compilerOutput(writer)
                    .addOwningLibraryToClasspath(SemanticRegions.class)
                    .addOwningLibraryToClasspath(IndexedResolvable.class)
                    .setOptions(JavacOptions.verboseDefaults())
                    .compile();
            System.out.println(new String(out.toByteArray(), UTF_16));
        }
        assertTrue(result.toString(), result.ok());
        result.rethrow();
        UnixPath classFile = UnixPath.get("foo/bar/Stuff.class");
        JFSCoordinates coords = JFSCoordinates.forPath(classFile, result.outputFiles());
        assertNotNull("Class file not present: " + classFile, coords);
        SemanticRegions<String> generated;
        try (JFSClassLoader ldr = jfs.getClassLoader(CLASS_OUTPUT)) {
            Class<?> type = Class.forName("foo.bar.Stuff", true, ldr);
            Method method = type.getMethod("generate");
            generated = (SemanticRegions<String>) method.invoke(null);
        }
        assertTrue("Regions do not match:\n" + ranges + "\n\nand\n\n" + generated,
                generated.equalTo(ranges));
    }

    @Test
    public void testCollectBetween() {
        SemanticRegions<String> ranges = collectBetweenRegions();
        Set<String> fortyToFifty = new TreeSet<>();
        for (int i = 40; i < 50; i++) {
            fortyToFifty.add(Integer.toString(i));
        }
        Set<String> tested40_60 = new TreeSet<>();

        List<? extends SemanticRegion<String>> forties = ranges.collectBetween(40, 60, str -> {
            tested40_60.add(str);
            return str.indexOf(':') < 0;
        });
        assertTrue(tested40_60.contains("45:50"));
        assertTested(tested40_60, 40, 50);
        assertFalse(forties.isEmpty());
        Set<String> keys = keys(forties);
        assertEquals(fortyToFifty, keys);

        Set<String> tested38_51 = new TreeSet<>();
        tested40_60.remove("40:52");
        Set<String> keys38_51 = keysFor(38, 51, ranges, tested38_51, str -> str.indexOf(':') < 0);
        assertEquals(tested40_60, tested38_51);
        tested40_60.add("40:52");

        Set<String> tested38_55 = new TreeSet<>();
        Set<String> keys38_55 = keysFor(38, 55, ranges, tested38_55, str -> str.indexOf(':') < 0);
        tested38_51.add("40:52");
        assertEquals(keys38_51, keys38_55);

        assertTrue(keysFor(200, 250, ranges, new HashSet<>(), str -> str.indexOf(':') < 0).isEmpty());
    }

    private void assertTested(Set<String> tested, int includeStart, int includeEnd) {
        for (int i = 0; i < 100; i++) {
            String s = Integer.toString(i);
            if (i >= includeStart && i < includeEnd) {
                assertTrue("Should have found " + s + " in " + tested, tested.contains(s));
            } else {
                assertFalse("Should NOT have found " + s + " in " + tested, tested.contains(s));
            }
        }
    }

    private <T extends Comparable<T>> Set<T> keysFor(int start, int end, SemanticRegions<T> regions, Set<T> tested, Predicate<T> test) {
        Set<T> ks = new TreeSet<>();
        List<? extends SemanticRegion<T>> forties = regions.collectBetween(start, end, str -> {
            tested.add(str);
            return test.test(str);
        });
        return keys(forties);
    }

    private static <T extends Comparable<T>> Set<T> keys(Iterable<? extends SemanticRegion<T>> regs) {
        Set<T> result = new TreeSet<>();
        for (SemanticRegion<T> reg : regs) {
            result.add(reg.key());
        }
        return result;
    }

    @Test
    public void testCombineWith() {
        SemanticRegions<String> a = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("c", 40, 60)
                .add("e", 80, 100)
                .add("g", 120, 140).add("g1", 122, 137)
                .add("i", 160, 180)
                .build();

        SemanticRegions<String> b = SemanticRegions.builder(String.class)
                .add("b", 20, 40).add("b1", 22, 27).add("b11", 23, 25)
                .add("d", 60, 80)
                .add("f", 100, 120)
                .add("h", 140, 160).add("h1", 142, 157)
                .add("j", 180, 200)
                .build();

        SemanticRegions<String> expect = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("b", 20, 40).add("b1", 22, 27).add("b11", 23, 25)
                .add("c", 40, 60)
                .add("d", 60, 80)
                .add("e", 80, 100)
                .add("f", 100, 120)
                .add("g", 120, 140).add("g1", 122, 137)
                .add("h", 140, 160).add("h1", 142, 157)
                .add("i", 160, 180)
                .add("j", 180, 200)
                .build();

        SemanticRegions<String> combined = a.combineWith(b);
        assertTrue("Not equal - expected " + expect + "\n got " + combined, combined.equalTo(expect));

        SemanticRegions<String> combinedWithSelf = a.combineWith(a);
        SemanticRegions<String> doubled = SemanticRegions.builder(String.class)
                .add("a", 0, 20)
                .add("a", 0, 20)
                .add("a1", 0, 10)
                .add("a1", 0, 10)
                .add("a11", 1, 5)
                .add("a11", 1, 5)
                .add("c", 40, 60)
                .add("c", 40, 60)
                .add("e", 80, 100)
                .add("e", 80, 100)
                .add("g", 120, 140)
                .add("g", 120, 140)
                .add("g1", 122, 137)
                .add("g1", 122, 137)
                .add("i", 160, 180)
                .add("i", 160, 180)
                .build();

        assertTrue("Not equal - expected " + doubled + "\n got " + combinedWithSelf, combinedWithSelf.equalTo(doubled));

        SemanticRegions<String> aInsert = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("c", 40, 60).build();

        SemanticRegions<String> bInsert = SemanticRegions.builder(String.class)
                .add("a12", 15, 17)
                .add("b", 22, 40)
                .add("c1", 42, 50).build();

        SemanticRegions<String> insertExpected = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5).add("a12", 15, 17)
                .add("b", 22, 40)
                .add("c", 40, 60)
                .add("c1", 42, 50)
                .build();

        SemanticRegions<String> insertCombo = aInsert.combineWith(bInsert);

        assertTrue("Not equal - expected " + insertExpected + "\n got " + insertCombo, insertCombo.equalTo(insertExpected));
    }

    @Test(expected = IllegalStateException.class)
    public void testCombineMustBeConsistent() {
        SemanticRegions<String> a = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("c", 40, 60).build();

        SemanticRegions<String> b = SemanticRegions.builder(String.class)
                .add("c1", 45, 70).build();

        a.combineWith(b);
    }

    @Test
    public void testDifferences() {
        SemanticRegions<String> a = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70).build();

        SemanticRegions<String> b = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70).build();

        assertNoDifferences(a, b);
        assertNoDifferences(a, a);

        b = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70)
                .add("f", 71, 75).add("f1", 72, 74).add("g", 80, 90)
                .build();

        assertAdded(a, b, "f", "f1", "g");
        b = SemanticRegions.builder(String.class)
                .add("a", 0, 21).add("a1", 0, 10).add("a11", 1, 5)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70).build();
        assertAdded(a, b, "a");
        assertRemoved(a, b, "a");

        b = SemanticRegions.builder(String.class)
                .add("a", 0, 21).add("a11", 1, 5)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70).build();

        assertRemoved(a, b, "a", "a1");

        b = SemanticRegions.builder(String.class)
                .add("a", 0, 20).add("a1", 0, 10).add("a11", 1, 5).add("a12", 7, 9)
                .add("b", 22, 24).add("c", 30, 40).add("d", 40, 50)
                .add("d1", 42, 47).add("e", 50, 70).build();

        assertAdded(a, b, "a12");
    }

    private void assertRemoved(SemanticRegions<String> a, SemanticRegions<String> b, String... removedKeys) {
        boolean hadDifferences = SemanticRegions.differences(a, b, (removed, added) -> {
            assertFalse("Nothing removed; added: " + added, removed.isEmpty());
            Set<String> foundRemovedKeys = new TreeSet<>();
            for (SemanticRegion<String> rem : removed) {
                foundRemovedKeys.add(rem.key());
            }
            assertEquals("Removed keys differ", new TreeSet<>(Arrays.asList(removedKeys)), foundRemovedKeys);
        });
        assertTrue("Method did not return true but presumably found differences", hadDifferences);
    }

    private void assertAdded(SemanticRegions<String> a, SemanticRegions<String> b, String... addedKeys) {
        boolean hadDifferences = SemanticRegions.differences(a, b, (removed, added) -> {
            assertFalse("No added items; removed items: " + removed, added.isEmpty());
            Set<String> foundAddedKeys = new TreeSet<>();
            for (SemanticRegion<String> ad : added) {
                foundAddedKeys.add(ad.key());
            }
            assertEquals("Added keys differ", new TreeSet<>(Arrays.asList(addedKeys)), foundAddedKeys);
        });
        assertTrue("Method did not return true but presumably found differences", hadDifferences);
    }

    private void assertNoDifferences(SemanticRegions<String> a, SemanticRegions<String> b) {
        boolean hadDifferences = SemanticRegions.differences(a, b, (removed, added) -> {
            assertTrue(removed.isEmpty());
            assertTrue(added.isEmpty());
        });
        assertFalse("No differences, but method returned true as if there were", hadDifferences);
    }

    @Test
    public void testLastOffsetFinding() {
        int[] vals = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        int off = ArrayUtil.lastOffsetLessThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.lastOffsetLessThanOrEqualTo(6, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(7, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(8, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(9, vals, vals.length);
        assertEquals(1, off);
        off = ArrayUtil.lastOffsetLessThanOrEqualTo(10, vals, vals.length);
        assertEquals(2, off);

        for (int i = 0; i < 65; i++) {
            int expect = i / 5;
            int o = ArrayUtil.lastOffsetLessThanOrEqualTo(i, vals, vals.length);
            assertEquals(i + " got " + o, expect, o);
        }
    }

    @Test
    public void testFirstOffsetFinding() {
        int[] vals = new int[]{0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60};
        int off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(30, vals, vals.length);
        assertEquals(6, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(60, vals, vals.length);
        assertEquals(vals.length - 1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(61, vals, vals.length);
        assertEquals(-1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(31, vals, vals.length);
        assertEquals(7, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(41, vals, vals.length);
        assertEquals(9, off);

        vals = new int[]{0, 5, 10, 15, 20, 20, 20, 25, 30, 30, 35, 35, 40, 45, 50, 50, 50, 55, 60};

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(0, vals, vals.length);
        assertEquals(0, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(30, vals, vals.length);
        assertEquals(8, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(60, vals, vals.length);
        assertEquals(vals.length - 1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(61, vals, vals.length);
        assertEquals(-1, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(31, vals, vals.length);
        assertEquals(10, off);

        off = ArrayUtil.firstOffsetGreaterThanOrEqualTo(41, vals, vals.length);
        assertEquals(13, off);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced1() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced2() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 8, 9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced3() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 8, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced4() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 16, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced5() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 15, 20);
        reg.add("b", 15, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced6() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 10, 20);
        reg.add("b", 10, 15);
        reg.add("b", 15, 21);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced7() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 20, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced8() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 20, 20);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvariantsAreEnforced9() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", -3, -2);
    }

    @Test
    public void testDuplicateNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 0, 10);
        reg.add("b", 20, 30);
        reg.add("c", 20, 30);
        reg.add("d", 30, 40);
        reg.add("e", 30, 40);
        reg.add("f", 32, 38);
        reg.add("g", 33, 37);
        reg.add("h", 60, 80);
        SemanticRegion<String> h = reg.at(62);
        assertEquals("h", h.key());
        SemanticRegion<String> g = reg.at(34);
        SemanticRegion<String> f = reg.at(32);
        SemanticRegion<String> e = reg.at(31);
        SemanticRegion<String> e1 = reg.at(30);
        SemanticRegion<String> c = reg.at(25);
        SemanticRegion<String> c1 = reg.at(20);
        SemanticRegion<String> c2 = reg.at(29);
        assertEquals("g", g.key());
        assertEquals("f", f.key());
        assertEquals("e", e.key());
        assertEquals("c", c.key());
        assertEquals(e, e1);
        assertEquals(c, c1);
        assertEquals(c, c2);
        assertEquals("d", e.parent().key());
        assertEquals("b", c.parent().key());

        assertEquals(Arrays.asList("a", "b", "d", "h"), toList(reg.outermostKeys()));
    }

    @Test
    public void testLastElementHasNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 40);
        reg.add("c", 50, 60);
        reg.add("d", 52, 58);
        reg.add("e", 54, 56);
        reg.add("f", 55, 56);
        assertEquals("f", reg.at(55).key());
        assertEquals("a", reg.at(10).key());
        assertEquals("c", reg.at(50).key());
        assertEquals("d", reg.at(53).key());
        assertEquals("e", reg.at(54).key());
        assertEquals("d", reg.at(56).key());
        assertEquals(6, reg.size());
        assertEquals(Arrays.asList("a", "b", "c"), toList(reg.outermostKeys()));
    }

    @Test
    public void testOneOuterElement() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 0, 100);
        reg.add("b", 10, 90);
        reg.add("c", 20, 80);
        reg.add("d", 30, 70);
        reg.add("e", 40, 60);
        reg.add("f", 45, 55);
        reg.add("g", 50, 51);
        assertEquals(Arrays.asList("a"), toList(reg.outermostKeys()));
        String ts = reg.toString();
        for (int i = 0; i < 10; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "a", r.key());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "a", r.key());
        }

        for (int i = 10; i < 20; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "b", r.key());
            assertNotNull(r.parent());
            assertEquals("a", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            List<SemanticRegion<String>> kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "b", r.key());
            assertNotNull(r.parent());
            assertEquals("a", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
        }

        for (int i = 20; i < 30; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "c", r.key());
            assertNotNull(r.parent());
            assertEquals("b", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            List<SemanticRegion<String>> kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "c", r.key());
            assertNotNull(r.parent());
            assertEquals("b", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            kids = r.parent().children();
            assertFalse("Children of parent contains the parent: " + kids, kids.contains(r.parent()));
            assertTrue("Children of parent of " + r + " do not contain the child: " + kids, kids.contains(r));
            assertEquals(1, r.children().size());
        }
        for (int i = 30; i < 40; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "d", r.key());
            assertNotNull(r.parent());
            assertEquals("c", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "d", r.key());
            assertNotNull(r.parent());
            assertEquals("c", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertEquals(1, r.children().size());
        }
        for (int i = 40; i < 45; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "e", r.key());
            assertNotNull(r.parent());
            assertEquals("d", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
            int other = 99 - i;
            r = reg.at(other);
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertNotNull(other + " " + r + " in " + ts, r);
            assertEquals(other + " " + r + " in " + ts, "e", r.key());
            assertNotNull(r.parent());
            assertEquals("d", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
        }
        for (int i = 45; i < 50; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "f", r.key());
            assertNotNull(r.parent());
            assertEquals("e", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(1, r.children().size());
        }

        for (int i = 50; i < 51; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertTrue(r.parent().children().contains(r));
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "g", r.key());
            assertNotNull(r.parent());
            assertEquals("f", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
            assertEquals(0, r.children().size());
        }

        for (int i = 51; i < 55; i++) {
            SemanticRegion<String> r = reg.at(i);
            assertNotNull(i + " " + r + " in " + ts, r);
            assertEquals(i + " " + r + " in " + ts, "f", r.key());
            assertNotNull(r.parent());
            assertEquals("e", r.parent().key());
            assertNotNull(r.outermost());
            assertEquals("a", r.outermost().key());
            assertTrue("Children of parent of " + r + " do not contain the child: " + r.parent().children(), r.parent().children().contains(r));
        }

        assertNull(reg.at(101));
        assertNull(reg.at(-1));
        sanityCheckRegions(reg);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> toList(Iterable<T> it) {
        if (it instanceof List<?>) {
            return (List<T>) it;
        }
        List<T> result = new ArrayList<>();
        for (T t : it) {
            result.add(t);
        }
        return result;
    }

    @Test
    public void testMultiLayerNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("b1", 32, 50);
        reg.add("b1a1", 35, 45);
        reg.add("b1a1a1", 36, 44);
        reg.add("b1a1a1a1", 38, 42);
        reg.add("b2", 50, 60);
        reg.add("b3", 70, 80);
        reg.add("c", 110, 120);

        assertEquals(Arrays.asList("a", "b", "c"), toList(reg.outermostKeys()));
        assertEquals(9, reg.size());
        assertNotNull(reg.at(110));
        assertEquals("c", reg.at(110).key());
        assertNotNull(reg.at(111));
        assertEquals("c", reg.at(111).key());
        assertNotNull(reg.at(119));
        assertEquals("c", reg.at(119).key());

        assertNotNull(reg.at(10));
        assertEquals("a", reg.at(10).key());
        assertNotNull(reg.at(11));
        assertEquals("a", reg.at(11).key());
        assertNotNull(reg.at(19));
        assertEquals("a", reg.at(19).key());
        assertNull(reg.at(0));
        assertNull(reg.at(1));
        assertNull(reg.at(9));

        SemanticRegion<String> fiftySixty = reg.at(51);
        assertNotNull(fiftySixty);
        assertEquals("b2", fiftySixty.key());
        assertEquals(50, fiftySixty.start());
        assertEquals(60, fiftySixty.end());
        SemanticRegion<String> seventyEighty = reg.at(71);
        assertNotNull(seventyEighty);
        assertEquals("b3", seventyEighty.key());
        assertEquals(70, seventyEighty.start());
        assertEquals(80, seventyEighty.end());

        SemanticRegion<String> deepest = reg.at(39);
        assertNotNull(deepest);
        assertEquals("b1a1a1a1", deepest.key());
        assertEquals(4, deepest.nestingDepth());

        SemanticRegion<String> nextDeepest = reg.at(37);
        assertNotNull(nextDeepest);
        assertEquals("b1a1a1", nextDeepest.key());
        assertEquals(3, nextDeepest.nestingDepth());

        SemanticRegion<String> nextNextDeepest = reg.at(35);
        assertNotNull(nextNextDeepest);
        assertEquals("b1a1", nextNextDeepest.key());
        assertEquals(2, nextNextDeepest.nestingDepth());

        SemanticRegion<String> nextNextNextDeepest = reg.at(34);
        assertNotNull(nextNextNextDeepest);
        assertEquals("b1", nextNextNextDeepest.key());
        assertEquals(1, nextNextNextDeepest.nestingDepth());

        SemanticRegion<String> nextNextNextNextDeepest = reg.at(31);
        assertNotNull(nextNextNextNextDeepest);
        assertEquals("b", nextNextNextNextDeepest.key());
        assertEquals(0, nextNextNextNextDeepest.nestingDepth());

        assertEquals(nextDeepest, deepest.parent());
        assertEquals(nextNextDeepest, nextDeepest.parent());
        assertEquals(nextNextNextDeepest, nextNextDeepest.parent());
        assertEquals(nextNextNextNextDeepest, nextNextNextDeepest.parent());
        assertNull("Parent of " + nextNextNextNextDeepest + " should be null, not " + nextNextNextNextDeepest.parent(), nextNextNextNextDeepest.parent());

        List<SemanticRegion<String>> l = new LinkedList<>();
        l.add(fiftySixty);
        l.add(seventyEighty);
        l.add(deepest);
        l.add(nextDeepest);
        l.add(nextNextDeepest);
        l.add(nextNextNextDeepest);
        l.add(nextNextNextNextDeepest);
        Set<Integer> seen = new HashSet<>();
        for (SemanticRegion<String> s : l) {
            int start = s.start();
            int end = s.end();
            for (int i = start; i < end; i++) {
                if (seen.contains(i)) {
                    continue;
                }
                seen.add(i);
                assertTrue(s.contains(i));
                SemanticRegion<String> found = reg.at(i);
                assertEquals("For position " + i + " of " + s + " in " + reg, s, found);
            }
        }
        sanityCheckRegions(reg);
    }

    @Test
    public void testBoundaryCase1() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("c", 30, 50);
        reg.add("d", 50, 60);
        reg.add("e", 70, 80);
        reg.add("f", 110, 120);
        assertEquals(Arrays.asList("a", "b", "f"), toList(reg.outermostKeys()));
        SemanticRegion<String> sem = reg.at(50);
        assertNotNull(sem);
        assertEquals("Wrong entry " + sem + " for 50 in " + reg, "d", sem.key());
        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(50));
        sanityCheckRegions(reg);
    }

    @Test
    public void testBoundarySearch() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 21, 22);
        reg.add("c", 23, 24);
        reg.add("d", 25, 26);
        reg.add("e", 27, 28);
        reg.add("f", 28, 29);
        reg.add("g", 29, 30);
        reg.add("h", 30, 100);
        reg.add("h1", 30, 99);
        reg.add("h2", 35, 60);
        reg.add("h3", 35, 40);
        reg.add("h4", 38, 40);

        assertNotNull(reg.at(29));

        for (SemanticRegion<String> r : reg) {
            assertNotNull("Null result for region start: " + r.start() + " for " + r, reg.at(r.start()));
            assertNotNull("Null result for region end-1: " + r.end() + " for " + r, reg.at(r.end() - 1));
        }

        assertEquals("h1", reg.at(30).key());
        assertEquals("g", reg.at(29).key());
        assertEquals("f", reg.at(28).key());
        assertEquals("e", reg.at(27).key());
        assertEquals("h1", reg.at(31).key());
        assertEquals("h3", reg.at(35).key());
        assertEquals("h3", reg.at(36).key());
        assertEquals("h2", reg.at(41).key());
        assertEquals("h4", reg.at(38).key());
        assertEquals("h4", reg.at(39).key());
        List<String> keys = new LinkedList<>();
        reg.at(30).keysAtPoint(39, keys);
        assertFalse(keys.isEmpty());
        assertEquals(Arrays.asList("h1", "h2", "h3", "h4"), keys);
        sanityCheckRegions(reg);
    }

    @Test
    public void testSingleLayerNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 100);
        reg.add("c", 30, 50);
        reg.add("d", 50, 60);
        reg.add("e", 70, 80);
        reg.add("f", 110, 120);
        assertEquals(Arrays.asList("a", "b", "f"), toList(reg.outermostKeys()));
        assertEquals(6, reg.size());

        SemanticRegion<String> r = reg.at(32);
        assertEquals("c", r.key());

        Assert.assertArrayEquals(new int[]{-1, -1}, reg.indexAndDepthAt(1));
        Assert.assertArrayEquals(new int[]{0, 0}, reg.indexAndDepthAt(11));
        Assert.assertArrayEquals(new int[]{2, 1}, reg.indexAndDepthAt(31));
//        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(50));
        Assert.assertArrayEquals(new int[]{3, 1}, reg.indexAndDepthAt(51));
        Assert.assertArrayEquals(new int[]{4, 1}, reg.indexAndDepthAt(79));

        int ix = 0;
        for (SemanticRegion<String> ss : reg) {
            assertNotNull(ss);
            int expDepth;
            boolean expectHasChildren = "b".equals(ss.key());
            switch (ss.key()) {
                case "a":
                case "b":
                case "f":
                    expDepth = 0;
                    break;
                default:
                    SemanticRegion<String> par = ss.parent();
                    assertNotNull(ss + " index " + ix + " should not have null parent", par);
                    assertEquals("b", par.key());
                    expDepth = 1;
            }
            assertEquals(expDepth, ss.nestingDepth());
            assertEquals("Unexpected children: " + ss.allChildren() + " in " + ss, expectHasChildren, ss.iterator().hasNext());
            if (!expectHasChildren) {
                for (int i = ss.start(); i < ss.end(); i++) {
                    SemanticRegion<String> found = reg.at(i);
                    assertNotNull(i + " in " + ss + " of " + reg, found);
                    assertEquals(i + " in " + ss + " of " + reg, ss.key(), found.key());
                }
            } else {
                assertNull(ss.parent());
            }
            if (expDepth == 1) {
                assertEquals("b", ss.parent().key());
                assertEquals("b", ss.outermost().key());
            } else {
                assertNull(ss.outermost());
                assertNull(ss.parent());
            }
            ix++;
        }
        sanityCheckRegions(reg);
    }

    @Test
    public void testNoNesting() {
        SemanticRegions<String> reg = new SemanticRegions<>(String.class);
        reg.add("a", 10, 20);
        reg.add("b", 30, 40);
        reg.add("c", 50, 60);
        reg.add("d", 70, 80);
        assertEquals(4, reg.size());
        assertNull(reg.at(1));
        SemanticRegion<String> s = reg.at(10);
        assertEquals("a", s.key());
        assertEquals(10, s.start());
        assertEquals(20, s.end());
        assertTrue(s.contains(10));
        assertTrue(s.contains(15));
        assertTrue(s.contains(19));
        assertFalse(s.contains(20));

        int ix = 0;
        for (SemanticRegion<String> ss : reg) {
            assertEquals(0, ss.nestingDepth());
            for (int i = ss.start(); i < ss.end(); i++) {
                assertEquals(ss, reg.at(i));
            }
        }
        assertEquals(Arrays.asList("a", "b", "c", "d"), toList(reg.outermostKeys()));
        sanityCheckRegions(reg);
    }

    static void sanityCheckRegions(SemanticRegions<String> reg) {
        SemanticRegions.Index<String> index = SemanticRegions.index(reg);
        boolean duplicates = index.size() != reg.size();
        for (SemanticRegion<String> r : reg) {
            SemanticRegion<String> test = reg.forIndex(r.index());
            assertEquals(r, test);
            assertEquals(r.key(), test.key());
            assertEquals(r.start(), test.start());
            assertEquals(r.end(), test.end());
            assertEquals(r.index(), test.index());
            assertEquals(r.nestingDepth(), test.nestingDepth());
            List<SemanticRegion<String>> parents = r.parents();
            List<SemanticRegion<String>> children = r.children();
            List<SemanticRegion<String>> allChildren = r.allChildren();
            assertFalse(children.contains(r));
            assertFalse(parents.contains(r));
            for (int i = r.start(); i < r.end(); i++) {
                SemanticRegion<String> atPoint = reg.at(i);
                assertTrue(r.equals(atPoint) || allChildren.contains(atPoint));
            }
            SemanticRegion<String> parent = r.parent();
            if (parent != null) {
                assertTrue(parent.children().contains(r));
            } else {
                assertEquals(0, r.nestingDepth());
            }
            SemanticRegion<String> outer = r.outermost();
            if (outer != null) {
                assertTrue(outer.allChildren().contains(r));
            }
            int expectedChildDepth = r.nestingDepth() + 1;
            for (SemanticRegion<String> kid : children) {
                int nd = kid.nestingDepth();
                assertEquals("Nesting depth mismatch: " + kid
                        + " child of " + r + " should have nesting depth "
                        + expectedChildDepth + " not " + nd + " (in "
                        + reg + ")", expectedChildDepth, nd);
            }
            if (!duplicates) {
                SemanticRegion<String> fromIndex = index.get(r.key());
                assertNotNull(fromIndex);
                assertEquals(r, fromIndex);
            }
        }
        assertTrue(reg.trim().equalTo(reg.copy()));
    }
}
