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
package org.nemesis.antlr.v4.netbeans.v8.project.helper.java;

/******************************************************************************
 * This class loader keeps a reference of classes it loads (job done by       *
 * ClassLoader) but we do not keep a static reference of class loader you     *
 * instantiate, so as soon as your TemporaryClassLoader instance is out of    *
 * scope, it may be garbage collected and classes loaded by it as well if you *
 * do not use them.                                                           *
 *                                                                            *
 * @author Frédéric Yvon Vinet                                                *
 */
public class TemporaryClassLoader extends ClassLoader {
    public TemporaryClassLoader(ClassLoader parent) {
        super(parent);
    }

    
    @Override
    public Class<? extends Object> loadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name);
    }
}
