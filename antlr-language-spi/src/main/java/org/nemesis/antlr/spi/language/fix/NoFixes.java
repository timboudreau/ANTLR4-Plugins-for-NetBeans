package org.nemesis.antlr.spi.language.fix;

import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;

/**
 *
 * @author Tim Boudreau
 */
final class NoFixes implements LazyFixList {

    static final LazyFixList NO_FIXES = new NoFixes();

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        // do nothing
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        // do nothing
    }

    @Override
    public boolean probablyContainsFixes() {
        return false;
    }

    @Override
    public List<Fix> getFixes() {
        return Collections.emptyList();
    }

    @Override
    public boolean isComputed() {
        return true;
    }

    @Override
    public String toString() {
        return "<no-fixes>";
    }

}
