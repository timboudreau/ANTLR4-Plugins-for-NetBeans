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
package org.nemesis.jfs.isolation;

import static org.junit.Assert.fail;
import org.junit.Test;
import org.nemesis.jfs.isolation.pk1.Pk1;
import org.nemesis.jfs.isolation.pk1.pk1a.Pk1a;

/**
 *
 * @author Tim Boudreau
 */
public class IsolationClassLoaderTest {

    @Test
    public void testSomeMethod() throws Throwable {
        if (true) {
            return;
        }
        IsolationClassLoaderBuilder b = IsolationClassLoader.builder()
                .usingSystemClassLoader()
                .loadingFromParent(Pk1a.class);
        testOne(b, Pk1a.class.getName(), false);
        testOne(b, Pk1.class.getName(), true);
    }

    private void testOne(IsolationClassLoaderBuilder ldr, String instantiate, boolean shouldFail) throws Throwable {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            try (IsolationClassLoader nue = ldr.build()) {
                Thread.currentThread().setContextClassLoader(nue);
                Class<?> type = nue.loadClass(instantiate);
//                assertSame("Wrong classloader", nue, type.getClassLoader());

                System.out.println("CL " + type.getClassLoader());
                Object o = type.newInstance();
                System.out.println("Instantiated " + o + " (" + o.getClass().getName() + ")");
                if (shouldFail) {
                    fail("Instantiated " + o + " but should have failed for " + instantiate);
                }
            }
        } catch (Throwable thrown) {
            if (!shouldFail || thrown instanceof AssertionError) {
                throw thrown;
            }
            thrown.printStackTrace();
        } finally {
            Thread.currentThread().setContextClassLoader(cl);;
        }
    }

}
