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
package org.nemesis.antlr.spi.language;

import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;

import static javax.swing.text.Document.StreamDescriptionProperty;
import static org.openide.util.Parameters.notNull;

/**
 * Some utility adapter and convenience methods.
 *
 * @author Tim Boudreau
 */
public final class NbAntlrUtils {

    public static CharStream newCharStream( LexerInput input, String name ) {
        return new AntlrStreamAdapter( input, name );
    }

    public static <T extends TokenId> Lexer<T> createLexer( LexerRestartInfo<T> info, NbLexerAdapter<T, ?> adapter ) {
        return new GenericAntlrLexer<>( info, adapter );
    }

    public static AbstractEditorAction createGotoDeclarationAction( NameReferenceSetKey<?>... keys ) {
        return new AntlrGotoDeclarationAction( keys );
    }

    public static TaskFactory createErrorHighlightingTaskFactory( String mimeType ) {
        return AntlrInvocationErrorHighlighter.factory( mimeType );
    }

    public static void parseImmediately( Document doc, BiConsumer<Extraction, Exception> consumer ) {
        parseImmediately( Source.create( doc ), consumer );
    }

    public static void parseImmediately( FileObject file, BiConsumer<Extraction, Exception> consumer ) {
        parseImmediately( Source.create( file ), consumer );
    }

    public static Extraction parseImmediately( Document doc ) throws Exception {
        Extraction[] ext = new Extraction[ 1 ];
        Exception[] ex = new Exception[ 1 ];
        parseImmediately( doc, ( res, thrown ) -> {
                      ex[ 0 ] = thrown;
                      ext[ 0 ] = res;
                  } );
        if ( ex[ 0 ] != null ) {
            throw ex[ 0 ];
        }
        return ext[ 0 ];
    }

    public static Extraction parseImmediately( FileObject file ) throws Exception {
        Extraction[] ext = new Extraction[ 1 ];
        Exception[] ex = new Exception[ 1 ];
        parseImmediately( file, ( res, thrown ) -> {
                      ex[ 0 ] = thrown;
                      ext[ 0 ] = res;
                  } );
        if ( ex[ 0 ] != null ) {
            throw ex[ 0 ];
        }
        return ext[ 0 ];
    }

    private static void parseImmediately( Source src, BiConsumer<Extraction, Exception> consumer ) {
        String mime = src.getMimeType();
        if ( mime == null || ParserManager.canBeParsed( mime ) ) {
            try {
                onBeforeParse( src );
                ParserManager.parse( Collections.singleton( src ), new UserTask() {
                                 @Override
                                 public void run( ResultIterator resultIterator ) throws Exception {
                                     Parser.Result res = resultIterator.getParserResult();
                                     if ( res instanceof ExtractionParserResult ) {
                                         consumer.accept( ( ( ExtractionParserResult ) res ).extraction(), null );
                                     } else {
                                         consumer.accept( null, new IllegalStateException(
                                                          "Not an ExtractionParserResult: "
                                                          + res + " (" + res.getClass().getName()
                                                          + "). Some other parser intercepted it?" ) );
                                     }
                                 }
                             } );
            } catch ( ParseException ex ) {
                consumer.accept( new Extraction(), ex );
            }
        } else {
            consumer.accept( null, new IllegalArgumentException(
                             "No parser registered for mime type " + mime ) );
        }
    }

    private static void onBeforeParse( Source source ) {
        if ( MODE.get() == PostprocessingMode.DEFERRED ) {
            Set<Object> reparseLater = REPARSE_AFTER_DEFERRAL.get();
            Document doc = source.getDocument( false );
            if ( doc != null ) {
                reparseLater.add( doc );
            } else {
                reparseLater.add( source.getFileObject() );
            }
        }
    }

    private static ThreadLocal<Set<Object>> REPARSE_AFTER_DEFERRAL = ThreadLocal.withInitial( LinkedHashSet::new );
    private static ThreadLocal<PostprocessingMode> MODE = ThreadLocal.withInitial( () -> PostprocessingMode.ENABLED );

    /**
     * Disables post-processing of parser results - such as running parser result
     * hooks to populate errors, within the closure of the passed supplier. This
     * is used for a few features, such as in-place rename, where a parse is done on every
     * keystroke and running full analysis to populate error hints and such would be useless
     * and interfere with performance.
     *
     * @param <T>  The type returned
     * @param supp A supplier
     *
     * @return The result of invoking the supplier
     */
    public static <T> T withPostProcessingDisabled( Supplier<T> supp ) {
        return withPostProcessingMode( PostprocessingMode.SUSPENDED, supp );
    }

