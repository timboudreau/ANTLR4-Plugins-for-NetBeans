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
package org.nemesis.antlr.navigator;

import java.util.List;
import javax.swing.ListModel;
import javax.swing.event.ListDataListener;

class ListListModel<T> implements ListModel<T> {

    private final List<T> list;

    ListListModel(List<T> list) {
        this.list = list;
    }

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public T getElementAt(int index) {
        return list.get(index);
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        // do nothing
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        // do nothing
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int size = getSize();
        if (size == 0) {
            sb.append("-empty-");
        } else {
            for (int i = 0; i < size; i++) {
                if (i != size - 1) {
                    sb.append('\n');
                }
                sb.append(i).append(". ").append(getElementAt(i));
            }
        }
        return sb.toString();
    }
}
