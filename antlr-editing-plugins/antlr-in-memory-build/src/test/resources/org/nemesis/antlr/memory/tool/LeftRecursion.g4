/*
 * Copyright 2020 Mastfrog Technologies.
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
grammar LeftRecursion;

// TODO:  Add your lexer and parser rules here.
// A top level rule the ends with EOF to guarantee the WHOLE file is parsed
compilation_unit
    : ( array
      | statement )+ EOF;

array
    : OpenBracket expr ( Comma expr )+ CloseBracket;

number
    : Integer;

prefixen
    : Tilde OpenBracket Star Integer+ CloseBracket;

statement
    : ( exprOuter
      | expr
      | block ) Semi;
thing
    : Tilde* modExpr;

exprOuter
    : expr
    | block
    | thing;

block
    : prefixen* OpenBrace ( array
                          | exprOuter
                          | blarg )+ CloseBrace;
blarg
    : 'wug wug';

fnurg : 'pools';

poob
    : ( poob Tilde )* 'gah' poob
    | blarg;

expr
    : prefixen* number
    | prefixen* expr Star expr
    | prefixen* expr Plus expr
    | prefixen* expr Div expr
    | prefixen* expr Minus expr
    | prefixen* thing Plus expr
    | parenExpr
    | array
    | prefixen* array;

modExpr
    : expr Percent expr;

parenExpr
    : OpenParen expr CloseParen;

Integer
    : MINUS? DIGITS;

LineComment
    : OPEN_LINE_COMMENT .*? S_LINE_END -> channel ( 1 );

Comment
    : OPEN_COMMENT .*? CLOSE_COMMENT -> channel ( 1 );

Whitespace
    : [ \t\n\r] -> channel ( 2 );

Comma
    : ',';

Tilde
    : '~';

Star
    : '*';

Percent
    : '%';

OpenBrace
    : '{';

CloseBrace
    : '}';

OpenParen
    : '(';

CloseParen
    : ')';

OpenBracket
    : '[';

CloseBracket
    : ']';

Semi
    : ';';

Plus
    : '+';

Minus
    : '-';

Div
    : '/';

fragment OPEN_LINE_COMMENT
    : '//';

fragment OPEN_COMMENT
    : '/*';

fragment CLOSE_COMMENT
    : '*/';

fragment S_LINE_END
    : '\r'? '\n';

fragment DIGITS
    : DIGIT+;

fragment DIGIT
    : [0-9];

fragment MINUS
    : '-';
