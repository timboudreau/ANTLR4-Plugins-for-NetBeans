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
package com.mastfrog.editor.features;

import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.util.collections.ArrayUtils;
import com.mastfrog.util.preconditions.Checks;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import org.nemesis.charfilter.CharPredicate;

/**
 *
 * @author Tim Boudreau
 */
public final class EditorFeaturesBuilder {

    Set<EnablableEditProcessorFactory<?>> ops = new HashSet<>();
    Set<EditPhase> triggers = EnumSet.noneOf(EditPhase.class);
    private final Class<? extends EditorFeatures> ownerType;
    final String mimeType;

    EditorFeaturesBuilder(String mimeType, Class<? extends EditorFeatures> ownerType) {
        this.mimeType = mimeType;
        this.ownerType = ownerType;
    }

    /**
     * Insert some boilerplate text - text that is always needed after some
     * character under certain circumstances - when a particular character is
     * typed; the boilerplate you pass here uses a microformat for specifying
     * the caret location after insertion - by default it is the <code>^</code>
     * character so <code>(^)</code> on typing <code>(</code> will replace the
     * typed character with () and position the caret <i>before</i> the closing
     * <code>)</code>. If your boilerplate text <i>contains</i> the
     * <code>^</code> character, you can replace it with another character by
     * starting your string with that character followed by a backslash - e.g.
     * <code>$\($)</code> would do the same thing as the above, but using
     * <code>$</code> as the caret indicator character.
     * <p>
     * The builder will allow you to narrow the specific context in which this
     * feature should be available.
     * </p>
     *
     * @param txt The boilerplate text, optinonally indicating the
     * post-insertion caret position
     * @return A builder
     */
    public BoilerplateInsertionBuilder insertBoilerplate(String txt) {
        return new BoilerplateInsertionBuilder(this, txt);
    }

    /**
     * For brace pairs and other cases where there is a <i>specific token</i>
     * for opening and closing delimiters (such as parentheses or braces).
     *
     * @param openingTokenId The token id for the opening token
     * @return A builder
     */
    public DelimiterTokenPairDeletionBuilder deleteMateOf(int openingTokenId) {
        return new DelimiterTokenPairDeletionBuilder(this, openingTokenId);
    }

    private EditorFeaturesBuilder add(EnablableEditProcessorFactory<?> op) {
        ops.add(op);
        triggers.add(op.initiatesFrom());
        return this;
    }

    /**
     * Cause typing a character if it is right before the same character
     * (ignoring whitespace) - useful if you have other features that will have
     * inserted the character in question - for example, many users have a habit
     * of quickly typing <code>()</code>, and if you have an insert-boilerplate
     * feature to automatically create the <code>)</code>, they will frequently
     * wind up entering <code>())</code> - the right thing to do is to ignore
     * the keystroke since you've already inserted it for them, and just
     * position the caret on the far side of the <code>)</code> - which is what
     * this will do.
     * <p>
     * Remember to disable this sort of behavior inside tokens such as string
     * literals and comments where it could be annoying (the builder offers an
     * opportunity to do that).
     * </p>
     *
     * @param typed The character the user may type
     * @return A builder
     */
    public ElideBuilder elide(char typed) {
        return new ElideBuilder(this, typed, false);
    }

    /**
     * Cause typing a character immediately <i>before</i> the same character to
     * be ignored and position the caret after the subsequent one - useful, for
     * example, when you have a template that inserts a semicolon at the end of
     * a line, but the user is likely to type one out of habit.
     *
     * @param typed The character the user may type
     * @return A builder
     */
    public ElideBuilder elidePreceding(char typed) {
        return new ElideBuilder(this, typed, true);
    }

    /**
     * Builder for specifying the circumstances under which a keystroke should
     * be ignored because it has already been inserted for the user.
     */
    public static final class ElideBuilder {

        private final char typed;
        private final EditorFeaturesBuilder owner;
        private String name;
        private String description;
        private String category;
        private final boolean backwards;

