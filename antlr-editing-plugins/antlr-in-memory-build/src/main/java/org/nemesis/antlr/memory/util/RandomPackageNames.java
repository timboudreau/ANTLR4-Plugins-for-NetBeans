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
