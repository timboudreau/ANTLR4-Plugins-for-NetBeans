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
package org.nemesis.antlr.spi.language;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Cannot seem to find a way to reparse a modified file and have the
 * infrastructure actually notice that it has been modified and not return a
 * stale parse result. May be because tests are missing native file listener? At
 * any rate this hack should do it for now. Basically, we need to invalidate the
 * parsing plumbing's cached Source instances for all files belonging to the
 * mime type of some grammar file whenever the grammar file changes.
 *
 * @author Tim Boudreau
 */
final class SourceInvalidator implements Consumer<FileObject> {

    private Class<?> sourceAccessorClass;
    private Object instance;
    private Method invalidateMethod;
    private static boolean reflectionFailed;
    private static final Logger LOG = Logger.getLogger(SourceInvalidator.class.getName());

    SourceInvalidator() {
    }

    public static Consumer<FileObject> create() {
        if (!reflectionFailed) {
            SourceInvalidator invalidator = new SourceInvalidator();
            try {
                invalidator.invalidateMethod();
                return invalidator;
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                LOG.log(Level.INFO, null, ex);
            }
        }
        return (ignored) -> {
        };
    }

    Class<?> getSourceAccessorClass(ClassLoader ldr) throws ClassNotFoundException {
        if (sourceAccessorClass != null) {
            return sourceAccessorClass;
        }
        sourceAccessorClass = ldr.loadClass("org.netbeans.modules.parsing.impl.SourceAccessor");
        return sourceAccessorClass;
    }

    Method getGetInstanceMethod(ClassLoader ldr) throws ClassNotFoundException, NoSuchMethodException {
        Class<?> sa = getSourceAccessorClass(ldr);
        return sa.getMethod("getINSTANCE");
    }

    Object getInstance(ClassLoader ldr) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        return getGetInstanceMethod(ldr).invoke(null);
    }

    Object instance() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (instance != null) {
            return instance;
        }
        return instance = getInstance(Thread.currentThread().getContextClassLoader());
    }

    Method invalidateMethod() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        if (invalidateMethod != null) {
            return invalidateMethod;
        }
        Object obj = instance();
        if (obj == null) {
            return null;
        }
        Class<?> type = obj.getClass();
        Method m = invalidateMethod = type.getDeclaredMethod("invalidate", Source.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    void doInvalidate(FileObject file) {
        try {
            Method m = invalidateMethod();
            Source src = Source.create(file);
            LOG.log(Level.FINEST, "Invalidate Source instance {0} for {1}",
                    new Object[]{src, file});
            m.invoke(instance(), src, true);
        } catch (ClassNotFoundException | NoSuchMethodException
                | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException ex) {
            reflectionFailed = true;
            LOG.log(Level.WARNING, null, ex);
        }
    }

    @Override
    public void accept(FileObject t) {
        if (reflectionFailed) {
            return;
        }
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader nue = Lookup.getDefault().lookup(ClassLoader.class);
            Thread.currentThread().setContextClassLoader(nue);
            doInvalidate(t);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
