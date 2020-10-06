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
package org.nemesis.antlr.spi.language.highlighting;

import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 * Utility for fetching font colorings, which tracks and updates itself if the colorings
 * change, to simplify writing highlighters that need to consumer colorings.
 *
 * @author Tim Boudreau
 */
public final class EditorAttributesFinder implements LookupListener, Function<String, AttributeSet> {

    private static final Map<String, EditorAttributesFinder> FINDERS
            = com.mastfrog.util.collections.MapFactories.WEAK_VALUE.createMap(7, true);
    private Lookup.Result<FontColorSettings> settingsResult;
    private FontColorSettings settings;
    private AttributeSet errors;
    private AttributeSet warnings;
    private Map<String, AttributeSet> derived;
    private final String mimeType;

    EditorAttributesFinder(String mimeType) {
        this.mimeType = mimeType;
    }

    public static EditorAttributesFinder forMimeType(String mimeType) {
        return FINDERS.computeIfAbsent(mimeType, EditorAttributesFinder::new);
    }

    private synchronized FontColorSettings settings() {
        if (settings != null) {
            return settings;
        }
        if (settingsResult != null) {
            Collection<? extends FontColorSettings> all = settingsResult.allInstances();
            if (!all.isEmpty()) {
                return settings = all.iterator().next();
            } else {
                return null;
            }
        }
        Lookup lookup = MimeLookup.getLookup(MimePath.parse(mimeType));
        FontColorSettings result = null;
        settingsResult = lookup.lookupResult(FontColorSettings.class);
        Collection<? extends FontColorSettings> all = settingsResult.allInstances();
        if (!all.isEmpty()) {
            result = all.iterator().next();
        }
        settingsResult.addLookupListener(this);
        return settings = result;
    }

    @Override
    public synchronized void resultChanged(LookupEvent le) {
        settings = null;
        errors = null;
        warnings = null;
        derived = null;
    }

    private AttributeSet defaultErrors() {
        return AttributesUtilities.createImmutable(
                EditorStyleConstants.WaveUnderlineColor,
                Color.RED.darker());
    }

    private AttributeSet defaultWarnings() {
        return AttributesUtilities.createImmutable(
                EditorStyleConstants.WaveUnderlineColor,
                Color.ORANGE);
    }

    private AttributeSet find(String... names) {
        FontColorSettings colorings;
        Map<String, AttributeSet> derived;
        synchronized (this) {
            colorings = settings();
            derived = this.derived;
        }
        if (colorings == null) {
            return null;
        }
        if (derived != null) {
            for (String name : names) {
                AttributeSet result = derived.get(name);
                if (result != null) {
                    return result;
                }
            }
        }
        for (String nm : names) {
            AttributeSet attrs = colorings.getFontColors(nm);
            if (attrs == null) {
                attrs = colorings.getTokenFontColors(nm);
            }
            if (attrs != null) {
                return attrs;
            }
        }
        return null;
    }

    /**
     * Find an attribute set matching one of the passed names, using the
     * failover supplier if none.
     *
     * @param failover The failover supplier if no coloring exists that matches
     * any of the names
     * @param names A list of names to look up in the FontColoringSettings for
     * this mime type
     * @return An attribute set
     */
    public AttributeSet find(Supplier<AttributeSet> failover, String... names) {
        AttributeSet result = find(names);
        return result == null ? failover.get() : result;
    }

    /**
     * Get an attribute set with the
     *
     * @param t
     * @return
     */
    @Override
    public AttributeSet apply(String t) {
        return find(EMPTY_SUPPLIER, t);
    }

    /**
     * Create an attribute set that somehow munges colors from an original, and
     * cache it under the name passed, clearing it if the global set of colors
     * for this mime type changes.
     *
     * @param from The name
     * @param func A function to process the original set
     * @return A derived attribute set
     */
    public AttributeSet derive(String from, Function<AttributeSet, AttributeSet> func) {
        Map<String, AttributeSet> derivedLocal;
        synchronized (this) {
            derivedLocal = this.derived;
            if (derivedLocal == null) {
                derivedLocal = this.derived = new HashMap<>();
            }
        }
        AttributeSet result = derivedLocal.get(from);
        if (result == null) {
            result = func.apply(find(EMPTY_SUPPLIER, from));
            if (result != null) {
                derivedLocal.put(from, result);
            }
        }
        return result;
    }

    /**
     * Derive an attribute set with a tooltip from an original.
     *
     * @param orig The original set, or null if none
     * @param tooltip The tool tip text
     * @return A new attribute set
     */
    public AttributeSet withTooltip(AttributeSet orig, String tooltip) {
        if (orig == null) {
            SimpleAttributeSet res = new SimpleAttributeSet();
            res.addAttribute(EditorStyleConstants.Tooltip, tooltip);
            return res;
        } else {
            SimpleAttributeSet res = new SimpleAttributeSet();
            res.addAttribute(EditorStyleConstants.Tooltip, tooltip);
            return AttributesUtilities.createComposite(orig, res);
        }
    }

    /**
     * Defive an attribute set from the first one matching the passed array of
     * names, adding the passed tool tip text to it.
     *
     * @param tooltip The tool tip text
     * @param names A list of names to search the registered colorings for
     * @return An attribute set containing the original colorings plus the tool
     * tip
     */
    public AttributeSet withTooltip(String tooltip, String... names) {
        AttributeSet result = find(names);
        if (result == null) {
            SimpleAttributeSet res = new SimpleAttributeSet();
            res.addAttribute(EditorStyleConstants.Tooltip, tooltip);
            return res;
        } else {
            SimpleAttributeSet res = new SimpleAttributeSet();
            res.addAttribute(EditorStyleConstants.Tooltip, tooltip);
            return AttributesUtilities.createComposite(result, res);
        }
    }

    /**
     * Get the standard coloring for errors.
     *
     * @return An attribute set
     */
    public AttributeSet errors() {
        if (errors != null) {
            return errors;
        }
        AttributeSet result = find("error", "errors");
        if (result == null) {
            result = defaultErrors();
        }
        return errors = result;
    }

    /**
     * Get the standard coloring for warnings.
     *
     * @return An attribute set
     */
    public AttributeSet warnings() {
        if (warnings != null) {
            return warnings;
        }
        AttributeSet result = find("warning", "warnings");
        if (result == null) {
            result = defaultWarnings();
        }
        return warnings = result;
    }

    private static final AttributeSet EMPTY = new EmptyAttributeSet();
    static final Supplier<AttributeSet> EMPTY_SUPPLIER = () -> EMPTY;

    private static final class EmptyAttributeSet implements AttributeSet {

        @Override
        public int getAttributeCount() {
            return 0;
        }

        @Override
        public boolean isDefined(Object o) {
            return false;
        }

        @Override
        public boolean isEqual(AttributeSet attr) {
            return attr.getAttributeCount() == 0;
        }

        @Override
        public AttributeSet copyAttributes() {
            return this;
        }

        @Override
        public Object getAttribute(Object o) {
            return null;
        }

        @Override
        public Enumeration<?> getAttributeNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public boolean containsAttribute(Object o, Object o1) {
            return false;
        }

        @Override
        public boolean containsAttributes(AttributeSet attributes) {
            return false;
        }

        @Override
        public AttributeSet getResolveParent() {
            return null;
        }
    }
}
