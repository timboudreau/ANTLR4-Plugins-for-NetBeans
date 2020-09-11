/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2015 Terence Parr
 *  Copyright (c) 2012-2015 Sam Harwell
 *  Copyright (c) 2015-2016 Gerald Rosenberg
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 *	A grammar for ANTLR v4 implemented using v4 syntax
 *
 *	Modified 2015.06.16 gbr
 *	-- update for compatibility with Antlr v4.5
 *
 *      Modified 2017.01 by FYV:
 *      -- there is a version for syntax coloring that emits all tokens
 *         and a version for syntax checking (present one) that:
 *         - does not emit indent characters (space, tab, carriage rturn, line
 *           feed) and comments except document comments because they cannot be
 *           placed anywhere in syntax,
 *         - emits tokens for header action block content for Java package
 *           statement and Java import statements.
 **/
lexer grammar ANTLRv4Lexer;

import LexBasic;

tokens
   { TOKEN_ID, PARSER_RULE_ID, ID, LEXER_CHAR_SET }


@lexer::members {
protected int initialStackedModeNumber = -1; // -1 means undefined value

public int getInitialStackedModeNumber() {
    return initialStackedModeNumber;
}

public void setInitialStackedModeNumber(int initialStackedModeNumber) {
    this.initialStackedModeNumber = initialStackedModeNumber;
}

protected boolean isThereOperatorNext() {
    boolean answer = false;
    char nextChar = (char) 0;
    boolean found = false;
    boolean end = false;
    int i = 1;
 // We loop till we find a non space character
    while (!found && !end) {
        nextChar = (char) _input.LA(i);
        if (nextChar == (char) IntStream.EOF)
            end = true;
        else {
            if ((nextChar != ' ')  &&
                (nextChar != '\t') &&
                (nextChar != '\n') &&
                (nextChar != '\r')    ) {
//                System.out.println("NBANTLRv4Lexer.nextToken() : next char=" + nextChar + " int value=" + (int) nextChar);
                found = true;
            } else
                i++;
        }
    }
    if (!end) {
        char nextChar2 = (char) _input.LA(++i);
        StringBuilder tokenText = new StringBuilder().append(nextChar);
        tokenText.append(nextChar2);
        if ((nextChar == '=')                  ||
            (tokenText.toString().equals("+="))  )
            answer = true;
    }
    return answer;
}
}

// ======================================================
// Lexer specification
//
// -------------------------
// Comments

DOC_COMMENT :
    DocComment
    ;

// For syntax checking purpose we skip block comments
BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

// For syntax checking purpose we skip line comments
LINE_COMMENT :
    LineComment -> channel(1)
    ;

// -------------------------
// Integer
//
INT :
    DecimalNumeral
    ;

// -------------------------
// Literal string
//
// ANTLR makes no distinction between a single character literal and a
// multi-character string. All literals are single quote delimited and
// may contain unicode escape sequences of the form \uxxxx, where x
// is a valid hexadecimal number (per Unicode standard).

STRING_LITERAL
   : SQuoteLiteral
   ;

UNTERMINATED_STRING_LITERAL
   : USQuoteLiteral
   ;

/*******************************************
 * rule for passing in BracketContent mode *
 *******************************************/
BEGIN_ARGUMENT :
    LBrack
    {
//     System.out.println("'[' detected as a begin of argument");
     initialStackedModeNumber = _modeStack.size();
     pushMode(Argument);
    }
    ;

// -------------------------
// Actions

BEGIN_ACTION :
    LBrace
    {
     initialStackedModeNumber = _modeStack.size();
     pushMode(Action);
    }
    ;

// -------------------------
// Keywords
//
// Keywords may not be used as labels for rules or in any other context where
// they would be ambiguous with the keyword vs some other identifier.  OPTIONS,
// TOKENS, & CHANNELS blocks are handled idiomatically in dedicated lexical modes.

OPTIONS :
    'options' -> pushMode (Options)
    ;

TOKENS
   : 'tokens' -> pushMode (Tokens)
   ;

CHANNELS
   : 'channels' -> pushMode (Channels)
   ;

