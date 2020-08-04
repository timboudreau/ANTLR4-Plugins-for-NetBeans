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

package org.nemesis.test.fixtures.support;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;

/**
 *
 * @author Tim Boudreau
 */
public class ListenableConsoleHandler extends ConsoleHandler {

    public ListenableConsoleHandler() {
    }

    @Override
    public void publish(LogRecord record) {
        if (!TestFixtures.triggers.isEmpty()) {
            String msg = record.getMessage();
            List<ConsoleListenEntry> toRemove = new ArrayList<>();
            for (ConsoleListenEntry e : TestFixtures.triggers) {
                if (e.go(msg)) {
                    toRemove.add(e);
                }
            }
            if (!toRemove.isEmpty()) {
                TestFixtures.triggers.removeAll(toRemove);
            }
        }
        super.publish(record);
    }

}
