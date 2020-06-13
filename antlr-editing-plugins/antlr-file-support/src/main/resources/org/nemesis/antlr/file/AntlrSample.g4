/*
 * A license comment
 */
grammar AntlrSample;

@lexer::members { private void logStatus(String msg) {
    System.out.println(msg + " mode " + modeNames[_mode] ": " + _input.getText(new Interval(_tokenStartCharIndex, _input.index()))); }
 }}
// Handle several kinds of timestamp
timestampDecl : ( def? Colon ts=Timestamp constraints* ) #IsoTimestamp
        | ( def? Colon amt=digits constraints* ) #UnixTimestamp
        | ( def? Colon lit=relative constraints* ) #FooTimestamp;

constraints : ( min=min | max=max | req=req )+;

max : 'max' value=timestampLiteral;

min : Min value=timestampLiteral;

req : 'required';

def : 'default'? '=' def=timestampLiteral;

relative : 'today' | 'yesterday' | 'tomorrow';

timestampLiteral : Timestamp;

digits : DIGIT ( DIGIT | '_' )*;

Thing : Timestamp~[\r\n]*;

Timestamp : Datestamp 'T' Time { logStatus("Timestamp"); };

Datestamp : FOUR_DIGITS '-' TWO_DIGITS '-' TWO_DIGITS;

Time : TWO_DIGITS ':' TWO_DIGITS ':' TWO_DIGITS TS_FRACTION? TS_OFFSET;

Min : 'min';

Colon : ':';

fragment FOUR_DIGITS : DIGIT DIGIT DIGIT DIGIT;

fragment TWO_DIGITS : DIGIT DIGIT;

fragment TS_FRACTION : '.' DIGIT+;

fragment TS_OFFSET : 'Z' | TS_NUM_OFFSET;

fragment TS_NUM_OFFSET : ( '+' | '-' ) DIGIT DIGIT ':' DIGIT DIGIT;

fragment DIGIT : [0-9];