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


package org.nemesis.antlrformatting.api;

import org.antlr.v4.runtime.Vocabulary;

/**
 * Factory for Criterion instances which uses a shared instance
 * of Vocabulary for the convenience of not needing to pass it
 * to every invocation of static methods on Criterion.
 *
 * @author Tim Boudreau
 */
public final class Criteria {

    private final Vocabulary vocab;

    private Criteria(Vocabulary vocab) {
        this.vocab = vocab;
    }

    public static Criteria forVocabulary(Vocabulary vocab) {
        assert vocab != null : "vocab null";
        return new Criteria(vocab);
    }

    public Criterion matching(int val) {
        return Criterion.matching(vocab, val);
    }

    public Criterion notMatching(int val) {
        return Criterion.notMatching(vocab, val);
    }

    public Criterion anyOf(int... ints) {
        return Criterion.anyOf(vocab, ints);
    }

    public Criterion noneOf(int... ints) {
        return Criterion.noneOf(vocab, ints);
    }
}
