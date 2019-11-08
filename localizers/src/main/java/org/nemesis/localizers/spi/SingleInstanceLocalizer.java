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
import java.util.Map;
import java.util.MissingResourceException;
import java.util.logging.Level;
import javax.swing.Icon;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;

/**
 * Localizer for a single static instance of some type - a singleton which
 * perhaps was not intended for UI presentation but needs to be without
 * polluting it with presentation information. Used with extraction keys and
 * similar. This class is principally intended to be used with an annotation
 * processor which will generate subclasses of it and generate a resource bundle
 * containing the appropriate information.
 *
 * @param <T> The type
 */
public abstract class SingleInstanceLocalizer<T> extends Localizer<T> {

    private final String bundleKey;
    private final String iconPath;
    private final Map<String, String> hints;
    private final T instance;
    private boolean noBundle;

    /**
     * Create a new instance which will test for equality with the passed
     * instance, and use the passed bundle key (dot-delimited as in a
     * <code>layer.xml</code> file) or image path (on the classpath,
     * slash-delimited as accepted by <code>ImageUtilities.loadImage()</code>).
     *
     * @param instance The instance
     * @param bundleKeyOrDisplayName The key, in a resource bundle named Bundle
     * adjacent to <i>this</i> class on the classpath, to look up the display
     * name from, using the same format as is used in <code>layer.xml</code>
     * files, e.g. <code>com.mymodule.Bundle#foo</code>. If no bundle is found
     * or no such key is found in the bundle, the bundle key itself is returned
     * and the failure logged.
     * @param iconPath The icon path, as understood by
     * ImageUtilities.loadImageIcon
     * @param hints A set of hints, in the form key,value,key,value - must be an
     * even number of hints
     */
    protected SingleInstanceLocalizer(T instance, String bundleKeyOrDisplayName, String iconPath, String... hints) {
        assert hints.length % 2 == 0 : "Unbalanced hints array";
        this.instance = Checks.notNull("instance", instance);
        this.bundleKey = bundleKeyOrDisplayName;
        this.iconPath = iconPath;
        this.hints = Localizer.mapFromHintList(hints);
    }

    private void logException(String what, T key, String k, MissingResourceException e) {
        if (Localizer.LOG.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder(240);
            sb.append("Missing resource fetching ").append(what)
                    .append(" in instance localizer for ").append(instance)
                    .append(" of ").append(instance).append(" (")
                    .append(instance.getClass().getName()).append(") with ")
                    .append(getClass().getName()).append(" using key '")
                    .append(k).append("'. Will not retry.");
            Localizer.LOG.log(Level.FINEST, sb.toString(), e);
        }
    }

    @Override
    protected String displayName(T obj) {
        if (noBundle) {
            return bundleKey;
        }
        if (bundleKey != null) {
            try {
                return NbBundle.getMessage(getClass(), bundleKey);
            } catch (MissingResourceException mre) {
                noBundle = true;
                logException("displayName", obj, bundleKey, mre);
                return bundleKey;
            }
        }
        return null;
    }

    @Override
    protected final boolean matches(Object o) {
        return o == instance || instance.equals(o);
    }

    @Override
    protected Map<String, String> hints(T o) {
        return hints;
    }

    @Override
    protected Icon icon(T key) {
        if (iconPath != null) {
            return ImageUtilities.loadImageIcon(iconPath, true);
        }
        return super.icon(key);
    }

}
