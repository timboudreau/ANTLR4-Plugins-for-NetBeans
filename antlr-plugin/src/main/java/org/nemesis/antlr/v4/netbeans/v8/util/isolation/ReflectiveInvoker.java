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
package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

/**
 * Callback which actually refelctively runs code.  Called with an
 * isolated classloader that can be used to load foreign classes, which
 * is already set as the thread's context class loader.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ReflectiveInvoker<T> {

    /**
     * Invoke some foreign code.
     *
     * @param isolatedClassLoader A class loader which can load classes from
     * the classpath provided to the caller.
     *
     * @param addTo If useful, this is the result that will be eventually
     * returned, which can be manipulated.
     *
     * @return An object of some type.
     * @throws Exception If something goes wrong - will be caught and included
     * in the result object.
     */
    T invoke(ClassLoader isolatedClassLoader, ForeignInvocationResult<T> addTo) throws Exception;

    /**
     * If true, System.out and System.err will be wrapped so that
     * output written on the thread that invokes foreign code will
     * be captured and can be from the execution result.
     * <p>
     * Output on threads other is unaffected.
     * </p>
     *
     * @return Whether or not to capture output
     */
    default boolean captureStandardOutputAndError() {
        return false;
    }

    /**
     * If true, a temporary SecurityManager will be installed which blocks
     * calls to System.exit(), and allows the invocation result to capture
     * the attempted exit code.
     * <p>
     * Calls to System.exit() on other threads is not unaffected.
     * </p>
     *
     * @return True to install a SecurityManager and block calls to System.exit()
     */
    default boolean blockSystemExit() {
        return true;
    }
}
