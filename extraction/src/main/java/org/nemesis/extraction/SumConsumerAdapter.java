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

import java.util.function.BiPredicate;
import java.util.function.LongConsumer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;

/**
 *
 * @author Tim Boudreau
 */
final class SumConsumerAdapter<RuleType extends RuleNode, KeyType> implements BiPredicate<RuleType, BiPredicate<KeyType, int[]>> {

    private final BiPredicate<RuleType, BiPredicate<KeyType, int[]>> orig;
    private final SummingFunction summer;
    private final LongConsumer sumConsumer;

    static <RuleType extends RuleNode, KeyType> BiPredicate<RuleType, BiPredicate<KeyType, int[]>> wrap(BiPredicate<RuleType, BiPredicate<KeyType, int[]>> extractor, SummingFunction summer, LongConsumer sums) {
        return new SumConsumerAdapter<>(extractor, summer, sums);
    }

    public SumConsumerAdapter(BiPredicate<RuleType, BiPredicate<KeyType, int[]>> orig, SummingFunction summer, LongConsumer sumConsumer) {
        this.orig = orig;
        this.summer = summer;
        this.sumConsumer = sumConsumer;
    }

    @Override
    public boolean test(RuleType t, BiPredicate<KeyType, int[]> u) {
        if (summer != null && t instanceof ParserRuleContext) {
            ParserRuleContext prc = (ParserRuleContext) t;
            BiPredicate<KeyType, int[]> wrapOrig = (kt, bounds) -> {
                if (bounds != null && bounds.length == 2) {
                    boolean result = orig.test(t, u);
                    if (result) {
                        long sum = summer.sum(prc, bounds[0], bounds[1]);
                        sumConsumer.accept(sum);
                    }
                    return result;
                }
                return false;
            };
            return orig.test(t, wrapOrig);
        } else {
            return orig.test(t, u);
        }
    }

}
