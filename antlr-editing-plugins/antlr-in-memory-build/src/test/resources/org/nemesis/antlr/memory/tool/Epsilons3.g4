grammar Epsilons3;

compilation_unit
    : things+ EOF;

things
    : intArray
    | namespaceStatement
    | fabble;

desc
    : lines=DESCRIPTION*;

namespaceStatement
    : desc? K_NAMESPACE ( IDENT | QUALIFIED_ID ) S_SEMICOLON;

foo :  koog moo*;

fabble : GOOP*;

koog : gu=baz (baz)?;

baz : K_NAMESPACE | (foo (K_NAMESPACE|foo) );

GOOP : K_NAMESPACE? S_ASTERISK* MOOP? PARB;

PARB : MOOP*;

GLORK : STUFF OP;

fragment MOOP : (DESC_DELIMITER? TRUE*)* | (FALSE? STRING*);

fragment STUFF : S_PERCENT? STRING*;

thing : S_SLASH exp=desc;

moo : (thing x=baz*);

numericExpression
    : ( L_INT | L_FLOAT ) #singleFloat
    | (( L_FLOAT | L_INT ) OP numericExpression ) #mathFloat
    | ( S_OPEN_PARENS numericExpression S_CLOSE_PARENS ) #parentheticFloat;

intArray
    : desc? S_OPEN_BRACKET L_INT* S_CLOSE_BRACKET POOB;

POOB : (OP? QUALIFIED_ID?)+;

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
