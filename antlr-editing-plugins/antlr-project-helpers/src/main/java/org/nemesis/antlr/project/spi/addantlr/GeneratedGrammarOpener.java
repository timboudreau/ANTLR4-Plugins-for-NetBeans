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
package org.nemesis.antlr.project.spi.addantlr;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;

/**
 * Used by SkeletonGrammarType to create a task for formatting and opening the
 * grammars it has written.
 *
 * @author Tim Boudreau
 */
public abstract class GeneratedGrammarOpener {

    static GeneratedGrammarOpener getDefault() {
        GeneratedGrammarOpener result = Lookup.getDefault().lookup(GeneratedGrammarOpener.class);
        if (result == null) {
            result = new NoOp();
        }
        return result;
    }

    public abstract Runnable createOpenerTask(Iterable<? extends FileObject> toOpen);

    static boolean warned;

    static final class NoOp extends GeneratedGrammarOpener implements Runnable {

        @Override
        public Runnable createOpenerTask(Iterable<? extends FileObject> toOpen) {
            return this;
        }

        @Override
        public void run() {
            if (!warned) {
                Logger.getLogger(GeneratedGrammarOpener.class.getName())
                        .log(Level.WARNING, "No implementation of {0} installed"
                                + " - generated grammars will not be opened",
                                GeneratedGrammarOpener.class.getName());
                warned = true;
            }
        }

    }
}
