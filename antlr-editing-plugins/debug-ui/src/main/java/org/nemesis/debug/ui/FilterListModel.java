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


package org.nemesis.debug.ui;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Tim Boudreau
 */
class FilterListModel implements ListModel<EmittedItem>, ListDataListener {

    private final ListModel<EmittedItem> orig;
    private final List<EmittedItem> copy = new ArrayList<>(200);
    private final List<ListDataListener> listeners = new ArrayList<>(5);
    private final Predicate<EmittedItem> filter;

    public FilterListModel(ListModel<EmittedItem> orig, Predicate<EmittedItem> filter) {
        this.orig = orig;
        this.filter = filter;
        sync();
        orig.addListDataListener(this);
    }

    void sync() {
        int oldSize = copy.size();
        int sz = orig.getSize();
        copy.clear();
        for (int i = 0; i < sz; i++) {
            EmittedItem el = orig.getElementAt(i);
            if (filter.test(el)) {
                copy.add(el);
            }
        }
        fire(new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, Math.max(sz, oldSize)));
    }

    private void fire(ListDataEvent evt) {
        for (ListDataListener l : listeners) {
            switch (evt.getType()) {
                case ListDataEvent.CONTENTS_CHANGED:
                    l.contentsChanged(evt);
                    break;
                case ListDataEvent.INTERVAL_ADDED:
                    l.intervalAdded(evt);
                    break;
                case ListDataEvent.INTERVAL_REMOVED:
                    l.intervalRemoved(evt);
                    break;
            }
        }
    }

    @Override
    public int getSize() {
        return copy.size();
    }

    @Override
    public EmittedItem getElementAt(int index) {
        return copy.get(index);
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        listeners.add(l);
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) {
            orig.removeListDataListener(this);
        }
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        LinkedList<int[]> ranges = new LinkedList<>();
        for (int i = e.getIndex0(); i < e.getIndex1() + 1; i++) {
            EmittedItem el = orig.getElementAt(i);
            if (filter.test(el)) {
                copy.add(el);
                int[] currentRange = ranges.isEmpty() ? null : ranges.get(0);
                if (currentRange == null || currentRange[1] != i + 1) {
                    int[] newRange = new int[]{i, i};
                    ranges.add(newRange);
                } else {
                    currentRange[1]++;
                }
            }
        }
        for (int[] range : ranges) {
            ListDataEvent evt = new ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, range[0], range[1]);
            fire(evt);
        }
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        LinkedList<int[]> ranges = new LinkedList<>();
        for (int i = e.getIndex1(); i <= e.getIndex0(); i++) {
            EmittedItem el = orig.getElementAt(i);
            if (filter.test(el)) {
                copy.remove(i);
                int[] currentRange = ranges.isEmpty() ? null : ranges.get(0);
                if (currentRange == null || currentRange[0] - 1 != i) {
                    int[] newRange = new int[]{i, i};
                    ranges.push(newRange);
                } else {
                    currentRange[0]--;
                }
            }
        }
        for (int[] range : ranges) {
            ListDataEvent evt = new ListDataEvent(this, ListDataEvent.INTERVAL_REMOVED, range[0], range[1]);
            fire(evt);
        }
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        sync();
    }

}
