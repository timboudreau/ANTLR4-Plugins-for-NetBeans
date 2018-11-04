grammar Timestamps;

timestampDecl : 
    ( def? ':' ts=Timestamp constraints) #IsoTimestamp
    |(def? ':' amt=digits) #IntTimestamp 
    |(def? ':' digits) #FooTimestamp;

constraints: (min=min | max=max | req=req)*;

max : 'max' value=timestampLiteral;
min : Min value=timestampLiteral;
req : 'required';
def  : 'default'? '=' def=timestampLiteral;
timestampLiteral : Timestamp;

digits: DIGIT (DIGIT | '_')*;

Timestamp : Datestamp 'T' Time;
Datestamp : FOUR_DIGITS '-' TWO_DIGITS '-' TWO_DIGITS ;
Time : TWO_DIGITS ':' TWO_DIGITS ':' TWO_DIGITS TS_FRACTION? TS_OFFSET;

Min : 'min';

fragment FOUR_DIGITS : DIGIT DIGIT DIGIT DIGIT;
fragment TWO_DIGITS : DIGIT DIGIT;
fragment TS_FRACTION : '.' DIGIT+;
fragment TS_OFFSET
    : 'Z' | TS_NUM_OFFSET;
fragment TS_NUM_OFFSET
    : ( '+' | '-' ) DIGIT DIGIT ':' DIGIT DIGIT;
fragment DIGIT: [0-9];
