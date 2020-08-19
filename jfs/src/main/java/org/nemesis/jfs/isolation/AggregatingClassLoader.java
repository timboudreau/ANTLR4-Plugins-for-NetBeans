/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;

/**
 *
 * @author Tim Boudreau
 */
final class AggregatingClassLoader extends ClassLoader {

    private final ClassLoader[] delegateTo;

    AggregatingClassLoader(ClassLoader parent, Collection<? extends ClassLoader> all) {
        super(parent);
        delegateTo = all.toArray(new ClassLoader[notNull("all", all).size()]);
    }

    AggregatingClassLoader(Collection<? extends ClassLoader> all) {
        delegateTo = all.toArray(new ClassLoader[notNull("all", all).size()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<Enumeration<URL>> all = new ArrayList<>(delegateTo.length);
        for (int i = 0; i < delegateTo.length; i++) {
            Enumeration<URL> one = delegateTo[i].getResources(name);
            if (one.hasMoreElements()) {
                all.add(one);
            }
        }
        return all.isEmpty() ? Collections.emptyEnumeration()
                : new CompoundEnumeration(all.toArray(new Enumeration[all.size()]));
    }

    @Override
    protected Object getClassLoadingLock(String className) {
        return new Object();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = findLoadedClass(name);
        if (result == null) {
            for (int i = 0; i < delegateTo.length; i++) {
                ClassLoader cl = delegateTo[i];
                try {
                    result = cl.loadClass(name);
                    if (result != null) {
                        break;
                    }
                } catch (ClassNotFoundException cnfe) {
                    // do nothing
                }
            }
        }
        return result == null ? super.loadClass(name, resolve) : result;
    }

    @Override
    public void clearAssertionStatus() {
        for (int i = 0; i < delegateTo.length; i++) {
            delegateTo[i].clearAssertionStatus();
        }
    }

    @Override
    public void setClassAssertionStatus(String className, boolean enabled) {
        for (int i = 0; i < delegateTo.length; i++) {
            delegateTo[i].setClassAssertionStatus(className, enabled);
        }
    }

    @Override
    public void setPackageAssertionStatus(String packageName, boolean enabled) {
        for (int i = 0; i < delegateTo.length; i++) {
            delegateTo[i].setPackageAssertionStatus(packageName, enabled);
        }
    }

    @Override
    public void setDefaultAssertionStatus(boolean enabled) {
        for (int i = 0; i < delegateTo.length; i++) {
            delegateTo[i].setDefaultAssertionStatus(enabled);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        for (int i = 0; i < delegateTo.length; i++) {
            InputStream result = delegateTo[i].getResourceAsStream(name);
            if (result != null) {
                return result;
            }
        }
        return super.getResourceAsStream(name);
    }

    @Override
    protected URL findResource(String name) {
        for (int i = 0; i < delegateTo.length; i++) {
            URL result = delegateTo[i].getResource(name);
            if (result != null) {
                return result;
            }
        }
        return super.findResource(name);
    }

    @Override
    public URL getResource(String name) {
        return findResource(name);
    }

//    @Override //JDK9
    protected URL findResource(String moduleName, String name) throws IOException {
        for (int i = 0; i < delegateTo.length; i++) {
            URL result = delegateTo[i].getResource(name);
            if (result != null) {
                return result;
            }
        }
        // XXX implement property when not supporting JDK 8 anymore
        return findResource(name);
    }

//    @Override
    protected Class<?> findClass(String moduleName, String name) {
        Class<?> result = findLoadedClass(name);
        if (result == null) {
            for (int i = 0; i < delegateTo.length; i++) {
                try {
                    result = delegateTo[i].loadClass(name);
                    if (result != null) {
                        return result;
                    }
                } catch (ClassNotFoundException ex) {
                    // do nothing
                }
            }
        }
        return super.findClass(moduleName, name);
    }

    @Override
    public String getName() {
        StringBuilder sb = new StringBuilder("AggregatingClassLoader(");
        for (int i = 0; i < delegateTo.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(delegateTo[i].getName());
        }
        return sb.toString();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> result = findLoadedClass(name);
        if (result == null) {
            for (int i = 0; i < delegateTo.length; i++) {
                try {
                    result = delegateTo[i].loadClass(name);
                    if (result != null) {
                        return result;
                    }
                } catch (ClassNotFoundException ex) {
                    // do nothing
                }
            }
        }
        return loadClass(name, true);
    }

    final class CompoundEnumeration<E> implements Enumeration<E> {

        private final Enumeration<E>[] enums;
        private int index;

        public CompoundEnumeration(Enumeration<E>[] enums) {
            this.enums = enums;
        }

        private boolean next() {
            while (index < enums.length) {
                if (enums[index] != null && enums[index].hasMoreElements()) {
                    return true;
                }
                index++;
            }
            return false;
        }

        public boolean hasMoreElements() {
            return next();
        }

        public E nextElement() {
            if (!next()) {
                throw new NoSuchElementException();
            }
            return enums[index].nextElement();
        }
    }
}
