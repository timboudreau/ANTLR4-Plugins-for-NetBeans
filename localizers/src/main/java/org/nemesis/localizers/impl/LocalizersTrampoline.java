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
package org.nemesis.localizers.impl;

import java.awt.Image;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import org.nemesis.localizers.spi.Localizer;

/**
 *
 * @author Tim Boudreau
 */
public abstract class LocalizersTrampoline {

    public static LocalizersTrampoline DEFAULT;

    public static LocalizersTrampoline getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = Localizer.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(LocalizersTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public static <T> String getDisplayName(T obj) {
        return getDefault().displayName(obj);
    }

    /**
     * Get an icon for some object. The default implementation returns a
     * zero-sized icon. Implementations may return null; instances provided to
     * clients will fall back to the empty icon.
     *
     * @param key An object
     * @return An icon
     */
    public static <T> Icon getIcon(T obj) {
        return getDefault().icon(obj);
    }

    public static <T> Image getImage(T obj) {
        return getDefault().image(obj);
    }

    public static <T> Map<String, String> getHints(T obj) {
        return getDefault().<T>hints(obj);
    }

    protected abstract <T> String displayName(T obj);

    protected abstract <T> Icon icon(T obj);

    protected abstract <T> Image image(T obj);

    protected abstract <T> Map<String, String> hints(T obj);

}
