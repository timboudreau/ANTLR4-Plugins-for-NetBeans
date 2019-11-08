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
@Localize(displayName="Places", iconPath = "org/nemesis/foo/foo.png")
public enum EnumLocalizedByAnnotation {

    @Localize(displayName = "Prague, Czech Republic", iconPath = "org/nemesis/localizers/spi/pig.png",
            hints = {
                @Localize.KeyValuePair(key = "countryCode", value = "CZ"),
                @Localize.KeyValuePair(key = "currency", value = "CSK")
            })
    PRAGUE,
    @Localize(displayName = "Washington, District of Columbia, USA",
            hints = {
                @Localize.KeyValuePair(key = "countryCode", value = "US"),
                @Localize.KeyValuePair(key = "currency", value = "USD")
            })
    WASHINGTON;
}
