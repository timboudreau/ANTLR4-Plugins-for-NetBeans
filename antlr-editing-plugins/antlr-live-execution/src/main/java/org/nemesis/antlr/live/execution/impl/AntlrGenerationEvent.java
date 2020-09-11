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

package org.nemesis.antlr.live.execution.impl;

import org.nemesis.antlr.ANTLRv4Parser;
import org.nemesis.antlr.memory.AntlrGenerationResult;
import org.nemesis.extraction.Extraction;

/**
 *
 * @author Tim Boudreau
 */
final class AntlrGenerationEvent {

    final ANTLRv4Parser.GrammarFileContext tree;
    final String mimeType;
    final Extraction extraction;
    final AntlrGenerationResult res;

    AntlrGenerationEvent(ANTLRv4Parser.GrammarFileContext tree, String mimeType, Extraction extraction, AntlrGenerationResult res) {
        this.tree = tree;
        this.mimeType = mimeType;
        this.extraction = extraction;
        this.res = res;
    }

}
