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
package org.nemesis.antlr.file.editor.ext;

import com.mastfrog.predicates.integer.IntPredicates;
import com.mastfrog.util.collections.ArrayUtils;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import org.nemesis.charfilter.CharPredicate;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.lexer.Token;
import org.netbeans.editor.BaseDocument;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class EditorFeatures {

    private final Set<EnablableEditProcessorFactory<?>> ops = new HashSet<>();
    private final Interceptor interceptor = new Interceptor(this);
    private final Set<EditPhase> types = EnumSet.noneOf(EditPhase.class);
    private final String mimeType;

    protected EditorFeatures(String mimeType, Consumer<EditorFeatures.EditorFeaturesBuilder> configurer) {
        this.mimeType = mimeType;
        EditorFeaturesBuilder b = new EditorFeaturesBuilder(notNull("mimeType", mimeType), getClass());
        configurer.accept(b);
        ops.addAll(b.ops);
        types.addAll(b.triggers);
    }

    public List<EditorFeatureEnablementModel> enablableItems() {
        List<EditorFeatureEnablementModel> descs = new ArrayList<>(ops.size());
        ops.stream().filter((d) -> (d.name() != null)).forEach((d) -> {
            descs.add(new EditorFeatureEnablementModelImpl(mimeType, d));
        });
        return descs;
    }

    /**
     * The DeletedTextInterceptor.Factory for this instance; to register, create
     * an instance as a static variable and return a call to this method on it
     * from a static method annotated with &#064;MimeRegistration.
     *
     * @return The factory
     */
    protected final DeletedTextInterceptor.Factory deletionFactory() {
        return interceptor;
    }

    /**
     * The TypedTextInterceptor.Factory for this instance; to register, create
     * an instance as a static variable and return a call to this method on it
     * from a static method annotated with &#064;MimeRegistration.
     *
     * @return The factory
     */
    protected final TypedTextInterceptor.Factory typingFactory() {
        return interceptor;
    }

    /**
     * The TypedBreakInterceptor.Factory for this instance; to register, create
     * an instance as a static variable and return a call to this method on it
     * from a static method annotated with &#064;MimeRegistration.
     *
     * @return The factory
     */
    protected final TypedBreakInterceptor.Factory breakFactory() {
        return interceptor;
    }

    /**
     * Options panel controller for this instance; to register, create your
     * instance as a static variable and return a call to this method on it from
     * a static method annotated with
     * &#064;OptionsPanelController.SubRegistration
     *
     * @return The controller
     */
    protected final OptionsPanelController optionsPanelController() {
        return new EnablementOptionsPanelController(mimeType);
    }

    public static class EditorFeaturesBuilder {

        private Set<EnablableEditProcessorFactory<?>> ops = new HashSet<>();
        private Set<EditPhase> triggers = EnumSet.noneOf(EditPhase.class);
        private final Class<? extends EditorFeatures> ownerType;
        final String mimeType;

        private EditorFeaturesBuilder(String mimeType, Class<? extends EditorFeatures> ownerType) {
            this.mimeType = mimeType;
            this.ownerType = ownerType;
        }

        public BoilerplateInsertionBuilder insertBoilerplate(String txt) {
            return new BoilerplateInsertionBuilder(this, txt);
        }

        public DelimiterTokenPairDeletionBuilder deleteMateOf(int openingTokenId) {
            return new DelimiterTokenPairDeletionBuilder(this, openingTokenId);
        }

        private EditorFeaturesBuilder add(EnablableEditProcessorFactory<?> op) {
            ops.add(op);
            triggers.add(op.initiatesFrom());
            return this;
        }

        public ElideBuilder elide(char typed) {
            return new ElideBuilder(this, typed, false);
        }

        public ElideBuilder elidePreceding(char typed) {
            return new ElideBuilder(this, typed, true);
        }

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

            public ElideBuilder setName(String name) {
                this.name = name;
                return this;
            }

            public ElideBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public ElideBuilder setCategory(String category) {
                this.category = category;
                return this;
            }

            public EditorFeaturesBuilder whenCurrentTokenNot(int first, int... more) {
                return whenCurrentTokenNot(IntPredicates.anyOf(first, more));
            }

            public EditorFeaturesBuilder whenCurrentTokenNot(IntPredicate currentTokenNot) {
                return owner.add(new CharacterElisionEditProcessorFactory(owner.mimeType,
                        backwards, typed, currentTokenNot, owner.ownerType, name,
                        description, category));
            }
        }

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

            public DelimiterTokenPairDeletionBuilder setName(String name) {
                this.name = name;
                return this;
            }

            public DelimiterTokenPairDeletionBuilder setCategory(String category) {
                this.category = category;
                return this;
            }

            public DelimiterTokenPairDeletionBuilder setDescription(String desc) {
                this.description = desc;
                return this;
            }

            public DelimiterTokenPairDeletionBuilder ignoring(IntPredicate ignore) {
                if (this.ignore != null) {
                    this.ignore = this.ignore.or(ignore);
                } else {
                    this.ignore = ignore;
                }
                return this;
            }

            public DelimiterTokenPairDeletionBuilder ignoring(int first, int... more) {
                return ignoring(IntPredicates.anyOf(first, more));
            }

            public EditorFeaturesBuilder closingPairWith(int closingTokenId) {
                DelimiterTokenPairDeletion d = new DelimiterTokenPairDeletion(owner.mimeType,
                        openingTokenId, closingTokenId, name, description, owner.ownerType, ignore, category);
                return owner.add(d);
            }
        }

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
                CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(notNull("toInsert", toInsert));
                this.toInsert = cpait.insertText;
                this.caretBackup = cpait.caretBackup;
            }

            public BoilerplateInsertionBuilder setName(String name) {
                this.name = name;
                return this;
            }

            public BoilerplateInsertionBuilder setDescription(String description) {
                this.description = description;
                return this;
            }

            public BoilerplateInsertionBuilder setCategory(String category) {
                this.category = category;
                return this;
            }

            public BoilerplateInsertionBuilder onlyWhenAtLineEnd() {
                requireLineEnd = true;
                return this;
            }

            public BoilerplateInsertionBuilder onlyWhenNotAtLineEnd() {
                requireLineEnd = false;
                return this;
            }

            public TokenPatternBuilder<BoilerplateInsertionBuilder> whenPrecededByPattern(int... pattern) {
                return new TokenPatternBuilder<>(tpb -> {
                    if (precedingPattern != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    precedingPattern = tpb.toTokenPattern();
                    return this;
                }, false, pattern);
            }

            public TokenPatternBuilder<BoilerplateInsertionBuilder> whenFollowedByPattern(int... pattern) {
                return new TokenPatternBuilder<>(tpb -> {
                    if (followingPattern != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    followingPattern = tpb.toTokenPattern();
                    return this;
                }, true, pattern);
            }

            public TokenPatternBuilder<BoilerplateInsertionBuilder> whenPrecededByPattern(IntPredicate... pattern) {
                return new TokenPatternBuilder<>(tpb -> {
                    if (precedingPattern != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    precedingPattern = tpb.toTokenPattern();
                    return this;
                }, false, pattern);
            }

            public TokenPatternBuilder<BoilerplateInsertionBuilder> whenFollowedByPattern(IntPredicate... pattern) {
                return new TokenPatternBuilder<>(tpb -> {
                    if (followingPattern != null) {
                        throw new IllegalStateException("Preceding pattern already set");
                    }
                    followingPattern = tpb.toTokenPattern();
                    return this;
                }, true, pattern);
            }

            public FinishableBoilerplateInsertionBuilder whenPrecedingToken(int first, int... more) {
                if (precedingTokenTest != null) {
                    precedingTokenTest = precedingTokenTest.or(IntPredicates.anyOf(first, more));
                } else {
                    precedingTokenTest = IntPredicates.anyOf(first, more);
                }
                return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest,
                        precedingPattern, followingPattern, caretBackup, name, description, category);
            }

            public FinishableBoilerplateInsertionBuilder whenPrecedingToken(IntPredicate pred) {
                if (precedingTokenTest != null) {
                    precedingTokenTest = precedingTokenTest.or(pred);
                } else {
                    precedingTokenTest = pred;
                }
                return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest,
                        precedingPattern, followingPattern, caretBackup, name, description, category);
            }

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

                public FinishableBoilerplateInsertionBuilder(EditorFeaturesBuilder owner,
                        String toInsert, Boolean requireLineEnd,
                        IntPredicate precedingTokenTest, TokenPattern preceding,
                        TokenPattern following, int caretBackup,
                        String name, String description, String category) {
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

                public FinishableBoilerplateInsertionBuilder setCategory(String category) {
                    this.category = category;
                    return this;
                }

                public FinishableBoilerplateInsertionBuilder setName(String name) {
                    this.name = name;
                    return this;
                }

                public FinishableBoilerplateInsertionBuilder setDescription(String description) {
                    this.description = description;
                    return this;
                }

                public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenPrecededByPattern(int... pattern) {
                    return new TokenPatternBuilder<>(tpb -> {
                        if (preceding != null) {
                            throw new IllegalStateException("Preceding pattern already set");
                        }
                        preceding = tpb.toTokenPattern();
                        return this;
                    }, false, pattern);
                }

                public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenFollowedByPattern(int... pattern) {
                    return new TokenPatternBuilder<>(tpb -> {
                        if (following != null) {
                            throw new IllegalStateException("Preceding pattern already set");
                        }
                        following = tpb.toTokenPattern();
                        return this;
                    }, true, pattern);
                }

                public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenPrecededByPattern(IntPredicate... pattern) {
                    return new TokenPatternBuilder<>(tpb -> {
                        if (preceding != null) {
                            throw new IllegalStateException("Preceding pattern already set");
                        }
                        preceding = tpb.toTokenPattern();
                        return this;
                    }, false, pattern);
                }

                public TokenPatternBuilder<FinishableBoilerplateInsertionBuilder> whenFollowedByPattern(IntPredicate... pattern) {
                    return new TokenPatternBuilder<>(tpb -> {
                        if (following != null) {
                            throw new IllegalStateException("Preceding pattern already set");
                        }
                        following = tpb.toTokenPattern();
                        return this;
                    }, true, pattern);
                }

                public FinishableBoilerplateInsertionBuilder onlyWhenAtLineEnd() {
                    requireLineEnd = true;
                    return this;
                }

                public FinishableBoilerplateInsertionBuilder onlyWhenNotAtLineEnd() {
                    requireLineEnd = false;
                    return this;
                }

                public EditorFeaturesBuilder whenKeyTyped(CharPredicate predicate) {
                    InsertBoilerplateEditProcessorFactory op
                            = new InsertBoilerplateEditProcessorFactory(owner.mimeType,
                                    predicate,
                                    toInsert, precedingTokenTest,
                                    requireLineEnd, preceding, following,
                                    caretBackup, name, description,
                                    owner.ownerType, category,
                                    currentTokenNot);
                    return owner.add(op);
                }

                public EditorFeaturesBuilder whenKeyTyped(char first, char... more) {
                    CharPredicate pred = CharPredicate.anyOf(ArrayUtils.prepend(first, more));
                    return whenKeyTyped(pred);
                }

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

    private static final class DelimiterTokenPairDeletion extends EnablableEditProcessorFactory<DeletedTextInterceptor.Context> {

        private final int openingTokenId;
        private final int closingTokenId;
        private final EditorFeatureUtils utils;
        private final IntPredicate ignore;

        private DelimiterTokenPairDeletion(String mimeType, int openingTokenId, int closingTokenId, String name, String description, Class<?> ownerType, IntPredicate ignore, String category) {
            super(ownerType, name, description, category);
            this.openingTokenId = openingTokenId;
            this.closingTokenId = closingTokenId;
            utils = new EditorFeatureUtils(mimeType);
            this.ignore = ignore == null ? IntPredicates.alwaysFalse() : ignore;
        }

        @Override
        protected String id() {
            return "dt" + Integer.toHexString(openingTokenId)
                    + Integer.toHexString(closingTokenId);
        }

        @Override
        public Class<DeletedTextInterceptor.Context> type() {
            return DeletedTextInterceptor.Context.class;
        }

        @Override
        public EditPhase initiatesFrom() {
            return EditPhase.ON_BEFORE_REMOVE;
        }

        @Override
        @SuppressWarnings("empty-statement")
        public EditProcessor apply(EditPhase t, ContextWrapper u) {
            boolean result = t == EditPhase.ON_BEFORE_REMOVE;
            if (result) {
//                System.out.println("HAVE A DELETION " + t
//                        + " deleted text '" + u.getText() + "' openHint " + openHint + "");
                BaseDocument doc = u.document();
                DeleteToken[] res = new DeleteToken[1];
                doc.readLock();
                try {
                    int bal = utils.tokenBalance(doc, openingTokenId, closingTokenId);
//                    System.out.println("TRY DELET TOK BAL " + openingTokenId + " " + closingTokenId + " is " + bal);
                    if (bal != 0 || bal == Integer.MAX_VALUE) {
                        return null;
                    }
                    boolean bwd = u.isBackwardDelete();
                    utils.withTokenSequence(doc, u.getOffset(), bwd, ts -> {
//                        System.out.println("DELETION on token '" + ts.token().text() + "' " + ts.token().id() + " backward? " + bwd);
                        if (ts.token().id().ordinal() == openingTokenId) {
//                            System.out.println("  is a match");
                            int tokId = -1;
                            while (ts.moveNext() && ignore.test(tokId = ts.token().id().ordinal()));
//                            System.out.println("NOW ON '" + ts.token().text() + "' " + ts.token().id());
                            if (closingTokenId == tokId) {
//                                System.out.println("CLOSING TOKEN ID MATCHES");
                                try {
                                    Token<?> tok = ts.token();
                                    int off = ts.offset();
                                    int len = tok.length();
                                    Position startPos = doc.createPosition(off, Position.Bias.Backward);
                                    Position endPos = doc.createPosition(off + len, Position.Bias.Forward);
//                                    System.out.println("   WILL DELETE " + len + " at " + off
//                                            + " text '" + doc.getText(off, len) + "'");
                                    res[0] = new DeleteToken(startPos, endPos);
                                } catch (BadLocationException ex) {
                                    Exceptions.printStackTrace(ex);
                                }
                            }
                        }
                        return true;
                    });
                } finally {
                    doc.readUnlock();
                }
                return res[0];
            }
            return null;
        }

        static class DeleteToken implements EditProcessor {

            private final Position start;
            private final Position end;

            public DeleteToken(Position start, Position end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public String toString() {
                return "Delete token " + start.getOffset() + ":"
                        + end.getOffset();
            }

            @Override
            public void onRemove(ContextWrapper ctx) {
                int st = start.getOffset();
                int en = end.getOffset();
                if (st != en) {
                    try {
                        ctx.document().remove(st, en - st);
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
    }

    private static final class InsertBoilerplateEditProcessorFactory extends EnablableEditProcessorFactory<TypedTextInterceptor.Context>
            implements BiPredicate<EditPhase, ContextWrapper>, EditProcessorFactory<TypedTextInterceptor.Context> {

        private final CharPredicate keyTest;
        private final String toInsert;
        private final IntPredicate precedingToken;
        private final Boolean requireLineEnd;
        private final EditorFeatureUtils utils;
        private final TokenPattern precedingPattern;
        private final TokenPattern followingPattern;
        private final int caretBackup;
        private final IntPredicate currentTokenNot;

        InsertBoilerplateEditProcessorFactory(String mimeType, CharPredicate keyTest,
                String toInsert, IntPredicate precedingToken,
                Boolean requireLineEnd, TokenPattern precedingPattern,
                TokenPattern followingPattern, int caretBackup,
                String name, String description, Class<?> ownerType, String category,
                IntPredicate currentTokenNot) {
            super(ownerType, name, description, category);
            this.utils = new EditorFeatureUtils(mimeType);
            this.keyTest = keyTest;
            this.toInsert = toInsert;
            this.precedingToken = precedingToken;
            this.requireLineEnd = requireLineEnd;
            this.precedingPattern = precedingPattern;
            this.followingPattern = followingPattern;
            this.caretBackup = caretBackup;
            this.currentTokenNot = currentTokenNot;
        }

        public String id() {
            StringBuilder sb = new StringBuilder("i-");
            for (int i = 0; i < toInsert.length(); i++) {
                char c = toInsert.charAt(i);
                sb.append(Integer.toHexString(c));
            }
            return sb.toString();
        }

        @Override
        public EditPhase initiatesFrom() {
            return EditPhase.ON_TYPING_INSERT;
        }

        @Override
        public String toString() {
            return "InsertBoilerplateOp('" + toInsert + "' prec "
                    + precedingToken + " char " + keyTest
                    + " rle " + requireLineEnd + " for " + utils.mimeType()
                    + (name == null ? "" : " - " + name)
                    + ")";
        }

        @Override
        public boolean test(EditPhase op, ContextWrapper t) {
//            System.out.println("TEST " + op + " " + t + " for '" + toInsert + "'");
            boolean result = op == EditPhase.ON_TYPING_INSERT;
            if (result) {
//                System.out.println("boilerplate op '" + toInsert + " where " + precedingToken + " and " + keyTest);
                try {
                    if (requireLineEnd != null) {
                        boolean atLineEnd = utils.isAtRowEnd(t.document(), t.getOffset());
                        result = requireLineEnd.booleanValue()
                                == atLineEnd;
//                        System.out.println("At line end? " + atLineENd + " require " + requireLineEnd + " result " + result);
                    }
                    if (result) {
                        char c = t.getText().charAt(0);
//                        System.out.println("TEXT IS '" + c + "'");
                        result = keyTest.test(c);
                    }
                    if (result && currentTokenNot != null) {
                        result = utils.withTokenSequence(t.document(), t.getOffset(), true, ts -> {
                            ts.move(t.getOffset());
                            if (ts.moveNext()) {
                                int type = ts.token().id().ordinal();
                                boolean mismatch = currentTokenNot.test(type);
                                return !mismatch;
                            }
                            return true;
                        });
                    }
                    if (result) {
                        result = utils.previousTokenMatches(t.document(), t.getOffset(), precedingToken);
//                        System.out.println("PREV TOKEN MATCHES " + precedingToken + "? " + result);
                    }
                    if (result && precedingPattern != null) {
                        result = precedingPattern.test(utils, t, t.getOffset());
//                        System.out.println("Preceding pattern matches? " + result + " for " + precedingPattern);
                    }
                    if (result && followingPattern != null) {
                        result = followingPattern.test(utils, t, t.getOffset());
//                        System.out.println("Following pattern matches? " + result + " for " + followingPattern);
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                    result = false;
                }
            }
            return result;
        }

        @Override
        public Class<TypedTextInterceptor.Context> type() {
            return TypedTextInterceptor.Context.class;
        }

        @Override
        public EditProcessor apply(EditPhase op, ContextWrapper ctx) {
            if (test(op, ctx)) {
                return new IbOp();
            }
            return null;
        }

        class IbOp implements EditProcessor {

            int caretTo = -1;

            @Override
            public void onInsert(ContextWrapper ctx) {
                caretTo = ctx.getOffset() + toInsert.length();
                ctx.setText(toInsert, 0, true);
            }

            @Override
            public void onAfterInsert(ContextWrapper ctx) {
                if (caretTo != -1) {
                    ctx.component().getCaret().setDot(caretTo - caretBackup);
                }
            }
        }
    }

    static final class CharacterElisionEditProcessorFactory extends EnablableEditProcessorFactory<TypedTextInterceptor.Context> {

        private final boolean backwards;
        private final char what;
        private final IntPredicate currentTokenNot;
        private final EditorFeatureUtils utils;

        public CharacterElisionEditProcessorFactory(String mimeType, boolean backwards, char what, IntPredicate currentTokenNot, Class<?> ownerType, String name, String desc, String category) {
            super(ownerType, name, desc, category);
            this.backwards = backwards;
            this.what = what;
            this.currentTokenNot = currentTokenNot;
            this.utils = new EditorFeatureUtils(mimeType);
        }

        @Override
        protected String id() {
            return "eld-" + what + (backwards ? "B":"F");
        }

        @Override
        public Class<TypedTextInterceptor.Context> type() {
            return TypedTextInterceptor.Context.class;
        }

        @Override
        public EditPhase initiatesFrom() {
            return EditPhase.ON_BEFORE_TYPING_INSERT;
        }

        @Override
        public EditProcessor apply(EditPhase t, ContextWrapper u) {
            String txt = u.getText();
            if (txt.charAt(0) != what) {
                return null;
            }
            if (utils.nextTokenMatches(u.document(), u.getOffset(), currentTokenNot)) {
                return null;
            }
            return new Elision();
        }

        class Elision implements EditProcessor {

            int caretPos = -1;

            @Override
            public void onBeforeInsert(ContextWrapper ctx) {
                int ix = ctx.getOffset();
                BaseDocument doc = ctx.document();
                doc.readLock();
                try {
                    int len = doc.getLength();
                    try {
                        if (backwards) {
                            CharSequence seq = DocumentUtilities.getText(doc, 0, ix);
                            int max = seq.length();
                            for (int i = max - 1; i >= 0; i--) {
                                char c = seq.charAt(i);
                                if (what == c) {
                                    caretPos = 0;
                                    break;
                                } else if (!Character.isWhitespace(c)) {
                                    break;
                                }
                            }
                        } else {
                            CharSequence seq = DocumentUtilities.getText(doc, ix, len - ix);
                            int max = seq.length();
                            for (int i = 0; i < max; i++) {
                                char c = seq.charAt(i);
                                if (c == what) {
                                    caretPos = ix + i + 1;
                                    break;
                                } else if (!Character.isWhitespace(c)) {
                                    break;
                                }
                            }
                            if (caretPos != -1) {
                                ctx.component().getCaret().setDot(caretPos);
                            }
                        }
                    } catch (BadLocationException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                } finally {
                    doc.readUnlock();
                }

            }

            @Override
            public boolean consumesInitialEvent() {
                return caretPos != -1;
            }

        }

    }

    private <T> EditProcessor find(EditPhase ot, T ctx) {
        if (!this.types.contains(ot)) {
            return null;
        }
        for (EnablableEditProcessorFactory<?> op : ops) {
            if (op.initiatesFrom() == ot && op.isEnabled()) {
                EditProcessor result = tryOne(ot, op, ctx);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T, R> EditProcessor tryOne(EditPhase ot, EnablableEditProcessorFactory<T> op, R obj) {
        if (op.type().isInstance(obj)) {
            T cast = op.type().cast(obj);
            return op.apply(ot, ContextWrapper.wrap(cast));
        }
        return null;
    }

    private static final class Interceptor implements TypedTextInterceptor.Factory, DeletedTextInterceptor.Factory, TypedBreakInterceptor.Factory {

        private final Typing typing = new Typing();
        private final Deletions deletions = new Deletions();
        private final Breaks breaks = new Breaks();
        private final EditorFeatures features;

        Interceptor(EditorFeatures features) {
            this.features = features;
        }

        @Override
        public TypedTextInterceptor createTypedTextInterceptor(MimePath mp) {
            return typing;
        }

        @Override
        public DeletedTextInterceptor createDeletedTextInterceptor(MimePath mp) {
            return deletions;
        }

        @Override
        public TypedBreakInterceptor createTypedBreakInterceptor(MimePath mp) {
            return breaks;
        }

        private final class Typing implements TypedTextInterceptor {

            EditProcessor currentOp;

            @Override
            public boolean beforeInsert(Context cntxt) throws BadLocationException {
                currentOp = features.find(EditPhase.ON_BEFORE_TYPING_INSERT, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeInsert(ContextWrapper.wrap(cntxt));
                }
                boolean result = currentOp != null ? currentOp.consumesInitialEvent() : false;
                if (result) {
                    // other methods will never be called, we are consuming
                    // the event and taking over from here
                    currentOp = null;
                }
                return result;
            }

            @Override
            public void insert(MutableContext mc) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_TYPING_INSERT, mc);
                }
                if (currentOp != null) {
                    currentOp.onInsert(ContextWrapper.wrap(mc));
                }
            }

            @Override
            public void afterInsert(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_AFTER_TYPING_INSERT, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onAfterInsert(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }

            @Override
            public void cancelled(Context cntxt) {
                if (currentOp != null) {
                    currentOp.cancelled(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }
        }

        private final class Deletions implements DeletedTextInterceptor {

            EditProcessor currentOp;

            @Override
            public boolean beforeRemove(Context cntxt) throws BadLocationException {
                currentOp = features.find(EditPhase.ON_BEFORE_REMOVE, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeRemove(ContextWrapper.wrap(cntxt));
                }
                // If we return true, that stops keystroke processing entirely - 
                return currentOp != null ? currentOp.consumesInitialEvent() : false;
            }

            @Override
            public void remove(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_REMOVE, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onRemove(ContextWrapper.wrap(cntxt));
                }
            }

            @Override
            public void afterRemove(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_AFTER_REMOVE, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onAfterRemove(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }

            @Override
            public void cancelled(Context cntxt) {
                if (currentOp != null) {
                    currentOp.cancelled(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }
        }

        private final class Breaks implements TypedBreakInterceptor {

            EditProcessor currentOp;

            @Override
            public boolean beforeInsert(Context cntxt) throws BadLocationException {
                currentOp = features.find(EditPhase.ON_BEFORE_BREAK_INSERT, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeInsert(ContextWrapper.wrap(cntxt));
                }
                return currentOp != null ? currentOp.consumesInitialEvent() : false;
            }

            @Override
            public void insert(MutableContext mc) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_BREAK_INSERT, mc);
                }
                if (currentOp != null) {
                    currentOp.onBeforeInsert((ContextWrapper.wrap(mc)));
                }
            }

            @Override
            public void afterInsert(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_AFTER_BREAK_INSERT, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onAfterInsert(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }

            @Override
            public void cancelled(Context cntxt) {
                if (currentOp != null) {
                    currentOp.cancelled(ContextWrapper.wrap(cntxt));
                }
                currentOp = null;
            }
        }
    }
}
