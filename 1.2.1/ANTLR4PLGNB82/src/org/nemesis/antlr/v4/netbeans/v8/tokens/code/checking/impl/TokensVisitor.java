// Generated from Tokens.g4 by ANTLR 4.7.1
package org.nemesis.antlr.v4.netbeans.v8.tokens.code.checking.impl;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link TokensParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface TokensVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link TokensParser#token_declarations}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitToken_declarations(TokensParser.Token_declarationsContext ctx);
	/**
	 * Visit a parse tree produced by {@link TokensParser#line}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLine(TokensParser.LineContext ctx);
	/**
	 * Visit a parse tree produced by {@link TokensParser#token_declaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitToken_declaration(TokensParser.Token_declarationContext ctx);
}