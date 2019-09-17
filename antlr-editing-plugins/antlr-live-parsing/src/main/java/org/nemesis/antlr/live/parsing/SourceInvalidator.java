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
package org.nemesis.antlr.live.parsing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.netbeans.modules.parsing.api.Source;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Cannot seem to find a way to reparse a modified file and have the
 * infrastructure actually notice that it has been modified and not return a
 * stale parse result. May be because tests are missing native file listener? At
 * any rate this hack should do it for now.
 *
 * @author Tim Boudreau
 */
public final class SourceInvalidator implements Consumer<FileObject> {

    private Class<?> sourceAccessorClass;
    private Object instance;
    private Method invalidateMethod;

    SourceInvalidator() {
    }

    public static Consumer<FileObject> create() {
        SourceInvalidator invalidator = new SourceInvalidator();
        try {
            invalidator.invalidateMethod();
            return invalidator;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            EmbeddedAntlrParser.LOG.log(Level.INFO, null, ex);
            return (ignored) -> {
            };
        }
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
            System.out.println("INVALDIATE " + file);
            m.invoke(instance(), src, true);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            EmbeddedAntlrParser.LOG.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void accept(FileObject t) {
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
