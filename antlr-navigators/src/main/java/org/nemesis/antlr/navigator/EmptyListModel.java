package org.nemesis.antlr.navigator;

import javax.swing.ListModel;
import javax.swing.event.ListDataListener;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyListModel<T> implements ListModel<T> {

    static final ListModel<Object> EMPTY_MODEL = new EmptyListModel<>();

    @SuppressWarnings("unchecked")
    static <T> ListModel<T> emptyModel() {
        return (ListModel<T>) EMPTY_MODEL;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public T getElementAt(int index) {
        throw new IndexOutOfBoundsException(index + "");
    }

    @Override
    public void addListDataListener(ListDataListener l) {
        //do nothing
    }

    @Override
    public void removeListDataListener(ListDataListener l) {
        // do nothing
    }

}
