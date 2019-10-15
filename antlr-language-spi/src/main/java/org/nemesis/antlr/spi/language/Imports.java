package org.nemesis.antlr.spi.language;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.nemesis.extraction.Extraction;
import org.nemesis.source.api.GrammarSource;

/**
 * Annotation which can be applied to a NameRegionKey, indicating that an
 * ImportFinder should be generated for finding "imported" files using the name
 * values for regions associated with the key.  This will result in an ImportFinder
 * being generated and registered.
 * <p>
 * The following things are needed for successfully finding
 * imports:
 * <ol>
 * <li>An extractor must be registered which will find some entries in the
 * parse tree and associate them with the key you are annotating so there is
 * something to look for</li>
 * <li>There must be an implementation of <code>RelativeResolverImplementation</code>
 * registered against the same mime type; that is what actually figures out what
 * folders to look in and how to convert a name in source into a file.  You can
 * generate a simple one by setting a value for <code>simpleStrategy</code> which
 * is useful for trivial cases; for more realistic cases, the project is likely to
 * have something classpath-like that should be searched, and you will want to
 * implement ImportFindingStrategy to handle that.
 * </li>
 * </ol>
 * </p>
 * <p>
 * The following things are needed for successfully attributing
 * unknown name references (to make the go-to declaration action and hyperlinking
 * work on names that ought to be name references but could not be resolved within
 * the current source):
 * <ol>
 * <li>An import finder and its dependencies as described above</li>
 * <li>An implementation of RegisterableResolver registered against this
 * mime type (for straightforward cases, you can annotate a key with
 * <code>&#064;ReferenceableFromImports</code> and one will be generated
 * for you)</li>
 * </ol>
 * </p>
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Imports {

    String mimeType();

    SimpleImportFindingStrategy[] simpleStrategy() default {};

    Class<? extends ImportFindingStrategy> strategy()
            default ImportFindingStrategy.class;

    enum SimpleImportFindingStrategy {
        SIBLINGS,
        SIBLINGS_OR_SCAN_TO_PROJECT_ROOT
    }

    interface ImportFindingStrategy {

        GrammarSource<?> resolve(Extraction sourceExtraction, String name);
    }
}
