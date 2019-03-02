package org.nemesis.antlr.navigator;

import java.util.Collection;
import java.util.function.Consumer;
import org.openide.cookies.EditorCookie;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 * Listens to changes of context and triggers proper action
 */
final class ContextListener implements LookupListener {

    private final Consumer<Collection<? extends EditorCookie>> onChange;

    public ContextListener(Consumer<Collection<? extends EditorCookie>> onChange) {
        this.onChange = onChange;
    }

    @SuppressWarnings(value = "unchecked")
    public void resultChanged(LookupEvent ev) {
        Lookup.Result<EditorCookie> result = (Lookup.Result<EditorCookie>) ev.getSource();
        withResult(result);
    }

    void withResult(Lookup.Result<EditorCookie> result) {
        Collection<? extends EditorCookie> data = result.allInstances();
        onChange.accept(data);
    }

}
