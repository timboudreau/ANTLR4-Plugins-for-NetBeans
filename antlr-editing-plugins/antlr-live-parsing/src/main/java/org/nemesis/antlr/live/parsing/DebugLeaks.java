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
package org.nemesis.antlr.live.parsing;

import java.util.function.BiConsumer;
import org.nemesis.debug.api.TrackingRoots;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = TrackingRoots.class, position = 20)
public class DebugLeaks implements TrackingRoots {

    @Override
    public void collect(BiConsumer<String, Object> nameAndObject) {
        nameAndObject.accept("EmbeddedAntlrParsers singleton", EmbeddedAntlrParsers.instance());
    }
}
