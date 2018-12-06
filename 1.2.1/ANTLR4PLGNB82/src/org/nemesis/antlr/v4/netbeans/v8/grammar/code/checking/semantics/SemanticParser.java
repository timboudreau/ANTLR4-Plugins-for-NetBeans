package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.semantics;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4BaseVisitor;
import org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl.ANTLRv4Parser;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticParser {

    public static void extract(ANTLRv4Parser.GrammarFileContext file) {
        Set<String> names = file.accept(new NamesCollector());
        Set<String> imports = file.accept(new ImportFinder());

        Offsets offsets = new Offsets(names.toArray(new String[names.size()]));
        
    }

    private static class ImportFinder extends ANTLRv4BaseVisitor<Set<String>> {

        final Set<String> grammars = new LinkedHashSet<>();
        boolean delegatesVisited;

        @Override
        protected Set<String> defaultResult() {
            return grammars;
        }

        @Override
        protected Set<String> aggregateResult(Set<String> aggregate, Set<String> nextResult) {
            if (nextResult != null) {
                aggregate.addAll(nextResult);
            }
            return aggregate;
        }

        @Override
        public Set<String> visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            return null;
        }

        @Override
        public Set<String> visitDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
            ANTLRv4Parser.GrammarIdentifierContext gic = ctx.grammarIdentifier();
            if (gic != null) {
                ANTLRv4Parser.IdentifierContext ic = gic.identifier();
                if (ic != null) {
                    TerminalNode idTN = ic.ID();
                    if (idTN != null) {
                        Token idToken = idTN.getSymbol();
                        if (idToken != null) {
                            String importedGrammarName = idToken.getText();
                            if (!importedGrammarName.equals("<missing ID>")) {
                                grammars.add(importedGrammarName);
                            }
                        }
                    }
                }

            }
            return super.visitDelegateGrammar(ctx);
        }

        @Override
        public Set<String> visitOptionSpec(ANTLRv4Parser.OptionSpecContext ctx) {
            return super.visitOptionSpec(ctx); //To change body of generated methods, choose Tools | Templates.
        }
    }

    private static class NamesCollector extends ANTLRv4BaseVisitor<Set<String>> {

        private final Set<String> names = new TreeSet<>();

        @Override
        protected Set<String> defaultResult() {
            return names;
        }

        @Override
        public Set<String> visitTerminal(ANTLRv4Parser.TerminalContext ctx) {
            TerminalNode idTN = ctx.TOKEN_ID();
            if (idTN != null) {
                Token idToken = idTN.getSymbol();
                String id = idToken.getText();
                names.add(id);
            }
            return super.visitTerminal(ctx);
        }

        @Override
        public Set<String> visitParserRuleIdentifier(ANTLRv4Parser.ParserRuleIdentifierContext ctx) {
            TerminalNode pridTN = ctx.PARSER_RULE_ID();
            if (pridTN != null) {
                names.add(pridTN.getText());
            }
            return super.visitParserRuleIdentifier(ctx);
        }

        @Override
        public Set<String> visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx) {
            ANTLRv4Parser.ParserRuleIdentifierContext pric = ctx.parserRuleIdentifier();
            if (pric != null) {
                TerminalNode pridTN = pric.PARSER_RULE_ID();
                if (pridTN != null) {
                    Token parserRuleRefIdToken = pridTN.getSymbol();
                    if (parserRuleRefIdToken != null) {
                        names.add(parserRuleRefIdToken.getText());
                    }
                }
            }
            return super.visitParserRuleReference(ctx);
        }

        @Override
        public Set<String> visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx) {
            TerminalNode tid = ctx.TOKEN_ID();
            if (tid != null) {
                names.add(tid.getText());
            }
            return super.visitTokenRuleDeclaration(ctx);
        }

        @Override
        public Set<String> visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx) {
            TerminalNode idTN = ctx.TOKEN_ID();
            if (idTN != null) {
                Token idToken = idTN.getSymbol();
                if (idToken != null) {
                    names.add(idToken.getText());
                }
            }
            return super.visitFragmentRuleDeclaration(ctx);
        }

//        @Override
//        public Set<String> visitSingleTypeImportDeclaration(ANTLRv4Parser.SingleTypeImportDeclarationContext ctx) {
//            return super.visitSingleTypeImportDeclaration(ctx); //To change body of generated methods, choose Tools | Templates.
//        }

        @Override
        public Set<String> visitTokenList(ANTLRv4Parser.TokenListContext ctx) {
            List<TerminalNode> toks = ctx.TOKEN_ID();
            if (toks != null) {
                for (TerminalNode t : toks) {
                    String nm = t.getText();
                    if (!"<missing-id>".equals(nm)) {
                        names.add(nm);
                    }
                }
            }
            return super.visitTokenList(ctx);
        }
    }

    public static final class AntlrSemanticInfo {

    }

}