IMPORT :
    'import' -> pushMode(Import)
    ;

FRAGMENT :
    'fragment' -> pushMode(FragmentDeclaration)
    ;

LEXER :
    'lexer'
    ;

PARSER :
    'parser'
    ;

GRAMMAR :
    'grammar' -> pushMode(Identifier)
    ;

RETURNS
   : 'returns'
   ;

LOCALS
   : 'locals'
   ;

INIT
   : 'init'
   ;

AFTER
   : 'after'
   ;

THROWS
   : 'throws'
   ;

CATCH :
    'catch'
    {
//     System.out.println("'catch' keyword detected");
    }
    ;

FINALLY :
    'finally'
    {
//     System.out.println("'finally' keyword detected");
    }
    ;

MODE :
    'mode'
    {
     pushMode(Identifier);
//     System.out.println("A MODE token has been found");
    }
    ;

// -------------------------
// Punctuation

COLON :
    Colon
    ;

COLONCOLON
   : DColon
   ;

COMMA
   : Comma
   ;

SEMI :
    Semi
    ;

LPAREN
   : LParen
   ;

RPAREN
   : RParen
   ;

LBRACE
   : LBrace
   ;

RBRACE
   : RBrace
   ;

RARROW :
    RArrow
    ;

LT
   : Lt
   ;

ASSOC :
    'assoc'
    ;

RIGHT :
    'right'
    ;

LEFT :
    'left'
    ;

FAIL :
    'fail'
    ;

GT
   : Gt
   ;

ASSIGN
   : Equal
   ;

QUESTION
   : Question
   ;

STAR
   : Star
   ;

PLUS_ASSIGN
   : PlusAssign
   ;

PLUS
   : Plus
   ;

OR
   : Pipe
   ;

DOLLAR
   : Dollar
   ;

RANGE
   : Range
   ;

DOT
   : Dot
   ;

AT
   : At
   ;

HEADER :
    'header' -> pushMode(HeaderPrelude)
    ;

MEMBERS :
    'members'
    ;

SHARP
   : Sharp
   ;

NOT
   : Tilde
   ;

// -------------------------
// token or parser rule identifiers - allows unicode rule/token names
// Identifiers for:
// - grammar, imported grammars, modes, fragments are managed by rule ID in mode
//   Identifier,
// - options (superClass, tokenVocab, TokenLabelType) are managed by rule OPT_ID
//   in Options mode,
// - channels in channels statement are managed by CHN_ID rule in Channels mode,
// - token references in lexer command (type statement) in TYPE_TOKEN_ID
//   rule in TypeLexerCommand mode,
// - mode references, channel reference in lexer commands (resp. mode / pushMode,
//   channel statements) in LEXER_COMMAND_ID rule in LexerCommands mode,
//   in LexerCommands mode.
// - token declarations or parser rule declaration are managed here in 
//   TOKEN_OR_PARSER_RULE_ID rule,
TOKEN_OR_PARSER_RULE_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if (Character.isUpperCase(idValue.charAt(0))) {
//         System.out.println("TOKEN_OR_PARSER_RULE_ID: token declaration identifier");
         setType(TOKEN_ID);
         pushMode(TokenDeclaration);
     } else {
//         System.out.println("TOKEN_OR_PARSER_RULE_ID: parser rule identifier");
         setType(PARSER_RULE_ID);
         pushMode(ParserRuleDeclaration);
     }
//     System.out.println("TOKEN_OR_PARSER_RULE_ID: value=" + idValue);
//     System.out.println("TOKEN_OR_PARSER_RULE_ID: returned token=" + _SYMBOLIC_NAMES[_type]);
    }
    ;

// -------------------------
// Whitespace
WS :
    Ws+ -> channel(2)
    ;

// -------------------------
// Illegal Characters
//
// This is an illegal character trap which is always the last rule in the
// lexer specification. It matches a single character of any value and being
// the last rule in the file will match when no other rule knows what to do
// about the character. It is reported as an error but is not passed on to the
// parser. This means that the parser to deal with the grammar file anyway
// but we will not try to analyse or code generate from a file with lexical
// errors.
//
// Comment this rule out to allow the error to be propagated to the parser
ERRCHAR :
    .
    ;



