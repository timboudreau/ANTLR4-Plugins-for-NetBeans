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

import com.mastfrog.util.collections.ArrayUtils;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

/**
 * Swaps the delegated lookup based on focus.
 *
 * @author Tim Boudreau
 */
class FocusSwitchingProxyLookup extends ProxyLookup implements FocusListener {

    private final Lookup[] aGroup;
    private final Lookup[] bGroup;
    private final Component a;
    private final Component b;

    @SuppressWarnings("LeakingThisInConstructor")
    public FocusSwitchingProxyLookup(Component a, Lookup aLookup, Component b, Lookup bLookup, Lookup... more) {
        super(more);
        this.a = a;
        this.b = b;
        aGroup = ArrayUtils.prepend(aLookup, more);
        bGroup = ArrayUtils.prepend(bLookup, more);
//        if (a.hasFocus()) {
//            setLookups(aGroup);
//        } else if (b.hasFocus()) {
//            setLookups(bGroup);
//        }
        a.addFocusListener(this);
        b.addFocusListener(this);
    }

    @Override
    public void focusGained(FocusEvent e) {
        if (e.getComponent() == a) {
            setLookups(aGroup);
        } else if (e.getComponent() == b) {
            setLookups(bGroup);
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        // do nothing
    }

}
