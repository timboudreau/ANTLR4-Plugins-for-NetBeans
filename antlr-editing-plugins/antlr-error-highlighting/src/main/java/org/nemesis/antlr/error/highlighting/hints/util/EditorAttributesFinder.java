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
package org.nemesis.antlr.error.highlighting.hints.util;

import java.awt.Color;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import static org.nemesis.antlr.common.AntlrConstants.ANTLR_MIME_TYPE;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 *
 * @author Tim Boudreau
 */
public final class EditorAttributesFinder implements LookupListener, Function<String, AttributeSet> {

    private Lookup.Result<FontColorSettings> settingsResult;
    private FontColorSettings settings;
    private AttributeSet errors;
    private AttributeSet warnings;

    private FontColorSettings settings() {
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
        Lookup lookup = MimeLookup.getLookup(MimePath.parse(ANTLR_MIME_TYPE));
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
    public void resultChanged(LookupEvent le) {
        settings = null;
        errors = null;
        warnings = null;
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
        FontColorSettings colorings = settings();
        if (colorings == null) {
            return null;
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

    public AttributeSet find(Supplier<AttributeSet> failover, String... names) {
        AttributeSet result = find(names);
        return result == null ? failover.get() : result;
    }

    @Override
    public AttributeSet apply(String t) {
        return find(SimpleAttributeSet::new, t);
    }

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

    public AttributeSet warnings() {
        if (warnings != null) {
            return warnings;
        }
        AttributeSet result = find("error", "errors");
        if (result == null) {
            result = defaultWarnings();
        }
        return warnings = result;
    }
}