// ======================================================
// Lexer modes
// -------------------------
// Arguments
mode Argument;
// E.g., [int x, List<String> a[]]
NESTED_ARGUMENT
   : LBrack -> more, pushMode (Argument)
   ;

END_ARGUMENT :
    RBrack
    {
     popMode();
//     System.out.println("END_ARGUMENT: _modeStack.size()=" + _modeStack.size());
//     System.out.println("END_ARGUMENT: initialStackedModeNumber=" + initialStackedModeNumber);
     if (_modeStack.size() > initialStackedModeNumber) {
         more();
//         System.out.println("END_ARGUMENT : ']' detected but considered as undefined");
     } else {
//         System.out.println("END_ARGUMENT : ']' is an end of argument");
         setType(END_ARGUMENT);
         initialStackedModeNumber = -1;
     }
    }
    ;
      
// added this to return non-EOF token type here. EOF does something weird
UNTERMINATED_ARGUMENT
   : EOF -> popMode
   ;

ARGUMENT_CONTENT :
    ~[[\]]+
    {
//     System.out.println("ARGUMENT_CONTENT : argument content detected");
//     System.out.println("text=" + getText());
    }
    ;



// -------------------------
// Actions
//
// Many language targets use {} as block delimiters and so we
// must recursively match {} delimited blocks to balance the
// braces. Additionally, we must make some assumptions about
// literal string representation in the target language. We assume
// that they are delimited by ' or " and so consume these
// in their own alts so as not to inadvertantly match {}.
mode HeaderPrelude;
// We must manage comments because if left brace is in comments it must be
// ignored
HEADER_P_LINE_COMMENT
   : LineComment -> channel(1)
   ;

HEADER_P_BLOCK_COMMENT
   : BlockComment -> channel(1)
   ;

HEADER_P_START :
    LBrace
    {
     initialStackedModeNumber = _modeStack.size(); // normally equal to 1
     setType(BEGIN_ACTION);
     mode(HeaderAction); // mode and not pushMode
    };

HEADER_P_WS :
    Ws + -> channel(2)
    ;

HEADER_P_UNTERMINATED :
    EOF -> popMode
    ;

HEADER_P_ERRCHAR :
    . -> type(ERRCHAR)
    ;



mode HeaderAction;
// We must manage comments because if some keywords or left brace are in
//  comments they must be ignored
HEADER_LINE_COMMENT :
    LineComment -> channel(1)
    ;

// Here document comment is a special case of block comment
HEADER_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

HEADER_NESTED :
    LBrace -> channel(3), pushMode(HeaderAction)
    ;

HEADER_PACKAGE :
    'package' // here it is Java keyword
    -> pushMode(HeaderPackage)
/*
    {
     System.out.println("HEADER_PACKAGE : package statement detected");
     pushMode(HeaderPackage);
    }
*/
    ;

HEADER_IMPORT :
    'import' // here it is Java keyword
    -> pushMode(HeaderImport)
/*
    {
     System.out.println("HEADER_IMPORT : import statement detected");
     pushMode(HeaderImport);
    }
*/
    ;

HEADER_WS
   : Ws + -> channel(2)
   ;

HEADER_END :
    RBrace
    {
     if (_modeStack.size() > initialStackedModeNumber) {
        skip();
     } else {
        setType(END_ACTION);
        initialStackedModeNumber = -1;
        popMode();
     }
//     System.out.println("HEADER_END : type=" + VOCABULARY.getSymbolicName(_type));
    }
    ;

UNTERMINATED_HEADER :
    EOF -> popMode
    ;

HEADER_CONTENT
   : . -> channel(3)
   ;



/*
 * package Java statement:
 * We lex package identifier all other content is considered as ACTION_CONTENT
 * or skipped (WS, comments)
 */
mode HeaderPackage;
HDR_PCKG_LINE_COMMENT :
    LineComment -> channel(1)
    ;

