package org.nemesis.antlr.memory.output;

/**
 * Can be passed to a ForeignInvocationResult which has collected
 * output to parse its lines.
 *
 * @author Tim Boudreau
 */
public interface OutputParser<T> {

    T onLine(String line);
}
