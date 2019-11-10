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
package org.nemesis.antlr.refactoring.impl;

import java.util.Map;
import javax.swing.Icon;
import org.nemesis.extraction.SingletonEncounters.SingletonEncounter;
import org.nemesis.localizers.api.Localizers;
import org.nemesis.localizers.spi.Localizer;
import org.openide.util.lookup.ServiceProvider;

/**
 * Simply forwards localization of a NamedSemanticRegion to the
 * localizer (if any) for its key.
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Localizer.class, path
        = "loc/instances/org/nemesis/extraction/SingletonEncounters$SingletonEncounter")
public class SingletonEncounterLocalizer extends Localizer<SingletonEncounter<?>> {

    @Override
    protected boolean matches(Object o) {
        return o instanceof SingletonEncounter<?>;
    }

    @Override
    protected String displayName(SingletonEncounter<?> obj) {
        return Localizers.displayName(((SingletonEncounter<?>) obj).get());
    }

    @Override
    protected Map<String, String> hints(SingletonEncounter<?> obj) {
        return Localizers.hints(((SingletonEncounter<?>) obj).get());
    }

    @Override
    protected Icon icon(SingletonEncounter<?> obj) {
        return Localizers.icon(((SingletonEncounter<?>) obj).get());
    }
}