HDR_PCKG_WS :
    Ws + -> channel(2)
    ;

HDR_PCKG_DOT :
    Dot -> type(DOT);

HDR_PCKG_SEMI :
    Semi -> type(SEMI), popMode;

HDR_PCKG_ID :
    NameStartChar NameChar* -> type(ID)
    ;

HDR_PCKG_UNTERMINATED :
    EOF -> popMode, popMode
    ;

HDR_PCKG_CONTENT :
    . -> channel(3)
    ;



/*
 * Import Java statement:
 * We lex package identifier all other content is considered as ACTION_CONTENT
 */
mode HeaderImport;
HDR_IMPRT_LINE_COMMENT :
    LineComment -> channel(1)
    ;

HDR_IMPRT_WS :
    Ws + -> channel(2)
    ;

HDR_IMPRT_STATIC :
    'static'; // here it is Java keyword

HDR_IMPRT_DOT :
    Dot -> type(DOT);

HDR_IMPRT_STAR :
    Star -> type(STAR);

HDR_IMPRT_SEMI :
    Semi -> type(SEMI), popMode;

HDR_IMPRT_ID :
    NameStartChar NameChar* -> type(ID)
    ;

HDR_IMPRT_UNTERMINATED :
    EOF -> popMode, popMode
    ;

HDR_IMPRT_CONTENT :
    . -> channel(3)
    ;

mode Action;

// PENDING: nesting actions causes all kinds of weirdness if the
// action code contains the string '{' without a corresponding }.  It
// would be better to deal with this much more simply.

ACTION_CONTENT :
    SQuote LBrace SQuote
    | SQuote RBrace SQuote
//    -> type(ACTION_CONTENT)
    ;

SUB_ACTION_CONTENT :
    ~[{}]+? -> type(ACTION_CONTENT), more
    ;

NESTED_ACTION
   : LBrace -> more , pushMode(Action)
   ;

END_ACTION :
    RBrace
    {
     int mode = popMode();
//     System.out.println("END_ACTION: _modeStack.size()=" + _modeStack.size());
//     System.out.println("END_ACTION: initialStackedModeNumber=" + initialStackedModeNumber);
     if (_modeStack.size() > initialStackedModeNumber) {
        more();
     } else {
        setType(END_ACTION);
        initialStackedModeNumber = -1;
     }
    }
    ;

UNTERMINATED_ACTION :
    EOF -> popMode
    ;



// -------------------------
mode Options;

OPT_WS
   : Ws + -> channel(2)
   ;

OPT_DOC_COMMENT
   : DocComment -> type (DOC_COMMENT)
   ;

OPT_BLOCK_COMMENT
   : BlockComment -> channel(1)
   ;

OPT_LINE_COMMENT
   : LineComment -> channel(1)
   ;

OPT_LBRACE :
    LBrace -> type(LBRACE)
    ;

OPT_RBRACE :
    RBrace -> type(RBRACE), popMode
    ;

SUPER_CLASS :
    'superClass'
    ;

LANGUAGE :
    'language'
    ;

TOKEN_VOCAB :
    'tokenVocab'
    ;

TOKEN_LABEL_TYPE :
    'TokenLabelType'
    ;

OPT_DOT :
    Dot -> type (DOT)
    ;

OPT_ASSIGN :
    Equal -> type (ASSIGN)
    ;

OPT_STRING_LITERAL :
    SQuoteLiteral -> type(STRING_LITERAL)
    ;

OPT_INT :
    Int -> type(INT)
    ;

OPT_STAR :
    Star -> type(STAR)
    ;

OPT_SEMI :
    Semi -> type(SEMI)
    ;

OPT_ID :
    NameStartChar NameChar* -> type(ID)
    ;

OPT_UNTERMINATED :
    EOF -> popMode
    ;

OPT_ERRCHAR :
    . -> type(ERRCHAR)
    ;



// -------------------------
mode Tokens;

TOK_WS
   : Ws + -> channel(2)
   ;

// For coloring purpose, we do not skip document comments encountered in
// Tokens mode
TOK_DOC_COMMENT
   : DocComment -> type(DOC_COMMENT)
   ;

