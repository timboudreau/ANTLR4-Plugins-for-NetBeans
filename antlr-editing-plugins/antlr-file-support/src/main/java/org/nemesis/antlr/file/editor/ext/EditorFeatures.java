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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javax.swing.text.BadLocationException;
import org.nemesis.charfilter.CharPredicate;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.openide.util.Exceptions;

/**
 *
 * @author Tim Boudreau
 */
public class EditorFeatures {

    private final Set<Op<?>> ops = new HashSet<>();
    private final Interceptor interceptor = new Interceptor(this);
    private final Set<OpType> types = EnumSet.noneOf(OpType.class);

    protected EditorFeatures(String mimeType, Consumer<EditorFeatures.Builder> configurer) {
        Builder b = new Builder(notNull("mimeType", mimeType));
        configurer.accept(b);
        ops.addAll(b.ops);
        types.addAll(b.triggers);
    }

    protected final DeletedTextInterceptor.Factory deletionFactory() {
        return interceptor;
    }

    protected final TypedTextInterceptor.Factory typingFactory() {
        return interceptor;
    }

    protected final TypedBreakInterceptor.Factory breakFactory() {
        return interceptor;
    }

    protected static class Builder {

        private Set<Op<?>> ops = new HashSet<>();
        private Set<OpType> triggers = EnumSet.noneOf(OpType.class);
        final String mimeType;

        private Builder(String mimeType) {
            this.mimeType = mimeType;
        }

        public BoilerplateInsertionBuilder insertBoilerplate(String txt) {
            return new BoilerplateInsertionBuilder(this, txt);
        }

        private Builder add(Op<?> op) {
            ops.add(op);
            triggers.add(op.initiatesFrom());
            System.out.println("ADD OP " + op);
            return this;
        }

        protected static class BoilerplateInsertionBuilder {

            private final Builder owner;
            private final String toInsert;
            Boolean requireLineEnd;
            IntPredicate precedingTokenTest;
            TokenPattern precedingPattern;
            TokenPattern followingPattern;
            private final int caretBackup;

            private BoilerplateInsertionBuilder(Builder owner, String toInsert) {
                this.owner = owner;
                CaretPositionAndInsertText cpait = CaretPositionAndInsertText.parse(notNull("toInsert", toInsert));
                this.toInsert = cpait.insertText;
                this.caretBackup = cpait.caretBackup;
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
                        precedingPattern, followingPattern, caretBackup);
            }

            public FinishableBoilerplateInsertionBuilder whenPrecedingToken(IntPredicate pred) {
                if (precedingTokenTest != null) {
                    precedingTokenTest = precedingTokenTest.or(pred);
                } else {
                    precedingTokenTest = pred;
                }
                return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest,
                        precedingPattern, followingPattern, caretBackup);
            }

            protected static final class FinishableBoilerplateInsertionBuilder {

                private final Builder owner;
                private final String toInsert;
                Boolean requireLineEnd;
                IntPredicate precedingTokenTest;
                TokenPattern precedingPattern;
                TokenPattern followingPattern;
                private TokenPattern preceding;
                private TokenPattern following;
                private final int caretBackup;

