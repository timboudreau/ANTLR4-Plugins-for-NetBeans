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
package org.nemesis.antlr.live.language.coloring;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Set;
import javax.swing.event.ChangeListener;

/**
 *
 * @author Tim Boudreau
 */
public interface DynamicColorings extends Iterable<String>, Serializable {

    void addChangeListener(ChangeListener l);

    void addPropertyChangeListener(PropertyChangeListener listener);

    void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);

    void clear();

    boolean contains(String key);

    void deactivateAll();

    boolean isEmpty();

    Set<String> keys();

    void removeChangeListener(ChangeListener l);

    void removePropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(String propertyName, PropertyChangeListener listener);

    int rev();

    boolean setColor(String key, Color value);

    boolean setFlag(String key, AttrTypes flag, boolean val);

    boolean setForeground(String key, boolean val);
}
