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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.DefaultComboBoxModel;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
final class IndentComboBoxModel extends DefaultComboBoxModel<Integer> implements PropertyChangeListener {

    private final AntlrFormatterConfig config;
    private boolean inPropertyChange;
    private final boolean initialized;

    IndentComboBoxModel(AntlrFormatterConfig config) {
        this.config = config;
        addElement(2);
        for (int i = 4; i <= 36; i += 4) {
            addElement(i);
        }
        int curr = config.getIndent();
        inPropertyChange = true;
        setSelectedItem(curr);
        inPropertyChange = false;
        config.addPropertyChangeListener(AntlrFormatterConfig.KEY_INDENT, WeakListeners.propertyChange(this, AntlrFormatterConfig.KEY_INDENT, config));
        initialized = true;
    }

    @Override
    public void setSelectedItem(Object anObject) {
        if (initialized) {
            if (anObject instanceof String) {
                // editability
                anObject = Integer.parseInt(anObject.toString());
            }
            if (!inPropertyChange) {
                config.setIndent((Integer) anObject);
            }
        }
        super.setSelectedItem(anObject);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        inPropertyChange = true;
        try {
            if (AntlrFormatterConfig.KEY_MAX_LINE.equals(evt.getPropertyName())) {
                Integer val = Integer.parseInt(evt.getNewValue().toString());
                setSelectedItem(val);
            }
        } finally {
            inPropertyChange = false;
        }
    }
}
