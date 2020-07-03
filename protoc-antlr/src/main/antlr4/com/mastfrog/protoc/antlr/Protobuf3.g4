/**
 * A Protocol Buffers 3 grammar for ANTLR v4.
 *
 * Derived and adapted from:
 * https://developers.google.com/protocol-buffers/docs/reference/proto3-spec
 *
 * @author Marco Willemart
 */
grammar Protobuf3;
@lexer::members {
protected int initialStackedModeNumber = -1; // -1 means undefined value

public int getInitialStackedModeNumber() {
    return initialStackedModeNumber;
}

public void setInitialStackedModeNumber(int initialStackedModeNumber) {
    this.initialStackedModeNumber = initialStackedModeNumber;
}
}

//
// Proto file
//

proto
    :   syntax (   importStatement
               |   packageStatement
               |   option
               |   topLevelDef
               |   emptyStatement
               )*
        EOF
    ;

//
// Syntax
//

syntax
    :   SYNTAX ASSIGN (PROTO3_DOUBLE | PROTO3_SINGLE ) SEMI
    ;

//
// Import Statement
//

importStatement
    :   IMPORT (WEAK | PUBLIC)? StrLit SEMI
    ;

//
// Package
//

packageStatement
    :   PACKAGE fullIdent SEMI
    ;

//
// Option
//

option
    :   OPTION optionName ASSIGN constant SEMI
    ;

optionName
    :   (Ident | LPAREN fullIdent RPAREN ) (DOT Ident)*
    ;

//
// Top Level definitions
//

topLevelDef
   :   message
   |   enumDefinition
   |   service
   ;

// Message definition

message
    :   MESSAGE messageName messageBody
    ;

messageBody
    :   LBRACE (   field
            |   enumDefinition
            |   message
            |   option
            |   oneof
            |   mapField
            |   reserved
            |   emptyStatement
            )*
       RBRACE
    ;

// Enum definition

enumDefinition
    :   ENUM enumName enumBody
    ;

enumBody
    :   LBRACE (   option
            |   enumField
            |   emptyStatement
            )*
        RBRACE
    ;

enumField
    :   Ident ASSIGN MINUS? IntLit (LBRACK enumValueOption (COMMA  enumValueOption)* RBRACK)? SEMI
    ;

enumValueOption
    :   optionName ASSIGN constant
    ;

// Service definition

service
    :   SERVICE serviceName LBRACE (   option
                                  |   rpc
                                  // not defined in the protobuf specification
                                  //|   stream
                                  |   emptyStatement
                                  )*
        RBRACE
    ;

rpc
    :   RPC rpcName LPAREN STREAM? messageType RPAREN
        RETURNS LPAREN STREAM? messageType RPAREN ((LBRACE (option | emptyStatement)* RBRACE) | SEMI)
    ;

//
// Reserved
//

reserved
    :   RESERVED (ranges | fieldNames) SEMI
    ;

ranges
    :   range (COMMA range)*
    ;

range
    :   IntLit
    |   IntLit TO IntLit
    ;

fieldNames
    :   StrLit (COMMA StrLit)*
    ;

//
// Fields
//

type
    :   (   DOUBLE
        |   FLOAT
        |   INT32
        |   INT64
        |   UINT32
        |   UINT64
        |   SINT32
        |   SINT64
        |   FIXED32
        |   FIXED64
        |   SFIXED32
        |   SFIXED64
        |   BOOL
        |   STRING
        |   BYTES
        )
    |   messageOrEnumType
    ;

fieldNumber
    : IntLit
    ;

// Normal field

field
    :   REPEATED? type fieldName ASSIGN fieldNumber (LBRACK fieldOptions RBRACK)? SEMI
    ;

fieldOptions
    :   fieldOption (COMMA  fieldOption)*
    ;

fieldOption
    :   optionName ASSIGN constant
    ;

// Oneof and oneof field

oneof
    :   ONEOF oneofName LBRACE (oneofField | emptyStatement)* RBRACE
    ;

oneofField
    :   type fieldName ASSIGN fieldNumber (LBRACK fieldOptions RBRACK)? SEMI
    ;

// Map field

mapField
    :   MAP LCHEVR keyType COMMA type RCHEVR mapName ASSIGN fieldNumber (LBRACK fieldOptions RBRACK)? SEMI
    ;

keyType
    :   INT32
    |   INT64
    |   UINT32
    |   UINT64
    |   SINT32
    |   SINT64
    |   FIXED32
    |   FIXED64
    |   SFIXED32
    |   SFIXED64
    |   BOOL
    |   STRING
    ;

