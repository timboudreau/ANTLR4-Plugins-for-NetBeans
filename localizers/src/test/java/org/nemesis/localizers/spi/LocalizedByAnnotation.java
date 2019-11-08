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

import org.nemesis.localizers.annotations.Localize;

/**
 *
 * @author Tim Boudreau
 */
public class LocalizedByAnnotation {

    @Localize(
            displayName = "org.nemesis.localizers.spi.OtherBundle#LocName",
            iconPath = "org/nemesis/localizers/spi/pig.png",
            hints = {
                @Localize.KeyValuePair(key = "1", value = "one"),
                @Localize.KeyValuePair(key = "2", value = "two")
            }
    )
    public static final LocalizedByAnnotation FIELD = new LocalizedByAnnotation("Hey There");
    private final String msg;

    public LocalizedByAnnotation(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "LocalizedByAnnotation(" + msg + ")";
    }
//    @Localize(displayName = "Foodbar")
//    String foozel = "q";
}
