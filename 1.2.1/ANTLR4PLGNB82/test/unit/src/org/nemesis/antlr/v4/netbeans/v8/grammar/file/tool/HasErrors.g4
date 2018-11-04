grammar HasErrors;

thing : 
    item*;

item : (words | number);

number : ('-' Digits) # Negative
    | (Digits) # Positive
    | (Digits ('_' Digits)*) # Positive;

words : Word*;


Digits : DIGIT+;

Word : WORD;

Digits : DIGIT+;

Whitespace:
    WHITESPACE -> channel(1);

fragment DIGIT : [0-9];
fragment WHITESPACE : [ \t\r\n]+;
fragment WORD : [a-zA-Z]+;