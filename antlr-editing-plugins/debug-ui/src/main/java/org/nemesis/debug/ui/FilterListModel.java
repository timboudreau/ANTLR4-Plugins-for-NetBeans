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
