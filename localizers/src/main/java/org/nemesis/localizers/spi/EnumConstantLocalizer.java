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

import com.mastfrog.util.preconditions.Checks;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.Icon;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;

/**
 * Localizer for enum constants, which delegates to a resource bundle for keys,
 * icons and values - for example, if you have an enum with values FOO and BAR,
 * you might populate the resource bundle with:
 * <pre>
 * foo.displayName=The Foo
 * foo.icon=com/mymodule/icons/foo.png
 * foo.hints=foo bar baz quux
 * </pre> This class is mainly expected to be used for generation by an
 * annotation processor.
 *
 * @param <T>
 */
public abstract class EnumConstantLocalizer<T extends Enum<T>> extends Localizer<T> {

    private final Class<T> enumType;
    private final Map<String, String> hints;
    private final Set<T> missingIcons;
    private final Set<T> missingDisplayNames;
    private final Set<T> missingHints;
    private final Map<T, String> bundles;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public EnumConstantLocalizer(Class<T> enumType, String... hints) {
        this.enumType = Checks.notNull("enumType", enumType);
        this.hints = mapFromHintList(hints);
        missingIcons = EnumSet.noneOf(enumType);
        missingDisplayNames = EnumSet.noneOf(enumType);
        missingHints = EnumSet.noneOf(enumType);
        bundles = initBundles();
    }

    /**
     * Override if different enum constants will either perform lookups not
     * using the enum constant's <code>name()</code> as the bundle key, or if
     * all entries are not in the same bundle. If that is the case, return a map
     * populated with those enum constants the above is true for, with values
     * which are either the bundle key to use (in which case, we will look for a
     * <code>Bundle.properties</code> file next to this class), or the fully
     * qualified bundle path and key in the form
     * <code>dot.delimited.path.to.Bundle#bundleKey</code> (note, do <i>not</i>
     * append <code>.properties</code> to the bundle path).
     * <p>
     * <b>Note:</b> This method is called from the superclass constructor and
     * should not assume any subclass fields are initialized yet.
     * </p>
     *
     * @return A map or null if no bundle-specific info is used
     */
    protected Map<T, String> initBundles() {
        return null;
    }

    protected String fetchString(T item, String computedKey) throws MissingResourceException {
        if (bundles != null && bundles.containsKey(item)) {
            String fetchIt = bundles.get(item);
            if (fetchIt.indexOf('#') < 0) {
                return NbBundle.getMessage(getClass(), fetchIt);
            } else {
                return super.localizedString(fetchIt);
            }
        }
        return NbBundle.getMessage(getClass(), computedKey);
    }

    @Override
    protected boolean matches(Object o) {
        return enumType.isInstance(o);
    }

    private void logException(String fatchingWhat, T key, String k, MissingResourceException e) {
        if (Localizer.LOG.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder(240);
            sb.append("Missing resource fetching ")
                    .append(fatchingWhat)
                    .append(" of ")
                    .append(key)
                    .append(" (")
                    .append(key.getDeclaringClass().getName())
                    .append(") with ")
                    .append(getClass().getName())
                    .append(" using key '")
                    .append(k)
                    .append("'. Will not retry.");
            Localizer.LOG.log(Level.FINEST, sb.toString(), e);
        }
    }

    @Override
    protected Map<String, String> hints(T key) {
        Map<String, String> result = hints;
        if (!missingHints.contains(key)) {
            String k = fqn(key, "hints");
            try {
                String line = fetchString(key, k);
                if (line != null) {
                    String[] values = line.split(",");
                    if (values.length > 0) {
                        Map<String, String> nue = Localizer.mapFromHintList(values);
                        result = new HashMap<>(result);
                        result.putAll(nue);
                        return result;
                    }
                }
            } catch (MissingResourceException mre) {
                missingHints.add(key);
                logException("hints", key, k, mre);
            }
        }
        return hints;
    }

    private String fqn(T obj) {
        return /*enumType.getName().replace('$', '.') + "." +*/ obj.name();
    }

    private String fqn(T obj, String suffix) {
        return fqn(obj) + "." + suffix;
    }

    @Override
    protected Icon icon(T key) {
        if (!missingIcons.contains(key)) {
            String k = fqn(key, "icon");
            try {
                String item = NbBundle.getMessage(getClass(), k);
                return ImageUtilities.loadImageIcon(item, true);
            } catch (MissingResourceException mre) {
                missingIcons.add(key);
                logException("icon", key, k, mre);
            }
        }
        return NoIcon.INSTANCE;
    }

    @Override
    protected String displayName(T key) {
        if (!missingDisplayNames.contains(key)) {
            String k = fqn(key, "displayName");
            try {
                String item = NbBundle.getMessage(getClass(), k);
                return item;
            } catch (MissingResourceException mre) {
                missingDisplayNames.add(key);
                logException("display name", key, k, mre);
            }
        }
        return null;
    }
}
