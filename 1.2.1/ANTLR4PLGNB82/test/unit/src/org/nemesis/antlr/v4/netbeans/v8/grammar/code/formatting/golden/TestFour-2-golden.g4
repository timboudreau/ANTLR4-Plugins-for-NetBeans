/*
Leading block comment
*/
lexer grammar TestFour; 
     // indented line comments
     // should stay indented
     // as they were
Word : CHARS+;

Number : NUMBER+;

fragment CHARS : [a-zA-Z]; // this comment should stay on this line
fragment NUMBER : [0-9]; 