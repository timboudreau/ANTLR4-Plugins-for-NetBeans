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

import org.nemesis.antlr.common.TimedWeakReference;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.MapFactories;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.Document;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.nemesis.extraction.Extraction;
import org.nemesis.extraction.ExtractionParserResult;
import org.nemesis.extraction.key.NameReferenceSetKey;
import org.nemesis.misc.utils.concurrent.WorkCoalescer;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.TaskFactory;
import org.netbeans.spi.editor.AbstractEditorAction;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
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

    private static Document documentForFile( FileObject fo ) {
        try {
            DataObject dob = DataObject.find( fo );
            EditorCookie ck = dob.getLookup().lookup( EditorCookie.class );
            if ( ck != null ) {
                return ck.getDocument();
            }
            return null;
        } catch ( DataObjectNotFoundException ex ) {
            Exceptions.printStackTrace( ex );
            return null;
        }
    }

    public static void parseImmediately( FileObject file, BiConsumer<Extraction, Exception> consumer ) {
        Document doc = documentForFile( file );
        if ( doc != null ) {
            parseImmediately( doc, consumer );
            return;
        }
        parseImmediately( Source.create( file ), consumer );
    }

    @SuppressWarnings( "ThrowableResultIgnored" )
    public static Extraction parseImmediately( Document doc ) throws Exception {
        Obj<Extraction> ext = Obj.create();
        Obj<Exception> exc = Obj.create();
        parseImmediately( doc, ( res, thrown ) -> {
                      exc.set( thrown );
                      ext.set( res );
                  } );
        if ( exc.isSet() ) {
            throw exc.get();
        }
        return ext.get();
    }

    @SuppressWarnings( "ThrowableResultIgnored" )
    public static Extraction parseImmediately( FileObject file ) throws Exception {
        Obj<Extraction> ext = Obj.create();
        Obj<Exception> exc = Obj.create();
        parseImmediately( file, ( res, thrown ) -> {
                      exc.set( thrown );
                      ext.set( res );
                  } );
        if ( exc.isSet() ) {
            throw exc.get();
        }
        return ext.get();
    }

