/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics.data.named;

import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public interface SerializationCallback {

    void run() throws IOException, ClassNotFoundException;

}
