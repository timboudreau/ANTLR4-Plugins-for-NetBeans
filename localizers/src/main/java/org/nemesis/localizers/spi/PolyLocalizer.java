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

import com.mastfrog.util.strings.Strings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import javax.swing.Icon;

/**
 * Wraps a bunch of localizers and returns values from the first one that
 * returns non-null, with the exception of hints which are composed together if
 * non-empty.
 *
 * @author Tim Boudreau
 */
final class PolyLocalizer<T> extends Localizer<T> {

    private final List<Localizer<? super T>> all;

    public PolyLocalizer(List<Localizer<? super T>> all) {
        this.all = all;
    }

    @Override
    public String toString() {
        return "PolyLocalizer(" + Strings.join(',', all) + ")";
    }

    @Override
    protected boolean matches(Object o) {
        for (Localizer<? super T> l : all) {
            if (l.matches(o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Map<String, String> hints(T o) {
        Map<String, String> result = null;
        for (Localizer<? super T> l : all) {
            Map<String, String> m = l.hints(o);
            if (!m.isEmpty()) {
                if (result == null) {
                    result = m;
                } else {
                    result = new HashMap<>(result);
                    result.putAll(m);
                }
            }
        }
        return result == null ? super.hints(o) : result;
    }

    @Override
    protected Icon icon(T key) {
        for (Localizer<? super T> l : all) {
            Icon result = l.icon(key);
            if (result != NoIcon.INSTANCE) {
                return result;
            }
        }
        return super.icon(key);
    }

    @Override
    protected String displayName(T obj) {
        String result = null;
        for (Localizer<? super T> l : all) {
            try {
                result = localizedString(l.displayName(obj));
            } catch (MissingResourceException ex) {
                // already logged
            }
            if (result != null) {
                break;
            }
        }
        return result;
    }


}
