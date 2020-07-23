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
package com.mastfrog.antlr.project.helpers.ant;

import com.mastfrog.util.collections.CollectionUtils;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ui.OpenProjects;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author Tim Boudreau
 */
final class UpgradableProjectDetector implements PropertyChangeListener, Runnable {

    private final Set<Path> seenPaths = ConcurrentHashMap.newKeySet();
    private final RequestProcessor openHookProcessor = new RequestProcessor("antlr-ant-upgrade-tasks", 1);
    private final Set<ProjectUpgrader> pending = ConcurrentHashMap.newKeySet();
    private final RequestProcessor.Task task = openHookProcessor.create(this);
    private static final long INITIAL_DELAY = 40000;
    private final long earliestTimeToRun = System.currentTimeMillis() + INITIAL_DELAY;
    private final long DEFAULT_DELAY = 10000;
    private static AtomicBoolean listening = new AtomicBoolean();
    private static UpgradableProjectDetector LISTENER;
    static boolean enableListening = !Boolean.getBoolean("unit.test");

    static void ensureListening() {
        if (enableListening && listening.compareAndSet(false, true)) {
            LISTENER = new UpgradableProjectDetector();
            OpenProjects.getDefault().addPropertyChangeListener(LISTENER);
        }
    }

    private int delay() {
        long now = System.currentTimeMillis();
        return (int) Math.max(earliestTimeToRun - now, now + DEFAULT_DELAY);
    }

    static void runUpgrades(List<Upgrader> upgraders, Set<Upgrader> dontAsks) {
        if (LISTENER == null) { // tests
            for (Upgrader up : upgraders) {
                try {
                    up.upgrade();
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            return;
        }
        LISTENER.openHookProcessor.submit((Callable<Void>) () -> {
            dontAsks.forEach(dontAsk -> {
                dontAsk.dontAskAnymore();
            });
            upgraders.forEach(up -> {
                try {
                    up.upgrade();
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            return null;
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        LambdaUtils.ifEquals(OpenProjects.PROPERTY_OPEN_PROJECTS, evt::getPropertyName, () -> {
            Set<Project> projects = CollectionUtils.setOf((Project[]) evt.getNewValue());
            Map<Path, Project> projectForPath = LambdaUtils.map(projects, seenPaths::contains, prj -> FileUtil.toFile(prj.getProjectDirectory()).toPath());
            if (!projectForPath.isEmpty()) {
                seenPaths.addAll(projectForPath.keySet());
                projectForPath.entrySet().stream().map(e -> ProjectUpgrader.needsUpgrade(e.getValue())).filter(upgrader -> (upgrader != null && !upgrader.isDontAsk())).forEachOrdered(upgrader -> {
                    pending.add(upgrader);
                });
                if (!pending.isEmpty()) {
                    task.schedule(delay());
                }
            }
            return null;
        });
    }

    @Override
    public void run() {
        List<Upgrader> upgrades = new ArrayList<>(pending);
        if (!upgrades.isEmpty()) {
            pending.removeAll(upgrades);
            Collections.sort(upgrades);
            UpgradeProjectUI.show(upgrades);
        }
    }

}
