package org.nemesis.antlr.v4.netbeans.v8.grammar.code.formatting;

/**
 * Builder for criteria that activate a rule only if a value extracted
 * into the lexing state matches a desired value.
 *
 * @author Tim Boudreau
 */
public interface LexingStateCriteriaBuilder<T extends Enum<T>, R> {

    public R isGreaterThan(int value);

    public R isGreaterThanOrEqualTo(int value);

    public R isLessThan(int value);

    public R isLessThanOrEqualTo(int value);

    public R isEqualTo(int value);

    public R isUnset();

    public R isTrue();

    public R isFalse();

    public R isSet();

}
