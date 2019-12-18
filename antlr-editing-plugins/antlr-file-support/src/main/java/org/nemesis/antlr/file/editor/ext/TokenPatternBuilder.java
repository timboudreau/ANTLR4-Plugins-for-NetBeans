/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.file.editor.ext;

import com.mastfrog.predicates.integer.IntPredicates;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Describes a pattern of tokens to look for.
 *
 * @author Tim Boudreau
 */
public final class TokenPatternBuilder<T> {

    private final Function<TokenPatternBuilder<T>, T> convert;
    private final boolean forward;
    private final IntPredicate[] pattern;
    private boolean startOrEndOk;
    private IntPredicate ignore;
    private IntPredicate stop;

    TokenPatternBuilder(Function<TokenPatternBuilder<T>, T> convert, boolean forward, int[] pattern) {
        this.convert = convert;
        this.forward = forward;
        this.pattern = new IntPredicate[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            this.pattern[i] = IntPredicates.matching(pattern[i]);
        }
    }

    TokenPatternBuilder(Function<TokenPatternBuilder<T>, T> convert, boolean forward, IntPredicate[] pattern) {
        this.convert = convert;
        this.forward = forward;
        this.pattern = pattern;
    }

    TokenPattern toTokenPattern() {
        return new TokenPattern(forward, pattern, stop, startOrEndOk, ignore);
    }

    public TokenPatternBuilder<T> orDocumentStartOrEnd() {
        startOrEndOk = true;
        return this;
    }

    public TokenPatternBuilder<T> ignoring(IntPredicate ign) {
        if (ignore != null) {
            ignore = ignore.or(notNull("ign", ign));
        } else {
            ignore = notNull("ign", ign);
        }
        return this;
    }

    public TokenPatternBuilder<T> ignoring(int... items) {
        if (ignore != null) {
            ignore = ignore.or(IntPredicates.anyOf(items));
        } else {
            ignore = IntPredicates.anyOf(items);
        }
        return this;
    }

    public T stoppingOn(int... items) {
        stop = IntPredicates.anyOf(items);
        return convert.apply(this);
    }

    public T stoppingOn(IntPredicate pred) {
        stop = pred;
        return convert.apply(this);
    }
}
