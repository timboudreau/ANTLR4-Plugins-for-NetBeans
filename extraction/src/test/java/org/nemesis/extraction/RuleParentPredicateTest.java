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
import org.antlr.v4.runtime.tree.RuleNode;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class RuleParentPredicateTest {

    @Test
    public void testSomeMethod() {

        String msg = RuleParentPredicate.<String>builder(pred -> {
            return pred.toString();
        }).withParentType(RuleNode.class)
                .withParentType(ParserRuleContext.class)
                .thatHasOnlyOneChild()
                .skippingParent()
                .withParentType(RuleNode.class)
                .thatIsTop()
                .build();

        System.out.println(msg);
    }

}
