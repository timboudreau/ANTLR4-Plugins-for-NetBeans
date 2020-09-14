/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.editor.doc;

import org.nemesis.misc.utils.AsyncRenderSupport;
import org.netbeans.modules.editor.NbEditorDocument;

/**
 * Adds asynchronous render support to NbEditorDocument.
 *
 * @author Tim Boudreau
 */
public class EnhEditorDocument extends NbEditorDocument {

    private final AsyncRenderSupport renderSupport = new AsyncRenderSupport(super::render);

    @SuppressWarnings({"rawType", "unchecked", "deprecation"})
    public EnhEditorDocument(Class kitClass) {
        super(kitClass);
    }

    public EnhEditorDocument(String mimeType) {
        super(mimeType);
    }

    @Override
    public void render(Runnable r) {
        renderSupport.renderNow(r);
    }

    public void renderWhenPossible(Runnable r) {
        renderSupport.renderWhenPossible(r);
    }

    @Override
    public void runAtomicAsUser(Runnable r) {
        super.runAtomicAsUser(() -> {
            super.runAtomicAsUser(r);
            renderSupport.onExitingRender();
        });
    }

    @Override
    public void runAtomic(Runnable r) {
        super.runAtomic(() -> {
            super.runAtomic(r);
            renderSupport.onExitingRender();
        });
    }
}
