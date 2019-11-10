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
import org.nemesis.data.named.NamedSemanticRegion;
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
        = "loc/enums/org/nemesis/data/named/NamedSemanticRegion")
public class NamedSemanticRegionLocalizer extends Localizer<NamedSemanticRegion<?>> {

    @Override
    protected boolean matches(Object o) {
        return o instanceof NamedSemanticRegion<?>;
    }

    @Override
    protected String displayName(NamedSemanticRegion<?> obj) {
        return Localizers.displayName(((NamedSemanticRegion<?>) obj).kind());
    }

    @Override
    protected Map<String, String> hints(NamedSemanticRegion<?> obj) {
        return Localizers.hints(((NamedSemanticRegion<?>) obj).kind());
    }

    @Override
    protected Icon icon(NamedSemanticRegion<?> obj) {
        return Localizers.icon(((NamedSemanticRegion<?>) obj).kind());
    }
}
