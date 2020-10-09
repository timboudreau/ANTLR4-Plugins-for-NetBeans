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

package org.nemesis.misc.utils.concurrent;

import com.mastfrog.util.collections.SetFactories;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Shaking out potential races...
 *
 * @author Tim Boudreau
 */
final class CoaWatchdog extends TimerTask {

    private final Set<WorkCoalescer> items = SetFactories.WEAK_HASH.newSet(12, true);

    static final CoaWatchdog INSTANCE = new CoaWatchdog();
    static final Timer timer = new Timer("coa-watchdog", true);
    static {
        timer.scheduleAtFixedRate(INSTANCE, 60000, 30000);
    }

    @Override
    public void run() {
        List<WorkCoalescer> all = new ArrayList<>(items);
        for (WorkCoalescer wc : all) {
            wc.shake();
        }
    }

    static void register(WorkCoalescer w) {
        INSTANCE.items.add(w);
    }

}
