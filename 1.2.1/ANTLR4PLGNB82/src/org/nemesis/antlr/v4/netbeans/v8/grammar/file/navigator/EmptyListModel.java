package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import javax.swing.ListModel;
import javax.swing.event.ListDataListener;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.summary.RuleDeclaration;

/**
 *
 * @author Tim Boudreau
 */
final class EmptyListModel<T> implements ListModel<T> {

    static final EmptyListModel<RuleDeclaration> EMPTY_MODEL = new EmptyListModel<>();

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
