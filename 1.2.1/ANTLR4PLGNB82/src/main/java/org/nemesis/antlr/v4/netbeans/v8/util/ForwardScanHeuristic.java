package org.nemesis.antlr.v4.netbeans.v8.util;

/**
 * A heuristic for looking for a file in the children of
 * some folder.
 *
 * @author Tim Boudreau
 */
public interface ForwardScanHeuristic extends FileLocationHeuristic {

    public FileLocationHeuristic relativeToProjectRoot();
}
