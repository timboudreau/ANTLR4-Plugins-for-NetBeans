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
package org.nemesis.antlr.memory.tool;

import com.mastfrog.util.strings.Escaper;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.antlr.runtime.tree.BufferedTreeNodeStream;
import org.antlr.runtime.tree.Tree;
import org.antlr.runtime.tree.TreeNodeStream;
import org.antlr.v4.tool.ast.ActionAST;
import org.antlr.v4.tool.ast.GrammarRootAST;
import org.antlr.v4.tool.ast.RuleAST;

/**
 * Used to elide Antlr-3 style "returns" clauses, and possibly other things,
 * from the AST of loaded grammars, to avoid referencing unavailable classes
 * when building simply to create a parse tree.
 * <p>
 * It would be useful to elide *all* actions, &#064;members declarations, etc.,
 * in order to avoid running any foreign code, for security reasons; however,
 * some grammars (including our own Markdown grammar) use actions to update
 * variables that are critical to parsing correctly, so this is probably not an
 * option.
 * </p>
 *
 * @author Tim Boudreau
 */
final class GrammarASTElider {

    private final Predicate<Tree> elideTest;
    private boolean ruleContainsElision;
    private static final Logger LOG = Logger.getLogger(GrammarASTElider.class.getName());

    GrammarASTElider(Predicate<Tree> elideTest) {
        this.elideTest = elideTest;
    }

    public GrammarASTElider log() {
        LOG.setLevel(Level.ALL);
        return this;
    }

    public GrammarRootAST elide(GrammarRootAST grammar) {
        if (!needElision(grammar)) {
            return grammar;
        }
        if (LOG.isLoggable(Level.FINEST)) {
            out(grammar, 0);
        }
        return elideRoot(grammar, new BufferedTreeNodeStream(grammar));
    }

    private void out(Tree tree, int depth) {
        char[] c = new char[depth * 2];
        Arrays.fill(c, ' ');
        LOG.log(Level.FINEST, new String(c) + tree.getText() + " " + tree.getClass().getSimpleName());
        for (int i = 0; i < tree.getChildCount(); i++) {
            out(tree.getChild(i), depth + 1);
        }
    }

    private GrammarRootAST elideRoot(GrammarRootAST ast, TreeNodeStream stream) {
        GrammarRootAST copy = (GrammarRootAST) stream.getTreeAdaptor().dupNode(ast);
        elideReturnsClauses(ast, copy, stream, 0);
        assert ast.getChildCount() > 0;
        assert copy.getChild(0) != null : "Null child for 0 in " + textOf(copy);
        return copy;
    }

    private void onEnterRule(Tree ast) {
        ruleContainsElision = false;
    }

    private void elideReturnsClauses(Tree ast, Tree copy, TreeNodeStream stream, int depth) {
        if (ast instanceof RuleAST) {
            onEnterRule(ast);
        }
        char[] c = LOG.isLoggable(Level.FINE) ? new char[depth * 2] : null;
        if (elideTest.test(ast)) {
            ruleContainsElision = true;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, "{0} SKIP SUBTREE: {1}", new Object[]{new String(c), textOf(ast)});
            }
            return;
        }
        for (int i = 0; i < ast.getChildCount(); i++) {
            Tree kid = ast.getChild(i);
            if (ruleContainsElision && kid instanceof ActionAST) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "{0} SKIP SUBTREE: {1}", new Object[]{new String(c), textOf(kid)});
                }
                continue;
            }
            Tree childCopy = (Tree) stream.getTreeAdaptor().dupNode(kid);
            if (LOG.isLoggable(Level.FINER)) {
                System.out.println(new String(c) + "copy " + textOf(childCopy) + " "
                        + Escaper.CONTROL_CHARACTERS.escape(kid.getClass().getSimpleName()));
            }
            assert copy != null : "Got null copy";
            copy.addChild(childCopy);
            elideReturnsClauses(kid, childCopy, stream, depth + 1);
        }
    }

    private boolean needElision(Tree ast) {
        if (elideTest.test(ast)) {
            return true;
        }
        for (int i = 0; i < ast.getChildCount(); i++) {
            Tree child = ast.getChild(i);
            if (needElision(child)) {
                return true;
            }
        }
        return false;
    }

    private String textOf(Tree tree) {
        StringBuilder sb = new StringBuilder();
        textOf(tree, sb);
        return sb.toString();
    }

    private void textOf(Tree tree, StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(Escaper.CONTROL_CHARACTERS.escape(tree.getText())).append(' ');
        for (int i = 0; i < tree.getChildCount(); i++) {
            textOf(tree.getChild(i), sb);
        }
    }
}
