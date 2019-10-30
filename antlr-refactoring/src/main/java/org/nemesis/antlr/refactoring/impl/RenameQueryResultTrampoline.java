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
package org.nemesis.antlr.refactoring.impl;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.StyledDocument;
import org.nemesis.antlr.refactoring.spi.RenameAugmenter;
import org.nemesis.antlr.refactoring.spi.RenamePostProcessor;
import org.nemesis.antlr.refactoring.spi.RenameQueryResult;

/**
 *
 * @author Tim Boudreau
 */
public abstract class RenameQueryResultTrampoline {

    public static RenameQueryResultTrampoline DEFAULT;

    public static RenameQueryResultTrampoline getDefault() {
        if (DEFAULT != null) {
            return DEFAULT;
        }
        Class<?> type = RenameQueryResult.class;
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(RenameQueryResultTrampoline.class.getName()).log(Level.SEVERE,
                    null, ex);
        }
        assert DEFAULT != null : "The DEFAULT field must be initialized";
        return DEFAULT;
    }

    public static void onRename(RenameQueryResult res, String original, String nue, Runnable undo) {
        getDefault()._onRename(res, original, nue, undo);
    }

    public static void onNameUpdated(RenameQueryResult res, String orig, String newName, StyledDocument doc) {
        getDefault()._nameUpdated(res, orig, newName, doc);
    }

    public static void onCancelled(RenameQueryResult res) {
        getDefault()._cancelled(res);
    }

    public static RenameActionType typeOf(RenameQueryResult res) {
        return getDefault()._typeOf(res);
    }

    public static RenameQueryResult createVetoResult(String reason) {
        return getDefault()._veto(reason);
    }

    public static RenameQueryResult createProceedResult() {
        return getDefault()._proceed();
    }

    public static RenameQueryResult createUseRefactoringResult() {
        return getDefault()._useRefactoring();
    }

    public static RenameQueryResult createAugmentResult(RenameAugmenter aug) {
        return getDefault()._augment(aug);
    }

    public static RenameQueryResult createPostProcessResult(RenamePostProcessor processor) {
        return getDefault()._postProcess(processor);
    }

    public static boolean testChar(RenameQueryResult res, boolean initial, char typed) {
        return getDefault()._testChar(res, initial, typed);
    }

    public static RenameQueryResult createNothingFoundResult() {
        return getDefault()._nothingFound();
    }

    protected abstract boolean _testChar(RenameQueryResult res, boolean initial, char typed);

    protected abstract void _onRename(RenameQueryResult res, String original, String nue, Runnable undo);

    protected abstract void _nameUpdated(RenameQueryResult res, String orig, String newName, StyledDocument doc);

    protected abstract void _cancelled(RenameQueryResult res);

    protected abstract RenameActionType _typeOf(RenameQueryResult res);

    protected abstract RenameQueryResult _veto(String reason);

    protected abstract RenameQueryResult _proceed();

    protected abstract RenameQueryResult _useRefactoring();

    protected abstract RenameQueryResult _augment(RenameAugmenter aug);

    protected abstract RenameQueryResult _postProcess(RenamePostProcessor postProcessor);

    protected abstract RenameQueryResult _nothingFound();

}