        private ElideBuilder(EditorFeaturesBuilder owner, char typed, boolean backwards) {
            this.owner = owner;
            this.typed = typed;
            this.backwards = backwards;
        }

        /**
         * Set the name used in the options dialog panel for enabling and
         * disabling the feature you are building; if unset, it will not appear
         * in any options panel.
         *
         * @param name A localized name
         * @return this
         */
        public ElideBuilder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the description which will be used as a tooltip for the checkbox
         * to enable/disable the feature you are building in the options dialog
         * (the name must be set for this to have any effect)
         *
         * @param description
         * @return this
         */
        public ElideBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the category heading for the checkbox to enable/disable the
         * feature you are building in the options dialog (the name must be set
         * for this to have any effect)
         *
         * @param description
         * @return this
         */
        public ElideBuilder setCategory(String category) {
            this.category = category;
            return this;
        }

        /**
         * Disable this feature if the caret is in or immediately before one of
         * the passed token types - many features are not likely to be useful in
         * strings or comments - use this to disable it there.
         *
         * @param first The first token id
         * @param more Optional additional token ids
         * @return The owning builder - this feature is completed here
         */
        public EditorFeaturesBuilder whenCurrentTokenNot(int first, int... more) {
            return whenCurrentTokenNot(IntPredicates.anyOf(first, more));
        }

        /**
         * Disable this feature if the caret is in or immediately before one of
         * the passed token types - many features are not likely to be useful in
         * strings or comments - use this to disable it there.
         *
         * @param currentTokenNot A predicate (hint: use a static method
         * reference)
         * @return The owning builder - this feature is completed here
         */
        public EditorFeaturesBuilder whenCurrentTokenNot(IntPredicate currentTokenNot) {
            return owner.add(new CharacterElisionEditProcessorFactory(owner.mimeType, backwards, typed, currentTokenNot, owner.ownerType, name, description, category));
        }
    }

    /**
     * Builder for creating an editor feature which deletes the mate to a pair
     * of tokens (such as braces or parentheses or quotes) if nothing (or only
     * tokens you specify to ignore, for example as whitespace) is between the
     * initial token you passed in order to get a
     * DelimiterTokenPairDeletionBuilder and the one you will pass to close it.
     */
    public static final class DelimiterTokenPairDeletionBuilder {

        private final EditorFeaturesBuilder owner;
        private final int openingTokenId;
        private IntPredicate ignore;
        private String name;
        private String description;
        private String category;

        private DelimiterTokenPairDeletionBuilder(EditorFeaturesBuilder owner, int openingTokenId) {
            this.owner = owner;
            this.openingTokenId = openingTokenId;
        }

        /**
         * Set the name used in the options dialog panel for enabling and
         * disabling the feature you are building; if unset, it will not appear
         * in any options panel.
         *
         * @param name A localized name
         * @return this
         */
        public DelimiterTokenPairDeletionBuilder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the category heading for the checkbox to enable/disable the
         * feature you are building in the options dialog (the name must be set
         * for this to have any effect)
         *
         * @param description
         * @return this
         */
        public DelimiterTokenPairDeletionBuilder setCategory(String category) {
            this.category = category;
            return this;
        }

        /**
         * Set the description which will be used as a tooltip for the checkbox
         * to enable/disable the feature you are building in the options dialog
         * (the name must be set for this to have any effect)
         *
         * @param description
         * @return this
         */
        public DelimiterTokenPairDeletionBuilder setDescription(String desc) {
            this.description = desc;
            return this;
        }

        /**
         * Provide a predicate matching token ids to ignore when looking for the
         * second delimiter - typically you pass a predicate that matches
         * comment and whitespace tokens here.
         *
         * @param ignore A predicate which will match tokens that should be
         * ignored and not affect the search for the matching token
         * @return this
         */
        public DelimiterTokenPairDeletionBuilder ignoring(IntPredicate ignore) {
            if (this.ignore != null) {
                this.ignore = this.ignore.or(ignore);
            } else {
                this.ignore = ignore;
            }
            return this;
        }