    /**
     * Disables post-processing of parser results - such as running parser result
     * hooks to populate errors, within the closure of the passed supplier. This
     * is used for a few features, such as in-place rename, where a parse is done on every
     * keystroke and running full analysis to populate error hints and such would be useless
     * and interfere with performance.
     *
     * @param <T>  The type returned
     * @param supp A supplier
     *
     * @return The result of invoking the supplier
     */
    public static <T> T withPostProcessingDisabledThrowing( ThrowingSupplier<T> supp ) throws Exception {
        return withPostProcessingMode( PostprocessingMode.SUSPENDED, supp );
    }

    /**
     * Deferrs post-processing of parser results - such as running parser result
     * hooks to populate errors, within the closure of the passed supplier. This
     * is used for a few features, such as in-place rename, where a parse is done on every
     * keystroke and running full analysis to populate error hints and such would be useless
     * and interfere with performance. Using the deferred method, after all reentrant
     * calls have exited, the source will be invalidated and a reparse forced of any
     * sources parsed within the closure of the outermost passed supplier.
     *
     * @param <T>  The type returned
     * @param supp A supplier
     *
     * @return The result of invoking the supplier
     */
    public static <T> T withPostProcessingDeferred( Supplier<T> supp ) {
        return withPostProcessingMode( PostprocessingMode.DEFERRED, supp );
    }

    /**
     * Disables post-processing of parser results - such as running parser result
     * hooks to populate errors, within the closure of the passed supplier. This
     * is used for a few features, such as in-place rename, where a parse is done on every
     * keystroke and running full analysis to populate error hints and such would be useless
     * and interfere with performance. Using the deferred method, after all reentrant
     * calls have exited, the source will be invalidated and a reparse forced of any
     * sources parsed within the closure of the outermost passed supplier.
     *
     * @param <T>  The type returned
     * @param supp A supplier
     *
     * @return The result of invoking the supplier
     */
    public static <T> T withPostProcessingDeferredThrowing( ThrowingSupplier<T> supp ) throws Exception {
        return withPostProcessingMode( PostprocessingMode.DEFERRED, supp );
    }

    static PostprocessingMode postProcessingMode() {
        return MODE.get();
    }

    public static boolean isPostprocessingEnabled() {
        return MODE.get().isPostprocessing();
    }

    private static <T> T withPostProcessingMode( PostprocessingMode mode, ThrowingSupplier<T> supp ) throws Exception {
        notNull( "mode", mode );
        PostprocessingMode old = MODE.get();
        try {
            MODE.set( mode );
            return supp.get();
        } finally {
            MODE.set( old );
            onPostProcessingExit( old, mode );
        }
    }

    private static <T> T withPostProcessingMode( PostprocessingMode mode, Supplier<T> supp ) {
        notNull( "mode", mode );
        PostprocessingMode old = MODE.get();
        try {
            MODE.set( mode );
            return supp.get();
        } finally {
            MODE.set( old );
            onPostProcessingExit( old, mode );
        }
    }

    static Consumer<FileObject> INVALIDATOR = SourceInvalidator.create();

    private static void onPostProcessingExit( PostprocessingMode was, PostprocessingMode is ) {
        if ( is != was && was == PostprocessingMode.DEFERRED) {
            // Force an invalidate and reparse of anything parsed during
            // deferral, so that syntax highlighting is up-to-date and similar -
            // these will not have been called for any parses while suspended
            List<Object> toReparse = new ArrayList<>( REPARSE_AFTER_DEFERRAL.get() );
            for ( Object reparse : toReparse ) {
                if ( reparse instanceof Document ) {
                    Document d = ( Document ) reparse;
                    Object o = d.getProperty( StreamDescriptionProperty );
                    FileObject fo = null;
                    if ( o instanceof DataObject ) {
                        fo = ( ( DataObject ) o ).getPrimaryFile();
                    } else if ( o instanceof FileObject ) {
                        fo = ( FileObject ) o;
                    }
                    if ( fo != null && fo.isValid() ) {
                        INVALIDATOR.accept( fo );
                    }
                    try {
                        parseImmediately( d );
                    } catch ( Exception ex ) {
                        Exceptions.printStackTrace( ex );
                    }
                } else if ( reparse instanceof FileObject ) {
                    FileObject fo = ( FileObject ) reparse;
                    if ( fo.isValid() ) {
                        INVALIDATOR.accept( fo );
                        try {
                            parseImmediately( fo );
                        } catch ( Exception ex ) {
                            Exceptions.printStackTrace( ex );
                        }
                    }
                }
            }
        }
    }

    enum PostprocessingMode {
        ENABLED,
        DEFERRED,
        SUSPENDED;

        boolean isPostprocessing() {
            return this == ENABLED;
        }
    }

    private NbAntlrUtils() {
        throw new AssertionError();
    }

}
