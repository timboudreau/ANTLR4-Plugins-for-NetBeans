grammar Rust;

@parser::header { import java.util.*;
    }

@parser::members {
    Set<String> importedTypes = new HashSet<>();
                      Set<String> referencedTypes = new HashSet<>();
                      Set<String> definedTypes = new HashSet<>();
                      Map<String,String> aliasedTypes = new HashMap<>();
}

//import xidstart, xidcontinue;
compilation_unitx
    : items EOF;

items
    : (use_statement
      | extern_import_statement
      | extern_block
      | inner_attribute
      | unsafe_block
      | struct
      | function
      | mod)*;

mod
    : doc=doc_comment? outer_attribute* Pub? Mod name=Ident (LeftBrace (mod
                                                                       |
                                                                                   use_statement
                                                                       | function)*
                                                                    RightBrace)?;

extern_import_statement
    : extern_import Semicolon;

use_statement
    : outer_attribute* Pub? Use use_path Semicolon {
//    if (_localctx.path() != null) {
//        if (_localctx.path().end != null) {
//            importedTypes.add(_localctx.path().getText());
//            aliasedtypes.put(_localctx.path().end, localctx.path());
//        }
//    }
};

unsafe_block
    : Unsafe block;

block
    : LeftBrace (statement
                | inner_attribute
                | enclosing_doc_comment)* (ret=expression?
                                          | pat=expression_pattern) RightBrace;

statement
    : (statement_body? Semicolon)
    | loop
    | match
    | while_loop
    | for_loop
    | if_let
    | if_statement
    | function
    | block;

statement_body
    : variable_binding
    | function_invocation
    | assignment_expression
    | macro_invocation
    | return_statement;

loop
    : Loop block;

while_loop
    : While boolean_expression block;

if_let
    : If Let exp=expression Equals var=variable_name (statement
                                                     | block);

for_loop
    : (For var=variable_name In range=(RangeExclusive
                                       | RangeInclusive)(statement
                                                         | block)) #ForRanged
    | (For var=variable_name In expr=expression (statement
                                                | block)) #ForExpression;

return_statement
    : Return exp=expression?;

variable_binding
    : (Let props=variable_props name=variable_spec) #UnassignedBinding
    | (Let props=variable_props pattern=variable_pattern Equals assignedToPattern=expression_pattern) #PatternBinding
    | (Let props=variable_props name=variable_spec Equals aprops=assignee_props
              assignedTo=expression cast=variable_cast?) #SingleBinding
    | (Let props=variable_props type_spec pattern=variable_pattern ((Equals
                                                                                     aprops=assignee_props
                                                                                     name=variable_name)
                                                                   | exp=expression)) #DestructuringLetBinding;

variable_props
    : ref=Ref? mut=Mut?;

assignee_props
    : borrow=Ampersand? mutable=Mut?;

variable_pattern
    : LeftParen (variable_spec (Comma variable_spec)* Comma?) RightParen;

variable_spec
    : (name=variable_name (Colon type=type_spec)?)
    | anon=Underscore;

variable_cast
    : As (RawPointerMutable
         | RawPointerConst) type_spec;

assignment_expression
    : variable_expression assignment_operator expression;

variable_expression
    : borrow=Ampersand? deref=Asterisk? qualifier=path_head? variable_reference (LeftBracket
                                                                                        index=expression
                                                                                        RightBracket)?;

variable_reference
    : name=variable_name (Dot variable_name)*;

variable_name
    : Ident;

array_literal
    : (LeftBracket RightBracket) #EmptyArray
    | (LeftBracket StringLiteral (Comma StringLiteral)* RightBracket) #StringArray
    | (LeftBracket int_literal (Comma int_literal)* RightBracket) #IntArray
    | (LeftBracket float_literal (Comma float_literal)* RightBracket) #FloatArray
    | (LeftBracket BooleanLiteral (Comma BooleanLiteral)* RightBracket) #BooleanArray
    | (LeftBracket ByteLiteral (Comma ByteLiteral)* RightBracket) #ByteArray
    | (LeftBracket expression (Comma expression)* RightBracket) #ExpressionArray;

match
    : Match deref=Asterisk? var=expression match_block;

match_block
    : LeftBrace (first=match_case (Comma more=match_case)*)? defaultCase=default_match_case?
    Comma? RightBrace;

match_case
    : (first=literal (Pipe more=literal)* FatArrow (statement_body
                                                   | expression
                                                   | block)) #MultiCaseLiteral
    | (range=RangeExclusive FatArrow (statement_body
                                     | expression
                                     | block)) #ExclusiveRangeCase
    | (range=MatchRangeInclusive FatArrow (statement_body
                                          | expression
                                          | block)) #InclusiveRangeCase
    | (var=variable_name_pattern If boolean_expression FatArrow (statement_body
                                                                | expression
                                                                | block)) #PatternCase
    | (exp=expression (Pipe more=expression)* If boolean_expression FatArrow (statement_body
                                                                             |
                                                                                         expression
                                                                             |
                                                                                         block)) #ExpressionCase
    | (first=expression (Pipe more=expression)* FatArrow (statement_body
                                                         | expression
                                                         | block)) #MultiCaseExpression;

default_match_case
    : Comma? Underscore FatArrow (statement_body
                                 | expression
                                 | block);

expression
    : literal cast=variable_cast? #LiteralExpression
    | match cast=variable_cast? #MatchExpression
    | closure cast=variable_cast? #ClosureExpression
    | struct_instantiation cast=variable_cast? #StructExpression
    | function_invocation cast=variable_cast? #FunctionInvocationExpression
    | assignment_expression cast=variable_cast? #AssignmentExpression
    | tuple_expression #TupleExpressions
    | unsafe_expression #UnsafeExpression
    | unsafe_block #UnsafeBlockExpression
    | exp=variable_expression cast=variable_cast? #VariableExpression
    | exp=variable_expression Dot index=BareIntLiteral cast=variable_cast? #TupleFieldExpression
    | leftSide=expression arithmetic_operator rightSide=expression #ArithmeticExpression
    | Minus? LeftParen leftSide=expression arithmetic_operator rightSide=expression
    RightParen #ParenthesizedArithmeticExpression
    | leftSide=expression shift_operator rightSide=expression #ShiftExpression
    | leftSide=expression comparison_operator rightSide=expression #BooleanExpression
    | Bang? LeftParen ls=expression comparison_operator rs=expression RightParen #ParentheizedBooleanExpression;

unsafe_expression
    : Unsafe LeftBrace expression RightBrace;

if_statement
    : If boolean_expression block (Else If boolean_expression block)* (Else block)?;

function_invocation
    : (qualifier=path_head? func=Ident LeftParen invocation_args RightParen) #UnqualifiedFunctionInvocation
    | (qualifier=path_head? func=Ident LeftParen invocation_args RightParen) #QualifiedFunctionInvocation
    | (qualifier=type_hint? DoubleColon func=Ident LeftParen invocation_args
              RightParen) #TypeHintQualifiedFunctionInvocation;

type_hint
    : LeftAngleBracket type_spec As type_spec RightAngleBracket;

macro_invocation
    : macro=Ident Bang LeftParen invocation_args RightParen;

invocation_args
    : expression?
    | expression (Comma expression)*;

tuple_expression
    : LeftParen expression (Comma expression)* RightParen;

boolean_expression
    : Bang? BooleanLiteral #BLiteral
    | leftSide=expression comparison_operator rightSide=expression #BExpression
    | Bang? LeftParen boolean_expression RightParen #ParenthesizedBExpression;

expression_pattern
    : LeftParen expression (Comma expression)* RightParen;

