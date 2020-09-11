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
package org.nemesis.jfs;

import com.mastfrog.util.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.nemesis.jfs.spi.JFSUtilities;

/**
 * Allows for collecting the set of files modified between the point a
 * Checkpoint was created and the call to <code>updatedFiles()</code> (which may
 * be called exactly <i>once</i>).
 *
 * @author Tim Boudreau
 */
public final class Checkpoint {

    private final Set<JFSCoordinates> coords = ConcurrentHashMap.newKeySet(32);
    private volatile Checkpoints controller;
    private volatile boolean used;

    private Checkpoint(Checkpoints controller) {
        this.controller = controller;
    }

    /**
     * Get the set of updated files, clearing this Checkpoint's state, and
     * causing it not to be updated further; any subsequent call will throw an
     * exception.
     *
     * @return An immutable set of those files which were created or written
     * since this checkpoint was created
     */
    public Set<JFSCoordinates> updatedFiles() {
        if (used) {
            throw new IllegalStateException("Already used");
        }
        try {
            used = true;
            Set<JFSCoordinates> result = CollectionUtils.immutableSet(coords);
            coords.clear();
            return result;
        } finally {
            if (controller != null) {
                controller.remove(this);
                controller = null;
            }
        }
    }

    static Checkpoints newCheckpoints() {
        return new Checkpoints();
    }

    static class Checkpoints {

        private Set<Checkpoint> live;

        private Checkpoints() {

        }

        synchronized void remove(Checkpoint pt) {
            live.remove(pt);
        }

        synchronized Checkpoint newCheckpoint() {
            Checkpoint result = new Checkpoint(this);
            if (live == null) {
                live = Collections.synchronizedSet(JFSUtilities.newWeakSet());
            }
            live.add(result);
            return result;
        }

        void touch(JFSFileObject fo) {
            Set<Checkpoint> lv = live;
            if (lv == null) {
                synchronized (this) {
                    lv = live;
                }
                if (lv == null || lv.isEmpty()) {
                    return;
                }
            }
            List<Checkpoint> all = new ArrayList<>(lv);
            JFSCoordinates coords = fo.toCoordinates();
            all.forEach(c -> {
                c.coords.add(coords);
            });
        }

        synchronized void clear() {
            live.clear();
            live = null;
        }
    }
}
