grammar NoErrors;

thing : 
    item*;

item : (words | number);

number : (Hyphen Digits) # Negative
    | (Digits) # Positive
    | (Hyphen? Digits (Underscore Digits)*) # Split;

words : all=Word+;

Digits : DIGIT+;

Word : WORD;

Whitespace:
    WHITESPACE -> channel(1);

Hyphen : '-';
Underscore : '_';

fragment DIGIT : [0-9];
fragment WHITESPACE : [ \t\r\n]+;
fragment WORD : [a-zA-Z]+;