variable_name_pattern
    : LeftParen ((Underscore
                  | variable_name)(Comma (Underscore
                                         | variable_name))*)? RightParen;

literal
    : float_literal #FloatLiteral
    | BooleanLiteral #BooleanLiteral
    | ByteLiteral #ByteLiteral
    | int_literal #IntLiteral
    | StringLiteral #StringLiteral
    | array_literal #ArrayLiteral;

int_literal
    : (neg=Minus?? value=(BareIntLiteral
                          | FullIntLiteral) type=signed_int_subtype? cast=int_cast?) #SignedIntLiteral
    | (value=(BareIntLiteral
              | FullIntLiteral)(type=unsigned_int_subtype)? cast=int_cast) #UnsignedIntLiteral;

float_literal
    : value=FloatLiteral type=float_subtype? cast=float_cast;

int_cast
    : As (signed_int_subtype
         | unsigned_int_subtype);

float_cast
    : As float_subtype;

float_subtype
    : F32
    | F64;

signed_int_subtype
    : I8
    | I16
    | I32
    | I64
    | I128
    | ISIZE;

unsigned_int_subtype
    : U8
    | U16
    | U32
    | U64
    | U128
    | USIZE;

function
    : function_spec body=block;

function_spec
    : doc=doc_comment? attrs=outer_attribute* vis=visibility? Fn name=function_name
    lifetimes=lifetime_spec? LeftParen params=parameter_list? RightParen
    return_type?;

function_name
    : Ident;

extern_block
    : attrs=outer_attribute* Extern LeftBrace (extern_function_statement (Comma
                                                                                     extern_function_statement)*)?;

extern_function_statement
    : inner_attribute* attrs=outer_attribute* function_spec Semicolon;

closure
    : (attrs=outer_attribute* lifetimes=lifetime_spec? mv=Move? Pipe params=parameter_list?
              Pipe return_type? (body=block
                                | expr=expression))
    | (attrs=outer_attribute* mv=Move? DoublePipe return_type? (body=block
                                                               | expr=expression))
    | (attrs=outer_attribute* mv=Move? Pipe Pipe return_type? (body=block
                                                              | expr=expression));

struct
    : (doc=doc_comment? outer_attribute* Struct name=Ident lifetimes=lifetime_spec?
              LeftBrace (struct_item (Comma struct_item)* Comma?)? RightBrace) #PlainStruct

// XXX could split this out and include it in statement_body - checking for Semicolon here may cause problems
    | (doc=doc_comment? outer_attribute* Struct name=Ident lifetimes=lifetime_spec?
              LeftParen (type_spec (Comma type_spec)* Comma?)? RightParen
              Semicolon) #TupleStruct;

struct_item
    : (doc=doc_comment? outer_attribute* inner_attribute* name=Ident Colon props=param_props
              type=type_spec);

struct_instantiation
    : type=type_spec LeftBrace (struct_instantiation_item (Comma
                                                                      struct_instantiation_item)*
                                       Comma?)? RightBrace;

struct_instantiation_item
    : (name=Ident Colon params=param_props expression) #ExplictStructItem
    | (DotDot name=Ident) #CopyStructItem;

lifetime_spec
    : LeftAngleBracket first=lifetime (Comma more=lifetime)* RightAngleBracket;

lifetime
    : Lifetime;

visibility
    : Pub constraint=visibility_restriction?;

// Note that `pub(` does not necessarily signal the beginning of a visibility
// restriction! For example:
//
//     struct T(i32, i32, pub(i32));
//
// Here the `(` is part of the type `(i32)`.
visibility_restriction
    : LeftParen restrictedTo=Crate RightParen #CrateVisibility
    | LeftParen restrictedTo=Super RightParen #SuperVisibility
    | LeftParen In restrictedTo=Ident RightParen #ExplicitVisibility;

parameter_list
    : (parameter_spec (Comma parameter_spec)*)
    | (selfref=Ampersand? self=SelfRef (Comma parameter_spec)*);

parameter_spec
    : props=param_props name=variable_name (Colon Ampersand? life=lifetime? type=type_spec)?;

param_props
    : ref=Ampersand? life=lifetime? mutable=Mut? dynamic=Dyn?;

return_type
    : Arrow (type=type_spec
            | typePattern=type_pattern);

type_pattern
    : LeftParen type_spec (Comma type_spec) RightParen;

type_spec
    : path=path_head? type=Ident { referencedTypes.add(_ctx.getText()); } #Named
    | type=intrinsic_type #Intrinsic
    | LeftParen RightParen #Unit;

extern_import
    : Extern Crate create=Ident;

use_path
    : path_head end=use_path_end;

explicit_path
    : path_head end=Ident;

path_head
    : start=path_start segments=path_segment*;

/*
    | (start=path_start segments=path_segment* last=Ident As end=Ident)
    | (start=path_start segments=path_segment* LeftBrace end=Ident (Comma Ident)* RightBrace)
    | (start=path_start segments=path_segment* Asterisk)
*/
 use_path_end
    : end=Ident
    | last=Ident As end=Ident
    | LeftBrace end=Ident (Comma more=Ident) RightBrace;

path_segment
    : item=Ident DoubleColon;

path_start
    : item=SelfPath
    | item=SuperPath
    | DoubleColon
    | (item=Ident DoubleColon);

inner_attribute
    : InnerAttrPrefix metaitem RightBracket;

outer_attribute
    : AttrPrefix metaitem RightBracket;

metaitem
    : id=Ident
    | id=Ident LeftParen RightParen
    | id=Ident Equals literal
    | id=Ident LeftParen literal RightParen
    | id=Ident LeftParen metaitem (Comma metaitem) RightParen;

logical_operator
    : DoubleAmpersand
    | DoublePipe
    | Circumflex;

comparison_operator
    : DoubleEquals
    | LessThanOrEquals
    | GreaterThanOrEquals
    | LeftAngleBracket
    | RightAngleBracket
    | NotEquals;

arithmetic_operator
    : Plus
    | Minus
    | Asterisk
    | Slash
    | Percent;

shift_operator
    : ShiftLeft
    | ShiftRight;

assignment_operator
    : Equals
    | AssignMultiply
    | AssignDivide
    | AssignMod
    | AssignPlus
    | AssignMinus
    | AssignAnd
    | AssignOr
    | AssignShiftLeft
    | AssignShiftRight;

// unit_type : '()';
intrinsic_type
    : I8
    | I16
    | I32
    | I64
    | I128
    | ISIZE
    | F32
    | F64
    | U8
    | U16
    | U32
    | U64
    | U128
    | USIZE
    | Char
    | Bool;

doc_comment
    : DocCommentLine+;

enclosing_doc_comment
    : EnclosingDocCommentLine+;

aawoo
    : EnclosingDocCommentLine;

EnclosingDocCommentLine
    : EnclosingDocCommentPrefix ~[\r\n]*;

// Lexer
DocCommentLine
    : DocCommentPrefix ~[\r\n]*;

LineComment
    : LineCommentPrefix ~[\r\n]* -> channel(1);

BlockComment
    : BlockCommentPrefix (~[*/]
                         | Slash* BlockComment
                         | Slash+ (~[*/])
                         | Asterisk+ ~[*/])* Asterisk+ Slash -> channel(2);

Lifetime
    : SingleQuote (IDENT
                  | Static);

Whitespace
    : [ \t\r\n]+ -> channel(1);

BooleanLiteral
    : True
    | False;

RangeExclusive
    : DEC_DIGITS DotDot DEC_DIGITS;

MatchRangeInclusive
    : DEC_DIGITS TripleDot DEC_DIGITS;

RangeInclusive
    : DEC_DIGITS DotDotEquals DEC_DIGITS;

I8
    : 'i8';

I16
    : 'i16';

