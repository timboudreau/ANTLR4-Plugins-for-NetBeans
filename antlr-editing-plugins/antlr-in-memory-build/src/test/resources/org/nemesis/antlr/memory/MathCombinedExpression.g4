grammar MathCombinedExpression;

compilationUnit
    : (
        word
        | math )+ EOF;

math
    : Backtick
        ( statement
        | assertion )+ Backtick;

statement
    : expression Semicolon;

assertion
    : expression assertionRightSide;

assertionRightSide
    : Equals expression Semicolon;

operator
    : Plus
    | Minus
    | DividedBy
    | Times
    | Pow
    | Mod;

expression
    : number
    | expression operator expression ( operator expression )*
    | OpenParen expression CloseParen;

number
    : Integer;

word
    : WordChars;

Backtick
    : BACKTICK;

Equals
    : EQUALS;

Integer
    : DIGIT+;

Plus
    : PLUS;

Minus
    : MINUS;

Pow
    : POW;

Mod
    : PERCENT;

DividedBy
    : DIVIDED_BY;

Times
    : TIMES;

Semicolon
    : ';';

OpenParen
    : OPEN_PAREN;

CloseParen
    : CLOSE_PAREN;

Whitespace
    : WHITESPACE -> channel ( 1 );

WordChars
    : NON_WHITESPACE+;

fragment POW
    : '^';

fragment PERCENT
    : '%';

fragment BACKTICK
    : '`';

fragment DIVIDED_BY
    : '/';

fragment DIGIT
    : [0-9];

fragment EQUALS
    : '=';

fragment MINUS
    : '-';

fragment CLOSE_PAREN
    : ')';

fragment OPEN_PAREN
    : '(';

fragment PLUS
    : '+';

fragment TIMES
    : '*';

fragment WHITESPACE
    : [ \r\n\t];

fragment NON_WHITESPACE
    : ~[\r\n\t 0-9];
