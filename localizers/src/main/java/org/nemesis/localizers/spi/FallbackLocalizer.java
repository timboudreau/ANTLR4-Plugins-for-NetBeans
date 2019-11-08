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
package org.nemesis.localizers.spi;

import com.mastfrog.abstractions.Named;
import com.mastfrog.abstractions.Wrapper;
import java.text.NumberFormat;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.Icon;

/**
 * The last result localizer
 *
 * @author Tim Boudreau
 */
final class FallbackLocalizer extends Localizer<Object> {

    static final FallbackLocalizer INSTANCE = new FallbackLocalizer();

    @Override
    protected String displayName(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof Named) {
            return ((Named) obj).name();
        }
        if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name();
        }
        if (obj instanceof Wrapper<?>) {
            Wrapper<?> w = (Wrapper<?>) obj;
            if (w.has(Enum.class)) {
                Enum<?> e = w.find(Enum.class);
                if (e != obj) {
                    return Localizer.getDisplayName(e);
                }
            }
            if (w.has(Named.class)) {
                return w.find(Named.class).name();
            }
            if (w.has(Class.class)) {
                Class<?> type = w.find(Class.class);
                if (type != obj) {
                    Localizer.getDisplayName(type);
                }
            }
            if (w.has(Number.class)) {
                return NumberFormat.getInstance().format(w.find(Number.class));
            }
        }
        if (obj instanceof Class<?>) {
            return ((Class<?>) obj).getSimpleName();
        }
        if (obj instanceof Number) {
            return NumberFormat.getInstance().format((Number) obj);
        }
        if (obj instanceof Supplier<?>) {
            Object o = ((Supplier<?>) obj).get();
            if (o != obj) {
                return displayName(o);
            }
        }
        return obj.toString();
    }

    @Override
    protected Icon icon(Object obj) {
        if (obj instanceof Wrapper<?>) {
            Wrapper<?> w = (Wrapper<?>) obj;
            if (w.has(Enum.class)) {
                Enum<?> e = w.find(Enum.class);
                if (e != obj) {
                    return icon(e);
                }
            }
            if (w.has(Class.class)) {
                return icon(w.find(Class.class));
            }
        }
        return super.icon(obj);
    }

    @Override
    protected Map<String, String> hints(Object obj) {
        if (obj instanceof Wrapper<?>) {
            Wrapper<?> w = (Wrapper<?>) obj;
            if (w.has(Enum.class)) {
                return hints(w.find(Enum.class));
            }
            if (w.has(Class.class)) {
                return hints(w.find(Class.class));
            }
        } else if (obj instanceof Supplier<?>) {
            Object o = ((Supplier<?>) obj).get();
            if (o != obj) {
                return hints(o);
            }
        }
        return super.hints(obj);
    }

    @Override
    protected boolean matches(Object o) {
        return true;
    }
}
