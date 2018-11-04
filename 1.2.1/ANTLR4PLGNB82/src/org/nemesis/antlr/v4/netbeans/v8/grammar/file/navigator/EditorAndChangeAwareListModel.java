package org.nemesis.antlr.v4.netbeans.v8.grammar.file.navigator;

import javax.swing.DefaultListModel;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.ANTLRv4SemanticParser;
import org.openide.cookies.EditorCookie;

/**
 *
 * @author Tim Boudreau
 */
final class EditorAndChangeAwareListModel<T> extends DefaultListModel<T> {

    final EditorCookie cookie;
    final int change;
    final ANTLRv4SemanticParser semantics;

    public EditorAndChangeAwareListModel(EditorCookie cookie, int change, ANTLRv4SemanticParser semantics) {
        this.cookie = cookie;
        this.change = change;
        this.semantics = semantics;
    }

}
