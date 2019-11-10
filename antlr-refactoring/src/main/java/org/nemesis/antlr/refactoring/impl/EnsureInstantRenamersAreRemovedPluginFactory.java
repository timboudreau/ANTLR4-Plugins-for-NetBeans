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

import java.awt.EventQueue;
import java.util.Collections;
import java.util.Set;
import org.nemesis.antlr.refactoring.common.BeforeRefactoringTask;
import org.netbeans.modules.refactoring.api.AbstractRefactoring;
import org.netbeans.modules.refactoring.api.Problem;
import org.netbeans.modules.refactoring.api.ProgressEvent;
import org.netbeans.modules.refactoring.api.ProgressListener;
import org.netbeans.modules.refactoring.spi.RefactoringElementsBag;
import org.netbeans.modules.refactoring.spi.RefactoringPlugin;
import org.netbeans.modules.refactoring.spi.RefactoringPluginFactory;
import org.openide.util.WeakSet;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service=RefactoringPluginFactory.class, position=96)
public class EnsureInstantRenamersAreRemovedPluginFactory implements RefactoringPlugin, RefactoringPluginFactory {

    private static final Set<BeforeRefactoringTask> registry = Collections.synchronizedSet(new WeakSet<BeforeRefactoringTask>());

    public static void register(BeforeRefactoringTask run) {
        registry.add(run);
    }

    public static boolean deregister(BeforeRefactoringTask run) {
        return registry.remove(run);
    }

    @Override
    public RefactoringPlugin createInstance(AbstractRefactoring refactoring) {
        return this;
    }

    @Override
    public Problem preCheck() {
        return null;
    }

    @Override
    public Problem checkParameters() {
        return null;
    }

    @Override
    public Problem fastCheckParameters() {
        return null;
    }

    @Override
    public void cancelRequest() {
    }

    @Override
    public Problem prepare(RefactoringElementsBag refactoringElements) {
        refactoringElements.getSession().addProgressListener(new ProgressListener() {
            @Override
            public void start(ProgressEvent event) {
                final BeforeRefactoringTask[] performers = registry.toArray(new BeforeRefactoringTask[0]);
                registry.clear();
                for (BeforeRefactoringTask p : performers) {
                    p.prepare();
                }
                EventQueue.invokeLater(() -> {
                    for (BeforeRefactoringTask p : performers) {
                        p.perform();
                    }
                });
            }

            @Override
            public void step(ProgressEvent event) {
            }

            @Override
            public void stop(ProgressEvent event) {
            }
        });

        return null;
    }
}
