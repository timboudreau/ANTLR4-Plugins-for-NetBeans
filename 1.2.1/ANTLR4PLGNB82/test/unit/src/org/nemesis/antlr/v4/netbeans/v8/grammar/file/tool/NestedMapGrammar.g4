grammar NestedMapGrammar;

items : (map (Comma map)*) EOF;

map : OpenBrace (mapItem (Comma mapItem)*)? CloseBrace;

mapItem : id=Identifier Colon value;

value : booleanValue #Bool
    | numberValue #Num
    | stringValue #Str
;

booleanValue : val=(True | False);
stringValue : str=String;
numberValue : num=Number;


Number: Minus? Digits;

Digits : DIGIT+;


String : STRING | STRING2;

Whitespace:
    WHITESPACE -> channel(1);

OpenBrace : '{';
Comma : ',';
CloseBrace : '}';
Minus : '-';
Colon : ':';
True : TRUE;
False : FALSE;

Identifier : ID;

fragment TRUE : 'true';
fragment FALSE : 'false';
fragment STRING: '"' (ESC|.)*? '"';
fragment STRING2: '\''(ESC2|.)*? '\'';
fragment DIGIT : [0-9];
fragment WHITESPACE : [ \t\r\n]+;
fragment ID: ('a'..'z'|'A'..'Z' | '_')('a'..'z'|'A'..'Z'|'0'..'9' | '_')+;
fragment ESC : '\\"' | '\\\\' ;
fragment ESC2 : '\\\'' | '\\\\' ;

