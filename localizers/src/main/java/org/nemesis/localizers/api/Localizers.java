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
package org.nemesis.localizers.api;

import java.awt.Image;
import java.util.Map;
import java.util.Optional;
import javax.swing.Icon;
import org.nemesis.localizers.impl.LocalizersTrampoline;
import static org.nemesis.localizers.impl.LocalizersTrampoline.getHints;

/**
 * Localizers, which are registered in named lookups by type - this provides a
 * framework for providing localized display names, icons, and string hints for
 * anything else that might be needed, for ad-hoc objects which may not be
 * UI-related but need a localized representation in a UI - the use case it was
 * developed for was the need for the refactoring UI (but little else) to give
 * localized names to enum constants used for extracting objects from an Antlr
 * parse, without forcing that enum or something else to implement some
 * interface for localization, when unless refactoring support was being
 * developed, it would never be used.
 * <p>
 * Specific registration paths are used for ad-hoc object instances
 * </p>
 *
 * @author Tim Boudreau
 */
public class Localizers {

    /**
     * Get a display name for an object, searching registered localizers for one
     * which accepts this object, with the following fallbacks:
     * <ul>
     * <li>If the object implements
     * <code>com.mastfrog.abstractions.Named</code>, calls <code>name()</code>
     * on it and returns that.</li>
     * <li>Calls toString() on the object or returns "null" if the argument is
     * null</li>
     * </ul>
     *
     * @param <T> The type
     * @param obj An object
     * @return A string
     */
    public static <T> String displayName(T obj) {
        return LocalizersTrampoline.getDisplayName(obj);
    }

    /**
     * Get an icon for some object. The default implementation returns a
     * zero-sized icon. Implementations may return null; instances provided to
     * clients will fall back to the empty icon.
     *
     * @param key An object
     * @return An icon
     */
    public static <T> Icon icon(T obj) {
        return LocalizersTrampoline.getIcon(obj);
    }

    /**
     * Get an icon for some object. The default implementation returns a
     * zero-sized icon. Implementations may return null; instances provided to
     * clients will fall back to the empty icon.
     *
     * @param key An object
     * @param fallback An icon to use if no localizer provides a default one
     * @return An icon
     */
    public static <T> Icon icon(T obj, Icon fallback) {
        Icon result = LocalizersTrampoline.getIcon(obj);
        if (fallback != null && result.getIconWidth() == 0) {
            result = fallback;
        }
        return result;
    }

    /**
     * Return an unmodifiable map of string hints for the passed object; will
     * not return null.
     *
     * @param <T> The type
     * @param obj An object
     * @return A map
     */
    public static <T> Map<String, String> hints(T obj) {
        return LocalizersTrampoline.getHints(obj);
    }

    /**
     * Get one hint for some object, looking up any registered localizers.
     *
     * @param <T> The type
     * @param obj The object
     * @param hintName The hint name
     * @return An Optional which may or may not contain a value
     */
    static <T> Optional<String> hint(T obj, String hintName) {
        Map<String, String> hints = getHints(obj);
        return hints == null || hints.isEmpty() ? Optional.empty()
                : Optional.ofNullable(hints.get(hintName));
    }

    /**
     * Get an image of the icon associated with this object. May return an
     * empty, zero sized image if none is available. Will not return null.
     *
     * @param <T> The object type
     * @param obj An object
     * @return An image
     */
    public static <T> Image image(T obj) {
        return LocalizersTrampoline.getImage(obj);
    }

}
