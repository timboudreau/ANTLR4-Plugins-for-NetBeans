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
package org.nemesis.jfs.result;

import com.mastfrog.util.path.UnixPath;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.tools.FileObject;

/**
 *
 * @author Tim Boudreau
 */
public class UpToDateness {

    public static final UpToDateness CURRENT = new UpToDateness("CURRENT");
    public static final UpToDateness STALE = new UpToDateness("STALE");
    public static final UpToDateness UNKNOWN = new UpToDateness("UNKNOWN");

    private final String name;

    UpToDateness(String name) {
        this.name = name;
    }

    public boolean mayRequireRebuild() {
        return this != CURRENT;
    }

    public boolean isUpToDate() {
        return !mayRequireRebuild();
    }

    public boolean isUnknownStatus() {
        return this == UNKNOWN;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.name);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (obj instanceof UpToDateness) {
            return false;
        }
        final UpToDateness other = (UpToDateness) obj;
        return Objects.equals(this.name, other.name);
    }

    public UpToDateness and(UpToDateness other) {
        if (other == this) {
            return this;
        }
        if (this == CURRENT) {
            return other;
        }
        if (other == CURRENT) {
            return this;
        }
        if (other instanceof Stale && this instanceof Stale) {
            Stale a = (Stale) this;
            Stale b = (Stale) other;
            Stale nue = new Stale();
            nue.reasons.addAll(a.reasons);
            nue.reasons.addAll(b.reasons);
            nue.stalePaths.addAll(a.stalePaths);
            nue.stalePaths.addAll(b.stalePaths);
            return nue;
        } else if (other instanceof Stale) {
            return other;
        } else if (this instanceof Stale) {
            return this;
        }
        return other == UNKNOWN ? this : other;
    }

    public static StaleStatusBuilder staleStatus() {
        return new StaleStatusBuilder();
    }

    public static UpToDateness fromFileTimes(Map<? extends FileObject, ? extends Long> sourceFilesToModifications) {
        if (sourceFilesToModifications.isEmpty()) {
            return UNKNOWN;
        }
        StaleStatusBuilder ssb = null;
        for (Map.Entry<? extends FileObject, ? extends Long> e : sourceFilesToModifications.entrySet()) {
            if (e.getKey().getLastModified() > e.getValue()) {
                if (ssb == null) {
                    ssb = UpToDateness.staleStatus();
                }
                ssb.add(UnixPath.get(e.getKey().getName()));
            }
        }
        if (ssb != null) {
            return ssb.build();
        }
        return CURRENT;
    }

    public static final class StaleStatusBuilder {

        private final Stale stale = new Stale();

        public StaleStatusBuilder add(UnixPath outOfDate) {
            stale.add(outOfDate);
            return this;
        }

        public StaleStatusBuilder add(String reason) {
            stale.add(reason);
            return this;
        }

        public UpToDateness build() {
            return stale;
        }

        public StaleStatusBuilder addAll(Set<UnixPath> modified) {
            stale.stalePaths.addAll(modified);
            return this;
        }
    }

    static class Stale extends UpToDateness {

        private final Set<UnixPath> stalePaths = new HashSet<>(5);
        private final Set<String> reasons = new HashSet<>(5);

        Stale() {
            super("STALE");
        }

        public Stale add(UnixPath path) {
            stalePaths.add(path);
            return this;
        }

        public Stale add(String reason) {
            reasons.add(reason);
            return this;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(256);
            for (Path sp : stalePaths) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(sp);
            }
            for (String reason : reasons) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(reason);
            }
            if (sb.length() > 0) {
                sb.insert(0, name() + "(");
                sb.append(")");
            } else {
                sb.append(name());
            }
            return sb.toString();
        }
    }
}