//
// Lexical elements
//

// Keywords

BOOL            : 'bool';
BYTES           : 'bytes';
DOUBLE          : 'double';
ENUM            : 'enum';
FIXED32         : 'fixed32';
FIXED64         : 'fixed64';
FLOAT           : 'float';
IMPORT          : 'import';
INT32           : 'int32';
INT64           : 'int64';
MAP             : 'map';
MESSAGE         : 'message';
ONEOF           : 'oneof';
OPTION          : 'option';
PACKAGE         : 'package';
PROTO3_DOUBLE   : '"proto3"';
PROTO3_SINGLE   : '\'proto3\'';
PUBLIC          : 'public';
REPEATED        : 'repeated';
RESERVED        : 'reserved';
RETURNS         : 'returns';
RPC             : 'rpc';
SERVICE         : 'service';
SFIXED32        : 'sfixed32';
SFIXED64        : 'sfixed64';
SINT32          : 'sint32';
SINT64          : 'sint64';
STREAM          : 'stream';
STRING          : 'string';
SYNTAX          : 'syntax';
TO              : 'to';
UINT32          : 'uint32';
UINT64          : 'uint64';
WEAK            : 'weak';

// Letters and digits

fragment
Letter
    :   [A-Za-z_]
    ;

fragment
DecimalDigit
    :   [0-9]
    ;

fragment
OctalDigit
    :   [0-7]
    ;

fragment
HexDigit
    :   [0-9A-Fa-f]
    ;

// Identifiers

Ident
    :   Letter (Letter | DecimalDigit)*
    ;

fullIdent
    :   Ident (DOT Ident)*
    ;

messageName
    :   Ident
    ;

enumName
    :   Ident
    ;

messageOrEnumName
    :   Ident
    ;

fieldName
    :   Ident
    ;

oneofName
    :   Ident
    ;

mapName
    :   Ident
    ;

serviceName
    :   Ident
    ;

rpcName
    :   Ident
    ;
//foo 
messageType
    :   DOT? (Ident DOT)* messageName
    ;

messageOrEnumType
    :   DOT? (Ident DOT)* messageOrEnumName
    ;

// Integer literals

IntLit
    :   DecimalLit
    |   OctalLit
    |   HexLit
    ;

fragment
DecimalLit
    :   [1-9] DecimalDigit*
    ;

fragment
OctalLit
    :   '0' OctalDigit*
    ;

fragment
HexLit
    :   '0' ('x' | 'X') HexDigit+
    ;

// Floating-point literals

FloatLit
    :   (   Decimals '.' Decimals? Exponent?
        |   Decimals Exponent
        |   '.' Decimals Exponent?
        )
    |   'inf'
    |   'nan'
    ;

fragment
Decimals
    :   DecimalDigit+
    ;

fragment
Exponent
    :   ('e' | 'E') ('+' | '-')? Decimals
    ;

// Boolean

BoolLit
    :   'true'
    |   'false'
    ;

// String literals

StrLit
    :   '\'' CharValue* '\''
    |   '"' CharValue* '"'
    ;

fragment
CharValue
    :   HexEscape
    |   OctEscape
    |   CharEscape
    |   ~[\u0000\n\\]
    ;

fragment
HexEscape
    :   '\\' ('x' | 'X') HexDigit HexDigit
    ;

fragment
OctEscape
    :   '\\' OctalDigit OctalDigit OctalDigit
    ;

fragment
CharEscape
    :   '\\' [abfnrtv\\'"]
    ;

Quote
    :   '\''
    |   '"'
    ;

// Empty Statement

emptyStatement
    :   SEMI
    ;

// Constant

constant
    :   fullIdent
    |   (MINUS | PLUS)? IntLit
    |   (MINUS | PLUS)? FloatLit
    |   (   StrLit
        |   BoolLit
        )
    ;

// Separators

LPAREN          : '(';
RPAREN          : ')';
LBRACE          : '{';
RBRACE          : '}';
LBRACK          : '[';
RBRACK          : ']';
LCHEVR          : '<';
RCHEVR          : '>';
SEMI            : ';';
COMMA           : ',';
DOT             : '.';
MINUS           : '-';
PLUS            : '+';

// Operators

ASSIGN          : '=';

// Whitespace and comments

WS  :   [ \t\r\n\u000C]+ -> channel (1)
    ;

COMMENT
    :   '/*' .*? '*/' -> channel (2)
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> channel (2)
    ;
