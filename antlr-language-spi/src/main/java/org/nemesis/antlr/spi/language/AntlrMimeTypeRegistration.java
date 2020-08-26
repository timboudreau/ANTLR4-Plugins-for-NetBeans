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
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.antlr.v4.runtime.Vocabulary;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Registered in the default lookup, this simply allows a test to see if
 * a mime type is one handled by the Antlr plugins. This is used by
 * Antlr refactoring support to decide enablement, and possibly a few other
 * things.
 *
 * @author Tim Boudreau
 */
public abstract class AntlrMimeTypeRegistration {

    private final String type;
    private final Vocabulary vocabulary;
    private final NbParserHelper<?, ?, ?, ?> helper;

    protected AntlrMimeTypeRegistration( String type, Vocabulary vocabulary, NbParserHelper<?, ?, ?, ?> helper ) {
        this.type = notNull( "type", type );
        this.vocabulary = vocabulary;
        this.helper = helper;
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + '(' + mimeType() + ')';
    }

    static NbParserHelper<?,?,?,?> helper(String mimeType) {
        AntlrMimeTypeRegistration reg = registry().registrationFor( mimeType );
        return reg == null ? null : reg.helper;
    }

    final String mimeType() {
        return type;
    }

    /**
     * Determines if the built-in generic name completion supplier should be used for
     * code completion based on the extraction of the document.
     *
     * @return Whether or not the default completion supplier should be used
     */
    protected boolean defaultCompletionSupplierEnabled() {
        return true;
    }

    public static boolean isDefaultCompletionSupplierEnabled(String mimeType) {
        AntlrMimeTypeRegistration reg = registry().registrationFor( mimeType );
        if (reg != null) {
            return reg.defaultCompletionSupplierEnabled();
        }
        return false;
    }

    @Override
    public final boolean equals( Object o ) {
        if ( o == this ) {
            return true;
        } else if ( o == null ) {
            return false;
        } else if ( o instanceof AntlrMimeTypeRegistration ) {
            return ( ( AntlrMimeTypeRegistration ) o ).mimeType().equals( type );
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return 37 * mimeType().hashCode();
    }

    private static Registry registry;

    private static Registry registry() {
        if ( registry == null ) {
            registry = new Registry();
        }
        return registry;
    }

    /**
     * Optimized test for whether a FileObject is handled by the Antlr modules and should
     * have related features available to it.
     *
     * @param fo A file object
     *
     * @return true if it is handled by the Antlr modules
     */
    public static boolean isAntlrLanguage( FileObject fo ) {
        return FileUtil.getMIMEType( fo, registry().typesArray() ) != null;
    }

    /**
     * Determine if a mime type is handled by the Antlr modules and should
     * have related features available to it.
     *
     * @param mimeType A mime type
     *
     * @return True if it is one
     */
    public static boolean isAntlrLanguage( String mimeType ) {
        return registry().types().contains( mimeType );
    }

    /**
     * Get the Antlr 4 Vocabulary for a mime type. If not present,
     * a dummy instance is returned.
     *
     * @param mimeType A mime type
     *
     * @return a vocabulary
     */
    public static Vocabulary vocabulary( String mimeType ) {
        return registry().vocabularyFor( notNull( "mimeType", mimeType ) );
    }

    public static <T> T runExclusiveForProject(
            String mimeType,
            Lookup.Provider project, Object projectIdentifier,
            ThrowingSupplier<T> runExclusive ) throws Exception {
        if ( project == null ) {
            return runExclusive.get();
        }
        return registry().runExclusive( mimeType, project, projectIdentifier, runExclusive );
    }

    static class Registry implements LookupListener {
        private final Lookup.Result<AntlrMimeTypeRegistration> result;
        private final Set<String> allMimeTypes = ConcurrentHashMap.newKeySet( 10 );
        private String[] typesArray;

        @SuppressWarnings( "LeakingThisInConstructor" )
        public Registry() {
            result = Lookup.getDefault().lookupResult( AntlrMimeTypeRegistration.class );
            result.addLookupListener( this );
        }

        AntlrMimeTypeRegistration registrationFor(String mimeType) {
            for (AntlrMimeTypeRegistration reg : result.allInstances()) {
                if (mimeType.equals( reg.type)) {
                    return reg;
                }
            }
            return null;
        }

        <T> T runExclusive( String mimeType, Lookup.Provider project, Object projectIdentifier,
                ThrowingSupplier<T> toRun ) throws Exception {
            for ( AntlrMimeTypeRegistration reg : result.allInstances() ) {
                if ( mimeType.equals( reg.type ) ) {
                    if (reg.helper == null) {
                        return toRun.get();
                    }
                    return reg.helper.runExclusiveForProject( project, projectIdentifier, toRun );
                }
            }
            return toRun.get();
        }

        Vocabulary vocabularyFor( String mimeType ) {
            for ( AntlrMimeTypeRegistration reg : result.allInstances() ) {
                if ( mimeType.equals( reg.type ) ) {
                    return reg.vocabulary;
                }
            }
            return NoVocabulary.INSTANCE;
        }

        String[] typesArray() {
            String[] result = typesArray;
            if ( result == null ) {
                synchronized ( this ) {
                    Set<String> all = types();
                    typesArray = result = all.toArray( new String[ all.size() ] );
                    Arrays.sort( typesArray );
                }
            }
            return result;
        }

        Set<String> types() {
            if ( allMimeTypes.isEmpty() ) {
                for ( AntlrMimeTypeRegistration reg : result.allInstances() ) {
                    allMimeTypes.add( reg.mimeType() );
                }
            }
            return allMimeTypes;
        }

        @Override
        public void resultChanged( LookupEvent le ) {
            allMimeTypes.clear();
            synchronized ( this ) {
                typesArray = null;
            }
        }
    }

    private static final class NoVocabulary implements Vocabulary {
        private static final NoVocabulary INSTANCE = new NoVocabulary();

        @Override
        public int getMaxTokenType() {
            return 0;
        }

        @Override
        public String getLiteralName( int tokenType ) {
            return Integer.toString( tokenType );
        }

        @Override
        public String getSymbolicName( int tokenType ) {
            return Integer.toString( tokenType );
        }

        @Override
        public String getDisplayName( int tokenType ) {
            return Integer.toString( tokenType );
        }
    }
}
