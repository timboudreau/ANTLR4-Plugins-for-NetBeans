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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultButtonModel;
import javax.swing.event.ChangeListener;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
abstract class ConfigButtonModel<T> extends DefaultButtonModel {

    protected final AntlrFormatterConfig config;
    protected final String property;
    protected final Supplier<T> fetcher;
    private boolean listening;
    private boolean inPropertyChange;
    private boolean inAction;
    protected T oldValue;
    private final Lis lis = new Lis();

    ConfigButtonModel(AntlrFormatterConfig config, String property, Supplier<T> fetcher) {
        this.config = config;
        this.property = property;
        this.fetcher = fetcher;
        oldValue = fetcher.get();
    }

    private void maybeAddNotify() {
        if (!listening) {
            listening = true;
            addActionListener(lis);
            config.addPropertyChangeListener(AntlrFormatterConfig.KEY_COLON_HANDLING, WeakListeners.propertyChange(lis, AntlrFormatterConfig.KEY_COLON_HANDLING, config));
        }
    }

    @Override
    public void addItemListener(ItemListener l) {
        maybeAddNotify();
        super.addItemListener(l);
    }

    @Override
    public void addActionListener(ActionListener l) {
        maybeAddNotify();
        super.addActionListener(l);
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        maybeAddNotify();
        super.addChangeListener(l);
    }

    protected abstract void onChange(T oldValue, T newValue);

    protected abstract void onAction();

    class Lis implements PropertyChangeListener, ActionListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (inPropertyChange) {
                return;
            }
            inPropertyChange = true;
            try {
                if (property.equals(evt.getPropertyName())) {
                    T newValue = fetcher.get();
                    if (!Objects.equals(oldValue, newValue)) {
                        onChange(oldValue, newValue);
                        oldValue = newValue;
                    }
                }
            } finally {
                inPropertyChange = false;
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (inPropertyChange || inAction) {
                return;
            }
            inAction = true;
            try {
                onAction();
            } finally {
                inAction = false;
            }
        }
    }
}