        /**
         * Provide a list of token ids to ignore when looking for the second
         * delimiter - typically you pass a predicate that matches comment and
         * whitespace tokens here.
         *
         * @param ignore A predicate which will match tokens that should be
         * ignored and not affect the search for the matching token
         * @return this
         */
        public DelimiterTokenPairDeletionBuilder ignoring(int first, int... more) {
            return ignoring(IntPredicates.anyOf(first, more));
        }

        /**
         * Provide the closing token id - for quotes and similar, it is okay if
         * this is the same as the opening token.
         *
         * @param closingTokenId The closing token id
         * @return The owning builder - the feature is complete and added to it
         */
        public EditorFeaturesBuilder closingPairWith(int closingTokenId) {
            DelimiterTokenPairDeletion d = new DelimiterTokenPairDeletion(owner.mimeType, openingTokenId, closingTokenId, name, description, owner.ownerType, ignore, category);
            return owner.add(d);
        }
    }

    /**
     * A builder for inserting "boilerplate" - text which is always required
     * after a certain character is typed under certain circumstances - for
     * example, in Antlr's grammar language, the opening name of a rule is
     * always going to be followed by <code>:</code> and the rule is always
     * going to be terminated with a <code>;</code> - so what makes sense is to
     * insert <code>: ;</code> and place the caret before the <code>;</code>.
     * <p>
     * To control the circumstances in which the feature is active, detailed
     * token pattern matching is available.
     * </p>
     */
    public static class BoilerplateInsertionBuilder {

        private final EditorFeaturesBuilder owner;
        private final String toInsert;
        Boolean requireLineEnd;
        IntPredicate precedingTokenTest;
        TokenPattern precedingPattern;
        TokenPattern followingPattern;
        String name;
        String description;
        String category;
        final int caretBackup;

        private BoilerplateInsertionBuilder(EditorFeaturesBuilder owner, String toInsert) {
            this.owner = owner;
            CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(Checks.notNull("toInsert", toInsert));
            this.toInsert = cpait.insertText;
            this.caretBackup = cpait.caretBackup;
        }

