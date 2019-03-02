package org.nemesis.antlrformatting.api;

/**
 * Builder for criteria that activate a rule only if a value extracted into the
 * lexing state matches a desired value.
 *
 * @author Tim Boudreau
 */
public abstract class LexingStateCriteriaBuilder<T extends Enum<T>, R> {

    LexingStateCriteriaBuilder() {

    }

    public abstract R isGreaterThan(int value);

    public abstract R isGreaterThanOrEqualTo(int value);

    public abstract R isLessThan(int value);

    public abstract R isLessThanOrEqualTo(int value);

    public abstract R isEqualTo(int value);

    public abstract R isUnset();

    public abstract R isTrue();

    public abstract R isFalse();

    public abstract R isSet();
}
