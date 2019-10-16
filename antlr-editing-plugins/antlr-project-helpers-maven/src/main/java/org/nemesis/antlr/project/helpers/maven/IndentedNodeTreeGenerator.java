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
package org.nemesis.antlr.project.helpers.maven;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Convenience builder-like class for inserting nodes under some tree
 * of nodes in a document, some of which may already exist.
 *
 * @author Tim Boudreau
 */
final class IndentedNodeTreeGenerator {

    private final String name;
    private final List<IndentedNodeTreeGenerator> kids = new LinkedList<>();
    private final IndentedNodeTreeGenerator parent;
    private String textContent;
    private int startingDepth = -1;

    IndentedNodeTreeGenerator(String name, int startingDepth) {
        this(name, null);
        this.startingDepth = startingDepth;
    }

    public IndentedNodeTreeGenerator(String name, IndentedNodeTreeGenerator parent) {
        this.name = name;
        this.parent = parent;
    }

    @Override
    public String toString() {
        if (textContent != null) {
            return "<" + name + ">" + textContent + "</" + name + ">";
        } else {
            return "<" + name + ">";
        }
    }

    IndentedNodeTreeGenerator text(String text) {
        this.textContent = text;
        return this;
    }

    IndentedNodeTreeGenerator then(String name) {
        IndentedNodeTreeGenerator result = new IndentedNodeTreeGenerator(name, this);
        kids.add(result);
        return result;
    }

    IndentedNodeTreeGenerator addSingletonChild(String name, String text) {
        IndentedNodeTreeGenerator result = new IndentedNodeTreeGenerator(name, this);
        result.text(text);
        kids.add(result);
        return this;
    }

    int depth() {
        if (parent == null) {
            return startingDepth;
        }
        int result = parent.depth() + 1;
        return result;
    }

    IndentedNodeTreeGenerator pop() {
        return parent;
    }

    void go(Element target, Document doc, boolean lastChild) {
        boolean prependWhitespace = true;
        Node par = target.getParentNode();
        if (par instanceof Element) {
            NodeList nl = par.getChildNodes();
            if (nl.getLength() > 0) {
                Node n = nl.item(nl.getLength() - 1);
                if (n instanceof Text) {
                    if (n.getTextContent().trim().isEmpty()) {
                        prependWhitespace = false;
                    }
                }
            }
        }
        if (prependWhitespace) {
            target.appendChild(doc.createTextNode(indentString(depth())));
        }
        Element nue = doc.createElement(name);
        if (textContent != null) {
            nue.setTextContent(textContent);
        }
        target.appendChild(nue);
        if (kids.isEmpty()) {
            if (lastChild) {
                target.appendChild(doc.createTextNode(indentString(depth() - 1)));
            }
            return;
        }
        for (Iterator<IndentedNodeTreeGenerator> it = kids.iterator(); it.hasNext();) {
            it.next().go(nue, doc, !it.hasNext());
        }
        target.appendChild(doc.createTextNode(indentString(depth() - (lastChild ? 1 : 0))));
    }

    private static String indentString(int depth) {
        if (depth <= 0) {
            return "";
        }
        char[] c = new char[(depth * 4) + 1];
        Arrays.fill(c, ' ');
        c[0] = '\n';
        return new String(c);
    }

}
