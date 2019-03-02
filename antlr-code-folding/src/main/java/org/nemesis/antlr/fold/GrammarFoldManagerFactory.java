/*
BSD License

Copyright (c) 2016, Frédéric Yvon Vinet
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

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.nemesis.antlr.fold;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.extraction.key.RegionsKey;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldManagerFactory;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public final class GrammarFoldManagerFactory<T> implements FoldManagerFactory {

    private final RegionsKey<T> key;
    private final SemanticRegionToFoldConverter<? super T> converter;
    private static final Logger LOG = Logger.getLogger(GrammarFoldManagerFactory.class.getName());

    public GrammarFoldManagerFactory(RegionsKey<T> key, SemanticRegionToFoldConverter<? super T> converter) {
        this.key = key;
        this.converter = converter;
        LOG.log(Level.FINE, "Create GrammarFoldManagerFactory for {0} with {1}", new Object[]{key, converter});
    }

    public static <T> FoldManagerFactory create(RegionsKey<T> key) {
        return new GrammarFoldManagerFactory<>(key, SemanticRegionToFoldConverter.DEFAULT);
    }

    public static <T> FoldManagerFactory create(RegionsKey<T> key, SemanticRegionToFoldConverter<T> converter) {
        return new GrammarFoldManagerFactory<>(key, converter);
    }

    @Override
    public FoldManager createFoldManager() {
        return new ExtractionElementsFoldManager(key, converter);
    }
}
