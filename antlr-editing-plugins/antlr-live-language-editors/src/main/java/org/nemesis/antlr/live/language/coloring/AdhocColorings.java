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

import org.nemesis.antlr.live.language.coloring.AttrTypes;
import org.nemesis.antlr.live.language.coloring.AdhocColoring;
import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.event.ChangeListener;
import org.openide.util.ChangeSupport;

/**
 *
 * @author Tim Boudreau
 */
public class AdhocColorings implements DynamicColorings {

    private static final long serialVersionUID = 1;
    private final Map<String, AdhocColoring> colorings;
    private transient PropertyChangeSupport supp;
    private AtomicInteger rev = new AtomicInteger();
    private transient ChangeSupport csupp;

    public AdhocColorings() {
        colorings = new TreeMap<>();
    }

    private AdhocColorings(Map<String, AdhocColoring> all) {
        this.colorings = new TreeMap<>(all);
    }

    @Override
    public int rev() {
        return rev.get();
    }

    @Override
    public void deactivateAll() {
        // XXX this will fire a lot of changes
        for (String key : colorings.keySet()) {
            setFlag(key, AttrTypes.ACTIVE, false);
        }
    }

    @Override
    public void clear() {
        colorings.clear();
        rev.getAndIncrement();
        fire();
    }

    @Override
    public Iterator<String> iterator() {
        return colorings.keySet().iterator();
    }

    public AdhocColoring get(String key) {
        return colorings.get(key);
    }

    @Override
    public boolean isEmpty() {
        return colorings.isEmpty();
    }

    @Override
    public boolean contains(String key) {
        return colorings.containsKey(key);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AdhocColoring> e : colorings.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    int size() {
        return colorings.size();
    }

    public AdhocColoring add(String key, Color color, AttrTypes... types) {
        int flags = 0;
        for (AttrTypes t : types) {
            flags |= t.maskValue();
        }
        AdhocColoring c = new AdhocColoring(flags, color);
        addOne(key, c);
        return c;
    }

    public AdhocColoring add(String key, Color color, Set<AttrTypes> types) {
        int flags = 0;
        for (AttrTypes t : types) {
            flags |= t.maskValue();
        }
        AdhocColoring c = new AdhocColoring(flags, color);
        addOne(key, c);
        return c;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        csupp().addChangeListener(l);
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        csupp().removeChangeListener(l);
    }

    private void addOne(String key, AdhocColoring coloring) {
        if (!colorings.containsKey(key)) {
            colorings.put(key, coloring);
            firePropertyChange(key, coloring, coloring);
        } else {
            AdhocColoring old = colorings.get(key);
            if (!old.equals(coloring)) {
                colorings.put(key, coloring);
                firePropertyChange(key, old, coloring);
            }
        }
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(colorings.keySet());
    }

    private void fire() {
        if (csupp != null) {
            csupp.fireChange();
        }
    }

    private ChangeSupport csupp() {
        if (csupp == null) {
            csupp = new ChangeSupport(this);
        }
        return csupp;
    }

    private PropertyChangeSupport supp() {
        if (supp == null) {
            supp = new PropertyChangeSupport(this);
        }
        return supp;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        supp().addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        supp().removePropertyChangeListener(listener);
    }

    @Override
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        supp().addPropertyChangeListener(propertyName, listener);
    }

    @Override
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        supp().removePropertyChangeListener(propertyName, listener);
    }

    public void store(OutputStream out) throws IOException {
        byte[] eq = "=".getBytes(UTF_8);
        for (String key : colorings.keySet()) {
            out.write(key.getBytes(UTF_8));
            out.write(eq);
            out.write(colorings.get(key).toLine().getBytes(UTF_8));
        }
        out.flush();
    }

    public static AdhocColorings load(InputStream in) {
        Scanner scanner = new Scanner(in);
        try {
            Map<String, AdhocColoring> map = new HashMap<>();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] parts = line.split("\\=", 2);
                if (parts.length == 2) {
                    String key = parts[0];
                    AdhocColoring val = AdhocColoring.parse(parts[1]);
                    map.put(key, val);
                }
            }
            return new AdhocColorings(map);
        } finally {
            scanner.close();
        }
    }

    @Override
    public boolean setColor(String key, Color value) {
        AdhocColoring coloring = this.colorings.get(key);
        if (coloring != null) {
            AdhocColoring old = coloring.copyAttributes();
            boolean result = coloring.setColor(value);
            if (result) {
                firePropertyChange(key, old, coloring);
            }
            return result;
        }
        return false;
    }

    @Override
    public boolean setForeground(String key, boolean val) {
        AdhocColoring coloring = this.colorings.get(key);
        if (coloring != null) {
            AdhocColoring old = coloring.copyAttributes();
            boolean result;
            if (val) {
                result = coloring.addFlag(AttrTypes.FOREGROUND)
                        | coloring.removeFlag(AttrTypes.BACKGROUND); // bitwise or intentional
            } else {
                result = coloring.removeFlag(AttrTypes.FOREGROUND)
                        | coloring.addFlag(AttrTypes.BACKGROUND); // bitwise or intentional
            }
            if (result) {
                firePropertyChange(key, old, coloring);
            }
            return result;
        }
        return false;
    }

    @Override
    public boolean setFlag(String key, AttrTypes flag, boolean val) {
        AdhocColoring coloring = this.colorings.get(key);
        if (coloring != null) {
            AdhocColoring old = coloring.copyAttributes();
            boolean result;
            if (val) {
                result = coloring.addFlag(flag);
            } else {
                result = coloring.removeFlag(flag);
            }
            if (result) {
                firePropertyChange(key, old, coloring);
            }
            return result;
        }
        return false;
    }

    void addIfAbsent(String key, Color color, Set<AttrTypes> of) {
        if (!colorings.containsKey(key)) {
            int flags = 0;
            for (AttrTypes t : of) {
                flags |= t.maskValue();
            }
            AdhocColoring nue = new AdhocColoring(flags, color);
            colorings.put(key, new AdhocColoring(flags, color));
            firePropertyChange(key, null, nue);
        }
    }

    private <T> void firePropertyChange(String key, T old, T nue) {
        rev.incrementAndGet();
        if (supp != null) {
            supp.firePropertyChange(key, old, nue);
        }
        fire();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 71 * hash + Objects.hashCode(this.colorings);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AdhocColorings other = (AdhocColorings) obj;
        return Objects.equals(this.colorings, other.colorings);
    }
}
