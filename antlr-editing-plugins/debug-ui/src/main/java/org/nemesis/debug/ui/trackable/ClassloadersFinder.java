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
package org.nemesis.debug.ui.trackable;

import java.net.URLStreamHandlerFactory;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nemesis.debug.api.TrackingRoots;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = TrackingRoots.class, position = 2000)
public class ClassloadersFinder implements TrackingRoots {

    private static final Pattern PAT = Pattern.compile("^ModuleCL@.*?\\[(.*?)\\]$");

    @Override
    public Predicate<? super Object> shouldIgnorePredicate() {
        return obj -> {
            if (obj instanceof Logger) {
                return false;
            }
            if (obj.getClass().getName().startsWith("org.nemesis.debug")) {
                return true;
            }
            return false;
        };
    }

    @Override
    public void collect(BiConsumer<String, Object> nameAndObject) {
        for (URLStreamHandlerFactory fac : Lookup.getDefault().lookupAll(URLStreamHandlerFactory.class)) {
            if (fac != null) {
                nameAndObject.accept(fac.getClass().getSimpleName(), fac);
            }
        }

        Map<ClassLoader, Boolean> m = new IdentityHashMap();
        for (Object o : Lookup.getDefault().lookupAll(Object.class)) {
            if (o == null) {
                continue;
            }
            ClassLoader cl = o.getClass().getClassLoader();
            if (cl == null) {
                continue;
            }
            try {
                if (!m.containsKey(cl)) {
                    m.put(cl, true);
                    String name = cl.getName();
                    if ("OneModuleClassLoader".equals(cl.getClass().getSimpleName())) {
                        String toS = cl.toString();
                        if (toS != null) {
                            Matcher mat = PAT.matcher(toS);
                            if (mat.find()) {
                                name = mat.group(1);
                            }
                        }
                    }
                    if (name == null) {
                        name = cl.getClass().getSimpleName();
                    } else {
                        if (!cl.getClass().getSimpleName().equals("OneModuleClassLoader")) {
                            name = name + "(" + cl.getClass().getSimpleName() + ")";
                        }
                    }
                    nameAndObject.accept(name, cl);
                }
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            }
        }
    }
}
