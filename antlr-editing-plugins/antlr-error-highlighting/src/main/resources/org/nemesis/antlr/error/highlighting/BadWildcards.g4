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
grammar BadWildcards;

compilation_unit
    : things+ EOF;

things
    : intArray
    | namespaceStatement;

description
    : lines=DESCRIPTION*;

namespaceStatement
    : description? K_NAMESPACE ( IDENT | QUALIFIED_ID ) S_SEMICOLON;

foo : numericExpression baz;

baz : K_NAMESPACE* | foo*;

GOOP : K_NAMESPACE? S_ASTERISK*;

fragment MOOP : (DESC_DELIMITER? TRUE*)+ | (FALSE? STRING*);

thing : S_SLASH exp=description?;

moo : (thing x=baz*);

numericExpression
    : ( L_INT | L_FLOAT ) #singleFloat
    | (( L_FLOAT | L_INT ) OP numericExpression ) #mathFloat
    | ( S_OPEN_PARENS numericExpression S_CLOSE_PARENS ) #parentheticFloat;

intArray
    : description? S_OPEN_BRACKET L_INT* S_CLOSE_BRACKET;

OP
    : S_PLUS
    | S_MINUS
    | S_SLASH
    | S_ASTERISK
    | S_PERCENT;

QUALIFIED_ID
    : IDENT ( S_DOT IDENT )+;

K_NAMESPACE
    : 'namespace';//fooxegae
K_INT
    : 'int';

K_INT_ARRAY
    : 'intArray';

S_SLASH
    : '/';

S_ASTERISK
    : '*';

S_PERCENT
    : '%';

S_OPEN_PARENS
    : '(';

S_CLOSE_PARENS
    : ')';

S_OPEN_BRACE
    : '{';

S_CLOSE_BRACE
    : '}';

S_COLON
    : ':';

S_SEMICOLON
    : ';';

S_COMMA
    : ',';

S_DOT
    : '.';

S_OPEN_BRACKET
    : '[';

S_CLOSE_BRACKET
    : ']';

COMMENT
    : '/*'.*? '*/' -> channel( 1 );

S_WHITESPACE
    : ( ' ' | '\t' | '\n' | '\r' )+ -> channel( 2 );

L_STRING
    : ( STRING | STRING2 );

L_FLOAT
    : ( S_MINUS )? DIGIT+ ( S_DOT DIGIT+ );

L_INT
    : ( S_MINUS )? DIGITS;

DESCRIPTION
    : DESC_DELIMITER ( ESC |. )*? S_LINE_END;

IDENT
    : ( 'a'..'z' | 'A'..'Z' | '_' )( 'a'..'z' | 'A'..'Z' | '0'..'9' | '_' )*;

fragment DESC_DELIMITER
    : '**';

fragment TRUE
    : 'true';

fragment FALSE
    : 'false';

fragment STRING
    : '"' ( ESC |. )*? '"';

fragment STRING2
    : '\'' ( ESC2 |. )*? '\'';

fragment DIGITS
    : DIGIT+;

fragment DIGIT
    : [0-9];

fragment S_PLUS
    : '+';

fragment S_MINUS
    : '-';

fragment ESC
    : '\\"'
    | '\\\\';

fragment ESC2
    : '\\\''
    | '\\\\';

fragment WS
    : ' '
    | '\t'
    | '\n'
    | '\r';

fragment S_LINE_END
    : '\r'? '\n';
