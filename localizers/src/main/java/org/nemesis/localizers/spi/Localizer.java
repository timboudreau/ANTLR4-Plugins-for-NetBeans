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

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import org.nemesis.localizers.impl.LocalizersTrampoline;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;

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
 * Specific registration paths are used for ad-hoc object instances.
 * </p>
 * <h3>Localizer Lookup</h3>
 * <p>
 * Localizers are registered by the type they can usefully operate on, in named
 * services (<code>Lookups.forPath()</code> is used to find them), not the
 * configuration filesystem. For minimizing the work done in lookup, the path
 * they are registered on corresponds to a prefix (varies on if they localize
 * enum constants, class objects or random instances of some type) plus the
 * fully-qualified class name with '.' characters converted to '/'.
 * </p><p>
 * The looking up of localizers proceeds as follows:
 * <ul>
 * <li>Look up any localizers registered for the exact type returned by
 * <code>getClass()</code></li>
 * <li>Look up any localizers registered for the any type returned by
 * <code>getClass().getInterfaces()</code></li>
 * <li>Repeat the preceding steps for each superclass</li>
 * <li>If nothing has been found, or no found localizer accepts the object
 * passed, try the following:
 * <ul>
 * <li>If the object implements <code>com.mastfrog.abstractions.Wrapper</code>
 * and returns <code>true</code> from <code>has(Class.class | Enum.class),
 * which is not the same object, retry the above steps with the result a
 * call to <code>unwrap()</code> on that type</li>
 * <li>If the object implements <code>java.util.function.Supplier</code>, call
 * <code>get()</code> and try the above steps on the result of that.</code></li>
 * <li>If the object implements <code>com.mastfrog.util.Named</code>, call
 * <code>name()</code> on it and return that if searching for a display
 * name</li>
 * <li>If the object is an Enum, call <code>name()</code> on it if searching for
 * a display name</li>
 * <li>If still nothing is found, call <code>toString()</code> if searching for
 * a display name, return an empty map if searching for hints, or return an
 * empty image or icon if searching for either of those.</li>
 * </ul>
 * </li>
 * </ul>
 * </p>
 *
 * @author Tim Boudreau
 */
@Messages("noValue=No Value")
public abstract class Localizer<T> {

    /**
     * Base registration path for enum types - for example, if you were
     * registering a localizer for an enum "com.foo.MyEnum", you would use:
     * <pre>
     * &#064;ServiceProvider(service=Localizer.class, path="/loc/enums/com/foo/MyEnum")
     * </pre> to have it called to provide names and icons for that enum
     * constant. Note that this constant has a trailing slash;
     */
    public static final String ENUM_LOOKUP_BASE = "loc/enums/"; // keep in sync with same constant on LocalizeAnnotationProcessor
    /**
     * Base registration path for Localizers for <i>Class objects</i> - for
     * example registering a localizer for an type
     * "org.netbeans.modules.refactoring.api.RenameRefactoring", you would use:
     * <pre>
     * &#064;ServiceProvider(service=Localizer.class,
     * path="/loc/type/org/netbeans/modules/refactoring/api/RenameRefactoring")
     * </pre> to have it called to provide names and icons for that enum
     * constant. Note that this constant has a trailing slash;
     */
    public static final String TYPE_LOOKUP_BASE = "loc/types/"; // keep in sync with same constant on LocalizeAnnotationProcessor
    /**
     * Base registration path for ad-hoc object instances - for example, if you
     * were registering a localizer for instances of "com.foo.MyClass", you
     * would use:
     * <pre>
     * &#064;ServiceProvider(service=Localizer.class, path="/loc/instances/com/foo/MyClass")
     * </pre> to have it called to provide names and icons for that enum
     * constant. Note that this constant has a trailing slash;
     */
    public static final String INSTANCE_LOOKUP_BASE = "loc/instances/"; // keep in sync with same constant on LocalizeAnnotationProcessor

    private static final LocalizerRegistry REGISTRY = new LocalizerRegistry();
    static final Pattern BUNDLE_KEY_PATTERN = Pattern.compile("^([a-zA-Z0-9\\-_]+\\..*)\\#(\\S+?)\\s*?$");
    static final Logger LOG = Logger.getLogger(Localizer.class.getName());

    /**
     * Determines if this localizer should be used for querying attributes
     * associated with the passed object.
     *
     * @param o An object
     * @return True if this localizer can provide at least some attributes for
     * the object.
     */
    protected abstract boolean matches(Object o);

    /**
     * Get a localized display name for an object.
     *
     * @param obj An object
     * @return null or a display name
     */
    protected abstract String displayName(T obj);

