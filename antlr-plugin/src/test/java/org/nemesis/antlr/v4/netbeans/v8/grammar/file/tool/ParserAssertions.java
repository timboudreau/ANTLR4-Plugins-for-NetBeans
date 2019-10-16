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
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool;

import org.junit.Assert;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies;

/**
 *
 * @author Tim Boudreau
 */
public class ParserAssertions {

    private final AntlrProxies.ParseTreeProxy prox;
    private int tokenCursor = -1;

    public ParserAssertions(AntlrProxies.ParseTreeProxy prox) {
        this.prox = prox;
    }

    public ParserAssertions assertTokenText(int ix, String text) {
        Assert.assertTrue(ix < prox.tokenCount());
        Assert.assertTrue(ix >= 0);
        AntlrProxies.ProxyToken tk = prox.tokens().get(ix);
        Assert.assertEquals(text, tk.getText());
        return this;
    }

    public ParserAssertions assertTokenRuleName(int ix, String typeName) {
        Assert.assertTrue(ix < prox.tokenCount());
        Assert.assertTrue(ix >= 0);
        AntlrProxies.ProxyToken tk = prox.tokens().get(ix);
        boolean found = false;
        StringBuilder sb = new StringBuilder();
        for (AntlrProxies.ParseTreeElement el : tk.referencedBy()) {
            String name = el.name();
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
            if (typeName.equals(name)) {
                found = true;
                break;
            }
        }
        //            System.out.println("RULES: " + sb);
        if (!found) {
            Assert.fail("Rule '" + typeName + "' does not cover " + tk + " - found " + sb);
        }
        return this;
    }

    public ParserAssertions assertTokenType(int ix, String typeName) {
        Assert.assertTrue(ix < prox.tokenCount());
        Assert.assertTrue(ix >= 0);
        AntlrProxies.ProxyToken tk = prox.tokens().get(ix);
        AntlrProxies.ProxyTokenType type = prox.tokenTypeForInt(tk.getType());
        Assert.assertEquals(tk + " - " + type, typeName, type.name());
        return this;
    }

    public ParserAssertions assertNextNonWhitespace(String text, String typeName, String... ruleName) {
        while (++tokenCursor < prox.tokenCount()) {
            AntlrProxies.ProxyToken tk = prox.tokens().get(tokenCursor);
            if (!tk.getText().trim().isEmpty()) {
                if (text != null) {
                    assertTokenText(tokenCursor, text);
                }
                if (typeName != null) {
                    assertTokenType(tokenCursor, typeName);
                }
                for (String rule : ruleName) {
                    assertTokenRuleName(tokenCursor, rule);
                }
                break;
            }
        }
        return this;
    }

}
