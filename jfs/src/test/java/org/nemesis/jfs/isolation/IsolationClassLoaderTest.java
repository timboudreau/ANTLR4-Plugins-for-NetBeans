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