        /**
         * Set the name used in the options dialog panel for enabling and
         * disabling the feature you are building; if unset, it will not appear
         * in any options panel.
         *
         * @param name A localized name
         * @return this
         */
        public BoilerplateInsertionBuilder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the description which will be used as a tooltip for the checkbox
         * to enable/disable the feature you are building in the options dialog
         * (the name must be set for this to have any effect)
         *
         * @param description
         * @return this
         */
        public BoilerplateInsertionBuilder setDescription(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set the category heading for the checkbox to enable/disable the
         * feature you are building in the options dialog (the name must be set
         * for this to have any effect)
         *
         * @param description
         * @return this
         */
        public BoilerplateInsertionBuilder setCategory(String category) {
            this.category = category;
            return this;
        }

        /**
         * If you call this method, the feature you are created will only be
         * active when there are no more characters other than whitespace on the
         * line where the user is typing.
         *
         * @return this
         */
        public BoilerplateInsertionBuilder onlyWhenAtLineEnd() {
            requireLineEnd = true;
            return this;
        }

        /**
         * If you call this method, the feature you are created will only be
         * active when the user is <b>not</b> typing at the end of the line
         * (ignoring whitespace).
         *
         * @return this
         */
        public BoilerplateInsertionBuilder onlyWhenNotAtLineEnd() {
            requireLineEnd = false;
            return this;
        }

        /**
         * Make this feature active <i>only</code> when when the current caret
         * position is preceded by a sequence of the token ids passed, before a
         * stop token (which you are about to specify) is encountered. For
         * example, in Antlr grammar language, we want to insert
         * <code>: ;</code> when the user types a new rule name, but <i>only if
         * it really is the start of a rule</i> - so we look for the pattern of
         * a semicolon followed by an identifier.
         * <p>
         * Note that even though this method does a reverse search, the order of
         * the pattern is the order the tokens occur in the file, not the order
         * they would be encountered traversing backwards from the caret
         * position.
         * </p>
         *
         * @param pattern A pattern of token ids - if some positions should
         * match more than one potential token ids, use the method that takes an
         * array of <code>IntPredicate</code>s.
         * @return A pattern builder
         */
        public TokenPatternBuilder<BoilerplateInsertionBuilder> whenPrecededByPattern(int... pattern) {
            return new TokenPatternBuilder<>((tpb) -> {
                if (precedingPattern != null) {
                    throw new IllegalStateException("Preceding pattern already set");
                }
                precedingPattern = tpb.toTokenPattern();
                return this;
            }, false, pattern);
        }

        /**
         * Make this feature active <i>only</code> when when the current caret
         * position is followed by a sequence of the token ids passed, before a
         * stop token (which you are about to specify) is encountered.
         *
         * @param pattern A pattern of token ids - if some positions should
         * match more than one potential token ids, use the method that takes an
         * array of <code>IntPredicate</code>s.
         * @return A pattern builder
         */
        public TokenPatternBuilder<BoilerplateInsertionBuilder> whenFollowedByPattern(int... pattern) {
            return new TokenPatternBuilder<>((tpb) -> {
                if (followingPattern != null) {
                    throw new IllegalStateException("Preceding pattern already set");
                }
                followingPattern = tpb.toTokenPattern();
                return this;
            }, true, pattern);
        }

        /**
         * Make this feature active <i>only</code> when when the current caret
         * position is preceded by a sequence of the token ids passed, before a
         * stop token (which you are about to specify) is encountered. For
         * example, in Antlr grammar language, we want to insert
         * <code>: ;</code> when the user types a new rule name, but <i>only if
         * it really is the start of a rule</i> - so we look for the pattern of
         * a semicolon followed by an identifier.
         * <p>
         * Note that even though this method does a reverse search, the order of
         * the pattern is the order the tokens occur in the file, not the order
         * they would be encountered traversing backwards from the caret
         * position.
         * </p>
         *
         * @param pattern An array of token ids (ordinals), each of which must
         * pass for one token in order for the pattern to be matched.
         * @return A pattern builder
         */
        public TokenPatternBuilder<BoilerplateInsertionBuilder> whenPrecededByPattern(IntPredicate... pattern) {
            return new TokenPatternBuilder<>((tpb) -> {
                if (precedingPattern != null) {
                    throw new IllegalStateException("Preceding pattern already set");
                }
                precedingPattern = tpb.toTokenPattern();
                return this;
            }, false, pattern);
        }

        /**
         * Make this feature active <i>only</code> when when the current caret
         * position is followed by a sequence of the token ids passed, before a
         * stop token (which you are about to specify) is encountered.
         *
         * @param pattern An array of IntPredicates, each of which must pass for
         * one token in order for the pattern to be matched.
         * @return A pattern builder
         */
        public TokenPatternBuilder<BoilerplateInsertionBuilder> whenFollowedByPattern(IntPredicate... pattern) {
            return new TokenPatternBuilder<>((tpb) -> {
                if (followingPattern != null) {
                    throw new IllegalStateException("Preceding pattern already set");
                }
                followingPattern = tpb.toTokenPattern();
                return this;
            }, true, pattern);
        }

        /**
         * Activate this rule only when the token immediately before the caret
         * matches one of the passed token ids (ordinals on NetBeans TokenId
         * instances)
         *
         * @param first The first token id
         * @param more Optional more tokens
         * @return A builder
         */
        public FinishableBoilerplateInsertionBuilder whenPrecedingToken(int first, int... more) {
            if (precedingTokenTest != null) {
                precedingTokenTest = precedingTokenTest.or(IntPredicates.anyOf(first, more));
            } else {
                precedingTokenTest = IntPredicates.anyOf(first, more);
            }
            return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest, precedingPattern, followingPattern, caretBackup, name, description, category);
        }

