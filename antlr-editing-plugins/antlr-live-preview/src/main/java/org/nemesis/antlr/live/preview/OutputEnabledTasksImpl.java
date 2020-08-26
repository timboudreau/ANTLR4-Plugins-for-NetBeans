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
package org.nemesis.antlr.live.preview;

import com.mastfrog.util.collections.CollectionUtils;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import org.nemesis.antlr.memory.spi.AntlrLoggers;
import org.nemesis.antlr.memory.spi.OutputEnabledTasks;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.NbPreferences;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = OutputEnabledTasks.class)
public class OutputEnabledTasksImpl extends OutputEnabledTasks {

    private static final Set<String> KNOWN_TASKS = CollectionUtils.immutableSet(AntlrLoggers.COMMON_TASKS);
    private final Set<String> enabledTasks = ConcurrentHashMap.newKeySet(KNOWN_TASKS.size());
    private static OutputEnabledTasksImpl IMPL;

    public OutputEnabledTasksImpl() {
        IMPL = this;
        Preferences prefs = prefs();
        for (String k : KNOWN_TASKS) {
            if (prefs.getBoolean(k, false)) {
                enabledTasks.add(k);
            }
        }
    }

    static OutputEnabledTasksImpl getDefault() {
        if (IMPL != null) {
            return IMPL;
        }
        OutputEnabledTasks tasks = Lookup.getDefault().lookup(OutputEnabledTasks.class);
        if (tasks instanceof OutputEnabledTasksImpl) {
            return (OutputEnabledTasksImpl) tasks;
        }
        return new OutputEnabledTasksImpl();
    }

    @Override
    protected boolean outputEnabled(Path path, String task) {
        return prefs().getBoolean(task, false);
    }

    public boolean isTaskEnabled(String task) {
        return KNOWN_TASKS.contains(task) && enabledTasks.contains(task);
    }

    public Set<String> knownTasks() {
        return KNOWN_TASKS;
    }

    @Messages({
        AntlrLoggers.STD_TASK_COMPILE_ANALYZER + "=Compile Analyzer",
        AntlrLoggers.STD_TASK_COMPILE_GRAMMAR + "=Compile Grammar",
        AntlrLoggers.STD_TASK_GENERATE_ANALYZER + "=Generate Analyzer",
        AntlrLoggers.STD_TASK_GENERATE_ANTLR + "=Generate Classes from Antlr Grammar",
        AntlrLoggers.STD_TASK_RUN_ANALYZER + "=Run Analyzer"})
    public String displayName(String task) {
        if (KNOWN_TASKS.contains(task)) {
            switch (task) {
                case AntlrLoggers.STD_TASK_COMPILE_ANALYZER:
                    return Bundle.compile_analyzer();
                case AntlrLoggers.STD_TASK_COMPILE_GRAMMAR:
                    return Bundle.compile_grammar();
                case AntlrLoggers.STD_TASK_GENERATE_ANALYZER:
                    return Bundle.generate_analyzer();
                case AntlrLoggers.STD_TASK_GENERATE_ANTLR:
                    return Bundle.generate_antlr_sources();
                case AntlrLoggers.STD_TASK_RUN_ANALYZER:
                    return Bundle.run_analyzer();
            }
        }
        return task;
    }

    public void setTaskEnablement(String task, boolean state) {
        if (state) {
            enabledTasks.add(task);
            prefs().putBoolean(task, true);
        } else {
            enabledTasks.remove(task);
            prefs().putBoolean(task, false);
        }
    }

    private Preferences prefs() {
        return NbPreferences.forModule(OutputEnabledTasksImpl.class);
    }

    @Messages({
        "enable=Enable Debug Output in Output Window",
        "enableAll=Enable All"
    })
    public JPopupMenu createEnablementPopup() {
        JPopupMenu menu = new JPopupMenu();
        JMenu sub = new JMenu(Bundle.enable());
        Map<String, String> nameForDisplayName = new TreeMap<>();
        for (String task : KNOWN_TASKS) {
            nameForDisplayName.put(displayName(task), task);
        }
        for (Map.Entry<String, String> e : nameForDisplayName.entrySet()) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(e.getKey());
            item.setSelected(isTaskEnabled(e.getValue()));
            item.addActionListener(ae -> {
                boolean ena = isTaskEnabled(e.getValue());
                setTaskEnablement(e.getValue(), !ena);
            });
            sub.add(item);
        }
        menu.add(sub);
        return menu;
    }
}
