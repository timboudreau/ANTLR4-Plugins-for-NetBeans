/*
 * Copyright 2019 Mastfrog Technologies.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
grammar Ebnf;

ebnf_sequence
    : nameprefix? ebnfitem+
    ;

ebnfitem
    : (value=item ebnf=ebnfsuffix ) #ebnfItem
    | value=item #plainItem
    ;

ebnfsuffix
    : kind=( Star | Plus ) ungreedy=Question?
    | kind=Question
    ;

item
    : name orItem?
    | literal orItem?
    | block orItem?
    | characterRange orItem?
    | lexerCharSet orItem?
    ;

orItem
    : OR ebnfitem
    ;

name
    : IDENTIFIER
    ;

block
    : LPAREN ebnfitem+ RPAREN
    ;

literal
    : LITERAL
    ;

nameprefix
    : IDENTIFIER Equal
    ;

characterRange
    : LITERAL RANGE LITERAL
    ;

lexerCharSet
    : LEXER_CHAR_SET
    ;

OR
    : '|'
    ;

LPAREN
    : '('
    ;

RPAREN
    : ')'
    ;

LINE_COMMENT
    : LineComment -> channel( 1 );

BLOCK_COMMENT
    : BlockComment -> channel( 1 );

RANGE
    : Range
    ;

IDENTIFIER
    : NameStartChar NameChar*
    ;

LITERAL
    : SQuoteLiteral
    | DQuoteLiteral
    ;

LEXER_CHAR_SET
    : LBrack (~[\]\\] | EscAny )+ RBrack
    ;

WHITESPACE
    : Ws+ -> channel( 2 );

fragment Ws
    : Hws
    | Vws
    ;

fragment Hws
    : [ \t]
    ;

fragment Vws
    : [\r\n\f]
    ;

//fragment CharLiteral
//    : SQuote ( EscSeq |~['\r\n\\] ) SQuote
//    ;

fragment SQuoteLiteral
    : SQuote ( EscSeq |~['\r\n\\] )+ SQuote
    ;

fragment DQuoteLiteral
    : DQuote ( EscSeq |~["\r\n\\] )+ DQuote
    ;

//fragment USQuoteLiteral
//    : SQuote ( EscSeq |~['\r\n\\] )* SQuote
//    ;

// -----------------------------------
// Escapes
// Any kind of escaped character that we can embed within ANTLR literal strings.
// FYV : no EOF in token rules
// fragment EscSeq
//   : Esc ([btnfr"'\\] | UnicodeEsc | . | EOF)
//   ;
fragment EscSeq
    : Esc ( [btnfr"'\\] | UnicodeEsc |. )
    ;

fragment EscAny
    : Esc.
    ;

fragment Esc
    : '\\'
    ;

fragment UnicodeEsc
    : 'u' ( HexDigit ( HexDigit ( HexDigit HexDigit? )? )? )?
    ;

// -----------------------------------
// Digits
fragment HexDigit
    : [0-9a-fA-F]
    ;

fragment LBrack
    : '['
    ;

fragment RBrack
    : ']'
    ;

fragment SQuote
    : '\''
    ;

fragment DQuote
    : '"'
    ;

fragment NameChar
    : NameStartChar
    | '0'..'9'
    | Underscore
    | '\u00B7'
    | '\u0300'..'\u036F'
    | '\u203F'..'\u2040'
    ;

fragment NameStartChar
    : LatinChar
    | '\u00C0'..'\u00D6'
    | '\u00D8'..'\u00F6'
    | '\u00F8'..'\u02FF'
    | '\u0370'..'\u037D'
    | '\u037F'..'\u1FFF'
    | '\u200C'..'\u200D'
    | '\u2070'..'\u218F'
    | '\u2C00'..'\u2FEF'
    | '\u3001'..'\uD7FF'
    | '\uF900'..'\uFDCF'
    | '\uFDF0'..'\uFFFD'
    ;

fragment Underscore
    : '_'
    ;

// -----------------------------------
// Character ranges
// Added by FYV
fragment UpperCaseLatinChar
    : 'A'..'Z'
    ;

// Added by FYV
fragment LowerCaseLatinChar
    : 'a'..'z'
    ;

fragment LatinChar
    : UpperCaseLatinChar
    | LowerCaseLatinChar
    ;

// FYV : EOF must not be part of a comment. EOF does not terminates a block
//      comment. If there is no '\*\/' sequence at the end then there is a syntax
//      error.
//      Furthermore, if you let EOF in the rule, it eats the EOF and the general
//      rule that defines the general input file syntax will not find the EOF
//      symbol.
// fragment BlockComment
//   : '/*' .*? ('*/' | EOF)
//   ;
fragment BlockComment
    : '/*'.*? '*/'
    ;

// FYV : EOF must not be part of a comment. EOF does not terminates a document
//      comment. If there is no '\*\/' sequence at the end then there is a syntax
//      error.
//      Furthermore, if you let EOF in the rule, it eats the EOF and the general
//      rule that defines the general input file syntax will not find the EOF
//      symbol.
// fragment DocComment
//   : '/**' .*? ('*/' | EOF)
//   ;
fragment DocComment
    : '/**'.*? '*/'
    ;

fragment LineComment
    : '//'~[\r\n]*
    ;

fragment Range
    : '..'
    ;

Equal
    : '='
    ;

Question
    : '?'
    ;

Star
    : '*'
    ;

Plus
    : '+'
    ;