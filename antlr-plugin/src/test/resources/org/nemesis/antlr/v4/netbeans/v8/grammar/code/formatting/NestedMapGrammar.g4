grammar NestedMapGrammar;

// This is a line comment

items : (map (Comma map)*) EOF;

map : OpenBrace (mapItem (Comma mapItem)*)? CloseBrace;

mapItem : id=Identifier Colon value;

/*
This is a block comment.
*/
value : booleanValue #Bool
    | numberValue #Num
    | stringValue #Str
;

        /* An indented
      multi-line
           block comment */

booleanValue : val=(True | False);
stringValue : str=String; // whatevs
numberValue : num=Number; /* A block comment
                             That continues below */


Number: Minus? Digits;
        // Indented line comment
        // which should have the line below it indented
        // and a third line

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

