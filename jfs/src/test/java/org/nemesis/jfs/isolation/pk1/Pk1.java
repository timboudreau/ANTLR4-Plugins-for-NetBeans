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
package org.nemesis.jfs.isolation.pk1;

import org.nemesis.jfs.isolation.pk1.pk1a.Pk1a;

/**
 *
 * @author Tim Boudreau
 */
public class Pk1 {

    private final Pk1a pk1a;

    public Pk1() {
        pk1a = new Pk1a("Stuff");
    }
}
