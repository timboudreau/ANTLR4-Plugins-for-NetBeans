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
package org.nemesis.antlr.memory.spi;

import java.nio.file.Path;
import org.openide.util.Lookup;

/**
 * Implement to enable output for internal tasks on a case-by-case or project or
 * file by file basis; some output is really only useful for debugging the Antlr
 * module, while other output is useful to users.
 *
 * @author Tim Boudreau
 */
public abstract class OutputEnabledTasks {

    private static OutputEnabledTasks INSTANCE;
    private static boolean DEFAULT_OUTPUT_ENABLED
            = Boolean.getBoolean("antlr.output.enabled");

    static final boolean isOutputEnabled(Path path, String task) {
        if (INSTANCE == null) {
            INSTANCE = Lookup.getDefault().lookup(OutputEnabledTasks.class);
            if (INSTANCE == null) {
                INSTANCE = DummyOutputEnabledTasks.INSTANCE;
            }
        }
        return INSTANCE.outputEnabled(path, task);
    }

    protected abstract boolean outputEnabled(Path path, String task);

    private static final class DummyOutputEnabledTasks extends OutputEnabledTasks {

        private static final DummyOutputEnabledTasks INSTANCE = new DummyOutputEnabledTasks();

        @Override
        protected boolean outputEnabled(Path path, String task) {
            return DEFAULT_OUTPUT_ENABLED ;
        }
    }
}
