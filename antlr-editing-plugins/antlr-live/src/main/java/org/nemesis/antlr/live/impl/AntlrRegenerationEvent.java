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
package org.nemesis.antlr.live.impl;

import java.util.function.Consumer;
import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.common.AntlrConstants;
import org.nemesis.antlr.live.Subscriber;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;

/**
 * Encapsulates the information passed to Subscriber instances into an object which
 * can be passed to the dispatcher for the Subscribable implementation maintained by
 * AntlrGenerationSubscriptionsImpl.
 */
final class AntlrRegenerationEvent implements Consumer<Subscriber> {

    final ANTLRv4Parser.GrammarFileContext tree;
    final Extraction extraction;
    final AntlrGenerationResult res;
    final ParseResultContents populate;
    final Fixes fixes;

    AntlrRegenerationEvent(ANTLRv4Parser.GrammarFileContext tree, Extraction extraction, AntlrGenerationResult res, ParseResultContents populate, Fixes fixes) {
        this.tree = tree;
        this.extraction = extraction;
        this.res = res;
        this.populate = populate;
        this.fixes = fixes;
    }

    @Override
    public void accept(Subscriber t) {
        t.onRebuilt(tree, AntlrConstants.ANTLR_MIME_TYPE, extraction, res, populate, fixes);
    }

}
