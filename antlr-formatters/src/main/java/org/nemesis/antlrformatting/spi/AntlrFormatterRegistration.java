package org.nemesis.antlrformatting.spi;

import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Register a formatter for an Antlr based language; this annotation should be
 * applied to a class which implements AntlrFormatterStub.
 *
 * @author Tim Boudreau
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface AntlrFormatterRegistration {

    /**
     * The MIME type this formatter applies to.
     *
     * @return A valid mime type
     */
    String mimeType();

    /**
     * A list of token types (they will be static int fields on your generated
     * Antlr <code>Lexer</code> subclass) which should be treated as whitespace;
     * it is very important to specify this to get a formatter that works
     * correctly. If unspecified (empty) you will get a heuristic token checker
     * that looks to see if the token's literal name is all whitespace
     * characters, or the programmatic name of the token contains the string
     * "whitespace". It will probably not do what you want, but can be useful in
     * early development.
     * <p>
     * <i>If you are also using the <code>&#064;AntlrLanguageRegistration</code>
     * annotation, and have filled in the <code>syntax()</code> section with the
     * list of whitespace tokens, then you can leave this empty and it will be
     * found there.</i>
     * </p>
     *
     * @return An array of token ids.
     */
    int[] whitespaceTokens() default {};
}