I32
    : 'i32';

I64
    : 'i64';

I128
    : 'i128';

F32
    : 'f32';

F64
    : 'f64';

U8
    : 'u8';

U16
    : 'u16';

U32
    : 'u32';

U64
    : 'u64';

U128
    : 'u128';

USIZE
    : 'usize';

ISIZE
    : 'isize';

DocCommentPrefix
    : '///';

EnclosingDocCommentPrefix
    : '//!';

RawPointerConst
    : '*const';

RawPointerMutable
    : '*mut';

SelfPath
    : 'self::';

SuperPath
    : 'super::';

SelfRef
    : '&self';

As
    : 'as';

Auto
    : 'auto';

Bool
    : 'bool';

Box
    : 'box';

Break
    : 'break';

Crate
    : 'crate';

Char
    : 'char';

Const
    : 'const';

Continue
    : 'continue';

Default
    : 'default';

Dyn
    : 'dyn';

Else
    : 'else';

Enum
    : 'enum';

Extern
    : 'extern';

False
    : 'false';

For
    : 'for';

Let
    : 'let';

Loop
    : 'loop';

If
    : 'if';

Match
    : 'match';

Mod
    : 'mod';

Move
    : 'move';

Pub
    : 'pub';

Ref
    : 'ref';

Return
    : 'return';

Super
    : 'super';

Static
    : 'static';

Struct
    : 'struct';

Self
    : 'self';

SelfCaps
    : 'Self';

True
    : 'true';

Type
    : 'type';

Trait
    : 'trait';

Impl
    : 'impl';

Union
    : 'union';

Unsafe
    : 'unsafe';

Use
    : 'use';

Where
    : 'where';

While
    : 'while';

Mut
    : 'mut';

AttrPrefix
    : '#[';

AssignShiftLeft
    : '<<=';

AssignShiftRight
    : '>>=';

ByteLiteralPrefix
    : 'b\'';

ByteStringPrefix
    : 'b"';

TripleDot
    : '...';

InnerAttrPrefix
    : '#![';

DotDotEquals
    : '..=';

Fn
    : 'fn';

In
    : 'in';

HexLiteralPrefix
    : '0x';

OctalLiteralPrefix
    : '0o';

BitsLiteralPrefix
    : '0b';

BlockCommentPrefix
    : '/*';

LineCommentPrefix
    : '//';

DoubleAmpersand
    : '&&';

DoubleColon
    : '::';

DoubleEquals
    : '==';

DoublePipe
    : '||';

DoubleBackslash
    : '\\\\';

Arrow
    : '->';

FatArrow
    : '=>';

NotEquals
    : '!=';

LessThanOrEquals
    : '<=';

GreaterThanOrEquals
    : '>=';

ShiftLeft
    : '<<';

ShiftRight
    : '>>';

AssignMultiply
    : '*=';

AssignDivide
    : '/=';

AssignMod
    : '%=';

AssignPlus
    : '+=';

AssignMinus
    : '-=';

AssignAnd
    : '&=';

AssignXor
    : '^=';

AssignOr
    : '|=';

Newline
    : '\n';

CarriageReturn
    : '\r';

Ampersand
    : '&';

Quote
    : '"';

SingleQuote
    : '\'';

Backslash
    : '\\';

Bang
    : '!';

Colon
    : ':';

Comma
    : ',';

Dot
    : '.';

DotDot
    : '..';

LeftParen
    : '(';

RightParen
    : ')';

LeftBrace
    : '{';

RightBrace
    : '}';

LeftBracket
    : '[';

RightBracket
    : ']';

Equals
    : '=';

Percent
    : '%';

LeftAngleBracket
    : '<';

RightAngleBracket
    : '>';

Hash
    : '#';

Plus
    : '+';

Minus
    : '-';

Slash
    : '/';

Asterisk
    : '*';

Underscore
    : '_';

QuestionMark
    : '?';

AtSymbol
    : '@';

Pipe
    : '|';

Circumflex
    : '^';

Space
    : ' ';

DollarSign
    : '$';

Semicolon
    : ';';

Ident
    : IDENT;

CharLiteral
    : SingleQuote (CHAR
                  | Quote) SingleQuote;

StringLiteral
    : Quote STRING_ELEMENT* Quote
    | 'r' RAW_STRING_BODY;

ByteLiteral
    : ByteLiteralPrefix (BYTE
                        | Quote) SingleQuote;

ByteStringLiteral
    : ByteStringPrefix BYTE_STRING_ELEMENT* Quote
    | 'br' RAW_BYTE_STRING_BODY;

// BareIntLiteral and FullIntLiteral both match '123'; BareIntLiteral wins by virtue of
// appearing first in the file. (This comment is to point out the dependency on
// a less-than-obvious ANTLR rule.)
BareIntLiteral
    : DEC_DIGITS;

FullIntLiteral
    : DEC_DIGITS INT_SUFFIX?
    | HexLiteralPrefix Underscore* [0-9a-fA-F] [0-9a-fA-F_]*
    | OctalLiteralPrefix Underscore* [0-7] [0-7_]*
    | BitsLiteralPrefix Underscore* [01] [01_]*;

// Some lookahead is required here. ANTLR does not support this
// except by injecting some Java code into the middle of the pattern.
//
// A floating-point literal may end with a dot, but:
//
// *   `100..f()` is parsed as `100 .. f()`, not `100. .f()`,
//     contrary to the usual rule that lexers are greedy.
//
// *   Similarly, but less important, a letter or underscore after `.`
//     causes the dot to be interpreted as a separate token by itself,
//     so that `1.abs()` parses a method call. The type checker will
//     later reject it, though.
//
FloatLiteral
    : DEC_DIGITS Dot [0-9] [0-9_]* EXPONENT?
    | DEC_DIGITS (Dot {
        /* dot followed by another dot is a range, not a float */
                                        _input.LA(1) != Dot &&
                                        /* dot followed by an identifier is an integer with a function call, not a float */
                                        _input.LA(1) != Underscore &&
                                        !(_input.LA(1) >= 'a' && _input.LA(1) <= 'z') &&
                                        !(_input.LA(1) >= 'A' && _input.LA(1) <= 'Z')
 }?)
    | DEC_DIGITS EXPONENT
    | DEC_DIGITS;

