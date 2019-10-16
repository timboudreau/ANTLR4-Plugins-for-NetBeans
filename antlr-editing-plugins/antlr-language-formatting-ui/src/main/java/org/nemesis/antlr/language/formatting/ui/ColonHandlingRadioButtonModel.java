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

import org.nemesis.antlr.language.formatting.config.ColonHandling;
import java.util.Objects;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;

/**
 *
 * @author Tim Boudreau
 */
final class ColonHandlingRadioButtonModel extends ConfigButtonModel<ColonHandling> {

    private final ColonHandling what;

    ColonHandlingRadioButtonModel(AntlrFormatterConfig config, ColonHandling what) {
        super(config, AntlrFormatterConfig.KEY_COLON_HANDLING, config::getColonHandling);
        this.what = what;
    }

    @Override
    protected void onChange(ColonHandling oldValue, ColonHandling newValue) {
        boolean shouldSelect = Objects.equals(what, newValue);
        if (shouldSelect != isSelected()) {
            setSelected(shouldSelect);
        }
    }

    @Override
    protected void onAction() {
        if (!isSelected()) {
            config.setColonHandling(what);
        }
    }
}
