grammar Epsilon;

// TODO:  Add your lexer and parser rules here

// Example rules
compilation_unit
    : array
    | number
    | mods
    | hexenites
    ;

array
    : OpenBrace number (Comma number)+ CloseBrace;

number
    : Integer;

mods : mod+;

mod : (ModA | ModB | ModC)*;

hexenites : hexen+;

hexen : Hex*;

Integer
    : MINUS? DIGITS;

LineComment
    : OPEN_LINE_COMMENT.*? S_LINE_END -> channel(1);

Comment
    : OPEN_COMMENT.*? CLOSE_COMMENT -> channel(1);

Whitespace
    : (' ' | '\t' | '\n' | '\r')+ -> channel(2);

Comma
    : ',';

OpenBrace
    : '[';

CloseBrace
    : ']';



ModA : '`';

ModB : '^';

ModC : '!';

HexNum : (HEX | DIGIT)+;

fragment HEX : 'a' | 'b' | 'c' | 'd' | 'e' | 'f';

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