                public FinishableBoilerplateInsertionBuilder(Builder owner,
                        String toInsert, Boolean requireLineEnd,
                        IntPredicate precedingTokenTest, TokenPattern preceding,
                        TokenPattern following, int caretBackup) {
                    this.owner = owner;
                    this.toInsert = toInsert;
                    this.requireLineEnd = requireLineEnd;
                    this.precedingTokenTest = precedingTokenTest;
                    this.preceding = preceding;
                    this.following = following;
                    this.caretBackup = caretBackup;
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

                public Builder whenKeyTyped(char first, char... more) {
                    CharPredicate pred = CharPredicate.anyOf(ArrayUtils.prepend(first, more));
                    InsertBoilerplateOp op
                            = new InsertBoilerplateOp(owner.mimeType, pred,
                                    toInsert, precedingTokenTest,
                                    requireLineEnd, preceding, following,
                                    caretBackup);
                    return owner.add(op);
                }
            }
        }
    }

    private static final class InsertBoilerplateOp implements BiPredicate<OpType, ContextWrapper>, Op<TypedTextInterceptor.Context> {

        private final CharPredicate keyTest;
        private final String toInsert;
        private final IntPredicate precedingToken;
        private final Boolean requireLineEnd;
        private final EditorFeatureUtils utils;
        private final TokenPattern precedingPattern;
        private final TokenPattern followingPattern;
        private final int caretBackup;

        public InsertBoilerplateOp(String mimeType, CharPredicate keyTest,
                String toInsert, IntPredicate precedingToken,
                Boolean requireLineEnd, TokenPattern precedingPattern,
                TokenPattern followingPattern, int caretBackup) {
            this.utils = new EditorFeatureUtils(mimeType);
            this.keyTest = keyTest;
            this.toInsert = toInsert;
            this.precedingToken = precedingToken;
            this.requireLineEnd = requireLineEnd;
            this.precedingPattern = precedingPattern;
            this.followingPattern = followingPattern;
            this.caretBackup = caretBackup;
        }

        @Override
        public OpType initiatesFrom() {
            return OpType.ON_TYPING_INSERT;
        }

        @Override
        public String toString() {
            return "InsertBoilerplateOp('" + toInsert + "' prec "
                    + precedingToken + " char " + keyTest
                    + " rle " + requireLineEnd + " for " + utils.mimeType() + ")";
        }

        @Override
        public boolean test(OpType op, ContextWrapper t) {
            System.out.println("TEST " + op + " " + t + " for '" + toInsert + "'");
            if (op != OpType.ON_TYPING_INSERT) {
                System.out.println("  wrong op type");
            }
            boolean result = op == OpType.ON_TYPING_INSERT;
            if (result) {
                System.out.println("boilerplate op '" + toInsert + " where " + precedingToken + " and " + keyTest);
                try {
                    if (requireLineEnd != null) {
                        boolean atLineENd = utils.isAtRowEnd(t.document(), t.getOffset());
                        result = requireLineEnd.booleanValue()
                                == atLineENd;
                        System.out.println("At line end? " + atLineENd + " require " + requireLineEnd + " result " + result);
                    }
                    if (result) {
                        char c = t.getText().charAt(0);
                        System.out.println("TEXT IS '" + c + "'");
                        result = keyTest.test(c);
                    }
                    if (result) {
                        result = utils.previousTokenMatches(t.document(), t.getOffset(), precedingToken);
                        System.out.println("PREV TOKEN MATCHES " + precedingToken + "? " + result);
                    }
                    if (result && precedingPattern != null) {
                        result = precedingPattern.test(utils, t, t.getOffset());
                        System.out.println("Preceding pattern matches? " + result + " for " + precedingPattern);
                    }
                    if (result && followingPattern != null) {
                        result = followingPattern.test(utils, t, t.getOffset());
                        System.out.println("Following pattern matches? " + result + " for " + followingPattern);
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
        public TextOperation apply(OpType op, ContextWrapper ctx) {
            if (test(op, ctx)) {
                return new IbOp();
            }
            return null;
        }

        class IbOp implements TextOperation {

            int caretTo = -1;

            @Override
            public void onInsert(ContextWrapper ctx) {
                caretTo = ctx.getOffset() + toInsert.length();
                ctx.setText(toInsert, 0, true);
                System.out.println("IBOP on insert " + caretTo + " inserted '" + toInsert + "'");
            }

            @Override
            public void onAfterInsert(ContextWrapper ctx) {
                if (caretTo != -1) {
                    ctx.component().getCaret().setDot(caretTo - caretBackup);
                }
            }
        }
    }

    private <T> TextOperation find(OpType ot, T ctx) {
        if (!this.types.contains(ot)) {
            return null;
        }
        for (Op<?> op : ops) {
            if (op.initiatesFrom() == ot) {
                TextOperation result = tryOne(ot, op, ctx);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T, R> TextOperation tryOne(OpType ot, Op<T> op, R obj) {
        if (op.type().isInstance(obj)) {
            T cast = op.type().cast(obj);
            return op.apply(ot, ContextWrapper.wrap(cast));
        }
        return null;
    }

    private static class Interceptor implements TypedTextInterceptor.Factory, DeletedTextInterceptor.Factory, TypedBreakInterceptor.Factory {

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

            TextOperation currentOp;

            @Override
            public boolean beforeInsert(Context cntxt) throws BadLocationException {
                currentOp = features.find(OpType.ON_BEFORE_TYPING_INSERT, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeInsert((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
            }

            @Override
            public void insert(MutableContext mc) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_TYPING_INSERT, mc);
                }
                if (currentOp != null) {
                    currentOp.onInsert((ContextWrapper.wrap(mc)));
                }
            }

            @Override
            public void afterInsert(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_AFTER_TYPING_INSERT, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onAfterInsert((ContextWrapper.wrap(cntxt)));
                }
                currentOp = null;
            }

            @Override
            public void cancelled(Context cntxt) {
                if (currentOp != null) {
                    currentOp.cancelled((ContextWrapper.wrap(cntxt)));
                }
                currentOp = null;
            }
        }

        private final class Deletions implements DeletedTextInterceptor {

            TextOperation currentOp;

            @Override
            public boolean beforeRemove(Context cntxt) throws BadLocationException {
                currentOp = features.find(OpType.ON_BEFORE_REMOVE, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeRemove((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
            }

            @Override
            public void remove(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_REMOVE, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onRemove((ContextWrapper.wrap(cntxt)));
                }
            }

            @Override
            public void afterRemove(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_AFTER_REMOVE, cntxt);
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

            TextOperation currentOp;

            @Override
            public boolean beforeInsert(Context cntxt) throws BadLocationException {
                currentOp = features.find(OpType.ON_BEFORE_BREAK_INSERT, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeInsert((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
            }

            @Override
            public void insert(MutableContext mc) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_BREAK_INSERT, mc);
                }
                if (currentOp != null) {
                    currentOp.onBeforeInsert((ContextWrapper.wrap(mc)));
                }
            }

            @Override
            public void afterInsert(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(OpType.ON_AFTER_BREAK_INSERT, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onAfterInsert((ContextWrapper.wrap(cntxt)));
                }
                currentOp = null;
            }

            @Override
            public void cancelled(Context cntxt) {
                if (currentOp != null) {
                    currentOp.cancelled((ContextWrapper.wrap(cntxt)));
                }
                currentOp = null;
            }
        }
    }
}
