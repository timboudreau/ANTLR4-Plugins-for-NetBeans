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
