/*
BSD License

Copyright (c) 2016-2018, Frédéric Yvon Vinet, Tim Boudreau
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright
  notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright
  notice, this list of conditions and the following disclaimer in the
  documentation and/or other materials provided with the distribution.
* The name of its author may not be used to endorse or promote products
  derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
 */
package org.nemesis.antlr.live.parsing.extract;

import java.util.Set;
import java.util.TreeSet;
import org.nemesis.jfs.JFSFileObject;

/**
 *
 * @author Tim Boudreau
 */
public class ExtractionCodeGenerationResult {

    private JFSFileObject generatedFile;
    private final String grammarName;
    private final String pkg;
    private final Set<String> examined = new TreeSet<>();

    ExtractionCodeGenerationResult(String grammarName, String pkg) {
        this.grammarName = grammarName;
        this.pkg = pkg;
    }

    ExtractionCodeGenerationResult setResult(JFSFileObject fo) {
        generatedFile = fo;
        return this;
    }

    public ExtractionCodeGenerationResult examined(String what) {
        examined.add(what);
        return this;
    }

    public boolean isSuccess() {
        return generatedFile != null;
    }

    public JFSFileObject file() {
        return generatedFile;
    }

    @Override
    public String toString() {
        if (generatedFile != null) {
            return grammarName + " -> " + generatedFile.getName();
        }
        StringBuilder sb = new StringBuilder();
        for (String p : examined) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(p);
        }
        sb.insert(0, " - tried: ");
        sb.insert(0, pkg);
        sb.insert(0, " in ");
        sb.insert(0, grammarName);
        sb.insert(0, "Could not find a generated parser or lexer for ");
        return sb.toString();
    }

}
