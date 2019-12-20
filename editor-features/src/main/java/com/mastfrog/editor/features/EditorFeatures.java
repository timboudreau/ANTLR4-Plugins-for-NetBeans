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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.spi.editor.typinghooks.DeletedTextInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedBreakInterceptor;
import org.netbeans.spi.editor.typinghooks.TypedTextInterceptor;
import org.netbeans.spi.options.OptionsPanelController;

/**
 * Registry of editor features; provides a simple, builder-based, declarative
 * SPI over the editor API's fussy and difficult-to-use TypedTextInterceptor,
 * DeletedTextInterceptor and TypedBreakIterator.
 * <p>
 * To use, subclass and populate the builder passed to the consumer you
 * pass to the superclass initializer in the constructor.
 * </p>
 * <p>
 * Several types of features are supported - all itervene in the the typing
 * process, to insert additional characters, delete additional characters or
 * to ignore keystrokes.
 * </p>
 * <p>Implementations will typically
 * be generated by annotation processors, but the implementation pattern is
 * this:
 * </p>
 * <pre>
 * public final class MyEditorFeatures {
 *
 *  // The instance the objects we will register in various lookups
 *  // come from - no point in making it lazy, if this class is being
 *  // touched, something is looking it up and it is going to be used
 *  static final MyEditorFeatures INSTANCE = new MyEditorFeatures();
 *
 *  // For logging purposes, a factory for IntPredicate instances whose
 *  // toString() method will show you token *names* not numbers you have
 *  // to look up
 *  static final Criteria CRIT = Criteria.forVocabulary(MyAntlrLexer.VOCABULARY);
 *
 *   public MyEditorFeatures() {
 *     super(&quot;text/x-foo&quot;, builder -&gt; {
 *       builder.elide(':')
 *
 *          // name, category and description are optional but at least
 *          // name is needed if you want this feature to have a checkbox
 *          // in the options window to disable it
 *         .setCategory(&quot;Convenience&quot;)
 *         .setName(&quot;Elide : when typed before an existing :&quot;)
 *
 *         .whenCurrentTokenNot(CRIT.anyOf(
 *             MyLex.LINE_COMMENT,
 *             MyLexer.BLOCK_COMMENT,
 *             MyLexer.STRING_LITERAL
 *        ));
 *     });
 *   }
 *
 *   // If you are intercepting / altering as the user is typing
 *   &#064;MimeRegistration(mimeType = &quot;text/x-foo&quot;, service =
 *     TypedTextInterceptor.Factory.class, position = 10)
 *   public static TypedTextInterceptor.Factory typingFactoryRegistration() { return
 *     INSTANCE.typingFactory();
 *   }
 *
 *   // If you are altering deletions (say, to remove matching parens)
 *   &#064;MimeRegistration(mimeType = &quot;text/x-foo&quot;, service =
 *     DeletedTextInterceptor.Factory.class, position = 11)
 *   public static DeletedTextInterceptor.Factory deletionFactoryRegistration() {
 *     return INSTANCE.deletionFactory();
 *   }
 *
 *   // Needed so the options panel can function
 *   &#064;MimeRegistration(mimeType = &quot;text/x-foo&quot;, position = 171, service =
 *     EditorFeatures.class)
 *   public static EditorFeatures instance() {
 *     return INSTANCE;
 *   }
 * }
 * </pre>
 * <p>
 * While developed for Antlr, there is nothing Antlr specific about this class
 * or its builders - token ordinals from any language can be used.
 * </p>
 *
 * @author Tim Boudreau
 */
public class EditorFeatures {

    private final Set<EnablableEditProcessorFactory<?>> ops = new HashSet<>();
    private final Interceptor interceptor = new Interceptor(this);
    private final Set<EditPhase> types = EnumSet.noneOf(EditPhase.class);
    private final String mimeType;

    protected EditorFeatures(String mimeType, Consumer<EditorFeaturesBuilder> configurer) {
        this.mimeType = mimeType;
        EditorFeaturesBuilder b = new EditorFeaturesBuilder(
                notNull("mimeType", mimeType), getClass());
        configurer.accept(b);
        ops.addAll(b.ops);
        types.addAll(b.triggers);
    }

    final List<EditorFeatureEnablementModel> enablableItems() {
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
