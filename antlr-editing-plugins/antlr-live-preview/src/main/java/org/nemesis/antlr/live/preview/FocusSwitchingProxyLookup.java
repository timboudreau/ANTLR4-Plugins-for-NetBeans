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
package org.nemesis.antlr.live.preview;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

/**
 * Swaps the delegated lookup based on focus.
 *
 * @author Tim Boudreau
 */
final class FocusSwitchingProxyLookup extends ProxyLookup implements FocusListener, MouseListener {

    private final Lookup[] aGroup;
    private final Lookup[] bGroup;
    private final Component a;
    private final Component b;
    private boolean isA;

    @SuppressWarnings("LeakingThisInConstructor")
    FocusSwitchingProxyLookup(JComponent a, Lookup aLookup, JComponent b, Lookup bLookup, Lookup... more) {
        super(more);
        this.a = a;
        this.b = b;
        // For inscrutable reasons, WhereUsedQuery only works correctly in the
        // preview if the actoin map and editor are in the lookup
        List<Lookup> aAll = new ArrayList<>(Arrays.asList(more));
        aAll.add(0, Lookups.fixed(a, a.getActionMap()));
        aAll.add(0, aLookup);
        aGroup = aAll.toArray(new Lookup[aAll.size()]);
        List<Lookup> bAll = new ArrayList<>(Arrays.asList(more));
        bAll.add(0, Lookups.fixed(b, b.getActionMap()));
        bAll.add(0, bLookup);
        bGroup = bAll.toArray(new Lookup[bAll.size()]);;
        a.addFocusListener(this);
        b.addFocusListener(this);
        a.addMouseListener(this);
        b.addMouseListener(this);
        setLookups(aGroup);
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (e.getComponent() == a) {
            if (!isA) {
                isA = true;
                setLookups(aGroup);
            }
        } else if (e.getComponent() == b) {
            if (isA) {
                isA = false;
                setLookups(bGroup);
            }
        }
    }

    private void setComponent(Component comp) {
        if (comp == a) {
            if (!isA) {
                isA = true;
                setLookups(aGroup);
            }
        } else if (comp == b) {
            if (isA) {
                isA = false;
                setLookups(bGroup);
            }
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        // do nothing
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // For mouse events like right-click popups, we actually
        // need to get *ahead* of the focus event to have the
        // right lookup set ahead of action enablement checks
        setComponent((Component) e.getSource());
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // do nothing
    }

}
