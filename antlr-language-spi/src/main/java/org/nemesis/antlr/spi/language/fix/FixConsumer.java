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

import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.nemesis.extraction.Extraction;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.range.IntRange;
import java.util.ArrayList;
import java.util.Collection;
import org.netbeans.editor.BaseDocument;
import org.openide.filesystems.FileObject;
import org.openide.text.NbDocument;

/**
 * Consumer for proposed fixes which will apply hints.
 *
 * @author Tim Boudreau
 */
public abstract class FixConsumer implements BiConsumer<ThrowingFunction<BaseDocument, String>, FixImplementation> {

    FixConsumer() {
    }

    /**
     * Add a proposed fix.
     *
     * @param describer   A supplier for the fix description
     * @param implementer The code to run to implement the fix
     *
     * @return this
     */
    public final FixConsumer add( ThrowingFunction<BaseDocument, String> describer, FixImplementation implementer ) {
        accept( describer, implementer );
        return this;
    }

    /**
     * Add a proposed fix.
     *
     * @param describer   The fix description
     * @param implementer The code to run to implement the fix
     *
     * @return this
     */
    public final FixConsumer add( String description, FixImplementation implementer ) {
        accept( staticName( description ), implementer );
        return this;
    }

    private ThrowingFunction<BaseDocument, String> staticName( String name ) {
        return new StaticName( name );
    }

    // For loggability
    private static final class StaticName implements ThrowingFunction<BaseDocument, String> {
        private final String name;

        public StaticName( String name ) {
            this.name = name;
        }

        @Override
        public String apply( BaseDocument in ) {
            return name;
        }

        public String toString() {
            return name;
        }
    }

    public final FixConsumer addReplacement( String description, int start, int end, String replacementText ) {
        return add( description, replacement( start, end, replacementText ) );
    }

    public final FixConsumer addDeletion( String description, int start, int end ) {
        return add( description, deletion( start, end ) );
    }

    public final FixConsumer addDeletions( String description, Collection<? extends IntRange<? extends IntRange>> ranges ) {
        return add( description, deletions( ranges ) );
    }

    public final FixConsumer addInsertion( String description, int start, int end, String insertionText ) {
        return add( description, insertion( start, end, insertionText ) );
    }

    public final FixConsumer addReplacement( ThrowingFunction<BaseDocument, String> describer, int start, int end,
            String replacementText ) {
        return add( describer, replacement( start, end, replacementText ) );
    }

    public final FixConsumer addDeletion( ThrowingFunction<BaseDocument, String> describer, int start, int end ) {
        return add( describer, deletion( start, end ) );
    }

    public final FixConsumer addDeletions( ThrowingFunction<BaseDocument, String> describer,
            Collection<? extends IntRange<? extends IntRange>> ranges ) {
        return add( describer, deletions( ranges ) );
    }

    public final FixConsumer addInsertion( ThrowingFunction<BaseDocument, String> describer, int start, int end,
            String insertionText ) {
        return add( describer, insertion( start, end, insertionText ) );
    }

    static FixImplementation replacement( int start, int end, String replacementText ) {
        return ( BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits ) -> {
            edits.replace( document, start, end, replacementText );
        };
    }

    static FixImplementation insertion( int start, int end, String insertedText ) {
        return ( BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits ) -> {
            edits.insert( document, start, insertedText );
        };
    }

    static FixImplementation deletion( int start, int end ) {
        return ( BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits ) -> {
            edits.delete( document, start, end );
        };
    }

    static FixImplementation deletions( Collection<? extends IntRange<? extends IntRange>> ranges ) {
        ArrayList<IntRange<? extends IntRange>> all = new ArrayList<>( ranges );
        return ( BaseDocument document, Optional<FileObject> file, Extraction extraction, DocumentEditBag edits ) -> {
            edits.multiple( document, e -> {
                        for ( IntRange<? extends IntRange> r : all ) {
                            e.delete( r.start(), r.end() );
                        }
                    } );
        };
    }

    /**
     * Convenience method, since hints often mention a line number;
     * find the line number of an offset in a document, with the caveat
     * that NbDocument.findLineNumber will return the <i>previous</i>
     * line for a character that starts a line; so this simply tests
     * if the offset is > 0 and the preceding character is a newline,
     * and if so, adds one to the result.
     *
     * @param doc
     * @param offset
     *
     * @return
     */
    public final int lineNumberForDisplay( BaseDocument doc, int offset ) throws BadLocationException {
        int line = NbDocument.findLineNumber( ( StyledDocument ) doc, offset );
        if ( offset > 0 && doc.getText( offset - 1, 1 ).charAt( 0 ) == '\n' ) {
            line++;
        }
        return line;
    }
}
