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
