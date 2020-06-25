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
grammar ReturnsTest;

@parser::members {
    int skiddoo = 23;
}

// Example rules
compilation_unit : array | number;

array : OpenBrace number ( Comma number )+ CloseBrace;

number returns[Integer v] : Integer {_localctx.v = java.lang.Integer.parseInt(_localctx.getText());};

Integer : MINUS? DIGITS;

LineComment : OPEN_LINE_COMMENT.*? S_LINE_END -> channel (1 );

Comment : OPEN_COMMENT.*? CLOSE_COMMENT -> channel (1 );

Whitespace : {1 == 1}? ( ' ' | '\t' | '\n' | '\r' )+ -> channel (2 );

Comma : ',';

OpenBrace : '[';

CloseBrace : ']';

fragment OPEN_LINE_COMMENT : '//';

fragment OPEN_COMMENT : '/*';

fragment CLOSE_COMMENT : '*/';

fragment S_LINE_END : '\r'? '\n';

fragment DIGITS : DIGIT+;

fragment DIGIT : [0-9];

fragment MINUS : '-';