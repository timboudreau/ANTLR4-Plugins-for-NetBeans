/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED 
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY 
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.language;

import java.util.Collections;
import java.util.Set;
import org.netbeans.api.lexer.Language;
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
        Language<?> lang = Language.find(mimeType);
        System.out.println("FOUND LAN " + lang);
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
