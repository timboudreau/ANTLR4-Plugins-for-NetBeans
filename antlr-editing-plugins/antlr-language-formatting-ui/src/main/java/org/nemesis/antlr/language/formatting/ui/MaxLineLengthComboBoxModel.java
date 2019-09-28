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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.DefaultComboBoxModel;
import org.nemesis.antlr.language.formatting.config.AntlrFormatterConfig;
import org.openide.util.WeakListeners;

/**
 *
 * @author Tim Boudreau
 */
final class MaxLineLengthComboBoxModel extends DefaultComboBoxModel<Integer> implements PropertyChangeListener {

    private final AntlrFormatterConfig config;
    private boolean inPropertyChange;
    private final boolean initialized;

    MaxLineLengthComboBoxModel(AntlrFormatterConfig config) {
        this.config = config;
        for (int i = 30; i < 300; i += 10) {
            addElement(i);
        }
        int curr = config.getMaxLineLength();
        inPropertyChange = true;
        setSelectedItem(curr);
        inPropertyChange = false;
        config.addPropertyChangeListener(AntlrFormatterConfig.KEY_MAX_LINE, WeakListeners.propertyChange(this, AntlrFormatterConfig.KEY_MAX_LINE, config));
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
                config.setMaxLineLength((Integer) anObject);
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