// For coloring purpose, we do not skip block comments encountered in
// Tokens mode
TOK_BLOCK_COMMENT
   : BlockComment -> channel(1)
   ;

// For coloring purpose, we do not skip line comments encountered in
// Tokens mode
TOK_LINE_COMMENT
   : LineComment -> channel(1)
   ;

TOK_LBRACE
   : LBrace -> type(LBRACE)
   ;

TOK_RBRACE
   : RBrace -> type(RBRACE), popMode
   ;

TOK_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if ( Character.isUpperCase(idValue.charAt(0)) )
         setType(TOKEN_ID);
     else
         setType(ID); // in this case it will raise a syntactic error
//     System.out.println("TOK_ID: returned token type=" + _SYMBOLIC_NAMES[_type]);
    }
    ;

TOK_DOT
   : Dot -> type(DOT)
   ;

TOK_COMMA
   : Comma -> type(COMMA)
   ;

TOK_UNTERMINATED :
    EOF -> popMode
    ;

TOK_ERRCHAR :
    . -> type(ERRCHAR)
    ;


// -------------------------
mode Channels;

CHN_WS :
    Ws + -> channel(2)
    ;

CHN_DOC_COMMENT
   : DocComment -> type (DOC_COMMENT)
   ;

CHN_BLOCK_COMMENT
   : BlockComment -> channel(1)
   ;

CHN_LINE_COMMENT
   : LineComment -> channel(1)
   ;

CHN_LBRACE
   : LBrace -> type (LBRACE)
   ;

CHN_RBRACE
   : RBrace -> type (RBRACE) , popMode
   ;

// FYV : a channel identifier is always an ID whatever the first letter is
CHN_ID :
    NameStartChar NameChar* -> type(ID)
    ;

CHN_DOT
   : Dot -> type (DOT)
   ;

CHN_COMMA
   : Comma -> type (COMMA)
   ;

CHN_UNTERMINATED :
    EOF -> popMode
    ;

CHN_ERRCHAR :
    . -> type(ERRCHAR)
    ;



/***************
 * mode Import *
 ***************/
mode Import;

IMPORT_WS :
    Ws+ -> channel(2)
    ;

IMPORT_LINE_COMMENT :
    LineComment -> channel(1)
    ;

IMPORT_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

IMPORT_ID :
    NameStartChar NameChar* -> type(ID)
    ;

IMPORT_ASSIGN :
    Equal -> type(ASSIGN)
    ;

IMPORT_COMMA :
    Comma -> type(COMMA)
    ;

IMPORT_SEMI :
    Semi -> type(SEMI), popMode
    ;

IMPORT_UNTERMINATED :
    EOF -> popMode
    ;

IMPORT_ERRCHAR :
    . -> type(ERRCHAR)
    ;


/*******************
 * mode Identifier *
 *******************/
mode Identifier;

ID_WS :
    Ws+ -> channel(2)
    ;

ID_LINE_COMMENT :
    LineComment -> channel(1)
    ;

ID_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

ID_ID :
    NameStartChar NameChar* -> type(ID), popMode
    ;

ID_UNTERMINATED :
    EOF -> popMode
    ;

ID_ERRCHAR :
    . -> type(ERRCHAR), popMode
    ;



/**************************
 * token declaration mode *
 **************************/
mode TokenDeclaration;

TOKDEC_LINE_COMMENT :
    LineComment -> type(LINE_COMMENT)
    ;

TOKDEC_WS :
    Ws + -> channel(2)
    ;

TOKDEC_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if (Character.isUpperCase(idValue.charAt(0))) {
//         System.out.println("TOKDEC_ID: token declaration identifier");
         setType(TOKEN_ID);
     } else {
//         System.out.println("TOKDEC_ID: labelled element identifier");
         setType(ID);
     }
//     System.out.println("TOKDEC_ID: value=" + idValue);
//     System.out.println("TOKDEC_ID: returned token=" + _SYMBOLIC_NAMES[_type]);
    }
    ;

