package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

/**
 * Can be passed to a ForeignInvocationResult which has collected
 * output to parse its lines.
 *
 * @author Tim Boudreau
 */
public interface OutputParser<T> {

    T onLine(String line);
}
