/*
 * Copyright 2020 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language.fix;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.text.StyledDocument;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.LazyFixList;

/**
 * An implementation of Fixlist which is not terribly lazy; it is
 * much more likely that the extraction is still up-to-date with the
 * document at this point.
 *
 * @author Tim Boudreau
 */
class NotSoLazyFixList implements LazyFixList {
    private final StyledDocument doc;
    private final Consumer<FixConsumer> lazyFixes;
    private List<Fix> fixen = null;

    NotSoLazyFixList( StyledDocument doc, Consumer<FixConsumer> lazyFixes) {
        this.doc = doc;
        this.lazyFixes = lazyFixes;
    }

    @Override
    public void addPropertyChangeListener( PropertyChangeListener pl ) {
        // do nothing
    }

    @Override
    public void removePropertyChangeListener( PropertyChangeListener pl ) {
        // do nothing
    }

    @Override
    public boolean probablyContainsFixes() {
//        return !ext.isSourceProbablyModifiedSinceCreation();
        return true;
    }

    @Override
    public List<Fix> getFixes() {
        if ( fixen == null ) {
            FixConsumer fc = new FixConsumer( doc );
            lazyFixes.accept( fc );
            fixen = fc.entries();
        }
        return fixen;
    }

    @Override
    public boolean isComputed() {
        return true;
    }

}
