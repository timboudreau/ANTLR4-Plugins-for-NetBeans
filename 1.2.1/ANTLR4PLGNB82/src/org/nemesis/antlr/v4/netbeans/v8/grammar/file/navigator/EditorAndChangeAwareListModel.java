package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import javax.swing.DefaultListModel;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.GenericExtractorBuilder.Extraction;
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
