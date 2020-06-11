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
package org.nemesis.antlr.live.language;

import org.nemesis.antlr.live.language.coloring.AdhocColoringsRegistry;
import java.util.Collections;
import java.util.Set;
import org.netbeans.modules.parsing.spi.Parser;
import org.openide.util.Lookup;

/**
 *
 * @author Tim Boudreau
 */
public class DynamicLanguages {

    public static void deregister(String mimeType) {
        if (!AdhocColoringsRegistry.getDefault().isRegistered(mimeType)) {
            AdhocColoringsRegistry.getDefault().remove(mimeType);
        }
        if (!AdhocMimeDataProvider.getDefault().isRegistered(mimeType)) {
            AdhocMimeDataProvider.getDefault().removeMimeType(mimeType);
        }
    }

    public static Set<String> mimeTypes() {
        return AdhocColoringsRegistry.getDefault().mimeTypes();
    }

    public static boolean isRegistered(String mimeType) {
        return AdhocColoringsRegistry.getDefault().isRegistered(mimeType)
                && AdhocMimeDataProvider.getDefault().isRegistered(mimeType);
    }

    public static boolean ensureRegistered(String mimeType) {
        boolean result = false;
        int regs = 0;
        if (!AdhocColoringsRegistry.getDefault().isRegistered(mimeType)) {
            regs++;
            AdhocColoringsRegistry.getDefault().get(mimeType);
            result = true;
        }
        if (!AdhocMimeDataProvider.getDefault().isRegistered(mimeType)) {
            AdhocMimeDataProvider.getDefault().addMimeType(mimeType);
            regs++;
            result = true;
        }
//        if (regs < 2) {
//        Language<?> lang = Language.find(mimeType);
//        System.out.println("FOUND LAN " + lang);
//        }
        return result;
    }

    static AdhocMimeDataProvider mimeData() {
        return Lookup.getDefault().lookup(AdhocMimeDataProvider.class);
    }

    static Parser parser(String mimeType) {
        AdhocParserFactory pf = mimeData().getLookup(mimeType).lookup(AdhocParserFactory.class);
        if (pf == null) {
            return null;
        }
        return pf.createParser(Collections.emptyList());
    }

    private DynamicLanguages() {
        throw new AssertionError();
    }
}
