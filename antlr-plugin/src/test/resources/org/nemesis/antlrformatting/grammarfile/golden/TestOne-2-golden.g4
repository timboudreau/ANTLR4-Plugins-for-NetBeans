grammar TestOne;

word : Word+;

Word : CHARS+;

fragment CHARS : [a-zA-Z]; 