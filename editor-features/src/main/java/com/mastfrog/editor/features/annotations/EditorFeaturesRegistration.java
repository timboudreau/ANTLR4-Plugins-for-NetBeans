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
package com.mastfrog.editor.features.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for decribing automatically generated editor features which
 * involve intercepting keystrokes or deletions in the editor and altering the
 * input in-flight.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface EditorFeaturesRegistration {

    /**
     * The mime type.
     *
     * @return
     */
    String mimeType();

    /**
     * The display name of for the language; used for the options pane tab. Can
     * be omitted if you do not set the name on any of the nested annotations
     * (i.e. no options dialog tab will be generated).
     *
     * @return The display name; will be stored to a resource bundle and
     * generated code will reference it from there so it can be localized
     */
    String languageDisplayName() default "";

    /**
     * Register the circumstances in which you want to process a certain
     * character being typed, replacing the typed text with something else
     * (usually used to do things like insert a closing parenthesis when the
     * user types an opening one). The triggering condition can be narrowed with
     * preceding or subsequent token patterns and various other conditions, to
     * avoid it being triggered when it shouldn't be.
     *
     * @return An array of boilerplate entries
     */
    Boilerplate[] insertBoilerplate() default {};

    /**
     * Register the circumstances in which the typing of a particular character
     * (usually one which already immediately precedes or follows the caret)
     * should be <i>ignored</i> and the caret potentially repositioned after the
     * exiting character as if the insertion had taken place.
     *
     * @return An array of key character elisions
     */
    Elision[] elideTypedChars() default {};

    /**
     * Delete a corresponding, adjacent or near-adjacent token when a particular
     * one is deleted - used for, for example, deleting an auto-inserted ) when
     * the ( adjacent to it is deleted.
     *
     * @return An array of delimiter pairs that should be deleted together,
     * specifying the conditions when this feature should <i>not</i> be
     * triggered, and what intervening tokens (e.g. whitespace) can be ignored.
     */
    DelimiterPair[] deleteMatchingDelimiter() default {};

    /**
     * The order in which this set of features is processed; if unset, the
     * absolute value of the hash code of the qualified name of the class this
     * annotation occurs on is used (typically you do not have multiple features
     * which could override each other, but NetBeans layer infrastructure will
     * complain if multiple things have the same ordering attribute).
     *
     * @return An ordering position
     */
    int order() default 0;

    /**
     * The type of the Antlr lexer, so generated code and toString
     * representations of instances are readable and loggable, using references
     * to fields on the lexer instead of integer token ids.
     * <p>
     * To enable this library to work for languages which have no connection to
     * ANTLR, the lexer type can be omitted, but in that case all token ids
     * (including in string definitions of token patterns) must be specified as
     * integers, which may be a bit less than readable.
     * </p><p>
     * In the future it may be possible to specify an enum of NetBeans token ids
     * as an alternate way of specifying enough information to generate readable
     * code here.
     * </p>
     *
     * @return
     */
    Class<?> lexer() default Object.class;

    /**
     * Defines some boilerplate (text which is the same every time) which is to
     * be inserted instead of what the user typed, when a particular key is
     * typed; the definition of inserted text can include the caret location
     * post-insert, and can specify detailed, granular rules for when this
     * feature should be active.
     */
    @interface Boilerplate {

        /**
         * The display name for use in the options dialog tab (if any) - if
         * unset, this item will not have a checkbox to enable and disable it
         * there.
         *
         * @return The display name; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String name() default "";

        /**
         * The description of this item, which will be the tooltip of the
         * options dialog checkbox (if any); has no effect if the name is not
         * specified.
         *
         * @return The descriptionname; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String description() default "";

        /**
         * The category of this item, which will be used to group and provide a
         * heading for this item in the dialog checkbox (if any); has no effect
         * if the name is not specified.
         *
         * @return The category; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String category() default "";

        /**
         * Whether this item should be active only when the caret is, is not at
         * the end of the line; default is "any" (don't care).
         *
         * @return The line position
         */
        LinePosition linePosition() default LinePosition.ANY;

        /**
         * A pattern of tokens which must be findable preceding the token the
         * caret is in, for this item to be active. It uses a microformat which
         * the annotation processor parses, to specify a number of things:
         * <ol>
         * <li><code>&lt;</code> or <code>&gt;</code> - whether to scan forward
         * or backwards</li>
         * <li>An optional <code>|</code> if hitting the beginning or end of the
         * document while scanning for the pattern should be considered a
         * match</li>
         * <li>A list of <i>token names</i> which may contain <i>nesting</i>
         * in braces - this is a list of tokens <i>in the order they would
         * appear in the document</i> that are the pattern. The delimiter is
         * simply whitespace (no commas). Nesting (i.e. the token may be one of
         * several possibilities) is placed within curly brackets - so
         * <code>&lt; SEMI {THIS_ID THAT_ID}</code> says to scan backward from
         * the caret, looking for either of THIS_ID or THAT_ID preceded by SEMI
         * (all of these names are token ids from your lexer grammar).
         * </li>
         * <li>The character <code>!</code> followed by a (non-nestable) list of
         * <i>stop tokens</i> - ones which, if encountered before the pattern is
         * satisfied, indicate a <i>non-match</i>, terminating the search for
         * the pattern - indicating the item should not be used to process the
         * current keystroke.</li>
         * <li>The character <code>?</code> followed by a list of tokens which
         * should be ignored, and which pattern matching treats as if they did
         * not exist (usually used for whitespace, comments, etc.).
         * </li>
         * </ol>
         * <p>
         * Example from the ANTLR module:
         * </p>
         * <pre>
         * &lt;| SEMI {PARSER_RULE_ID TOKEN_ID} ! COLON ? PARDEC_WS ID_WS IMPORT_WS CHN_WS FRAGDEC_WS HDR_IMPRT_WS HDR_PCKG_WS HEADER_P_WS HEADER_WS LEXCOM_WS OPT_WS PARDEC_OPT_WS TOK_WS TOKDEC_WS TYPE_WS WS LINE_COMMENT BLOCK_COMMENT CHN_BLOCK_COMMENT FRAGDEC_LINE_COMMENT CHN_LINE_COMMENT DOC_COMMENT HDR_IMPRT_LINE_COMMENT HDR_PCKG_LINE_COMMENT HEADER_BLOCK_COMMENT HEADER_LINE_COMMENT HEADER_P_BLOCK_COMMENT HEADER_P_LINE_COMMENT ID_BLOCK_COMMENT ID_LINE_COMMENT IMPORT_BLOCK_COMMENT IMPORT_LINE_COMMENT LEXCOM_BLOCK_COMMENT LEXCOM_LINE_COMMENT OPT_BLOCK_COMMENT OPT_LINE_COMMENT PARDEC_LINE_COMMENT PARDEC_BLOCK_COMMENT PARDEC_OPT_LINE_COMMENT PARDEC_OPT_BLOCK_COMMENT TOK_BLOCK_COMMENT TOK_LINE_COMMENT TYPE_LINE_COMMENT
         * </pre>
         * <p>
         * This is used to insert the ": ^;" when you start a new rule; the
         * pattern looks to make sure the rule is only triggered when a rule
         * name, preceded (ignoring whitespace and comments) by a semicolon is
         * present (indicating it is the first token in the rule so it really is
         * the name of a new rule being started.
         * </p>
         * <p>
         * Whitespace is ignored in the pattern definition, other than being
         * needed to determine token name boundaries.
         * </p>
         *
         * @return A pattern
         */
        String whenPrecededByPattern() default "";

        /**
         * Specifies a second pattern - same rules as whenPrecededByPattern().
         *
         * @see whenPrecededByPattern
         * @return A pattern
         */
        String whenFollowedByPattern() default "";

        /**
         * Deactivate this feature when the caret is in any of the supplied
         * token ids.
         *
         * @return An array of token ids
         */
        int[] whenCurrentTokenNot() default {};

        /**
         * Activate this rule when the preceding token is one of these.
         *
         * @return An array of token ids
         */
        int[] whenPrecedingToken();

        /**
         * The character the user must have typed to activate this rule.
         *
         * @return
         */
        char onChar();

        /**
         * The text to insert. This also uses a microformat: Use the
         * <code>^</code> character to indicate the placement of the caret after
         * insertion, if it is not to be placed after the inserted text (for
         * example <code>(^)</code> will insert <code>()</code> and place the
         * caret between the parentheses).
         * <p>
         * If the <code>^</code> character is part of the text you want to
         * insert, you can specify a different character by making it the first
         * character of the text followed by a backslash - so <code>$\($)</code>
         * accomplishes the same thing as <code>(^)</code>.
         * </p>
         *
         * @return The string to insert, optionally indicating the post-insert
         * caret location
         */
        String inserting();
    }

    @interface DelimiterPair {

        /**
         * The display name for use in the options dialog tab (if any) - if
         * unset, this item will not have a checkbox to enable and disable it
         * there.
         *
         * @return The display name; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String name() default "";

        /**
         * The description of this item, which will be the tooltip of the
         * options dialog checkbox (if any); has no effect if the name is not
         * specified.
         *
         * @return The descriptionname; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String description() default "";

        /**
         * The category of this item, which will be used to group and provide a
         * heading for this item in the dialog checkbox (if any); has no effect
         * if the name is not specified.
         *
         * @return The category; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String category() default "";

        /**
         * The opening delimiter, which, if deleted, can trigger this feature to
         * delete its matching, opposite, delimiter.
         *
         * @return A token id
         */
        int openingToken();

        /**
         * The closing delimiter which should be deleted if it is present
         * subsequent to the opening token in the token sequence, with no or
         * only ignored tokens in between.
         *
         * @return The closing token id
         */
        int closingToken();

        /**
         * Ids of tokens to ignore.
         *
         * @return
         */
        int[] ignoring() default {};

    }

    @interface Elision {

        /**
         * The display name for use in the options dialog tab (if any) - if
         * unset, this item will not have a checkbox to enable and disable it
         * there.
         *
         * @return The display name; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String name() default "";

        /**
         * The description of this item, which will be the tooltip of the
         * options dialog checkbox (if any); has no effect if the name is not
         * specified.
         *
         * @return The description; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String description() default "";

        /**
         * The category of this item, which will be used to group and provide a
         * heading for this item in the dialog checkbox (if any); has no effect
         * if the name is not specified.
         *
         * @return The category; will be stored to a resource bundle and
         * generated code will reference it from there so it can be localized
         */
        String category() default "";

        /**
         * Array of token ids which, if the caret is in or immediately preceding
         * one of them, will cause this feature to be disabled.
         *
         * @return
         */
        int[] whenNotIn() default {};

        /**
         * Look backwards, not forward, for the matching character.
         *
         * @return True if this feature searches backwards for the character to
         * elide
         */
        boolean backward();

        /**
         * The character the user must type to potentially invoke this feature.
         *
         * @return A character
         */
        char onKeyTyped();
    }

    enum LinePosition {
        /**
         * Start of the line - there can be non-ignored tokens after but not
         * before the caret position, or the feature is inactive.
         */
        AT_START,
        /**
         * End of the line - there can be non-ignored tokens before, but not
         * after the caret position, or the feature is inactive.
         */
        AT_END,
        /**
         * Don't care if at the start or end of the line - active anywhere.
         */
        ANY
    }
}
