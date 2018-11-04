// Generated from ANTLRv4.g4 by ANTLR 4.7.1
package org.nemesis.antlr.v4.netbeans.v8.grammar.code.checking.impl;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ANTLRv4Parser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ANTLRv4Visitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#grammarFile}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrammarFile(ANTLRv4Parser.GrammarFileContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#grammarSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrammarSpec(ANTLRv4Parser.GrammarSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#grammarType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrammarType(ANTLRv4Parser.GrammarTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#analyzerDirectiveSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAnalyzerDirectiveSpec(ANTLRv4Parser.AnalyzerDirectiveSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#optionsSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOptionsSpec(ANTLRv4Parser.OptionsSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#optionSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOptionSpec(ANTLRv4Parser.OptionSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#superClassSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSuperClassSpec(ANTLRv4Parser.SuperClassSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#languageSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLanguageSpec(ANTLRv4Parser.LanguageSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokenVocabSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokenVocabSpec(ANTLRv4Parser.TokenVocabSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokenLabelTypeSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokenLabelTypeSpec(ANTLRv4Parser.TokenLabelTypeSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#delegateGrammars}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelegateGrammars(ANTLRv4Parser.DelegateGrammarsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#delegateGrammarList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelegateGrammarList(ANTLRv4Parser.DelegateGrammarListContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#delegateGrammar}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#grammarIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGrammarIdentifier(ANTLRv4Parser.GrammarIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokensSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokensSpec(ANTLRv4Parser.TokensSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokenList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokenList(ANTLRv4Parser.TokenListContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#channelsSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitChannelsSpec(ANTLRv4Parser.ChannelsSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#idList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdList(ANTLRv4Parser.IdListContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#action}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAction(ANTLRv4Parser.ActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#headerAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHeaderAction(ANTLRv4Parser.HeaderActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#memberAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberAction(ANTLRv4Parser.MemberActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#actionDestination}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActionDestination(ANTLRv4Parser.ActionDestinationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#headerActionBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHeaderActionBlock(ANTLRv4Parser.HeaderActionBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#headerActionContent}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitHeaderActionContent(ANTLRv4Parser.HeaderActionContentContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#packageDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackageDeclaration(ANTLRv4Parser.PackageDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#importDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitImportDeclaration(ANTLRv4Parser.ImportDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#singleTypeImportDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSingleTypeImportDeclaration(ANTLRv4Parser.SingleTypeImportDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#actionBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActionBlock(ANTLRv4Parser.ActionBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#modeSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModeSpec(ANTLRv4Parser.ModeSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#modeDec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitModeDec(ANTLRv4Parser.ModeDecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#ruleSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleSpec(ANTLRv4Parser.RuleSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleDeclaration(ANTLRv4Parser.ParserRuleDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#exceptionGroup}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExceptionGroup(ANTLRv4Parser.ExceptionGroupContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#exceptionHandler}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExceptionHandler(ANTLRv4Parser.ExceptionHandlerContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#finallyClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFinallyClause(ANTLRv4Parser.FinallyClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRulePrequel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRulePrequel(ANTLRv4Parser.ParserRulePrequelContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleReturns}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleReturns(ANTLRv4Parser.ParserRuleReturnsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#throwsSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitThrowsSpec(ANTLRv4Parser.ThrowsSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#localsSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLocalsSpec(ANTLRv4Parser.LocalsSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#ruleAction}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleAction(ANTLRv4Parser.RuleActionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleDefinition(ANTLRv4Parser.ParserRuleDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleLabeledAlternative}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleLabeledAlternative(ANTLRv4Parser.ParserRuleLabeledAlternativeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#altList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAltList(ANTLRv4Parser.AltListContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleAlternative}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleAlternative(ANTLRv4Parser.ParserRuleAlternativeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleElement(ANTLRv4Parser.ParserRuleElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#labeledParserRuleElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLabeledParserRuleElement(ANTLRv4Parser.LabeledParserRuleElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleAtom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleAtom(ANTLRv4Parser.ParserRuleAtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleReference}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleReference(ANTLRv4Parser.ParserRuleReferenceContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#actionBlockArguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitActionBlockArguments(ANTLRv4Parser.ActionBlockArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleSpec}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleSpec(ANTLRv4Parser.LexerRuleSpecContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokenRuleDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokenRuleDeclaration(ANTLRv4Parser.TokenRuleDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#fragmentRuleDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFragmentRuleDeclaration(ANTLRv4Parser.FragmentRuleDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleBlock(ANTLRv4Parser.LexerRuleBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleAlt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleAlt(ANTLRv4Parser.LexerRuleAltContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleElements}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleElements(ANTLRv4Parser.LexerRuleElementsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleElement(ANTLRv4Parser.LexerRuleElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleElementBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleElementBlock(ANTLRv4Parser.LexerRuleElementBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerCommands}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerCommands(ANTLRv4Parser.LexerCommandsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerCommand(ANTLRv4Parser.LexerCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexComChannel}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexComChannel(ANTLRv4Parser.LexComChannelContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexComMode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexComMode(ANTLRv4Parser.LexComModeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexComPushMode}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexComPushMode(ANTLRv4Parser.LexComPushModeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#lexerRuleAtom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLexerRuleAtom(ANTLRv4Parser.LexerRuleAtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#ebnf}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEbnf(ANTLRv4Parser.EbnfContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#ebnfSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEbnfSuffix(ANTLRv4Parser.EbnfSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#notSet}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotSet(ANTLRv4Parser.NotSetContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#blockSet}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockSet(ANTLRv4Parser.BlockSetContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#setElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSetElement(ANTLRv4Parser.SetElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(ANTLRv4Parser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#characterRange}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCharacterRange(ANTLRv4Parser.CharacterRangeContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#terminal}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTerminal(ANTLRv4Parser.TerminalContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#elementOptions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElementOptions(ANTLRv4Parser.ElementOptionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#elementOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElementOption(ANTLRv4Parser.ElementOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#tokenOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTokenOption(ANTLRv4Parser.TokenOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier(ANTLRv4Parser.IdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#ruleElementIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRuleElementIdentifier(ANTLRv4Parser.RuleElementIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#classIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassIdentifier(ANTLRv4Parser.ClassIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#genericClassIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericClassIdentifier(ANTLRv4Parser.GenericClassIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#packageIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPackageIdentifier(ANTLRv4Parser.PackageIdentifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link ANTLRv4Parser#parserRuleIdentifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParserRuleIdentifier(ANTLRv4Parser.ParserRuleIdentifierContext ctx);
}