        /**
         * Activate this rule only when the token immediately before the caret
         * matches one of the passed token ids (ordinals on NetBeans TokenId
         * instances)
         *
         * @param pred A predicate which must match the token immediately
         * preceding the caret position
         * @return A builder
         */
        public FinishableBoilerplateInsertionBuilder whenPrecedingToken(IntPredicate pred) {
            if (precedingTokenTest != null) {
                precedingTokenTest = precedingTokenTest.or(pred);
            } else {
                precedingTokenTest = pred;
            }
            return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest, precedingPattern, followingPattern, caretBackup, name, description, category);
        }

        /**
         * Builder which is able to finish creating a boilerplate insertion
         * feature.
         */
        public static final class FinishableBoilerplateInsertionBuilder {

            private final EditorFeaturesBuilder owner;
            private final String toInsert;
            Boolean requireLineEnd;
            IntPredicate currentTokenNot;
            IntPredicate precedingTokenTest;
            TokenPattern precedingPattern;
            TokenPattern followingPattern;
            private TokenPattern preceding;
            private TokenPattern following;
            private final int caretBackup;
            private String name;
            private String description;
            private String category;

            FinishableBoilerplateInsertionBuilder(EditorFeaturesBuilder owner, String toInsert, Boolean requireLineEnd, IntPredicate precedingTokenTest, TokenPattern preceding, TokenPattern following, int caretBackup, String name, String description, String category) {
                this.owner = owner;
                this.category = category;
                this.toInsert = toInsert;
                this.requireLineEnd = requireLineEnd;
                this.precedingTokenTest = precedingTokenTest;
                this.preceding = preceding;
                this.following = following;
                this.caretBackup = caretBackup;
                this.name = name;
                this.description = description;
            }

