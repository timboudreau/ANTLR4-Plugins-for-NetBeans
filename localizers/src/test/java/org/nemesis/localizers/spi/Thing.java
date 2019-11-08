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
package org.nemesis.localizers.spi;

import org.nemesis.localizers.spi.foo.OtherLocalizableInterface;

/**
 *
 * @author Tim Boudreau
 */
public class Thing implements OtherLocalizableInterface {

    public static final Thing THING_1 = new Thing(1);
    public static final Thing THING_2 = new Thing(2);

    private final int index;

    public Thing(int index) {
        this.index = index;
    }

    public String toString() {
        return "thing-" + index;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + this.index;
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
        final Thing other = (Thing) obj;
        return this.index == other.index;
    }

    @Override
    public String locInfo() {
        return index + "-Thing";
    }
}
