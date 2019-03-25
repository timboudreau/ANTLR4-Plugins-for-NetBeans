package org.nemesis.antlr.spi.language;

import org.nemesis.antlr.spi.language.highlighting.TokenCategorizer;
import org.nemesis.antlr.spi.language.highlighting.TokenCategory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;

/**
 * Register an Antlr-based language, generating all necessary configuration and
 * implementation classes for a basic implementation of language support and
 * syntax highlighting.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target(value = ElementType.TYPE)
public @interface AntlrLanguageRegistration {

    /**
     * The language name - used as a prefix in generated file names.
     *
     * @return The name
     */
    String name();

    /**
     * The mime type this language is registered under.
     *
     * @return The mime type
     */
    String mimeType();

    /**
     * The Antlr lexer class.
     *
     * @return
     */
    Class<? extends Lexer> lexer();

    /**
     * The bundle to use for display names of generated files.
     *
     * @return A bundle, such as com.foo.Bundle
     */
    String localizingBundle() default "";

    /**
     * If you will do your own token categorizing (which determines which tokens
     * belong to which fonts and colors categories / colorings), you can
     * implement a custom one and pass its class here; alternately, you can use
     * the categories() property below and the necessary files will be
     * generated. If you use both, the token categorizer will take priority.
     *
     * @return A categorizer
     */
    Class<? extends TokenCategorizer> tokenCategorizer() default TokenCategorizer.class;

//    boolean permissiveLexer() default true;
    /**
     * Define categories of tokens which will be used to color those tokens in
     * the editor.
     *
     * @return An array of token categories
     */
    TokenCategory[] categories() default {};

    /**
     * Breif sample text in the language in question. If set it will be used in
     * the <b>Fonts and Colors</b> options dialog page for your language.
     *
     * @return Sample text in the language in question
     */
    String sample() default "";

    /**
     * If present, generate a NetBeans parser using the Antlr parser this field
     * specifies, and which will run any Extractors registered against your
     * file's mime type.
     *
     * @return A ParserControl
     */
    ParserControl parser() default @ParserControl(entryPointRule = Integer.MIN_VALUE, type = Parser.class);

    SyntaxInfo syntax() default @SyntaxInfo;

    String lineCommentPrefix() default "";
    
    CodeCompletion genericCodeCompletion() default @CodeCompletion;

    public @interface CodeCompletion {
        int[] ignoreTokens() default {};
    }

    public @interface SyntaxInfo {

        /**
         * A list of token types (static fields on your generated lexer) that
         * indicate comments.
         *
         * @return the comment tokens
         */
        int[] commentTokens() default {};

        /**
         * A list of token types (static fields on your generated lexer) which
         * are whitespace.
         *
         * @return the whitespace tokens
         */
        int[] whitespaceTokens() default {};

        /**
         * A list of token types (static fields on your generated lexer) to skip
         * when matching braces
         *
         * @return the whitespace tokens
         */
        int[] bracketSkipTokens() default {};
    }

    public @interface ParserControl {

        /**
         * Get the entry point rule for the parser (all rules will be identified
         * by static int fields on your generated Antlr lexer). This determines
         * what class and parser method is the entry point, and <i>must</i> be
         * the type any registered ExtractorRegistrations expect. This should be
         * the ID of the Antlr parser rule which defines an entire file or
         * compilation unit in the language; conventionally, this is the first
         * rule in the grammar file, so the default is 0.
         *
         * @return The rule id
         */
        int entryPointRule() default 0;

        /**
         * The specific type of the Antlr parser to instantiate.
         *
         * @return A parser class
         */
        Class<? extends Parser> type();

        /**
         * The parser helper need not be defined, but allows you to hook into
         * the lifecycle of each parse and configure the parser, or perform
         * syntactic error analysis or similar, programmatically.
         *
         * @return A helper class
         */
        Class<? extends NbParserHelper> helper() default NbParserHelper.class;

        /**
         * If true, the generated NetBeans parser class will have a static
         * method that causes all non-garbage-collected parsers to fire a change
         * event, forcing a reparse. This is only useful for languages which
         * have global configuration (such as PHP) which can affect how the
         * language is parsed, what is or isn't an error, etc. If you are
         * dealing with such a language, return true here and listen on whatever
         * file or settings affect parsing, and call the generated global
         * reparse method when those settings change.
         *
         * @return True if the generated NetBeans parsers should support firing
         * changes to anything that might be using them
         */
        boolean changeSupport() default false;

        /**
         * Antlr supports multiple "channels" within a token stream from a
         * lexer, with different tokens assigned to different channels (for
         * example, routing comments or whitespace to a different channel,
         * rather than having every parser rule define every possible location
         * of whitespace or comments). Antlr parsers generally want only the
         * channel with content relevant to them passed. This is typically
         * channel 0, but can be set otherwise here.
         *
         * @return The channel to use when constructing a CommonTokenStream from
         * the Antlr lexer to pass to the Antlr parser.
         */
        int parserStreamChannel() default 0;

        /**
         * If true, generate a navigator panel which wil show the syntax tree of
         * this language. You will need the antlr-navigators project on the
         * classpath for the generated class to compile. Useful when debugging
         * parsers or language support.
         *
         * @return true if the panel will be generated
         */
        boolean generateSyntaxTreeNavigatorPanel() default false;
    }

    /**
     * If set, generate a DataObject class and register a file type in the IDE
     * for this language (generally you want this unless you are dealing with
     * any language that can be saved to disk, but for advanced cases you may
     * want to implement the DataObject type by hand).
     *
     * @return The file type
     */
    FileType file() default @FileType(extension = ".");

    public @interface FileType {

        /**
         * The file extension of files in this language (no dot).
         *
         * @return The extension, e.g. "java" for java files
         */
        String extension();

        /**
         * A / delimited path in the JAR to the icon to use for your file type.
         *
         * @return
         */
        String iconBase() default "";

        /**
         * If this method returns true, multiple editor views will be possible,
         * and your or other modules can register additional "views" which
         * appear as tab-buttons above the editor.
         *
         * @return True if this should have a multi-view editor
         */
        boolean multiview() default false;

        /**
         * This module defines a default set of actions on the generated file
         * type. You can exclude some of those by including them here - the
         * array entries should be combination of category/action-id, e.g.
         * Edit/org.openide.actions.CutAction
         *
         * @return A list of actions to omit from the popup menu for files of
         * this type
         */
        String[] excludedActions() default {};

        /**
         * If true, allow copying of files of this type.
         *
         * @return True if DataObject.isCopyAllowed() should return true for
         * your file type.
         */
        boolean copyAllowed() default true;

        /**
         * If true, allow deletion of files of this type.
         *
         * @return True if DataObject.isDeleteAllowed() should return true for
         * your file type.
         */
        boolean deleteAllowed() default true;

        /**
         * If true, allow moving of files of this type.
         *
         * @return True if DataObject.isMoveAllowed() should return true for
         * your file type.
         */
        boolean moveAllowed() default true;

        /**
         * If true, allow renaming of files of this type.
         *
         * @return True if DataObject.isRenameAllowed() should return true for
         * your file type.
         */
        boolean renameAllowed() default true;

        /**
         * If you want to hook into lifecycle methods of DataObjects
         * for your file type, implement this interface and specify
         * it here.
         *
         * @return A class which implements DataObjectHooks
         */
        Class<? extends DataObjectHooks> hooks() default NoHooks.class;
    }
}
