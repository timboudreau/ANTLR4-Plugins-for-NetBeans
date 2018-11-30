 /* Leading block comment */
grammar TestTwo;

word
    : Word+?;

Word
    : CHARS+;

fragment CHARS
    : [a-zA-Z];

fragment NUMS
    : [0-9]+?; 