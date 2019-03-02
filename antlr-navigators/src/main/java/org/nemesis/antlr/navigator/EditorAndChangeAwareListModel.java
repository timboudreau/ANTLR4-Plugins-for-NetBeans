package org.nemesis.antlr.navigator;

import javax.swing.DefaultListModel;
import org.nemesis.extraction.Extraction;
import org.openide.cookies.EditorCookie;

/**
 *
 * @author Tim Boudreau
 */
final class EditorAndChangeAwareListModel<T> extends DefaultListModel<T> {

    final EditorCookie cookie;
    final int change;
    final Extraction semantics;

    public EditorAndChangeAwareListModel(EditorCookie cookie, int change, Extraction semantics) {
        this.cookie = cookie;
        this.change = change;
        this.semantics = semantics;
    }

}
