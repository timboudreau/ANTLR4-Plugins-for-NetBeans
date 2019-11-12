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
package org.nemesis.extraction;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.data.Hashable;

/**
 * A function which computes a checksum or hash of a series of tokens, for
 * testing for duplicate regions.  Note that the default implementation returns
 * Long.MIN_VALUE as its null value.
 *
 * @see
 * org.nemesis.extraction.NamedRegionExtractorBuilder.summingWith(org.nemesis.extraction.SummingFunction)
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface SummingFunction extends Hashable {

    long updateSum(long previousValue, int offset, Token token);

    /**
     * Filter those tokens summed to only those on a particular channel, in
     * order to, for example, make whitespace and comments not affect the sum.
     *
     * @param channel A channel number (0 is the main channel in Antlr)
     * @return A function
     */
    default SummingFunction filterChannel(int channel) {
        return new FilterChannelSummingFunction(this, channel);
    }

    /**
     * Create an instance default token summing function which uses a default
     * large (by Antlr grammar standards) max number of tokens.
     *
     * @return
     */
    static SummingFunction createDefault() {
        return new DefaultSummingFunction();
    }

    /**
     * Create an instance of the default summing function specialized for the
     * passed maximum number of tokens. If the passed number is less than the
     * number of token types in the vocabulary, collisions are more likely.
     *
     * @param maxToken The max token id in a vocabulary.
     * @return A summing function
     */
    static SummingFunction createDefault(int maxToken) {
        return new DefaultSummingFunction(maxToken + 1);
    }

    /**
     * Create an instance of the default summing function specialized for the
     * passed vocabulary. If the passed number is less than the number of token
     * types in the vocabulary, collisions are more likely.
     *
     * @param maxToken The max token id in a vocabulary.
     * @return A summing function
     */
    static SummingFunction forVocabulary(Vocabulary vocab) {
        return createDefault(vocab.getMaxTokenType());
    }

    /**
     * Compute the sum of all tokens inside a parser rule context.
     *
     * @param ctx The context
     * @return A sum
     */
    default long sum(ParserRuleContext ctx) {
        return new SummingVisitor(this).sum(ctx);
    }

    /**
     * Compute the sum of all tokens inside a parser rule context, constraining
     * the result to only sum those tokens whose start index is greater than or
     * equal to the passed start constraint, and whose stop index is less than
     * the passed end constraint, where endConstraint is greater than
     * startConstraint.
     *
     * @param ctx The context
     * @param startConstraint the start char position to use to filter tokens
     * @param endConstraint the end (exclusive) char position to filter tokens
     * @return A sum
     */
    default long sum(ParserRuleContext ctx, int startConstraint, int endConstraint) {
        return new SummingVisitor(this, startConstraint, endConstraint).sum(ctx);
    }

    /**
     * XOR the output of this and another summing function.
     *
     * @param function Another summing function
     * @return A summing functoin
     */
    default SummingFunction xor(SummingFunction function) {
        return new XorSummingFunction(this, function);
    }
}
