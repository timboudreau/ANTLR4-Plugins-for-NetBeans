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
package org.nemesis.antlr.live.parsing;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.nio.file.Path;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.nemesis.antlr.live.parsing.extract.ParserExtractor;
import org.openide.util.NbPreferences;

/**
 * Features that can be explicitly enabled for extraction runs - right now, they
 * are IDE-wide but designed so that they could be made file-specific. Currently
 * the only thing supported is prediction mode. Flags are encoded as an int to
 * avoid leaking types in either direction through the class loader boundary.
 *
 * @author Tim Boudreau
 */
public final class EmbeddedParserFeatures {

    private static final EmbeddedParserFeatures INSTANCE = new EmbeddedParserFeatures();
    private int flags = 1;

    EmbeddedParserFeatures() { // pkg private for tests
        flags = NbPreferences.forModule(EmbeddedParserFeatures.class)
                .getInt("emb-features",
                        ParserExtractor.flagsforPredictionMode(PredictionMode.LL));
    }

    public static EmbeddedParserFeatures getInstance(Path grammarFile) {
        return INSTANCE;
    }

    public PredictionMode currentPredictionMode() {
        return ParserExtractor.predictionModeForFlags(flags);
    }

    /**
     * Set the ANTLR prediction mode to use. SLL is faster but will produce
     * syntax errors for some grammars; LL is the default;
     * LL_EXACT_AMBIG_DETECTION is slower still but needed for ambiguity
     * highlighting.
     *
     * @param predictionMode The prediction mode
     * @return this
     */
    public EmbeddedParserFeatures setPredictionMode(PredictionMode predictionMode) {
        int result = ParserExtractor.flagsforPredictionMode(notNull("predictionMode",
                predictionMode));
        if (flags != result) {
            flags = result;
            if (this == INSTANCE) {
                NbPreferences.forModule(EmbeddedParserFeatures.class)
                        .putInt("emb-features", result);
            }
        }
        return this;
    }

    public int currentFlags() {
        return flags;
    }
}