TOKDEC_ASSIGN :
    Equal -> type(ASSIGN)
    ;

TOKDEC_PLUS_ASSIGN :
    PlusAssign
    -> type(PLUS_ASSIGN)
    ;

TOKDEC_COLON :
    Colon -> type(COLON)
    ;

TOKDEC_LPAREN :
    LParen-> type(LPAREN)
    ;

TOKDEC_RPAREN :
    RParen -> type(RPAREN)
    ;

TOKDEC_INT :
    DecimalNumeral -> type(INT)
    ;

TOKDEC_STRING_LITERAL :
    SQuoteLiteral -> type(STRING_LITERAL)
    ;

TOKDEC_BEGIN_LEXER_CHAR_SET :
    LBrack -> more, pushMode(LexerCharSet)
    ;

TOKDEC_RANGE :
    Range -> type(RANGE)
    ;

TOKDEC_PLUS :
    Plus -> type(PLUS)
    ;

TOKDEC_STAR :
    Star -> type(STAR)
    ;

TOKDEC_QUESTION :
    Question -> type(QUESTION)
    ;

TOKDEC_DOT :
    Dot -> type(DOT)
    ;

TOKDEC_NOT :
    Tilde -> type(NOT)
    ;

TOKDEC_OR :
    Pipe -> type(OR)
    ;

TOKDEC_RARROW :
    RArrow -> type(RARROW), pushMode(LexerCommands)
    ;

TOKDEC_LBRACE :
    LBrace
    {
     initialStackedModeNumber = _modeStack.size();
     setType(BEGIN_ACTION);
     pushMode(Action);
    }
    ;

TOKDEC_SEMI :
    Semi -> type(SEMI), popMode
    ;

TOKDEC_UNTERMINATED :
    EOF -> popMode
    ;

TOKDEC_ERRCHAR:
    . -> type(ERRCHAR)
    ;



/*****************************
 * fragment declaration mode *
 *****************************/
mode FragmentDeclaration;

FRAGDEC_LINE_COMMENT :
    LineComment -> channel(1)
    ;

FRAGDEC_WS :
    Ws + -> channel(2)
    ;

FRAGDEC_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if (Character.isUpperCase(idValue.charAt(0))) {
//         System.out.println("FRAGDEC_ID: token declaration identifier");
         setType(TOKEN_ID);
     } else {
//         System.out.println("FRAGDEC_ID: labelled element identifier");
         setType(ID);
     }
//     System.out.println("FRAGDEC_ID: value=" + idValue);
//     System.out.println("FRAGDEC_ID: returned token=" + _SYMBOLIC_NAMES[_type]);
    }
    ;

FRAGDEC_ASSIGN :
    Equal -> type(ASSIGN)
    ;

FRAGDEC_PLUS_ASSIGN :
    PlusAssign
    -> type(PLUS_ASSIGN)
    ;

FRAGDEC_COLON :
    Colon -> type(COLON)
    ;

FRAGDEC_LPAREN :
    LParen-> type(LPAREN)
    ;

FRAGDEC_RPAREN :
    RParen -> type(RPAREN)
    ;

FRAGDEC_INT :
    DecimalNumeral -> type(INT)
    ;

FRAGDEC_DOT :
    Dot -> type(DOT)
    ;

FRAGDEC_STRING_LITERAL :
    SQuoteLiteral -> type(STRING_LITERAL)
    ;

FRAGDEC_BEGIN_LEXER_CHAR_SET :
    LBrack -> more, pushMode(LexerCharSet)
    ;

FRAGDEC_RANGE :
    Range -> type(RANGE)
    ;

FRAGDEC_OR :
    Pipe -> type(OR)
    ;

FRAGDEC_PLUS :
    Plus -> type(PLUS)
    ;

FRAGDEC_STAR :
    Star -> type(STAR)
    ;

FRAGDEC_QUESTION :
    Question -> type(QUESTION)
    ;

FRAGDEC_NOT :
    Tilde -> type(NOT)
    ;

FRAGDEC_RARROW :
    RArrow -> type(RARROW), pushMode(LexerCommands)
    ;

