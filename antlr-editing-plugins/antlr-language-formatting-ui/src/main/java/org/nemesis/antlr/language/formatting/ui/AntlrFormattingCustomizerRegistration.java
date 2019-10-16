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
package org.nemesis.antlr.language.formatting.ui;

import java.util.prefs.Preferences;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.netbeans.modules.options.editor.spi.PreferencesCustomizer;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = PreferencesCustomizer.Factory.class, path = "OptionsDialog/Editor/Formatting/text/x-g4")
public final class AntlrFormattingCustomizerRegistration implements PreferencesCustomizer.Factory {

    @Override
    public PreferencesCustomizer create(Preferences p) {
        return new AntlrFormattingCustomizer(new AntlrFormatterConfig(p));
    }

}
