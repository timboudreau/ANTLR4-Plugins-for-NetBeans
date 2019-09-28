/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
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
