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
package org.nemesis.antlr.v4.netbeans.v8.tokens.code.summary;

import java.nio.file.Path;
import java.util.Optional;

import javax.swing.text.Document;

import org.antlr.v4.runtime.Token;

import org.antlr.v4.runtime.tree.TerminalNode;

import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl.TokensBaseListener;
import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl.TokensParser.Token_declarationContext;
import org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl.TokensParser.Token_declarationsContext;

/**
 *
 * @author Frédéric Yvon Vinet
 */
public class Collector extends TokensBaseListener {
    protected final Document      doc;
    protected final Optional<Path>          sourceFilePath;
    protected       TokensSummary summary;
    
    public Collector(Document doc, Optional<Path> sourceFilePath) {
//        System.out.println("Collector:Collector(Document, Path) : begin");
        this.doc = doc;
        this.sourceFilePath = sourceFilePath;
        this.summary        = new TokensSummary(sourceFilePath);
     // If doc had a previous summary then it is garbage collected
        this.doc.putProperty(TokensSummary.class, summary);
//        System.out.println("Collector:Collector(Document, Path) : end");
    }
    
    
    @Override
    public void exitToken_declarations(Token_declarationsContext ctx) {
        summary.save();
    }
    
    
    @Override
    public void exitToken_declaration(Token_declarationContext ctx) {
        TerminalNode tokenIdTN = ctx.TOKEN_ID();
        TerminalNode tokenLiteralTN = ctx.TOKEN_LITERAL();
        if (tokenIdTN != null) {
            Token idToken = tokenIdTN.getSymbol();
            if (idToken != null) {
                String tokenId = idToken.getText();
                if (!tokenId.equals("<missing TOKEN_ID>")) {
                    this.summary.addTokenId(tokenId);
                    int offset = idToken.getStartIndex();
                    this.summary.putTokenIdOffset(tokenId, offset);
                }
            }
        } else if (tokenLiteralTN != null) {
            Token literalToken = tokenLiteralTN.getSymbol();
            if (literalToken != null) {
                String tokenLiteral = literalToken.getText();
                if (!tokenLiteral.equals("<missing TOKEN_LITERAL>")) {
                    this.summary.addTokenLiteral(tokenLiteral);
                    int offset = literalToken.getStartIndex();
                    this.summary.putTokenLiteralOffset(tokenLiteral, offset);
                }
            }
        }
    }
}
