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
package org.nemesis.antlr.live.preview;

import com.mastfrog.swing.cell.TextCellLabel;
import java.awt.Color;
import javax.swing.JComponent;
import javax.swing.JList;
import org.nemesis.antlr.live.parsing.extract.AntlrProxies;

/**
 * Manages converting a particular token into an (optionally HTML-ized)
 * breadcrumb string showing the path through the grammar rules to the grammar
 * root from a particular token; also maintains state in the form of a list of
 * distances from each rule to the target (which is the current caret position),
 * allowing these elements to be highlighted.
 *
 * @author Tim Boudreau
 */
public interface RulePathStringifier {

    public void tokenRulePathString(AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, StringBuilder into, boolean html, JComponent colorSource);

    public void configureTextCell(TextCellLabel lbl, AntlrProxies.ParseTreeProxy prx, AntlrProxies.ProxyToken tok, JComponent colorSource);

    public Color listBackgroundColorFor(String ruleName, JList<?> list);
}
