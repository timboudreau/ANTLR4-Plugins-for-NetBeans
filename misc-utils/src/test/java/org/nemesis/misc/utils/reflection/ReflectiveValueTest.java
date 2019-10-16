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
package org.nemesis.misc.utils.reflection;

import org.nemesis.misc.utils.reflection.ReflectionPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.nemesis.misc.utils.reflection.ReflectionPath.ResolutionResultType;
import static org.nemesis.misc.utils.reflection.ReflectiveValueTest.ErrB.ERRB_B;
import static org.nemesis.misc.utils.reflection.ReflectiveValueTest.ErrB.ERRB_C;

/**
 *
 * @author Tim Boudreau
 */
public class ReflectiveValueTest {

    @Test
    public void testSomeMethod() {
        A a = new A("hello", ErrA.THING_TWO);
        ReflectionPath<String> hello = new ReflectionPath<>("stuff", String.class); // 0
        ReflectionPath<String> two = new ReflectionPath<>("err.msg", String.class); // 1
        ReflectionPath<String> two1 = new ReflectionPath<>("err.message()", String.class); // 2
        ReflectionPath<String> errb_b = new ReflectionPath<>("err.b.toString()", String.class); // 3
        ReflectionPath<String> thing_two = new ReflectionPath<>("err.name()", String.class); // 4
        ReflectionPath<Integer> int_two = new ReflectionPath<>("err.b.code()", Integer.class); // 5
        ReflectionPath<Integer> null_val  = new ReflectionPath<>("err.bummer", Integer.class); // 6
        ReflectionPath<Integer> null_val2  = new ReflectionPath<>("err.bummerfudge", Integer.class); // 7
        ReflectionPath<Integer> none = new ReflectionPath<>("err.q.foo.baz()", Integer.class); // 8
        ReflectionPath<String> thrown = new ReflectionPath<>("err.b.throwIt()", String.class); // 5

        ReflectionPath<?>[] vals = new ReflectionPath<?>[]{hello, two, two1, errb_b, thing_two, int_two, 
            null_val, null_val2, none, thrown};
        Object[] expect = {"hello", "two", "two", "errb_b", "THING_TWO", 2, null, null, null, null};

        ReflectionPath.resolve(a, vals);
        for (int i = 0; i < vals.length; i++) {
            ReflectionPath<?> v = vals[i];
            System.out.println(i + ": " + v.path() + ": " + v.result());
        }

        for (int i = 0; i < vals.length; i++) {
            ReflectionPath<?> v = vals[i];
            if (i == 8) {
                assertEquals(v.result().type(), ResolutionResultType.NO_SUCH_ELEMENT);
            }
            if (i == 9) {
                assertEquals(v.result().type(), ResolutionResultType.EXCEPTION);
                assertTrue(v.result().thrown() + "", v.result().thrown() instanceof IllegalStateException);
            }
            if (i < 6) {
                assertTrue(v.get().isPresent());
                assertEquals(ResolutionResultType.SUCCESS, v.result().type());
                assertEquals(expect[i], v.get().get());
            }
        }

    }

    public static final class A {

        public final String stuff;

        public final ErrA err;

        public A(String stuff, ErrA err) {
            this.stuff = stuff;
            this.err = err;
        }
    }

    public enum ErrA {
        THING_ONE("one", ERRB_C),
        THING_TWO("two", ERRB_B),;

        final String bummer = null;
        private final String msg;
        private final ErrB b;

        ErrA(String msg, ErrB b) {
            this.msg = msg;
            this.b = b;
        }

        public String message() {
            return msg;
        }
    }

    public enum ErrB {
        ERRB_A(1), ERRB_B(2), ERRB_C(2);
        private int code;

        ErrB(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public String toString() {
            return name().toLowerCase();
        }

        public String throwIt() {
            throw new IllegalStateException("Foo");
        }
    }

}
