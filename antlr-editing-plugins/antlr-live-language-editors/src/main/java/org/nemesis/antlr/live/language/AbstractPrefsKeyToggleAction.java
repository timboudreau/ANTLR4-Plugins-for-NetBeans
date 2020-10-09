/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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

package org.nemesis.antlr.live.language;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import org.openide.util.actions.Presenter;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractPrefsKeyToggleAction extends AbstractAction implements Icon, Presenter.Popup, PropertyChangeListener, Runnable {

    private final String key;
    private JCheckBoxMenuItem presenter;

    @SuppressWarnings(value = {"OverridableMethodCallInConstructor", "LeakingThisInConstructor"})
    AbstractPrefsKeyToggleAction(boolean icon, String key, String displayName, String description) {
        this.key = key;
        putValue(NAME, displayName);
        if (icon) {
            putValue(SMALL_ICON, this);
        }
        putValue(SELECTED_KEY, key);
        if (description != null) {
            putValue(SHORT_DESCRIPTION, description);
        }
        AdhocErrorHighlighter.subs.subscribable.subscribe(key, this);
    }

    protected abstract boolean currentValue();

    protected abstract boolean updateValue(boolean val);

    private void setValue(boolean val) {
        if (updateValue(val)) {
            firePropertyChange(key, !val, val);
        }
    }

    protected void toggleValue() {
        setValue(!currentValue());
    }

    @Override
    public Object getValue(String key) {
        if (this.key.equals(key)) {
            return currentValue();
        }
        return super.getValue(key);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        if (presenter == null) {
            presenter = new JCheckBoxMenuItem((Action) this);
            presenter.setSelected(currentValue());
            String desc = (String) getValue(SHORT_DESCRIPTION);
            if (desc != null) {
                presenter.setToolTipText(desc);
            }
            PropertyChangeListener pcl = evt -> {
                presenter.repaint();
            };
            AdhocErrorHighlighter.subs.subscribable.subscribe(key, pcl);
            // hold a reference
            presenter.putClientProperty("_pcl", pcl);
        }
        return presenter;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleValue();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // triger a repaint in presenters
        EventQueue.invokeLater(this);
    }

    public void run() {
        setEnabled(false);
        setEnabled(true);
    }

}
