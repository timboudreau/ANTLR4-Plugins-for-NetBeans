package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.ArrayList;
import java.util.List;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
class RuleNameFinder extends ANTLRv4BaseVisitor<RuleNameFinder> {

    private final List<String> names = new ArrayList<>(30);

    @Override
    public RuleNameFinder visitParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext ctx) {
        if (ctx.parserRuleIdentifier() != null) {
            names.add(ctx.parserRuleIdentifier().getText());
        }
        super.visitParserRuleDeclaration(ctx);
        return this;
    }

    public String[] names() {
        return names.toArray(new String[names.size()]);
    }

}
