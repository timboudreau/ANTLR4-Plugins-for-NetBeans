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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.openide.text.PositionBounds;

/**
 * One entry owned by an AntlrRefactoringPluginFactory, which can produce a
 * RefactoringPlugin if its refactoring type is requested and the position
 * matches an element it recognizes.
 *
 * @author Tim Boudreau
 */
final class RefactoringPluginGenerator<R extends AbstractRefactoring> {

    private final Class<R> type;
    private final TriFunction<? super R, ? super Extraction, ? super PositionBounds, ? extends RefactoringPlugin> test;
    private static final Logger LOG = Logger.getLogger(RefactoringPluginGenerator.class.getName());

    static {
        if (AbstractRefactoringContext.debugLog) {
            LOG.setLevel(Level.ALL);
        }
    }

    RefactoringPluginGenerator(Class<R> type,
            TriFunction<? super R, ? super Extraction, ? super PositionBounds, ? extends RefactoringPlugin> test) {
        this.type = type;
        this.test = test;
    }

    boolean matches(Class<?> type) {
        return this.type == type;
    }

    RefactoringPlugin accept(AbstractRefactoring refactoring, Extraction extraction, PositionBounds pos) {
        if (type.isInstance(refactoring)) {
            RefactoringPlugin result = test.apply(type.cast(refactoring), extraction, pos);
            if (LOG.isLoggable(Level.FINEST) && result != null) {
                LOG.log(Level.FINEST, "{0} Created {1} for {2}",
                        new Object[]{
                            this, result, refactoring
                        });
            }
            return result;
        }
        return null;
    }

    @Override
    public String toString() {
        return "RefactoringPluginGenerator<" + type.getSimpleName() + ">("
                + test + ")";
    }

    boolean matches(AbstractRefactoring ref) {
        return type.isInstance(ref);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.type);
        hash = 61 * hash + Objects.hashCode(this.test);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RefactoringPluginGenerator<?> other = (RefactoringPluginGenerator<?>) obj;
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return Objects.equals(this.test, other.test);
    }
}