// Fragments used for literals below here
fragment SIMPLE_ESCAPE
    : Backslash [0nrt'"\\];

fragment CHAR
    : ~['"\r\n\\\ud800-\udfff]// a single BMP character other than a backslash, newline, or quote
    | [\ud800-\udbff] [\udc00-\udfff]// a single non-BMP character (hack for Java)
    | SIMPLE_ESCAPE
    | '\\x' [0-7] [0-9a-fA-F]
    | '\\u{' [0-9a-fA-F]+ RightBrace;

fragment OTHER_STRING_ELEMENT
    : SingleQuote
    | Backslash CarriageReturn? Newline [ \t]*
    | CarriageReturn
    | Newline;

fragment STRING_ELEMENT
    : CHAR
    | OTHER_STRING_ELEMENT;

fragment RAW_CHAR
    : ~[\ud800-\udfff]// any BMP character
    | [\ud800-\udbff] [\udc00-\udfff]; // any non-BMP character (hack for Java)
    // Here we use a non-greedy match to implement the
// (non-regular) rules about raw string syntax.
fragment RAW_STRING_BODY
    : Quote RAW_CHAR*? Quote
    | Hash RAW_STRING_BODY Hash;

fragment BYTE
    : Space// any ASCII character from 32 (space) to 126 (`~`),
    | Bang// except 34 (double quote), 39 (single quote), and 92 (backslash)
    | [#-&]
    | [(-[]
    | RightBracket
    | Circumflex
    | [_-~]
    | SIMPLE_ESCAPE
    | '\\x' [0-9a-fA-F] [0-9a-fA-F];

fragment BYTE_STRING_ELEMENT
    : BYTE
    | OTHER_STRING_ELEMENT;

fragment RAW_BYTE_STRING_BODY
    : Quote [\t\r\n -~]*? Quote
    | Hash RAW_BYTE_STRING_BODY Hash;

fragment DEC_DIGITS
    : [0-9] [0-9_]*;

fragment INT_SUFFIX
    : [ui] ('8Pipe16Pipe32Pipe64Pipesize');

fragment EXPONENT
    : [Ee] [+-]? Underscore* [0-9] [0-9_]*;

fragment FLOAT_SUFFIX
    : F32
    | F64;

fragment IDENT
    : XID_Start XID_Continue*;

fragment XID_Start
    : '\u0041'..'\u005a'
    | '_'
    | '\u0061'..'\u007a'
    | '\u00aa'
    | '\u00b5'
    | '\u00ba'
    | '\u00c0'..'\u00d6'
    | '\u00d8'..'\u00f6'
    | '\u00f8'..'\u0236'
    | '\u0250'..'\u02c1'
    | '\u02c6'..'\u02d1'
    | '\u02e0'..'\u02e4'
    | '\u02ee'
    | '\u0386'
    | '\u0388'..'\u038a'
    | '\u038c'
    | '\u038e'..'\u03a1'
    | '\u03a3'..'\u03ce'
    | '\u03d0'..'\u03f5'
    | '\u03f7'..'\u03fb'
    | '\u0400'..'\u0481'
    | '\u048a'..'\u04ce'
    | '\u04d0'..'\u04f5'
    | '\u04f8'..'\u04f9'
    | '\u0500'..'\u050f'
    | '\u0531'..'\u0556'
    | '\u0559'
    | '\u0561'..'\u0587'
    | '\u05d0'..'\u05ea'
    | '\u05f0'..'\u05f2'
    | '\u0621'..'\u063a'
    | '\u0640'..'\u064a'
    | '\u066e'..'\u066f'
    | '\u0671'..'\u06d3'
    | '\u06d5'
    | '\u06e5'..'\u06e6'
    | '\u06ee'..'\u06ef'
    | '\u06fa'..'\u06fc'
    | '\u06ff'
    | '\u0710'
    | '\u0712'..'\u072f'
    | '\u074d'..'\u074f'
    | '\u0780'..'\u07a5'
    | '\u07b1'
    | '\u0904'..'\u0939'
    | '\u093d'
    | '\u0950'
    | '\u0958'..'\u0961'
    | '\u0985'..'\u098c'
    | '\u098f'..'\u0990'
    | '\u0993'..'\u09a8'
    | '\u09aa'..'\u09b0'
    | '\u09b2'
    | '\u09b6'..'\u09b9'
    | '\u09bd'
    | '\u09dc'..'\u09dd'
    | '\u09df'..'\u09e1'
    | '\u09f0'..'\u09f1'
    | '\u0a05'..'\u0a0a'
    | '\u0a0f'..'\u0a10'
    | '\u0a13'..'\u0a28'
    | '\u0a2a'..'\u0a30'
    | '\u0a32'..'\u0a33'
    | '\u0a35'..'\u0a36'
    | '\u0a38'..'\u0a39'
    | '\u0a59'..'\u0a5c'
    | '\u0a5e'
    | '\u0a72'..'\u0a74'
    | '\u0a85'..'\u0a8d'
    | '\u0a8f'..'\u0a91'
    | '\u0a93'..'\u0aa8'
    | '\u0aaa'..'\u0ab0'
    | '\u0ab2'..'\u0ab3'
    | '\u0ab5'..'\u0ab9'
    | '\u0abd'
    | '\u0ad0'
    | '\u0ae0'..'\u0ae1'
    | '\u0b05'..'\u0b0c'
    | '\u0b0f'..'\u0b10'
    | '\u0b13'..'\u0b28'
    | '\u0b2a'..'\u0b30'
    | '\u0b32'..'\u0b33'
    | '\u0b35'..'\u0b39'
    | '\u0b3d'
    | '\u0b5c'..'\u0b5d'
    | '\u0b5f'..'\u0b61'
    | '\u0b71'
    | '\u0b83'
    | '\u0b85'..'\u0b8a'
    | '\u0b8e'..'\u0b90'
    | '\u0b92'..'\u0b95'
    | '\u0b99'..'\u0b9a'
    | '\u0b9c'
    | '\u0b9e'..'\u0b9f'
    | '\u0ba3'..'\u0ba4'
    | '\u0ba8'..'\u0baa'
    | '\u0bae'..'\u0bb5'
    | '\u0bb7'..'\u0bb9'
    | '\u0c05'..'\u0c0c'
    | '\u0c0e'..'\u0c10'
    | '\u0c12'..'\u0c28'
    | '\u0c2a'..'\u0c33'
    | '\u0c35'..'\u0c39'
    | '\u0c60'..'\u0c61'
    | '\u0c85'..'\u0c8c'
    | '\u0c8e'..'\u0c90'
    | '\u0c92'..'\u0ca8'
    | '\u0caa'..'\u0cb3'
    | '\u0cb5'..'\u0cb9'
    | '\u0cbd'
    | '\u0cde'
    | '\u0ce0'..'\u0ce1'
    | '\u0d05'..'\u0d0c'
    | '\u0d0e'..'\u0d10'
    | '\u0d12'..'\u0d28'
    | '\u0d2a'..'\u0d39'
    | '\u0d60'..'\u0d61'
    | '\u0d85'..'\u0d96'
    | '\u0d9a'..'\u0db1'
    | '\u0db3'..'\u0dbb'
    | '\u0dbd'
    | '\u0dc0'..'\u0dc6'
    | '\u0e01'..'\u0e30'
    | '\u0e32'
    | '\u0e40'..'\u0e46'
    | '\u0e81'..'\u0e82'
    | '\u0e84'
    | '\u0e87'..'\u0e88'
    | '\u0e8a'
    | '\u0e8d'
    | '\u0e94'..'\u0e97'
    | '\u0e99'..'\u0e9f'
    | '\u0ea1'..'\u0ea3'
    | '\u0ea5'
    | '\u0ea7'
    | '\u0eaa'..'\u0eab'
    | '\u0ead'..'\u0eb0'
    | '\u0eb2'
    | '\u0ebd'
    | '\u0ec0'..'\u0ec4'
    | '\u0ec6'
    | '\u0edc'..'\u0edd'
    | '\u0f00'
    | '\u0f40'..'\u0f47'
    | '\u0f49'..'\u0f6a'
    | '\u0f88'..'\u0f8b'
    | '\u1000'..'\u1021'
    | '\u1023'..'\u1027'
    | '\u1029'..'\u102a'
    | '\u1050'..'\u1055'
    | '\u10a0'..'\u10c5'
    | '\u10d0'..'\u10f8'
    | '\u1100'..'\u1159'
    | '\u115f'..'\u11a2'
    | '\u11a8'..'\u11f9'
    | '\u1200'..'\u1206'
    | '\u1208'..'\u1246'
    | '\u1248'
    | '\u124a'..'\u124d'
    | '\u1250'..'\u1256'
    | '\u1258'
    | '\u125a'..'\u125d'
    | '\u1260'..'\u1286'
    | '\u1288'
    | '\u128a'..'\u128d'
    | '\u1290'..'\u12ae'
    | '\u12b0'
    | '\u12b2'..'\u12b5'
    | '\u12b8'..'\u12be'
    | '\u12c0'
    | '\u12c2'..'\u12c5'
    | '\u12c8'..'\u12ce'
    | '\u12d0'..'\u12d6'
    | '\u12d8'..'\u12ee'
    | '\u12f0'..'\u130e'
    | '\u1310'
    | '\u1312'..'\u1315'
    | '\u1318'..'\u131e'
    | '\u1320'..'\u1346'
    | '\u1348'..'\u135a'
    | '\u13a0'..'\u13f4'
    | '\u1401'..'\u166c'
    | '\u166f'..'\u1676'
    | '\u1681'..'\u169a'
    | '\u16a0'..'\u16ea'
    | '\u16ee'..'\u16f0'
    | '\u1700'..'\u170c'
    | '\u170e'..'\u1711'
    | '\u1720'..'\u1731'
    | '\u1740'..'\u1751'
    | '\u1760'..'\u176c'
    | '\u176e'..'\u1770'
    | '\u1780'..'\u17b3'
    | '\u17d7'
    | '\u17dc'
    | '\u1820'..'\u1877'
    | '\u1880'..'\u18a8'
    | '\u1900'..'\u191c'
    | '\u1950'..'\u196d'
    | '\u1970'..'\u1974'
    | '\u1d00'..'\u1d6b'
    | '\u1e00'..'\u1e9b'
    | '\u1ea0'..'\u1ef9'
    | '\u1f00'..'\u1f15'
    | '\u1f18'..'\u1f1d'
    | '\u1f20'..'\u1f45'
    | '\u1f48'..'\u1f4d'
    | '\u1f50'..'\u1f57'
    | '\u1f59'
    | '\u1f5b'
    | '\u1f5d'
    | '\u1f5f'..'\u1f7d'
    | '\u1f80'..'\u1fb4'
    | '\u1fb6'..'\u1fbc'
    | '\u1fbe'
    | '\u1fc2'..'\u1fc4'
    | '\u1fc6'..'\u1fcc'
    | '\u1fd0'..'\u1fd3'
    | '\u1fd6'..'\u1fdb'
    | '\u1fe0'..'\u1fec'
    | '\u1ff2'..'\u1ff4'
    | '\u1ff6'..'\u1ffc'
    | '\u2071'
    | '\u207f'
    | '\u2102'
    | '\u2107'
    | '\u210a'..'\u2113'
    | '\u2115'
    | '\u2118'..'\u211d'
    | '\u2124'
    | '\u2126'
    | '\u2128'
    | '\u212a'..'\u2131'
    | '\u2133'..'\u2139'
    | '\u213d'..'\u213f'
    | '\u2145'..'\u2149'
    | '\u2160'..'\u2183'
    | '\u3005'..'\u3007'
    | '\u3021'..'\u3029'
    | '\u3031'..'\u3035'
    | '\u3038'..'\u303c'
    | '\u3041'..'\u3096'
    | '\u309d'..'\u309f'
    | '\u30a1'..'\u30fa'
    | '\u30fc'..'\u30ff'
    | '\u3105'..'\u312c'
    | '\u3131'..'\u318e'
    | '\u31a0'..'\u31b7'
    | '\u31f0'..'\u31ff'
    | '\u3400'..'\u4db5'
    | '\u4e00'..'\u9fa5'
    | '\ua000'..'\ua48c'
    | '\uac00'..'\ud7a3'
    | '\uf900'..'\ufa2d'
    | '\ufa30'..'\ufa6a'
    | '\ufb00'..'\ufb06'
    | '\ufb13'..'\ufb17'
    | '\ufb1d'
    | '\ufb1f'..'\ufb28'
    | '\ufb2a'..'\ufb36'
    | '\ufb38'..'\ufb3c'
    | '\ufb3e'
    | '\ufb40'..'\ufb41'
    | '\ufb43'..'\ufb44'
    | '\ufb46'..'\ufbb1'
    | '\ufbd3'..'\ufc5d'
    | '\ufc64'..'\ufd3d'
    | '\ufd50'..'\ufd8f'
    | '\ufd92'..'\ufdc7'
    | '\ufdf0'..'\ufdf9'
    | '\ufe71'
    | '\ufe73'
    | '\ufe77'
    | '\ufe79'
    | '\ufe7b'
    | '\ufe7d'
    | '\ufe7f'..'\ufefc'
    | '\uff21'..'\uff3a'
    | '\uff41'..'\uff5a'
    | '\uff66'..'\uff9d'
    | '\uffa0'..'\uffbe'
    | '\uffc2'..'\uffc7'
    | '\uffca'..'\uffcf'
    | '\uffd2'..'\uffd7'
    | '\uffda'..'\uffdc'
    | '\ud800' '\udc00'..'\udc0a'
    | '\ud800' '\udc0d'..'\udc25'
    | '\ud800' '\udc28'..'\udc39'
    | '\ud800' '\udc3c'..'\udc3c'
    | '\ud800' '\udc3f'..'\udc4c'
    | '\ud800' '\udc50'..'\udc5c'
    | '\ud800' '\udc80'..'\udcf9'
    | '\ud800' '\udf00'..'\udf1d'
    | '\ud800' '\udf30'..'\udf49'
    | '\ud800' '\udf80'..'\udf9c'
    | '\ud801' '\ue000'..'\ue09c'
    | '\ud802' '\ue400'..'\ue404'
    | '\ud802' '\u0808'
    | '\ud802' '\ue40a'..'\ue434'
    | '\ud802' '\ue437'..'\ue437'
    | '\ud802' '\u083c'
    | '\ud802' '\u083f'
    | '\ud835' '\ub000'..'\ub053'
    | '\ud835' '\ub056'..'\ub09b'
    | '\ud835' '\ub09e'..'\ub09e'
    | '\ud835' '\ud4a2'
    | '\ud835' '\ub0a5'..'\ub0a5'
    | '\ud835' '\ub0a9'..'\ub0ab'
    | '\ud835' '\ub0ae'..'\ub0b8'
    | '\ud835' '\ud4bb'
    | '\ud835' '\ub0bd'..'\ub0c2'
    | '\ud835' '\ub0c5'..'\ub104'
    | '\ud835' '\ub107'..'\ub109'
    | '\ud835' '\ub10d'..'\ub113'
    | '\ud835' '\ub116'..'\ub11b'
    | '\ud835' '\ub11e'..'\ub138'
    | '\ud835' '\ub13b'..'\ub13d'
    | '\ud835' '\ub140'..'\ub143'
    | '\ud835' '\ud546'
    | '\ud835' '\ub14a'..'\ub14f'
    | '\ud835' '\ub152'..'\ub2a2'
    | '\ud835' '\ub2a8'..'\ub2bf'
    | '\ud835' '\ub2c2'..'\ub2d9'
    | '\ud835' '\ub2dc'..'\ub2f9'
    | '\ud835' '\ub2fc'..'\ub313'
    | '\ud835' '\ub316'..'\ub333'
    | '\ud835' '\ub336'..'\ub34d'
    | '\ud835' '\ub350'..'\ub36d'
    | '\ud835' '\ub370'..'\ub387'
    | '\ud835' '\ub38a'..'\ub3a7'
    | '\ud835' '\ub3aa'..'\ub3c1'
    | '\ud835' '\ub3c4'..'\ub3c8'
    | '\ud840' '\udc00'..'\udffe'
    | '\ud841' '\ue000'..'\ue3fe'
    | '\ud842' '\ue400'..'\ue7fe'
    | '\ud843' '\ue800'..'\uebfe'
    | '\ud844' '\uec00'..'\ueffe'
    | '\ud845' '\uf000'..'\uf3fe'
    | '\ud846' '\uf400'..'\uf7fe'
    | '\ud847' '\uf800'..'\ufbfe'
    | '\ud848' '\ufc00'..'\ufffe'
    | '\ud849' '\u0000'..'\u03fe'
    | '\ud84a' '\u0400'..'\u07fe'
    | '\ud84b' '\u0800'..'\u0bfe'
    | '\ud84c' '\u0c00'..'\u0ffe'
    | '\ud84d' '\u1000'..'\u13fe'
    | '\ud84e' '\u1400'..'\u17fe'
    | '\ud84f' '\u1800'..'\u1bfe'
    | '\ud850' '\u1c00'..'\u1ffe'
    | '\ud851' '\u2000'..'\u23fe'
    | '\ud852' '\u2400'..'\u27fe'
    | '\ud853' '\u2800'..'\u2bfe'
    | '\ud854' '\u2c00'..'\u2ffe'
    | '\ud855' '\u3000'..'\u33fe'
    | '\ud856' '\u3400'..'\u37fe'
    | '\ud857' '\u3800'..'\u3bfe'
    | '\ud858' '\u3c00'..'\u3ffe'
    | '\ud859' '\u4000'..'\u43fe'
    | '\ud85a' '\u4400'..'\u47fe'
    | '\ud85b' '\u4800'..'\u4bfe'
    | '\ud85c' '\u4c00'..'\u4ffe'
    | '\ud85d' '\u5000'..'\u53fe'
    | '\ud85e' '\u5400'..'\u57fe'
    | '\ud85f' '\u5800'..'\u5bfe'
    | '\ud860' '\u5c00'..'\u5ffe'
    | '\ud861' '\u6000'..'\u63fe'
    | '\ud862' '\u6400'..'\u67fe'
    | '\ud863' '\u6800'..'\u6bfe'
    | '\ud864' '\u6c00'..'\u6ffe'
    | '\ud865' '\u7000'..'\u73fe'
    | '\ud866' '\u7400'..'\u77fe'
    | '\ud867' '\u7800'..'\u7bfe'
    | '\ud868' '\u7c00'..'\u7ffe'
    | '\ud869' '\u8000'..'\u82d5'
    | '\ud87e' '\ud400'..'\ud61c';

fragment XID_Continue
    : '\u0030'..'\u0039'
    | '\u0041'..'\u005a'
    | '\u005f'
    | '\u0061'..'\u007a'
    | '\u00aa'
    | '\u00b5'
    | '\u00b7'
    | '\u00ba'
    | '\u00c0'..'\u00d6'
    | '\u00d8'..'\u00f6'
    | '\u00f8'..'\u0236'
    | '\u0250'..'\u02c1'
    | '\u02c6'..'\u02d1'
    | '\u02e0'..'\u02e4'
    | '\u02ee'
    | '\u0300'..'\u0357'
    | '\u035d'..'\u036f'
    | '\u0386'
    | '\u0388'..'\u038a'
    | '\u038c'
    | '\u038e'..'\u03a1'
    | '\u03a3'..'\u03ce'
    | '\u03d0'..'\u03f5'
    | '\u03f7'..'\u03fb'
    | '\u0400'..'\u0481'
    | '\u0483'..'\u0486'
    | '\u048a'..'\u04ce'
    | '\u04d0'..'\u04f5'
    | '\u04f8'..'\u04f9'
    | '\u0500'..'\u050f'
    | '\u0531'..'\u0556'
    | '\u0559'
    | '\u0561'..'\u0587'
    | '\u0591'..'\u05a1'
    | '\u05a3'..'\u05b9'
    | '\u05bb'..'\u05bd'
    | '\u05bf'
    | '\u05c1'..'\u05c2'
    | '\u05c4'
    | '\u05d0'..'\u05ea'
    | '\u05f0'..'\u05f2'
    | '\u0610'..'\u0615'
    | '\u0621'..'\u063a'
    | '\u0640'..'\u0658'
    | '\u0660'..'\u0669'
    | '\u066e'..'\u06d3'
    | '\u06d5'..'\u06dc'
    | '\u06df'..'\u06e8'
    | '\u06ea'..'\u06fc'
    | '\u06ff'
    | '\u0710'..'\u074a'
    | '\u074d'..'\u074f'
    | '\u0780'..'\u07b1'
    | '\u0901'..'\u0939'
    | '\u093c'..'\u094d'
    | '\u0950'..'\u0954'
    | '\u0958'..'\u0963'
    | '\u0966'..'\u096f'
    | '\u0981'..'\u0983'
    | '\u0985'..'\u098c'
    | '\u098f'..'\u0990'
    | '\u0993'..'\u09a8'
    | '\u09aa'..'\u09b0'
    | '\u09b2'
    | '\u09b6'..'\u09b9'
    | '\u09bc'..'\u09c4'
    | '\u09c7'..'\u09c8'
    | '\u09cb'..'\u09cd'
    | '\u09d7'
    | '\u09dc'..'\u09dd'
    | '\u09df'..'\u09e3'
    | '\u09e6'..'\u09f1'
    | '\u0a01'..'\u0a03'
    | '\u0a05'..'\u0a0a'
    | '\u0a0f'..'\u0a10'
    | '\u0a13'..'\u0a28'
    | '\u0a2a'..'\u0a30'
    | '\u0a32'..'\u0a33'
    | '\u0a35'..'\u0a36'
    | '\u0a38'..'\u0a39'
    | '\u0a3c'
    | '\u0a3e'..'\u0a42'
    | '\u0a47'..'\u0a48'
    | '\u0a4b'..'\u0a4d'
    | '\u0a59'..'\u0a5c'
    | '\u0a5e'
    | '\u0a66'..'\u0a74'
    | '\u0a81'..'\u0a83'
    | '\u0a85'..'\u0a8d'
    | '\u0a8f'..'\u0a91'
    | '\u0a93'..'\u0aa8'
    | '\u0aaa'..'\u0ab0'
    | '\u0ab2'..'\u0ab3'
    | '\u0ab5'..'\u0ab9'
    | '\u0abc'..'\u0ac5'
    | '\u0ac7'..'\u0ac9'
    | '\u0acb'..'\u0acd'
    | '\u0ad0'
    | '\u0ae0'..'\u0ae3'
    | '\u0ae6'..'\u0aef'
    | '\u0b01'..'\u0b03'
    | '\u0b05'..'\u0b0c'
    | '\u0b0f'..'\u0b10'
    | '\u0b13'..'\u0b28'
    | '\u0b2a'..'\u0b30'
    | '\u0b32'..'\u0b33'
    | '\u0b35'..'\u0b39'
    | '\u0b3c'..'\u0b43'
    | '\u0b47'..'\u0b48'
    | '\u0b4b'..'\u0b4d'
    | '\u0b56'..'\u0b57'
    | '\u0b5c'..'\u0b5d'
    | '\u0b5f'..'\u0b61'
    | '\u0b66'..'\u0b6f'
    | '\u0b71'
    | '\u0b82'..'\u0b83'
    | '\u0b85'..'\u0b8a'
    | '\u0b8e'..'\u0b90'
    | '\u0b92'..'\u0b95'
    | '\u0b99'..'\u0b9a'
    | '\u0b9c'
    | '\u0b9e'..'\u0b9f'
    | '\u0ba3'..'\u0ba4'
    | '\u0ba8'..'\u0baa'
    | '\u0bae'..'\u0bb5'
    | '\u0bb7'..'\u0bb9'
    | '\u0bbe'..'\u0bc2'
    | '\u0bc6'..'\u0bc8'
    | '\u0bca'..'\u0bcd'
    | '\u0bd7'
    | '\u0be7'..'\u0bef'
    | '\u0c01'..'\u0c03'
    | '\u0c05'..'\u0c0c'
    | '\u0c0e'..'\u0c10'
    | '\u0c12'..'\u0c28'
    | '\u0c2a'..'\u0c33'
    | '\u0c35'..'\u0c39'
    | '\u0c3e'..'\u0c44'
    | '\u0c46'..'\u0c48'
    | '\u0c4a'..'\u0c4d'
    | '\u0c55'..'\u0c56'
    | '\u0c60'..'\u0c61'
    | '\u0c66'..'\u0c6f'
    | '\u0c82'..'\u0c83'
    | '\u0c85'..'\u0c8c'
    | '\u0c8e'..'\u0c90'
    | '\u0c92'..'\u0ca8'
    | '\u0caa'..'\u0cb3'
    | '\u0cb5'..'\u0cb9'
    | '\u0cbc'..'\u0cc4'
    | '\u0cc6'..'\u0cc8'
    | '\u0cca'..'\u0ccd'
    | '\u0cd5'..'\u0cd6'
    | '\u0cde'
    | '\u0ce0'..'\u0ce1'
    | '\u0ce6'..'\u0cef'
    | '\u0d02'..'\u0d03'
    | '\u0d05'..'\u0d0c'
    | '\u0d0e'..'\u0d10'
    | '\u0d12'..'\u0d28'
    | '\u0d2a'..'\u0d39'
    | '\u0d3e'..'\u0d43'
    | '\u0d46'..'\u0d48'
    | '\u0d4a'..'\u0d4d'
    | '\u0d57'
    | '\u0d60'..'\u0d61'
    | '\u0d66'..'\u0d6f'
    | '\u0d82'..'\u0d83'
    | '\u0d85'..'\u0d96'
    | '\u0d9a'..'\u0db1'
    | '\u0db3'..'\u0dbb'
    | '\u0dbd'
    | '\u0dc0'..'\u0dc6'
    | '\u0dca'
    | '\u0dcf'..'\u0dd4'
    | '\u0dd6'
    | '\u0dd8'..'\u0ddf'
    | '\u0df2'..'\u0df3'
    | '\u0e01'..'\u0e3a'
    | '\u0e40'..'\u0e4e'
    | '\u0e50'..'\u0e59'
    | '\u0e81'..'\u0e82'
    | '\u0e84'
    | '\u0e87'..'\u0e88'
    | '\u0e8a'
    | '\u0e8d'
    | '\u0e94'..'\u0e97'
    | '\u0e99'..'\u0e9f'
    | '\u0ea1'..'\u0ea3'
    | '\u0ea5'
    | '\u0ea7'
    | '\u0eaa'..'\u0eab'
    | '\u0ead'..'\u0eb9'
    | '\u0ebb'..'\u0ebd'
    | '\u0ec0'..'\u0ec4'
    | '\u0ec6'
    | '\u0ec8'..'\u0ecd'
    | '\u0ed0'..'\u0ed9'
    | '\u0edc'..'\u0edd'
    | '\u0f00'
    | '\u0f18'..'\u0f19'
    | '\u0f20'..'\u0f29'
    | '\u0f35'
    | '\u0f37'
    | '\u0f39'
    | '\u0f3e'..'\u0f47'
    | '\u0f49'..'\u0f6a'
    | '\u0f71'..'\u0f84'
    | '\u0f86'..'\u0f8b'
    | '\u0f90'..'\u0f97'
    | '\u0f99'..'\u0fbc'
    | '\u0fc6'
    | '\u1000'..'\u1021'
    | '\u1023'..'\u1027'
    | '\u1029'..'\u102a'
    | '\u102c'..'\u1032'
    | '\u1036'..'\u1039'
    | '\u1040'..'\u1049'
    | '\u1050'..'\u1059'
    | '\u10a0'..'\u10c5'
    | '\u10d0'..'\u10f8'
    | '\u1100'..'\u1159'
    | '\u115f'..'\u11a2'
    | '\u11a8'..'\u11f9'
    | '\u1200'..'\u1206'
    | '\u1208'..'\u1246'
    | '\u1248'
    | '\u124a'..'\u124d'
    | '\u1250'..'\u1256'
    | '\u1258'
    | '\u125a'..'\u125d'
    | '\u1260'..'\u1286'
    | '\u1288'
    | '\u128a'..'\u128d'
    | '\u1290'..'\u12ae'
    | '\u12b0'
    | '\u12b2'..'\u12b5'
    | '\u12b8'..'\u12be'
    | '\u12c0'
    | '\u12c2'..'\u12c5'
    | '\u12c8'..'\u12ce'
    | '\u12d0'..'\u12d6'
    | '\u12d8'..'\u12ee'
    | '\u12f0'..'\u130e'
    | '\u1310'
    | '\u1312'..'\u1315'
    | '\u1318'..'\u131e'
    | '\u1320'..'\u1346'
    | '\u1348'..'\u135a'
    | '\u1369'..'\u1371'
    | '\u13a0'..'\u13f4'
    | '\u1401'..'\u166c'
    | '\u166f'..'\u1676'
    | '\u1681'..'\u169a'
    | '\u16a0'..'\u16ea'
    | '\u16ee'..'\u16f0'
    | '\u1700'..'\u170c'
    | '\u170e'..'\u1714'
    | '\u1720'..'\u1734'
    | '\u1740'..'\u1753'
    | '\u1760'..'\u176c'
    | '\u176e'..'\u1770'
    | '\u1772'..'\u1773'
    | '\u1780'..'\u17b3'
    | '\u17b6'..'\u17d3'
    | '\u17d7'
    | '\u17dc'..'\u17dd'
    | '\u17e0'..'\u17e9'
    | '\u180b'..'\u180d'
    | '\u1810'..'\u1819'
    | '\u1820'..'\u1877'
    | '\u1880'..'\u18a9'
    | '\u1900'..'\u191c'
    | '\u1920'..'\u192b'
    | '\u1930'..'\u193b'
    | '\u1946'..'\u196d'
    | '\u1970'..'\u1974'
    | '\u1d00'..'\u1d6b'
    | '\u1e00'..'\u1e9b'
    | '\u1ea0'..'\u1ef9'
    | '\u1f00'..'\u1f15'
    | '\u1f18'..'\u1f1d'
    | '\u1f20'..'\u1f45'
    | '\u1f48'..'\u1f4d'
    | '\u1f50'..'\u1f57'
    | '\u1f59'
    | '\u1f5b'
    | '\u1f5d'
    | '\u1f5f'..'\u1f7d'
    | '\u1f80'..'\u1fb4'
    | '\u1fb6'..'\u1fbc'
    | '\u1fbe'
    | '\u1fc2'..'\u1fc4'
    | '\u1fc6'..'\u1fcc'
    | '\u1fd0'..'\u1fd3'
    | '\u1fd6'..'\u1fdb'
    | '\u1fe0'..'\u1fec'
    | '\u1ff2'..'\u1ff4'
    | '\u1ff6'..'\u1ffc'
    | '\u203f'..'\u2040'
    | '\u2054'
    | '\u2071'
    | '\u207f'
    | '\u20d0'..'\u20dc'
    | '\u20e1'
    | '\u20e5'..'\u20ea'
    | '\u2102'
    | '\u2107'
    | '\u210a'..'\u2113'
    | '\u2115'
    | '\u2118'..'\u211d'
    | '\u2124'
    | '\u2126'
    | '\u2128'
    | '\u212a'..'\u2131'
    | '\u2133'..'\u2139'
    | '\u213d'..'\u213f'
    | '\u2145'..'\u2149'
    | '\u2160'..'\u2183'
    | '\u3005'..'\u3007'
    | '\u3021'..'\u302f'
    | '\u3031'..'\u3035'
    | '\u3038'..'\u303c'
    | '\u3041'..'\u3096'
    | '\u3099'..'\u309a'
    | '\u309d'..'\u309f'
    | '\u30a1'..'\u30ff'
    | '\u3105'..'\u312c'
    | '\u3131'..'\u318e'
    | '\u31a0'..'\u31b7'
    | '\u31f0'..'\u31ff'
    | '\u3400'..'\u4db5'
    | '\u4e00'..'\u9fa5'
    | '\ua000'..'\ua48c'
    | '\uac00'..'\ud7a3'
    | '\uf900'..'\ufa2d'
    | '\ufa30'..'\ufa6a'
    | '\ufb00'..'\ufb06'
    | '\ufb13'..'\ufb17'
    | '\ufb1d'..'\ufb28'
    | '\ufb2a'..'\ufb36'
    | '\ufb38'..'\ufb3c'
    | '\ufb3e'
    | '\ufb40'..'\ufb41'
    | '\ufb43'..'\ufb44'
    | '\ufb46'..'\ufbb1'
    | '\ufbd3'..'\ufc5d'
    | '\ufc64'..'\ufd3d'
    | '\ufd50'..'\ufd8f'
    | '\ufd92'..'\ufdc7'
    | '\ufdf0'..'\ufdf9'
    | '\ufe00'..'\ufe0f'
    | '\ufe20'..'\ufe23'
    | '\ufe33'..'\ufe34'
    | '\ufe4d'..'\ufe4f'
    | '\ufe71'
    | '\ufe73'
    | '\ufe77'
    | '\ufe79'
    | '\ufe7b'
    | '\ufe7d'
    | '\ufe7f'..'\ufefc'
    | '\uff10'..'\uff19'
    | '\uff21'..'\uff3a'
    | '\uff3f'
    | '\uff41'..'\uff5a'
    | '\uff65'..'\uffbe'
    | '\uffc2'..'\uffc7'
    | '\uffca'..'\uffcf'
    | '\uffd2'..'\uffd7'
    | '\uffda'..'\uffdc'
    | '\ud800' '\udc00'..'\udc0a'
    | '\ud800' '\udc0d'..'\udc25'
    | '\ud800' '\udc28'..'\udc39'
    | '\ud800' '\udc3c'..'\udc3c'
    | '\ud800' '\udc3f'..'\udc4c'
    | '\ud800' '\udc50'..'\udc5c'
    | '\ud800' '\udc80'..'\udcf9'
    | '\ud800' '\udf00'..'\udf1d'
    | '\ud800' '\udf30'..'\udf49'
    | '\ud800' '\udf80'..'\udf9c'
    | '\ud801' '\ue000'..'\ue09c'
    | '\ud801' '\ue0a0'..'\ue0a8'
    | '\ud802' '\ue400'..'\ue404'
    | '\ud802' '\u0808'
    | '\ud802' '\ue40a'..'\ue434'
    | '\ud802' '\ue437'..'\ue437'
    | '\ud802' '\u083c'
    | '\ud802' '\u083f'
    | '\ud834' '\uad65'..'\uad68'
    | '\ud834' '\uad6d'..'\uad71'
    | '\ud834' '\uad7b'..'\uad81'
    | '\ud834' '\uad85'..'\uad8a'
    | '\ud834' '\uadaa'..'\uadac'
    | '\ud835' '\ub000'..'\ub053'
    | '\ud835' '\ub056'..'\ub09b'
    | '\ud835' '\ub09e'..'\ub09e'
    | '\ud835' '\ud4a2'
    | '\ud835' '\ub0a5'..'\ub0a5'
    | '\ud835' '\ub0a9'..'\ub0ab'
    | '\ud835' '\ub0ae'..'\ub0b8'
    | '\ud835' '\ud4bb'
    | '\ud835' '\ub0bd'..'\ub0c2'
    | '\ud835' '\ub0c5'..'\ub104'
    | '\ud835' '\ub107'..'\ub109'
    | '\ud835' '\ub10d'..'\ub113'
    | '\ud835' '\ub116'..'\ub11b'
    | '\ud835' '\ub11e'..'\ub138'
    | '\ud835' '\ub13b'..'\ub13d'
    | '\ud835' '\ub140'..'\ub143'
    | '\ud835' '\ud546'
    | '\ud835' '\ub14a'..'\ub14f'
    | '\ud835' '\ub152'..'\ub2a2'
    | '\ud835' '\ub2a8'..'\ub2bf'
    | '\ud835' '\ub2c2'..'\ub2d9'
    | '\ud835' '\ub2dc'..'\ub2f9'
    | '\ud835' '\ub2fc'..'\ub313'
    | '\ud835' '\ub316'..'\ub333'
    | '\ud835' '\ub336'..'\ub34d'
    | '\ud835' '\ub350'..'\ub36d'
    | '\ud835' '\ub370'..'\ub387'
    | '\ud835' '\ub38a'..'\ub3a7'
    | '\ud835' '\ub3aa'..'\ub3c1'
    | '\ud835' '\ub3c4'..'\ub3c8'
    | '\ud835' '\ub3ce'..'\ub3fe'
    | '\ud840' '\udc00'..'\udffe'
    | '\ud841' '\ue000'..'\ue3fe'
    | '\ud842' '\ue400'..'\ue7fe'
    | '\ud843' '\ue800'..'\uebfe'
    | '\ud844' '\uec00'..'\ueffe'
    | '\ud845' '\uf000'..'\uf3fe'
    | '\ud846' '\uf400'..'\uf7fe'
    | '\ud847' '\uf800'..'\ufbfe'
    | '\ud848' '\ufc00'..'\ufffe'
    | '\ud849' '\u0000'..'\u03fe'
    | '\ud84a' '\u0400'..'\u07fe'
    | '\ud84b' '\u0800'..'\u0bfe'
    | '\ud84c' '\u0c00'..'\u0ffe'
    | '\ud84d' '\u1000'..'\u13fe'
    | '\ud84e' '\u1400'..'\u17fe'
    | '\ud84f' '\u1800'..'\u1bfe'
    | '\ud850' '\u1c00'..'\u1ffe'
    | '\ud851' '\u2000'..'\u23fe'
    | '\ud852' '\u2400'..'\u27fe'
    | '\ud853' '\u2800'..'\u2bfe'
    | '\ud854' '\u2c00'..'\u2ffe'
    | '\ud855' '\u3000'..'\u33fe'
    | '\ud856' '\u3400'..'\u37fe'
    | '\ud857' '\u3800'..'\u3bfe'
    | '\ud858' '\u3c00'..'\u3ffe'
    | '\ud859' '\u4000'..'\u43fe'
    | '\ud85a' '\u4400'..'\u47fe'
    | '\ud85b' '\u4800'..'\u4bfe'
    | '\ud85c' '\u4c00'..'\u4ffe'
    | '\ud85d' '\u5000'..'\u53fe'
    | '\ud85e' '\u5400'..'\u57fe'
    | '\ud85f' '\u5800'..'\u5bfe'
    | '\ud860' '\u5c00'..'\u5ffe'
    | '\ud861' '\u6000'..'\u63fe'
    | '\ud862' '\u6400'..'\u67fe'
    | '\ud863' '\u6800'..'\u6bfe'
    | '\ud864' '\u6c00'..'\u6ffe'
    | '\ud865' '\u7000'..'\u73fe'
    | '\ud866' '\u7400'..'\u77fe'
    | '\ud867' '\u7800'..'\u7bfe'
    | '\ud868' '\u7c00'..'\u7ffe'
    | '\ud869' '\u8000'..'\u82d5'
    | '\ud87e' '\ud400'..'\ud61c'
    | '\udb40' '\udd00'..'\uddee';