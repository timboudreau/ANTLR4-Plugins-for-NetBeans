/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.simple;

import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;

/**
 * For tests, an interface to allow provision of sample files for testing.
 *
 * @author Tim Boudreau
 */
public interface SampleFile<L extends Lexer, P extends Parser> {

    CharStream charStream() throws IOException;

    InputStream inputStream();

    int length() throws IOException;

    L lexer() throws IOException;

    L lexer(ANTLRErrorListener l) throws IOException;

    P parser() throws IOException;

    String text() throws IOException;
}