            /**
             * Set the category heading for the checkbox to enable/disable the
             * feature you are building in the options dialog (the name must be
             * set for this to have any effect)
             *
             * @param description
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder setCategory(String category) {
                this.category = category;
                return this;
            }

            /**
             * Set the name used in the options dialog panel for enabling and
             * disabling the feature you are building; if unset, it will not
             * appear in any options panel.
             *
             * @param name A localized name
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder setName(String name) {
                this.name = name;
                return this;
            }

            /**
             * Set the description which will be used as a tooltip for the
             * checkbox to enable/disable the feature you are building in the
             * options dialog (the name must be set for this to have any effect)
             *
             * @param description
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            /**
             * Make this feature active <i>only</code> when when the current
             * caret position is preceded by a sequence of the token ids passed,
             * before a stop token (which you are about to specify) is
             * encountered. For example, in Antlr grammar language, we want to
             * insert <code>: ;</code> when the user types a new rule name, but
             * <i>only if it really is the start of a rule</i> - so we look for
             * the pattern of a semicolon followed by an identifier.
             * <p>
             * Note that even though this method does a reverse search, the
             * order of the pattern is the order the tokens occur in the file,
             * not the order they would be encountered traversing backwards from
             * the caret position.
             * </p>
             *
             * @param pattern An array of token ids (ordinals), each of which
             * must pass for one token in order for the pattern to be matched.
             * @return A pattern builder
             */
            public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenPrecededByPattern(int... pattern) {
                return new TokenPatternBuilder<>((tpb) -> {
                    if (preceding != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    preceding = tpb.toTokenPattern();
                    return this;
                }, false, pattern);
            }

            /**
             * Make this feature active <i>only</code> when when the current
             * caret position is followed by a sequence of the token ids passed,
             * before a stop token (which you are about to specify) is
             * encountered.
             *
             * @param pattern A pattern of token ids - if some positions should
             * match more than one potential token ids, use the method that
             * takes an array of <code>IntPredicate</code>s.
             * @return A pattern builder
             */
            public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenFollowedByPattern(int... pattern) {
                return new TokenPatternBuilder<>((tpb) -> {
                    if (following != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    following = tpb.toTokenPattern();
                    return this;
                }, true, pattern);
            }

            /**
             * Make this feature active <i>only</code> when when the current
             * caret position is preceded by a sequence of the token ids passed,
             * before a stop token (which you are about to specify) is
             * encountered. For example, in Antlr grammar language, we want to
             * insert <code>: ;</code> when the user types a new rule name, but
             * <i>only if it really is the start of a rule</i> - so we look for
             * the pattern of a semicolon followed by an identifier.
             * <p>
             * Note that even though this method does a reverse search, the
             * order of the pattern is the order the tokens occur in the file,
             * not the order they would be encountered traversing backwards from
             * the caret position.
             * </p>
             *
             * @param pattern An array of token ids (ordinals), each of which
             * must pass for one token in order for the pattern to be matched.
             * @return A pattern builder
             */
            public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenPrecededByPattern(IntPredicate... pattern) {
                return new TokenPatternBuilder<>((tpb) -> {
                    if (preceding != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    preceding = tpb.toTokenPattern();
                    return this;
                }, false, pattern);
            }

            /**
             * Make this feature active <i>only</code> when when the current
             * caret position is followed by a sequence of the token ids passed,
             * before a stop token (which you are about to specify) is
             * encountered.
             *
             * @param pattern An array of IntPredicates, each of which must pass
             * for one token in order for the pattern to be matched.
             * @return A pattern builder
             */
            public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenFollowedByPattern(IntPredicate... pattern) {
                return new TokenPatternBuilder<>((tpb) -> {
                    if (following != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    following = tpb.toTokenPattern();
                    return this;
                }, true, pattern);
            }

            /**
             * If you call this method, the feature you are created will only be
             * active when there are no more characters other than whitespace on
             * the line where the user is typing.
             *
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder onlyWhenAtLineEnd() {
                requireLineEnd = true;
                return this;
            }

            /**
             * If you call this method, the feature you are created will only be
             * active when the user is <b>not</b> typing at the end of the line
             * (ignoring whitespace).
             *
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder onlyWhenNotAtLineEnd() {
                requireLineEnd = false;
                return this;
            }

            /**
             * Specify the character(s) which, when typed, will trigger this
             * feature, as a predicate which can match a typed character.
             *
             * @param predicate A predicate which can match a character the user
             * types
             * @return The owning builder - this feature is completed and added
             * at this point
             */
            public EditorFeaturesBuilder whenKeyTyped(CharPredicate predicate) {
                InsertBoilerplateEditProcessorFactory op = new InsertBoilerplateEditProcessorFactory(owner.mimeType, predicate, toInsert, precedingTokenTest, requireLineEnd, preceding, following, caretBackup, name, description, owner.ownerType, category, currentTokenNot);
                return owner.add(op);
            }

            /**
             * Specify the character(s) which, when typed, will trigger this
             * feature, as a predicate which can match a typed character.
             *
             * @param predicate A predicate which can match a character the user
             * types
             * @return The owning builder - this feature is completed and added
             * at this point
             */
            public EditorFeaturesBuilder whenKeyTyped(char first, char... more) {
                CharPredicate pred = CharPredicate.anyOf(ArrayUtils.prepend(first, more));
                return whenKeyTyped(pred);
            }

            /**
             * Disable this feature when the caret is in a particular type of
             * token
             *
             * @param anyOf A predicate which, if it returns <i>true</i>, will
             * cause this feature to be disabled
             * @return this
             */
            public FinishableBoilerplateInsertionBuilder whenCurrentTokenNot(IntPredicate anyOf) {
                if (currentTokenNot != null) {
                    currentTokenNot = currentTokenNot.and(anyOf);
                } else {
                    currentTokenNot = anyOf;
                }
                return this;
            }
        }
    }

}
