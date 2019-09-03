package org.nemesis.antlr.memory.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates an unpredictable, legal Java package name, to doubly ensure
 * isolation.
 *
 * @author Tim Boudreau
 */
public final class RandomPackageNames {

    private static volatile int pkgUniquifier = 1;
    private static final String PKG_RANDOM_COMPONENT = packageFriendlyString("x", ThreadLocalRandom.current().nextInt());
    private static final String TS_PKG_COMPONENT = packageFriendlyString("ses", System.currentTimeMillis());

    static String packageFriendlyString(String prefix, long num) {
        return (prefix + Long.toString(num, 36)).replaceAll("-", "");
    }

    public static String newPackageName() {
        return "nbantlr." + PKG_RANDOM_COMPONENT + "." + TS_PKG_COMPONENT + "." + packageFriendlyString("run", pkgUniquifier++);
    }

    private RandomPackageNames() {
        throw new AssertionError();
    }
}
