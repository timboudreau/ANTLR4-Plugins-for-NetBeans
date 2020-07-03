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
package org.nemesis.antlr.refactoring;

import com.mastfrog.function.TriFunction;
import java.util.Collection;
import org.nemesis.extraction.Extraction;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.openide.text.PositionBounds;

/**
 * In addition to automatically generated refactoring support, you can register
 * CustomRefactorings in the MimeLookup for your mime type to integrate them.
 * The <code>apply()</code> method here should examine the selection or caret
 * region referenced by the passed <code>PositionBounds</code>, determine if the
 * extraction contains something this refactoring recognizes at that location,
 * and if so, return a RefactoringPlugin to handle the refactoring.
 *
 * @author Tim Boudreau
 */
public abstract class CustomRefactoring<R extends AbstractRefactoring> implements TriFunction<R, Extraction, PositionBounds, RefactoringPlugin> {

    private final Class<R> type;

    protected CustomRefactoring(Class<R> type) {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    static Collection<? extends CustomRefactoring<?>> registeredRefactorings(String mimeType) {
        return (Collection<? extends CustomRefactoring<?>>) MimeLookup.getLookup(mimeType).lookupAll(CustomRefactoring.class);
    }

    RefactoringPluginGenerator<R> toGenerator() {
        return new RefactoringPluginGenerator<>(type, this);
    }

    public final boolean equals(Object o) {
        return o == this || (o != null && o.getClass() == getClass());
    }

    public final int hashCode() {
        return getClass().hashCode();
    }

    public final String toString() {
        return getClass().getName();
    }

    /**
     * For many refactorings, the only purpose for the UI is to show Problem
     * instances if something is wrong, and the only way to do that is to have a
     * UI class to show, even though no part of it will actually ever be shown
     * at all.
     *
     * @param refactoring The refactoring
     * @param name The refactoring name
     * @param desc The refactoring description
     * @return A ui
     */
    public static RefactoringUI createDummyRefactoringUI(AbstractRefactoring refactoring, String name, String desc) {
        return new DummyRefactoringUI(refactoring, name, desc);
    }
}
