/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
