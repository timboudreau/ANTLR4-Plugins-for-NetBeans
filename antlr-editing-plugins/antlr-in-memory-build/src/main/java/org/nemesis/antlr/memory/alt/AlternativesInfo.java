/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.memory.alt;

import com.mastfrog.function.TriFunction;
import com.mastfrog.util.collections.IntList;
import java.util.Collections;
import java.util.List;

/**
 * Since our Antlr parser and Antlr's built-in one do not parse in exactly the
 * same sequence, this class allows them to be captured directly from Antlr's
 * syntax tree.  Note that due to the nature of Antlr's LeftRecursiveRuleRewriter,
 * the offsets of left recursive alternatives will be some place within the
 * alternative - 
 *
 * @author Tim Boudreau
 */
public final class AlternativesInfo {

    public final IntList starts;
    public final IntList ends;
    public final List<RuleAlt> altInfos;

    AlternativesInfo(IntList starts, IntList ends, List<RuleAlt> altInfos) {
        this.starts = starts;
        this.ends = ends;
        this.altInfos = Collections.unmodifiableList(altInfos);
        assert starts.size() == ends.size() && ends.size() == altInfos.size();
    }

    public int size() {
        return starts.size();
    }

    public void forEach(AlternativeConsumer c) {
        for (int i = 0; i < starts.size(); i++) {
            c.onAlternative(starts.get(i), ends.get(i), altInfos.get(i));
        }
    }

    public <T> T convert(TriFunction<IntList, IntList, List<RuleAlt>, T>  converter) {
        return converter.apply(starts, ends, altInfos);
    }

    public interface AlternativeConsumer {

        void onAlternative(int start, int end, RuleAlt alt);
    }

}