FRAGDEC_LBRACE :
    LBrace
    {
     initialStackedModeNumber = _modeStack.size();
     setType(BEGIN_ACTION);
     pushMode(Action);
    }
    ;

FRAGDEC_SEMI :
    Semi -> type(SEMI), popMode
    ;

FRAGDEC_UNTERMINATED :
    EOF -> popMode
    ;

FRAGDEC_ERRCHAR:
    . -> type(ERRCHAR)
    ;

/***************************
 * parser rule declaration *
 ***************************/
mode ParserRuleDeclaration;

PARDEC_LINE_COMMENT :
    LineComment -> channel(1)
    ;

PARDEC_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

PARDEC_WS :
    Ws + -> channel(2)
    ;

PARDEC_RETURNS :
    'returns' -> type(RETURNS)
    ;

PARDEC_LOCALS :
    'locals' -> type(LOCALS)
    ;

PARDEC_AT :
    At -> type(AT)
    ;

PARDEC_INIT :
    'init' -> type(INIT)
    ;

PARDEC_AFTER :
    'after' -> type(AFTER)
    ;

// catch and finally keywords are managed in normal mode because placed after
// semi colon that is parser rule declaration delimiter
PARDEC_THROWS :
    'throws' -> type(THROWS)
    ;

PARDEC_OPTIONS :
    'options' -> type(OPTIONS), pushMode(ParserRuleOptions)
    ;

PARDEC_DOT :
    Dot -> type(DOT)
    ;

PARDEC_LT
   : Lt -> type(LT)
   ;

PARDEC_ASSOC :
    'assoc' -> type(ASSOC)
    ;

PARDEC_RIGHT :
    'right' -> type(RIGHT)
    ;

PARDEC_LEFT :
    'left' -> type(LEFT)
    ;

PARDEC_FAIL :
    'fail' -> type(FAIL)
    ;

PARDEC_GT :
    Gt -> type(GT)
    ;

PARDEC_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if (isThereOperatorNext()) {
//         System.out.println("PARDEC_ID: identifier");
         setType(ID);
     } else {
         if (Character.isUpperCase(idValue.charAt(0))) {
//             System.out.println("PARDEC_ID: token reference identifier");
             setType(TOKEN_ID);
         } else {
//             System.out.println("PARDEC_ID: parser rule reference identifier");
             setType(PARSER_RULE_ID);
         }
     }
    }
    ;

PARDEC_BEGIN_ARGUMENT :
    LBrack
    {
     initialStackedModeNumber = _modeStack.size();
     setType(BEGIN_ARGUMENT);
     pushMode(Argument);
    }
    ;

PARDEC_COLON :
    Colon -> type(COLON)
    ;

PARDEC_STRING_LITERAL :
    SQuoteLiteral -> type(STRING_LITERAL)
    ;

PARDEC_ASSIGN :
    Equal -> type(ASSIGN)
    ;

PARDEC_PLUS_ASSIGN :
    PlusAssign
    -> type(PLUS_ASSIGN)
/*
    {
     setType(PLUS_ASSIGN);
     System.out.println("'+=' token detected in parser rule definition");
    }
*/
    ;

PARDEC_LPAREN :
    LParen-> type(LPAREN)
    ;

PARDEC_RPAREN :
    RParen -> type(RPAREN)
    ;

PARDEC_PLUS :
    Plus -> type(PLUS)
    ;

PARDEC_STAR :
    Star -> type(STAR)
    ;

PARDEC_QUESTION :
    Question -> type(QUESTION)
    ;

PARDEC_NOT :
    Tilde -> type(NOT)
    ;

PARDEC_OR :
    Pipe -> type(OR)
    ;

PARDEC_SHARP :
    Sharp -> type(SHARP), pushMode(Identifier)
    ;

PARDEC_LBRACE :
    LBrace
    {
     initialStackedModeNumber = _modeStack.size();
     setType(BEGIN_ACTION);
     pushMode(Action);
    }
    ;

PARDEC_SEMI :
    Semi -> type(SEMI), popMode
    ;

