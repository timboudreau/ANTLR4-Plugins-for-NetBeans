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
package com.mastfrog.protoc.antlr;

import java.util.function.BooleanSupplier;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.nemesis.antlr.spi.language.AntlrParseResult;
import org.nemesis.antlr.spi.language.NbParserHelper;
import org.nemesis.antlr.spi.language.ParseResultContents;
import org.nemesis.antlr.spi.language.SyntaxError;
import org.nemesis.antlr.spi.language.fix.Fixes;
import org.nemesis.extraction.Extraction;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.spi.editor.hints.ErrorDescription;

/**
 *
 * @author Tim Boudreau
 */
public class ProtocHelper extends NbParserHelper<Protobuf3Parser, Protobuf3Lexer, AntlrParseResult, Protobuf3Parser.ProtoContext> {

    @Override
    protected ErrorDescription convertError(Snapshot snapshot, SyntaxError error) {
        return super.convertError(snapshot, error); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void onCreateAntlrParser(Protobuf3Lexer lexer, Protobuf3Parser parser, Snapshot snapshot) throws Exception {
        super.onCreateAntlrParser(lexer, parser, snapshot); //To change body of generated methods, choose Tools | Templates.
    }


    @Override
    protected boolean onErrorNode(ErrorNode nd, ParseResultContents populate) {
        return super.onErrorNode(nd, populate); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void onParseCompleted(Protobuf3Parser.ProtoContext tree, Extraction extraction,
            ParseResultContents populate, Fixes fixes, BooleanSupplier cancelled) throws Exception {
        super.onParseCompleted(tree, extraction, populate, fixes, cancelled); //To change body of generated methods, choose Tools | Templates.
    }
}
