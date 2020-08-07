/*
 * Copyright 2019 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.spi.language.impl;

import com.mastfrog.antlr.code.completion.spi.Completer;
import com.mastfrog.antlr.code.completion.spi.CompletionsSupplier;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.nemesis.antlr.spi.language.AntlrMimeTypeRegistration;
import org.nemesis.antlr.spi.language.NbAntlrUtils;
import org.nemesis.extraction.Extraction;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;

/**
 * Used by generic code completion to find matches for names
 * by rule id, without specifically referencing Extraction.
 *
 * @author Tim Boudreau
 */
@ServiceProvider( service = CompletionsSupplier.class, position = 100000 )
public final class CompletionsSupplierImplementation extends CompletionsSupplier {
    @Override
    public Completer forDocument( Document document ) {
        String mime = ( String ) document.getProperty( "mimeType" );
        if ( mime != null && AntlrMimeTypeRegistration.isDefaultCompletionSupplierEnabled( mime ) ) {
            try {
                Extraction ext = NbAntlrUtils.parseImmediately( document );
                if ( ext != null ) {
                    return new CP( ext );
                } else {
                    Logger.getLogger( CompletionsSupplierImplementation.class.getName() )
                            .log( Level.INFO, "No extraction for completions from {0} of {1}",
                                  new Object[]{ document, mime } );
                }
            } catch ( Exception ex ) {
                Exceptions.printStackTrace( ex );
            }
        }
        return noop();
    }

    static class CP implements Completer {
        private final Extraction ext;

        public CP( Extraction ext ) {
            this.ext = ext;
        }

        @Override
        public void namesForRule( int parserRuleId, String optionalPrefix, int maxResultsPerKey, String optionalSuffix,
                BiConsumer<String, Enum<?>> names ) {
            ext.namesForRule( parserRuleId, optionalPrefix, maxResultsPerKey, optionalSuffix, names );
        }

    }
}