PARDEC_UNTERMINATED
   : EOF -> popMode
   ;

PARDEC_ERRCHAR:
    . -> type(ERRCHAR)
    ;


/*
 * There is no options for parser rule declaration but syntax allows the
 * possibility to add some in future.
 */
mode ParserRuleOptions;

PARDEC_OPT_WS :
    Ws + -> channel(2)
    ;

PARDEC_OPT_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

PARDEC_OPT_LINE_COMMENT :
    LineComment -> channel(1)
    ;

PARDEC_OPT_LBRACE :
    LBrace -> type (LBRACE)
    ;

PARDEC_OPT_ASSIGN :
    Equal -> type(ASSIGN)
    ;

PARDEC_OPT_SEMI :
    Semi -> type(SEMI)
    ;

PARDEC_OPT_ID :
    NameStartChar NameChar* -> type(ERRCHAR)
    ;

PARDEC_OPT_RBRACE :
    RBrace -> type (RBRACE) , popMode
    ;

PARDEC_OPT_UNTERMINATED :
    EOF -> popMode
    ;

PARDEC_OPT_ERRCHAR :
    . -> type(ERRCHAR)
    ;


/******************
 * Lexer commands *
 ******************/
mode LexerCommands;

LEXCOM_BLOCK_COMMENT :
    BlockComment -> channel(1)
    ;

LEXCOM_LINE_COMMENT :
    LineComment -> channel(1)
    ;

LEXCOM_WS :
    Ws + -> channel(2)
    ;

LEXCOM_SKIP :
    'skip'
    ;

LEXCOM_MORE :
    'more'
    ;

LEXCOM_TYPE :
    'type' -> pushMode(TypeLexerCommand)
    ;

LEXCOM_CHANNEL :
    'channel'
    ;

LEXCOM_MODE :
    'mode'
    ;

LEXCOM_PUSHMODE :
    'pushMode'
    ;

LEXCOM_POPMODE :
    'popMode'
    ;

LEXCOM_LPAREN :
    LParen -> type(LPAREN)
    ;

LEXCOM_ID :
    NameStartChar NameChar* -> type(ID)
    ;

LEXCOM_INT :
    DecimalNumeral -> type(INT)
    ;

LEXCOM_RPAREN :
    RParen -> type(RPAREN)
    ;

LEXCOM_COMMA :
    Comma -> type(COMMA)
    ;

LEXCOM_SEMI :
    Semi -> type(SEMI), popMode, popMode
    ;

LEXCOM_UNTERMINATED :
    EOF -> popMode
    ;

LEXCOM_ERRCHAR :
    . -> type(ERRCHAR)
    ;

/*************************
 * mode TypeLexerCommand *
 *************************/
mode TypeLexerCommand;

TYPE_WS :
    Ws + -> channel(2)
    ;

TYPE_LINE_COMMENT :
    LineComment -> channel(1)
    ;

TYPE_LPAREN :
    LParen -> type(LPAREN)
    ;

TYPE_RPAREN :
    RParen -> type(RPAREN), popMode
    ;

TYPE_TOKEN_ID :
    NameStartChar NameChar*
    {
     String idValue = getText();
     if ( Character.isUpperCase(idValue.charAt(0)) )
         setType(TOKEN_ID);
     else
         setType(ERRCHAR); // in this case it will raise a syntactic error
//     System.out.println("TYPE_TOKEN_ID: returned token type=" + _SYMBOLIC_NAMES[_type]);
    }
    ;

TYPE_UNTERMINATED
   : EOF -> popMode
   ;

TYPE_ERRCHAR:
    . -> type(ERRCHAR), popMode
    ;


// -------------------------

mode LexerCharSet;
LEXER_CHAR_SET_BODY
   : (~ [\]\\] | EscAny) + -> more
  ;

LEXER_CHAR_SET :
    RBrack -> popMode
    ;

LEXER_CHAR_SET_UNTERMINATED :
    EOF -> popMode
    ;

LEXER_CHAR_SET_ERRCHAR:
    . -> type(ERRCHAR), popMode
    ;
