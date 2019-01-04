lexer grammar NestedMapLexer;

BooleanValue : TRUE | FALSE;
String : STRING | STRING2;

Whitespace:
    WHITESPACE -> channel(1);

OpenBrace : '{';
Comma : ',';
CloseBrace : '}';
Colon : ':';
True : TRUE;
False : FALSE;

Number: MINUS ? DIGITS ;

Identifier : ID;

fragment DIGITS : DIGIT+;
fragment MINUS : '-';
fragment TRUE : 'true';
fragment FALSE : 'false';
fragment STRING: '"' (ESC|.)*? '"';
fragment STRING2: '\''(ESC2|.)*? '\'';
fragment DIGIT : [0-9];
fragment WHITESPACE : [ \t\r\n]+;
fragment ID: ('a'..'z'|'A'..'Z' | '_')('a'..'z'|'A'..'Z'|'0'..'9' | '_')+;
fragment ESC : '\\"' | '\\\\' ;
fragment ESC2 : '\\\'' | '\\\\' ;

