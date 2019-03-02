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
package org.nemesis.antlrformatting.api;

import org.nemesis.antlrformatting.impl.FormattingAccessor;
import org.nemesis.antlrformatting.spi.AntlrFormatterProvider;
import org.netbeans.modules.csl.api.Formatter;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Looks up a registered ANTLR formatter for a given MIME type.
 *
 * @author Tim Boudreau
 */
public final class AntlrFormatters {

    /**
     * AntlrFormatterProvider instances are registered with Lookups.forPath(BASE + mimeType).
     */
    public static final String BASE = "antlr/formatters/";

    private AntlrFormatters() {

    }

    public static Formatter forMimeType(String mime) {
        Lookup lkp = Lookups.forPath(BASE + mime);
        AntlrFormatterProvider<?,?> provider = lkp.lookup(AntlrFormatterProvider.class);
        if (provider != null) {
            return FormattingAccessor.getDefault().toFormatter(provider);
        }
        return null;
    }

    public static boolean hasFormatter(String mime) {
        Lookup lkp = Lookups.forPath(BASE + mime);
        Lookup.Item<?> item = lkp.lookupItem(TEMPLATE);
        return item != null;
    }

    static final Lookup.Template<AntlrFormatterProvider> TEMPLATE = new Lookup.Template<>(AntlrFormatterProvider.class);
}
