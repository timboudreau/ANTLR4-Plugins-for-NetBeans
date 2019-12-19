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
import java.util.prefs.Preferences;
import javax.swing.text.BadLocationException;
import org.nemesis.charfilter.CharPredicate;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;

/**
 *
 * @author Tim Boudreau
 */
public class EditorFeatures {

    private final Set<EnableableEditProcessorFactory<?>> ops = new HashSet<>();
    private final Interceptor interceptor = new Interceptor(this);
    private final Set<EditPhase> types = EnumSet.noneOf(EditPhase.class);
    private final String mimeType;

    protected EditorFeatures(String mimeType, Consumer<EditorFeatures.Builder> configurer) {
        this.mimeType = mimeType;
        Builder b = new Builder(notNull("mimeType", mimeType), getClass());
        configurer.accept(b);
        ops.addAll(b.ops);
        types.addAll(b.triggers);
    }

    public List<EditorFeatureEnablementModel> enablableItems() {
        List<EditorFeatureEnablementModel> descs = new ArrayList<>(ops.size());
        for (EnableableEditProcessorFactory<?> d : ops) {
            if (d.name() != null) {
                descs.add(new EditorFeatureEnablementModelImpl(mimeType, d));
            }
        }
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

    public static class Builder {

        private Set<EnableableEditProcessorFactory<?>> ops = new HashSet<>();
        private Set<EditPhase> triggers = EnumSet.noneOf(EditPhase.class);
        private final Class<? extends EditorFeatures> ownerType;
        final String mimeType;

        private Builder(String mimeType, Class<? extends EditorFeatures> ownerType) {
            this.mimeType = mimeType;
            this.ownerType = ownerType;
        }

        public BoilerplateInsertionBuilder insertBoilerplate(String txt) {
            return new BoilerplateInsertionBuilder(this, txt);
        }

        private Builder add(EnableableEditProcessorFactory<?> op) {
            ops.add(op);
            triggers.add(op.initiatesFrom());
            System.out.println("ADD OP " + op);
            return this;
        }

        public static class BoilerplateInsertionBuilder {

            private final Builder owner;
            private final String toInsert;
            Boolean requireLineEnd;
            IntPredicate precedingTokenTest;
            TokenPattern precedingPattern;
            TokenPattern followingPattern;
            String name;
            String description;
            final int caretBackup;

            private BoilerplateInsertionBuilder(Builder owner, String toInsert) {
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
                        precedingPattern, followingPattern, caretBackup, name, description);
            }

            public FinishableBoilerplateInsertionBuilder whenPrecedingToken(IntPredicate pred) {
                if (precedingTokenTest != null) {
                    precedingTokenTest = precedingTokenTest.or(pred);
                } else {
                    precedingTokenTest = pred;
                }
                return new FinishableBoilerplateInsertionBuilder(owner, toInsert, requireLineEnd, precedingTokenTest,
                        precedingPattern, followingPattern, caretBackup, name, description);
            }

            public static final class FinishableBoilerplateInsertionBuilder {

                private final Builder owner;
                private final String toInsert;
                Boolean requireLineEnd;
                IntPredicate precedingTokenTest;
                TokenPattern precedingPattern;
                TokenPattern followingPattern;
                private TokenPattern preceding;
                private TokenPattern following;
                private final int caretBackup;
                private String name;
                private String description;

                public FinishableBoilerplateInsertionBuilder(Builder owner,
                        String toInsert, Boolean requireLineEnd,
                        IntPredicate precedingTokenTest, TokenPattern preceding,
                        TokenPattern following, int caretBackup,
                        String name, String description) {
                    this.owner = owner;
                    this.toInsert = toInsert;
                    this.requireLineEnd = requireLineEnd;
                    this.precedingTokenTest = precedingTokenTest;
                    this.preceding = preceding;
                    this.following = following;
                    this.caretBackup = caretBackup;
                    this.name = name;
                    this.description = description;
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

                public Builder whenKeyTyped(char first, char... more) {
                    CharPredicate pred = CharPredicate.anyOf(ArrayUtils.prepend(first, more));
                    InsertBoilerplateOp op
                            = new InsertBoilerplateOp(owner.mimeType, pred,
                                    toInsert, precedingTokenTest,
                                    requireLineEnd, preceding, following,
                                    caretBackup, name, description,
                                    owner.ownerType);
                    return owner.add(op);
                }
            }
        }
    }

    static abstract class EnableableEditProcessorFactory<T> implements EditProcessorFactory<T> {

        protected final Class<?> ownerType;
        protected final String name;
        protected final String description;
        private Preferences prefs;

        protected EnableableEditProcessorFactory(Class<?> ownerType, String name, String description) {
            this.ownerType = ownerType;
            this.name = name;
            this.description = description;
        }

        private Preferences prefs() {
            if (prefs == null) {
                prefs = NbPreferences.forModule(ownerType);
            }
            return prefs;
        }

        public final boolean isEnabled() {
            return prefs().getBoolean("ef-" + id(), true);
        }

        public final void setEnabled(boolean enabled) {
            prefs().putBoolean("ef-" + id(), enabled);
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        protected abstract String id();
    }

    private static final class InsertBoilerplateOp extends EnableableEditProcessorFactory<TypedTextInterceptor.Context>
            implements BiPredicate<EditPhase, ContextWrapper>, EditProcessorFactory<TypedTextInterceptor.Context> {

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
                TokenPattern followingPattern, int caretBackup,
                String name, String description, Class<?> ownerType) {
            super(ownerType, name, description);
            this.utils = new EditorFeatureUtils(mimeType);
            this.keyTest = keyTest;
            this.toInsert = toInsert;
            this.precedingToken = precedingToken;
            this.requireLineEnd = requireLineEnd;
            this.precedingPattern = precedingPattern;
            this.followingPattern = followingPattern;
            this.caretBackup = caretBackup;
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
            System.out.println("TEST " + op + " " + t + " for '" + toInsert + "'");
            if (op != EditPhase.ON_TYPING_INSERT) {
                System.out.println("  wrong op type");
            }
            boolean result = op == EditPhase.ON_TYPING_INSERT;
            if (result) {
//                System.out.println("boilerplate op '" + toInsert + " where " + precedingToken + " and " + keyTest);
                try {
                    if (requireLineEnd != null) {
                        boolean atLineENd = utils.isAtRowEnd(t.document(), t.getOffset());
                        result = requireLineEnd.booleanValue()
                                == atLineENd;
//                        System.out.println("At line end? " + atLineENd + " require " + requireLineEnd + " result " + result);
                    }
                    if (result) {
                        char c = t.getText().charAt(0);
//                        System.out.println("TEXT IS '" + c + "'");
                        result = keyTest.test(c);
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

    private <T> EditProcessor find(EditPhase ot, T ctx) {
        if (!this.types.contains(ot)) {
            return null;
        }
        for (EnableableEditProcessorFactory<?> op : ops) {
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
    private <T, R> EditProcessor tryOne(EditPhase ot, EnableableEditProcessorFactory<T> op, R obj) {
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

            EditProcessor currentOp;

            @Override
            public boolean beforeInsert(Context cntxt) throws BadLocationException {
                currentOp = features.find(EditPhase.ON_BEFORE_TYPING_INSERT, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeInsert((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
            }

            @Override
            public void insert(MutableContext mc) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_TYPING_INSERT, mc);
                }
                if (currentOp != null) {
                    currentOp.onInsert((ContextWrapper.wrap(mc)));
                }
            }

            @Override
            public void afterInsert(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_AFTER_TYPING_INSERT, cntxt);
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

            EditProcessor currentOp;

            @Override
            public boolean beforeRemove(Context cntxt) throws BadLocationException {
                currentOp = features.find(EditPhase.ON_BEFORE_REMOVE, cntxt);
                if (currentOp != null) {
                    currentOp.onBeforeRemove((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
            }

            @Override
            public void remove(Context cntxt) throws BadLocationException {
                if (currentOp == null) {
                    currentOp = features.find(EditPhase.ON_REMOVE, cntxt);
                }
                if (currentOp != null) {
                    currentOp.onRemove((ContextWrapper.wrap(cntxt)));
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
                    currentOp.onBeforeInsert((ContextWrapper.wrap(cntxt)));
                }
                return currentOp != null;
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
