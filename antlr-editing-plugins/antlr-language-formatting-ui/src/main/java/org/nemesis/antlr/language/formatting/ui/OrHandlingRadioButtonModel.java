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

import java.util.Objects;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.nemesis.antlr.language.formatting.config.OrHandling;

/**
 *
 * @author Tim Boudreau
 */
public class OrHandlingRadioButtonModel extends ConfigButtonModel<OrHandling> {

    private final OrHandling what;

    public OrHandlingRadioButtonModel(AntlrFormatterConfig config, OrHandling what) {
        super(config, AntlrFormatterConfig.KEY_OR_HANDLING, config::getOrHandling);
        this.what = what;
    }

    @Override
    protected void onChange(OrHandling oldValue, OrHandling newValue) {
        boolean shouldSelect = Objects.equals(what, newValue);
        if (shouldSelect != isSelected()) {
            setSelected(shouldSelect);
        }
    }

    @Override
    protected void onAction() {
        if (!isSelected()) {
            config.setOrHandling(what);
        }
    }

}