    /**
     * Get an icon for some object. The default implementation returns a
     * zero-sized icon. Implementations may return null; instances provided to
     * clients will fall back to the empty icon.
     *
     * @param key An object
     * @return An icon
     */
    protected Icon icon(T key) {
        return NoIcon.INSTANCE;
    }

    /**
     * Calls ImageUtilitis.icon2Image on the result of calling
     * <code>icon()</code>.
     *
     * @param key The object
     * @return An image
     */
    protected final Image image(T key) {
        return ImageUtilities.icon2Image(icon(key));
    }

    /**
     * Get any hints associated with this object. The default implementation
     * returns an empty map.
     *
     * @param o An object
     * @return A map of strings
     */
    protected Map<String, String> hints(T o) {
        return Collections.emptyMap();
    }

    /**
     * Takes a string (or null), and if it is a string and follows the standard
     * bundle lookup pattern (as used in <code>layer.xml</code> files)
     * <code>dot.delimited.path.to.BundleName#bundleKey</code>, then will load a
     * string from the specified resource bundle, and if not, will simply return
     * the input.
     *
     * @param result A localized string, if any, or the input string
     * @return A string or null
     * @throws MissingResourceException If the bundle is absent, or the bundle
     * is present but the key is absent. Note that the exception is logged at
     * level FINE here, so need not be further logged if the implementation
     * expects an exception may be acceptable.
     */
    protected final String localizedString(String result) throws MissingResourceException {
        if (result != null) {
            Matcher m = Localizer.BUNDLE_KEY_PATTERN.matcher(result);
            if (m.find()) {
                String bundle = m.group(1);
                String key = m.group(2);
                try {
                    result = NbBundle.getBundle(bundle).getString(key);
                } catch (MissingResourceException ex) {
                    LOG.log(Level.FINE, "Received bundle '" + bundle
                            + "' with key '" + key + "' but failed", ex);
                    throw ex;
                }
            }
        }
        return result;
    }

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
    static <T> String getDisplayName(T obj) {
        if (obj == null) {
            return Bundle.noValue();
        }
        return REGISTRY.find(obj).displayName(obj);
    }

    /**
     * Get an icon for the passed object; if no localizer provides one, will
     * return a zero-width, zero-height non-null icon.
     *
     * @param <T> The object type
     * @param obj An object
     * @return An icon
     */
    static <T> Icon getIcon(T obj) {
        return REGISTRY.find(obj).icon(obj);
    }

    private static final BufferedImage EMPTY_IMAGE
            = new BufferedImage(1, 1, Utilities.isMac()
                    ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_ARGB);

    /**
     * Get an image of the icon associated with this object. May return an
     * empty, zero sized image if none is available. Will not return null.
     *
     * @param <T> The object type
     * @param obj An object
     * @return An image
     */
    static <T> Image getImage(T obj) {
        Icon icon = getIcon(obj);
        if (icon == NoIcon.INSTANCE) {
            return EMPTY_IMAGE;
        }
        return ImageUtilities.icon2Image(getIcon(obj));
    }

    /**
     * Return an unmodifiable map of string hints for the passed object; will
     * not return null.
     *
     * @param <T> The type
     * @param obj An object
     * @return A map
     */
    static <T> Map<String, String> getHints(T obj) {
        return REGISTRY.find(obj).hints(obj);
    }

    /**
     * Construct a Map&lt;String,String&gt; from an array of strings in the
     * format <code>key1, value1, key2, value2</code>.
     *
     * @param hints
     * @return
     */
    protected static Map<String, String> mapFromHintList(String... hints) {
        if (hints.length > 0) {
            if (hints.length % 2 != 0) {
                throw new IllegalArgumentException("Number of hints must be "
                        + "a multiple of 2 to construct a map, but was "
                        + "passed " + hints.length + ": "
                        + Arrays.toString(hints));
            }
            Map<String, String> m = new HashMap<>(hints.length / 2);
            for (int i = 0; i < hints.length; i += 2) {
                String k = hints[i];
                String v = hints[i + 1];
                if (m.containsKey(k)) {
                    throw new IllegalArgumentException("Same hint key is present "
                            + "twice: " + k + " in " + Arrays.toString(hints));
                }
                m.put(k, v);
            }
            return Collections.unmodifiableMap(m);
        } else {
            return Collections.emptyMap();
        }
    }

    static {
        LocalizersTrampoline.DEFAULT = new Tramp();
    }

    private static final class Tramp extends LocalizersTrampoline {

        @Override
        protected <T> String displayName(T obj) {
            return Localizer.getDisplayName(obj);
        }

        @Override
        protected <T> Icon icon(T obj) {
            return Localizer.getIcon(obj);
        }

        @Override
        protected <T> Map<String, String> hints(T obj) {
            return Localizer.getHints(obj);
        }

        @Override
        protected <T> Image image(T obj) {
            return Localizer.getImage(obj);
        }
    }
}