//    private static void parseImmediatelyBypassingParserManager( Source src, BiConsumer<Extraction, Exception> consumer ) {
//        // needed for tests to avoid initializing the module system
//        if ( AntlrMimeTypeRegistration.isAntlrLanguage( src.getMimeType() ) ) {
//            GrammarSource<Source> gs = GrammarSource.find( src, src.getMimeType() );
//            try {
//                CharStream str = gs.stream();
//                // XXX how to get a lexer?
//                LanguageHierarchy hier = MimeLookup.getLookup( src.getMimeType() )
//                        .lookup( LanguageHierarchy.class );
//                if ( hier == null ) {
//                    consumer.accept( null, new IllegalStateException(
//                                     "No language hierarchy for " + src.getMimeType() ) );
//                } else {
//                    // public static ANTLRv4Lexer createAntlrLexer(CharStream stream)
//                    Method meth = hier.getClass().getMethod(
//                            "createAntlrLexer", CharStream.class );
//                    org.antlr.v4.runtime.Lexer lex = ( org.antlr.v4.runtime.Lexer ) meth.invoke( null, str );
//                    CommonTokenStream cts = new CommonTokenStream( lex );
//                    RulesMapping<?> rm = RulesMapping.forMimeType( src.getMimeType() );
//
//                    // org.antlr.v4.runtime.Parser
//                    Class<?> parserType = rm.parserType();
//                    // org.antlr.v4.runtime.Parser p = parserType.newInstance(cts);
//                    org.antlr.v4.runtime.Parser parser
//                            = ( org.antlr.v4.runtime.Parser ) parserType.getConstructor(
//                                    TokenStream.class ).newInstance(
//                                            cts );
//
//                    Extractors.getDefault().extract( src.getMimeType(), gs, ( Class ) parserType );
//                }
//            } catch ( Exception ex ) {
//                consumer.accept( null, ex );
//            }
//        } else {
//            consumer.accept( null, new IllegalStateException(
//                             "Not a registered ANTLR mime type: " + src.getMimeType() ) );
//        }
//    }
    private static final Pattern PREVIEW_SYNTHETIC_MIME = Pattern.compile( "^test\\d+_(.*\\/(.*))$" );

    /**
     * The editor plumbing synthesizes its own weird mime type for the
     * Tools | Options | Fonts and Colors - likely so it has a mime type to update and it
     * can use the regular lexing plumbing to paint the preview without updating the
     * actual settings for the mime type until the user confirms the changes.
     * Our test of whether the type is parsable needs to elide the synthetic portion.
     *
     * @param src A source
     *
     * @return A munged mime type
     */
    private static String mimeTypeForSource( Source src ) {
        // e.g. test1790594912_text/x-g4
        String mime = src.getMimeType();
        // faster test than the regex, since this method will be called all the time
        return stripMimeType( mime );
    }

    static String stripMimeType( String mimeType ) {
        if ( mimeType.length() > 5 && mimeType.startsWith( "test" ) && Character.isDigit( mimeType.charAt( 5 ) ) && mimeType
                .lastIndexOf( '_' ) >= 0 ) {
            Matcher m = PREVIEW_SYNTHETIC_MIME.matcher( mimeType );
            if ( m.find() ) {
                mimeType = m.group( 1 );
            }
        }
        return mimeType;
    }

    private static void parseImmediately( Source src,
            BiConsumer<Extraction, Exception> consumer ) {
        String mime = mimeTypeForSource( src );
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

    private static final String EXTRACTION_DOCUMENT_PROPERTY = "_ext";

    static boolean extractionCachingEnabled() {
        return !Boolean.getBoolean( "antlr.extraction.cache.disabled" );
    }

    static void storeExtraction( Snapshot snap, Extraction ext ) {
        if ( ext == null || ext.isPlaceholder() || ext.isDisposed() || !extractionCachingEnabled() ) {
            return;
        }
        Document doc = null;
        if ( snap != null ) {
            doc = snap.getSource().getDocument( false );
        } else {
            Optional<Document> docOpt = ext.source().lookup( Document.class );
            if ( docOpt.isPresent() ) {
                doc = docOpt.get();
            }
        }
        storeExtraction( doc, ext );
    }

    @SuppressWarnings( "DoubleCheckedLocking" )
    static void storeExtraction( Document doc, Extraction ext ) {
        if ( doc != null && ext != null && !ext.isPlaceholder() ) {
            AtomicReference<TimedWeakReference<Extraction>> ref = extractionReference( doc );
            TimedWeakReference<Extraction> old = ref.get();
            TimedWeakReference<Extraction> nue = TimedWeakReference.create( ext );
            ref.set( nue );
            if ( old != null ) {
                old.discard();
            }
        }
    }

    @SuppressWarnings( "DoubleCheckedLocking" )
    private static AtomicReference<TimedWeakReference<Extraction>> extractionReference( Document doc ) {
        // Unless something weird is happening, the generated editor
        // kit will prepopulate this - may not be there if the developer
        // created their own kit
        AtomicReference<TimedWeakReference<Extraction>> ref = ( AtomicReference<TimedWeakReference<Extraction>> ) doc
                .getProperty( EXTRACTION_DOCUMENT_PROPERTY );
        if ( ref == null ) {
            synchronized ( NbAntlrUtils.class ) {
                ref = ( AtomicReference<TimedWeakReference<Extraction>> ) doc
                        .getProperty( EXTRACTION_DOCUMENT_PROPERTY );
                if ( ref == null ) {
                    ref = new AtomicReference<>();
                    doc.putProperty( EXTRACTION_DOCUMENT_PROPERTY, ref );
                }
            }
        }
        return ref;
    }

    private static final Map<FileObject, Extraction> foCache
            = Collections.synchronizedMap( CollectionUtils.weakValueMap( MapFactories.EQUALITY_CONCURRENT, 36,
                                                                         TimedWeakReference::create ) );

    public static Extraction extractionFor( FileObject fo ) {
        DataObject dob;
        try {
            dob = DataObject.find( fo );
            EditorCookie ck = dob.getLookup().lookup( EditorCookie.class );
            if ( ck != null ) {
                Document d = ck.getDocument();
                if ( d != null ) {
                    return extractionFor( d );
                }
            }
            Extraction result = foCache.get( fo );
            if ( result == null || result.isSourceProbablyModifiedSinceCreation() ) {
                result = NbAntlrUtils.parseImmediately( fo );
                if ( result != null && !result.isPlaceholder() ) {
                    foCache.put( fo, result );
                }
            }
            return result;
        } catch ( Exception ex ) {
            Exceptions.printStackTrace( ex );
        }
        return Extraction.empty( fo.getMIMEType() );
    }

    /**
     * Fetch the most recent extraction for the document, or trigger a parse to
     * create a new one if none is available. This method will *never* parse if it
     * does not need to, while parseImmediately() will *always* do so.
     *
     * @param document The document
     *
     * @return The extraction
     */
    @SuppressWarnings( "unchecked" )
    public static Extraction extractionFor( Document document ) {
        AtomicReference<TimedWeakReference<Extraction>> ref = null;
        TimedWeakReference<Extraction> weak = null;
        if ( extractionCachingEnabled() ) {
            ref = extractionReference( document );
            weak = ref.get();
            if ( weak != null ) {

                Extraction result = weak.getIf( ext -> {
                    return !ext.isSourceProbablyModifiedSinceCreation();
                } );
                if ( result != null ) {
                    return result;
                }
            }
        }
        TimedWeakReference<Extraction> newRef = null;
        Extraction r;
        // There is the theoretical possibility (and real, if the application is running
        // out of memory) that the Extraction can be garbage collected before we have a
        // chance to return it if we return the value of newRef.get(), so keep a strong
        // reference and use that as the return value
        Obj<Extraction> temporaryStrongRef = Obj.create();
        try {
            WorkCoalescer<TimedWeakReference<Extraction>> coa = coa( document );
            do {
                Extraction prev = temporaryStrongRef.get();
                newRef = coa.coalesceComputation( () -> {
                    try {
                        Extraction ext = parseImmediately( document );
                        if ( ext != null ) {
                            temporaryStrongRef.set( ext );
                            return TimedWeakReference.create( ext );
                        }
                        return null;
                    } catch ( Exception ex ) {
                        Exceptions.printStackTrace( ex );
                        return null;
                    }
                }, ref );
                if ( newRef != null && ref != null ) {
                    ref.set( newRef );
                }
                if ( prev == temporaryStrongRef.get() && prev != null ) {
                    break;
                } else if ( temporaryStrongRef.get() == null ) {
                    break;
                }
            } while ( temporaryStrongRef.get() == null || temporaryStrongRef.get()
                    .isSourceProbablyModifiedSinceCreation() );
        } catch ( InterruptedException ex ) {
            Exceptions.printStackTrace( ex );
        } catch ( WorkCoalescer.ComputationFailedException ex ) {
            Exceptions.printStackTrace( ex );
        }
        r = temporaryStrongRef.get();
        if ( weak != null && r != null ) {
            Extraction oldExt = weak.getIf( oe -> {
                return oe != r;
            } );
            if ( oldExt != null ) {
                // hasten collection
                oldExt = null;
                weak.discard();
            }
        }
        return r != null ? r : Extraction.empty( NbEditorUtilities.getMimeType( document ) );
    }

    static String idForDoc( Document doc ) {
        Object o = doc.getProperty( StreamDescriptionProperty );
        if ( o instanceof DataObject ) {
            return ( ( DataObject ) o ).getName();
        } else if ( o instanceof FileObject ) {
            return ( ( FileObject ) o ).getName();
        } else if ( o != null ) {
            return o.toString();
        } else {
            return doc.toString();
        }
    }

    static WorkCoalescer<TimedWeakReference<Extraction>> coa( Document doc ) {
        WorkCoalescer<TimedWeakReference<Extraction>> result = ( WorkCoalescer<TimedWeakReference<Extraction>> ) doc
                .getProperty( "_coa" );

        if ( result == null ) {
            result = new WorkCoalescer<>( "extraction-cache-" + idForDoc( doc ) );
            doc.putProperty( "_coa", result );
        }
        return result;
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

    public static void invalidateSource( FileObject fo ) {
        INVALIDATOR.accept( fo );
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
