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
package org.nemesis.antlr.v4.netbeans.v8.project.helper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ClassFileNameIterableTest {

    @Test
    public void testNoPackage() {
        ClassFileNameIterable iter = new ClassFileNameIterable("DefaultPackageClass");
        List<String> expected = Arrays.asList(
                "DefaultPackageClass.class"
        );
        List<String> got = new ArrayList<>();
        for (Path p : iter) {
            assertNotNull(p);
            got.add(p.toString());
        }
        assertEquals(expected, got);
    }

    @Test
    public void testAllPossibleClassFileNamesAreGeneratedFromFQN() {
        ClassFileNameIterable iter = new ClassFileNameIterable("com.foo.bar.baz.SomeClass.SomeInnerClass.SomeInnerInnerClass");
        List<String> expected = Arrays.asList(
                "com/foo/bar/baz/SomeClass/SomeInnerClass/SomeInnerInnerClass.class",
                "com/foo/bar/baz/SomeClass/SomeInnerClass$SomeInnerInnerClass.class",
                "com/foo/bar/baz/SomeClass$SomeInnerClass$SomeInnerInnerClass.class",
                "com/foo/bar/baz$SomeClass$SomeInnerClass$SomeInnerInnerClass.class",
                "com/foo/bar$baz$SomeClass$SomeInnerClass$SomeInnerInnerClass.class",
                "com/foo$bar$baz$SomeClass$SomeInnerClass$SomeInnerInnerClass.class",
                "com$foo$bar$baz$SomeClass$SomeInnerClass$SomeInnerInnerClass.class"
        );
        List<String> got = new ArrayList<>();
        for (Path p : iter) {
            assertNotNull(p);
            got.add(p.toString());
        }
        assertEquals(expected, got);
    }
}
