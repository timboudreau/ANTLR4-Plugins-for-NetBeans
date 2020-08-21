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
package org.nemesis.antlr.spi.language.fix;

import java.util.logging.Level;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.extraction.Extraction;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.text.StyledDocument;
import org.nemesis.editor.function.DocumentConsumer;
import org.nemesis.editor.position.PositionFactory;
import org.nemesis.editor.position.PositionRange;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;

import static org.nemesis.antlr.spi.language.fix.Fixes.LOG;
import static org.nemesis.antlr.spi.language.fix.Fixes.findDocument;

/**
 * Convenience class which makes adding error descriptions with hints to a
 * parsed document much more straightforward.
 *
 * @author Tim Boudreau
 */
final class FixesImpl extends Fixes {

    private final Extraction extraction;
    private final ParseResultContents contents;
    final StyledDocument document;
    final PositionFactory positions;
    private final Set<String> usedErrorIds = new HashSet<>();

    /**
     * Create a new Fixes with the passed extraction and parse result contents
     * to feed error descriptions into.
     *
     * @param extraction An extraction
     * @param contents   The contents of an AntlrParseResult
     */
    FixesImpl( Extraction extraction, ParseResultContents contents ) throws IOException {
        document = findDocument( extraction );
        this.extraction = extraction;
        this.contents = contents;
        this.positions = PositionFactory.forDocument( document );
    }
    
    Extraction extraction() {
        return extraction;
    }

    @Override
    public void copyFrom( Fixes other ) {
        if (other instanceof FixesImpl) {
            FixesImpl fi = ( FixesImpl ) other;
            if (fi.extraction().sourceLastModifiedAtExtractionTime() >= extraction.sourceLastModifiedAtExtractionTime()) {
                usedErrorIds.clear();
                usedErrorIds.addAll(fi.usedErrorIds);
                contents.replaceErrors( ((FixesImpl) other).contents);
            }
        }
    }

    @Override
    StyledDocument document() {
        return document;
    }

    @Override
    PositionFactory positions() {
        return positions;
    }

    @Override
    public boolean isUsedErrorId(String id) {
        return usedErrorIds.contains(id);
    }

    @Override
    public void add( String id, Severity severity, String description, PositionRange range,
            Supplier<? extends CharSequence> details,
            DocumentConsumer<FixConsumer> lazyFixes ) {
        LOG.log( Level.FINER, "Add hint {0} {2} ''{3}'' @ {4} with {5}",
                 new Object[]{ id, severity, description, range, lazyFixes } );
        if ( id == null ) {
            id = range.id() + ":" + description;
        } else {
            if (usedErrorIds.contains(id)) {
                return;
            }
        }
        usedErrorIds.add( id );
        ErrorDescription desc;
        CharSequence detailsSeq = details == null ? "" : new LazyCharSequence( details );
        if ( lazyFixes == null ) {
            desc = ErrorDescriptionFactory.createErrorDescription( id, severity, description,
                                                                   detailsSeq,
                                                                   NoFixes.NO_FIXES,
                                                                   document, range.startPosition(), range
                                                                   .endPosition() );
        } else {
            LazyFixList hints = new VeryLazyHintsList( lazyFixes, extraction, document );
//            LazyFixList hints = new NotSoLazyFixList( document, lazyFixes );
            desc = ErrorDescriptionFactory
                    .createErrorDescription( id, severity, description, detailsSeq, hints, document, range
                                             .startPosition(),
                                             range.endPosition() );
        }
        contents.addErrorDescription( desc );
    }
}
