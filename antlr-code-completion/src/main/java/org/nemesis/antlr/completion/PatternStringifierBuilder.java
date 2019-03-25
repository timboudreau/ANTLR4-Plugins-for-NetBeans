package org.nemesis.antlr.completion;

import java.util.EnumMap;
import java.util.regex.Pattern;

/**
 * Builds a stringifier function which uses regular expressions to determine
 * the string representation for various different {@link StringKind}s.
 *
 * @author Tim Boudreau
 */
public final class PatternStringifierBuilder<I> {

    private final CompletionBuilder<I> bldr;
    private final EnumMap<StringKind, Pattern> patternForKind
            = new EnumMap<>(StringKind.class);
    private final EnumMap<StringKind, String> formatForKind
            = new EnumMap<>(StringKind.class);

    PatternStringifierBuilder(CompletionBuilder<I> bldr) {
        this.bldr = bldr;
    }

    public CompletionBuilder<I> build() {
        return bldr.setStringifier(patternForKind, formatForKind);
    }

    /**
     * Get an interim builder which lets you specify other
     * properties for this pattern.
     *
     * @param pat A pattern, which must contain at least one group
     * to be useful
     * @return A builder
     */
    public InterimStringifierBuilder<I> withPattern(Pattern pat) {
        return new InterimStringifierBuilder<I>() {
            String fmt;

            @Override
            public InterimStringifierBuilder<I> withMessageFormat(String fmt) {
                if (fmt != null && !fmt.contains("{0")) {
                    throw new IllegalArgumentException("No occurrance of '{0' "
                            + "in format - this does not look like a string "
                            + "usable with MessageFormat.format()");
                }
                this.fmt = fmt;
                return this;
            }

            @Override
            public PatternStringifierBuilder<I> forKind(StringKind kind) {
                if (patternForKind.containsKey(kind)) {
                    throw new IllegalStateException("Already have pattern "
                            + patternForKind.get(kind).pattern() + " for " + kind);
                }
                patternForKind.put(kind, pat);
                if (fmt != null) {
                    formatForKind.put(kind, fmt);
                }
                return PatternStringifierBuilder.this;
            }
        };
    }

    public static abstract class InterimStringifierBuilder<I> {

        private InterimStringifierBuilder() {
            
        }

        public abstract InterimStringifierBuilder<I> withMessageFormat(String fmt);

        public abstract PatternStringifierBuilder<I> forKind(StringKind kind);
    }

}
