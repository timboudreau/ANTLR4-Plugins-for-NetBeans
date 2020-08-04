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
import java.lang.ref.WeakReference;
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
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
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

import static com.mastfrog.util.preconditions.Checks.notNull;
import static javax.swing.text.Document.StreamDescriptionProperty;

/**
 * Some utility adapter and convenience methods.
 *
 * @author Tim Boudreau
 */
public final class NbAntlrUtils {
    private static final ThreadLocal<Set<Object>> REPARSE_AFTER_DEFERRAL = ThreadLocal.withInitial( LinkedHashSet::new );
    private static final ThreadLocal<PostprocessingMode> MODE = ThreadLocal.withInitial(
            () -> PostprocessingMode.ENABLED );

    public static CharStream newCharStream( LexerInput input, String name ) {
        return new AntlrStreamAdapter( input, name );
    }

    public static <T extends TokenId> Lexer<T> createLexer( LexerRestartInfo<T> info, NbLexerAdapter<T, ?> adapter ) {
        return new GenericAntlrLexer<>( info, adapter );
    }

    public static AbstractEditorAction createGotoDeclarationAction( String mimeType, NameReferenceSetKey<?>... keys ) {
        return new AntlrGotoDeclarationAction( notNull( "mimeType", mimeType ), keys );
    }

    public static TaskFactory createErrorHighlightingTaskFactory( String mimeType ) {
        return AntlrInvocationErrorHighlighter.factory( mimeType );
    }

    private static int lastDocumentIdHash = -1;
    private static long lastExtractionRev = -1;
    private static WeakReference<Extraction> lastExtraction;

    public static void parseImmediately( Document doc, BiConsumer<Extraction, Exception> consumer ) {
        long ts = DocumentUtilities.getDocumentVersion( doc );
        Extraction result = null;
        synchronized ( NbAntlrUtils.class ) {
            if ( lastExtractionRev == ts && lastExtraction != null ) {
                if ( lastDocumentIdHash == System.identityHashCode( doc ) ) {
                    Extraction cached = lastExtraction.get();
                    if ( cached != null ) {
                        result = cached;
                    }
                }
            }
        }
        if ( result != null ) {
            consumer.accept( result, null );
            return;
        }
        parseImmediately( Source.create( doc ), ( ext, exp ) -> {
                      if ( exp == null && ext != null ) {
                          synchronized ( NbAntlrUtils.class ) {
                              lastExtractionRev = ts;
                              lastExtraction = new WeakReference<>( ext );
                              lastDocumentIdHash = System.identityHashCode( doc );
                          }
                      }
                      consumer.accept( ext, exp );
                  } );
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

    /*
     * private static void parseImmediatelyBypassingParserManager( Source src, BiConsumer<Extraction, Exception>
     * consumer ) {
     * // needed for tests to avoid initializing the module system
     * if ( AntlrMimeTypeRegistration.isAntlrLanguage( src.getMimeType() ) ) {
     * GrammarSource<Source> gs = GrammarSource.find( src, src.getMimeType() );
     * try {
     * CharStream str = gs.stream();
     * // XXX how to get a lexer?
     * LanguageHierarchy hier = MimeLookup.getLookup( src.getMimeType() )
     * .lookup( LanguageHierarchy.class );
     * if ( hier == null ) {
     * consumer.accept( null, new IllegalStateException(
     * "No language hierarchy for " + src.getMimeType() ) );
     * } else {
     * // public static ANTLRv4Lexer createAntlrLexer(CharStream stream)
     * Method meth = hier.getClass().getMethod(
     * "createAntlrLexer", CharStream.class );
     * org.antlr.v4.runtime.Lexer lex = ( org.antlr.v4.runtime.Lexer ) meth.invoke( null, str );
     * CommonTokenStream cts = new CommonTokenStream( lex );
     *
     * RulesMapping<?> rm = RulesMapping.forMimeType( src.getMimeType() );
     * // org.antlr.v4.runtime.Parser
     * Class<?> parserType = rm.parserType();
     * // org.antlr.v4.runtime.Parser p = parserType.newInstance(cts);
     * org.antlr.v4.runtime.Parser parser
     * = ( org.antlr.v4.runtime.Parser ) parserType.getConstructor(
     * TokenStream.class ).newInstance(
     * cts );
     *
     *
     * }
     * } catch ( Exception ex ) {
     * consumer.accept( null, ex );
     * }
     * } else {
     * consumer.accept( null, new IllegalStateException(
     * "Not a registered ANTLR mime type: " + src.getMimeType() ) );
     * }
     * }
     */
    private static void parseImmediately( Source src,
            BiConsumer<Extraction, Exception> consumer ) {
        String mime = src.getMimeType();
        if ( mime == null || ParserManager.canBeParsed( mime ) ) {
            try {
                onBeforeParse( src );
                ParserManager.parse( Collections.singleton( src ), new UserTask() {
                                 @Override
                                 public void run(
                                         ResultIterator resultIterator ) throws Exception {
                                     Parser.Result res = resultIterator.getParserResult();
                                     if ( res instanceof ExtractionParserResult ) {
                                         consumer.accept( ( ( ExtractionParserResult ) res ).extraction(), null );
                                     } else {
                                         if ( res == null ) {
                                             consumer.accept( null, null );
                                         } else {
                                             consumer.accept( null, new IllegalStateException(
                                                              "Not an ExtractionParserResult: "
                                                              + res + " ("
                                                              + ( res == null
                                                                          ? "null"
                                                                          : res.getClass().getName() )
                                                              + ") for source " + src
                                                              + ". Some other parser intercepted it?" ) );
                                         }
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

    public static void invalidateSource(FileObject fo) {
        INVALIDATOR.accept(fo);
    }

    private static void onPostProcessingExit( PostprocessingMode was, PostprocessingMode is ) {
        if ( is != was && was == PostprocessingMode.DEFERRED ) {
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

    /**
     * Parse trees are deliberately not embedded in an Antlr parser results,
     * so they do not memory-leak the entire parse tree for the lifetime of the
     * parser result, but may be needed for post-processing that happens within
     * the scope of post-parse runtime hooks. This method allows access to
     * it if called within that scope.
     *
     * @param <T>  The expected tree type
     * @param type The expected tree type
     *
     * @return A parse tree or null
     */
    public static <T extends ParserRuleContext> T currentTree( Class<T> type ) {
        return NbParserHelper.currentTree( type );
    }

    private NbAntlrUtils() {
        throw new AssertionError();
    }

}
