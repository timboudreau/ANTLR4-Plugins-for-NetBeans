grammar Rust;

@parser::header { import java.util.*; }

@parser::members { Set<String> importedTypes = new HashSet<>();
                      Set<String> referencedTypes = new HashSet<>();
                      Set<String> definedTypes = new HashSet<>();
                      Map<String,String> aliasedTypes = new HashMap<>();
}

import xidstart, xidcontinue;

compilation_unit : items EOF;

items : ( use_statement | extern_import_statement | extern_block | inner_attribute | unsafe_block | struct | function | mod)*;

mod : doc=doc_comment? outer_attribute* Pub? Mod name=Ident ( LeftBrace ( mod | use_statement | function)* RightBrace)?;

extern_import_statement : extern_import Semicolon;

use_statement : outer_attribute* Pub? Use use_path Semicolon {
//                                                              if (_localctx.path() != null) {
//                                                                  if (_localctx.path().end != null) {
//                                                                      importedTypes.add(_localctx.path().getText());
//                                                                      aliasedtypes.put(_localctx.path().end, localctx.path());
//                                                                  }
//                                                              }
};

unsafe_block : Unsafe block;

block : LeftBrace ( statement | inner_attribute | enclosing_doc_comment)* ( ret=expression? | pat=expression_pattern) RightBrace;

statement : ( statement_body? Semicolon)
          | loop
          | match
          | while_loop
          | for_loop
          | if_let
          | if_statement
          | function
          | block;

statement_body : variable_binding
               | function_invocation
               | assignment_expression
               | macro_invocation
               | return_statement;

loop : Loop block;

while_loop : While boolean_expression block;

if_let : If Let exp=expression Equals var=variable_name ( statement | block);

for_loop : ( For var=variable_name In range=( RangeExclusive | RangeInclusive) ( statement | block)) #ForRanged
         | ( For var=variable_name In expr=expression ( statement | block)) #ForExpression;

return_statement : Return exp=expression?;

variable_binding : ( Let props=variable_props name=variable_spec) #UnassignedBinding
                 | ( Let props=variable_props pattern=variable_pattern Equals assignedToPattern=expression_pattern) #PatternBinding
                 | ( Let props=variable_props name=variable_spec Equals aprops=assignee_props assignedTo=expression cast=variable_cast?) #SingleBinding
                 | ( Let props=variable_props type_spec pattern=variable_pattern( ( Equals aprops=assignee_props name=variable_name) | exp=expression)) #DestructuringLetBinding;

variable_props : ref=Ref? mut=Mut?;

assignee_props : borrow=Ampersand? mutable=Mut?;

variable_pattern : LeftParen ( variable_spec ( Comma variable_spec)* Comma?) RightParen;

variable_spec : ( name=variable_name ( Colon type=type_spec)?)
              | anon=Underscore;

variable_cast : As ( RawPointerMutable | RawPointerConst) type_spec;

assignment_expression : variable_expression assignment_operator expression;

variable_expression : borrow=Ampersand? deref=Asterisk? qualifier=path_head? variable_reference ( LeftBracket index=expression RightBracket)?;

variable_reference : name=variable_name ( Dot variable_name)*;

variable_name : Ident;

array_literal : ( LeftBracket RightBracket) #EmptyArray
              | ( LeftBracket StringLiteral ( Comma StringLiteral)* RightBracket) #StringArray
              | ( LeftBracket int_literal ( Comma int_literal)* RightBracket) #IntArray
              | ( LeftBracket float_literal ( Comma float_literal)* RightBracket) #FloatArray
              | ( LeftBracket BooleanLiteral ( Comma BooleanLiteral)* RightBracket) #BooleanArray
              | ( LeftBracket ByteLiteral ( Comma ByteLiteral)* RightBracket) #ByteArray
              | ( LeftBracket expression ( Comma expression)* RightBracket) #ExpressionArray;

match : Match deref=Asterisk? var=expression match_block;

match_block : LeftBrace ( first=match_case ( Comma more=match_case)*)? defaultCase=default_match_case? Comma? RightBrace;

match_case : ( first=literal ( Pipe more=literal)* FatArrow ( statement_body | expression | block)) #MultiCaseLiteral
           | ( range=RangeExclusive FatArrow ( statement_body | expression | block)) #ExclusiveRangeCase
           | ( range=MatchRangeInclusive FatArrow ( statement_body | expression | block)) #InclusiveRangeCase
           | ( var=variable_name_pattern If boolean_expression FatArrow ( statement_body | expression | block)) #PatternCase
           | ( exp=expression ( Pipe more=expression)* If boolean_expression FatArrow ( statement_body | expression | block)) #ExpressionCase
           | ( first=expression ( Pipe more=expression)* FatArrow ( statement_body | expression | block)) #MultiCaseExpression;

default_match_case : Comma? Underscore FatArrow ( statement_body | expression | block);

expression : literal cast=variable_cast? #LiteralExpression
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
           | Minus? LeftParen leftSide=expression arithmetic_operator rightSide=expression RightParen #ParenthesizedArithmeticExpression
           | leftSide=expression shift_operator rightSide=expression #ShiftExpression
           | leftSide=expression comparison_operator rightSide=expression #BooleanExpression
           | Bang? LeftParen ls=expression comparison_operator rs=expression RightParen #ParentheizedBooleanExpression;

unsafe_expression : Unsafe LeftBrace expression RightBrace;

if_statement : If boolean_expression block ( Else If boolean_expression block)* ( Else block)?;

function_invocation : ( qualifier=path_head? func=Ident LeftParen invocation_args RightParen) #UnqualifiedFunctionInvocation
                    | ( qualifier=path_head? func=Ident LeftParen invocation_args RightParen) #QualifiedFunctionInvocation
                    | ( qualifier=type_hint? DoubleColon func=Ident LeftParen invocation_args RightParen) #TypeHintQualifiedFunctionInvocation;

type_hint : LeftAngleBracket type_spec As type_spec RightAngleBracket;

macro_invocation : macro=Ident Bang LeftParen invocation_args RightParen;

invocation_args : expression?
                | expression ( Comma expression)*;

tuple_expression : LeftParen expression ( Comma expression)* RightParen;

boolean_expression : Bang? BooleanLiteral #BLiteral
                   | leftSide=expression comparison_operator rightSide=expression #BExpression
                   | Bang? LeftParen boolean_expression RightParen #ParenthesizedBExpression;

expression_pattern : LeftParen expression ( Comma expression)* RightParen;

variable_name_pattern : LeftParen ( ( Underscore | variable_name) ( Comma ( Underscore | variable_name))*)? RightParen;

literal : float_literal #FloatLiteral
        | BooleanLiteral #BooleanLiteral
        | ByteLiteral #ByteLiteral
        | int_literal #IntLiteral
        | StringLiteral #StringLiteral
        | array_literal #ArrayLiteral;

int_literal : ( neg=Minus? ? value=( BareIntLiteral | FullIntLiteral) type=signed_int_subtype? cast=int_cast?) #SignedIntLiteral
            | ( value=( BareIntLiteral | FullIntLiteral) ( type=unsigned_int_subtype)? cast=int_cast) #UnsignedIntLiteral;

float_literal : value=FloatLiteral type=float_subtype? cast=float_cast;

int_cast : As ( signed_int_subtype | unsigned_int_subtype);

float_cast : As float_subtype;

float_subtype : F32
              | F64;

signed_int_subtype : I8
                   | I16
                   | I32
                   | I64
                   | I128
                   | ISIZE;

unsigned_int_subtype : U8
                     | U16
                     | U32
                     | U64
                     | U128
                     | USIZE;

function : function_spec body=block;

function_spec : doc=doc_comment? attrs=outer_attribute* vis=visibility? Fn name=Ident lifetimes=lifetime_spec? LeftParen params=parameter_list? RightParen return_type?;

extern_block : attrs=outer_attribute* Extern LeftBrace ( extern_function_statement ( Comma extern_function_statement)*)?;

extern_function_statement : inner_attribute* attrs=outer_attribute* function_spec Semicolon;

closure : ( attrs=outer_attribute* lifetimes=lifetime_spec? mv=Move? Pipe params=parameter_list? Pipe return_type? ( body=block | expr=expression))
        | ( attrs=outer_attribute* mv=Move? DoublePipe return_type? ( body=block | expr=expression))
        | ( attrs=outer_attribute* mv=Move? Pipe Pipe return_type? ( body=block | expr=expression));

struct : ( doc=doc_comment? outer_attribute* Struct name=Ident lifetimes=lifetime_spec? LeftBrace ( struct_item ( Comma struct_item)* Comma?)? RightBrace) #PlainStruct// XXX could split this out and include it in statement_body - checking for Semicolon here may cause problems
       | ( doc=doc_comment? outer_attribute* Struct name=Ident lifetimes=lifetime_spec? LeftParen ( type_spec ( Comma type_spec)* Comma?)? RightParen Semicolon) #TupleStruct;

struct_item : ( doc=doc_comment? outer_attribute* inner_attribute* name=Ident Colon props=param_props type=type_spec);

struct_instantiation : type=type_spec LeftBrace ( struct_instantiation_item ( Comma struct_instantiation_item)* Comma?)? RightBrace;

struct_instantiation_item : ( name=Ident Colon params=param_props expression) #ExplictStructItem
                          | ( DotDot name=Ident) #CopyStructItem;

lifetime_spec : LeftAngleBracket first=lifetime ( Comma more=lifetime)* RightAngleBracket;

lifetime : Lifetime;

visibility : Pub constraint=visibility_restriction?;
 // Note that `pub(` does not necessarily signal the beginning of a visibility
 // restriction! For example:
 //
 //     struct T(i32, i32, pub(i32));
 //
 // Here the `(` is part of the type `(i32)`.
visibility_restriction : LeftParen restrictedTo=Crate RightParen #CrateVisibility
                       | LeftParen restrictedTo=Super RightParen #SuperVisibility
                       | LeftParen In restrictedTo=Ident RightParen #ExplicitVisibility;

parameter_list : ( parameter_spec ( Comma parameter_spec)*)
               | ( selfref=Ampersand? self=SelfRef ( Comma parameter_spec)*);

parameter_spec : props=param_props name=variable_name ( Colon Ampersand? life=lifetime? type=type_spec)?;

param_props : ref=Ampersand? life=lifetime? mutable=Mut? dynamic=Dyn?;

return_type : Arrow ( type=type_spec | typePattern=type_pattern);

type_pattern : LeftParen type_spec ( Comma type_spec) RightParen;

type_spec : path=path_head? type=Ident {referencedTypes.add(_ctx.getText());
}
#Named
          | type=intrinsic_type #Intrinsic
          | LeftParen RightParen #Unit;

extern_import : Extern Crate create=Ident;

use_path : path_head end=use_path_end;

explicit_path : path_head end=Ident;

path_head : start=path_start segments=path_segment*;
 /* | (start=path_start segments=path_segment* last=Ident As end=Ident)
    | (start=path_start segments=path_segment* LeftBrace end=Ident (Comma Ident)* RightBrace)
    | (start=path_start segments=path_segment* Asterisk)
 */
use_path_end : end=Ident
             | last=Ident As end=Ident
             | LeftBrace end=Ident ( Comma more=Ident) RightBrace;

path_segment : item=Ident DoubleColon;

path_start : item=SelfPath
           | item=SuperPath
           | DoubleColon
           | ( item=Ident DoubleColon);

inner_attribute : InnerAttrPrefix metaitem RightBracket;

outer_attribute : AttrPrefix metaitem RightBracket;

metaitem : id=Ident
         | id=Ident LeftParen RightParen
         | id=Ident Equals literal
         | id=Ident LeftParen literal RightParen
         | id=Ident LeftParen metaitem ( Comma metaitem) RightParen;

logical_operator : DoubleAmpersand
                 | DoublePipe
                 | Circumflex;

comparison_operator : DoubleEquals
                    | LessThanOrEquals
                    | GreaterThanOrEquals
                    | LeftAngleBracket
                    | RightAngleBracket
                    | NotEquals;

arithmetic_operator : Plus
                    | Minus
                    | Asterisk
                    | Slash
                    | Percent;

shift_operator : ShiftLeft
               | ShiftRight;

assignment_operator : Equals
                    | AssignMultiply
                    | AssignDivide
                    | AssignMod
                    | AssignPlus
                    | AssignMinus
                    | AssignAnd
                    | AssignOr
                    | AssignShiftLeft
                    | AssignShiftRight; // unit_type : '()';
intrinsic_type : I8
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

doc_comment : DocCommentLine+;

enclosing_doc_comment : EnclosingDocCommentLine+;

aawoo : EnclosingDocCommentLine;

EnclosingDocCommentLine : EnclosingDocCommentPrefix ~[\r\n]*; // Lexer
DocCommentLine : DocCommentPrefix ~[\r\n]*;

LineComment : LineCommentPrefix~[\r\n]* -> channel (1);

BlockComment : BlockCommentPrefix (~ [*/] | Slash* BlockComment | Slash+ (~[*/]) | Asterisk+ ~[*/])* Asterisk+ Slash -> channel (2);

Lifetime : SingleQuote ( IDENT | Static);

Whitespace : [ \t\r\n]+ -> channel (1);

BooleanLiteral : True
               | False;

RangeExclusive : DEC_DIGITS DotDot DEC_DIGITS;

MatchRangeInclusive : DEC_DIGITS TripleDot DEC_DIGITS;

RangeInclusive : DEC_DIGITS DotDotEquals DEC_DIGITS;

I8 : 'i8';

I16 : 'i16';

I32 : 'i32';

I64 : 'i64';

I128 : 'i128';

F32 : 'f32';

F64 : 'f64';

U8 : 'u8';

U16 : 'u16';

U32 : 'u32';

U64 : 'u64';

U128 : 'u128';

USIZE : 'usize';

ISIZE : 'isize';

DocCommentPrefix : '///';

EnclosingDocCommentPrefix : '//!';

RawPointerConst : '*const';

RawPointerMutable : '*mut';

SelfPath : 'self::';

SuperPath : 'super::';

SelfRef : '&self';

As : 'as';

Auto : 'auto';

Bool : 'bool';

Box : 'box';

Break : 'break';

Crate : 'crate';

Char : 'char';

Const : 'const';

Continue : 'continue';

Default : 'default';

Dyn : 'dyn';

Else : 'else';

Enum : 'enum';

Extern : 'extern';

False : 'false';

For : 'for';

Let : 'let';

Loop : 'loop';

If : 'if';

Match : 'match';

Mod : 'mod';

Move : 'move';

Pub : 'pub';

Ref : 'ref';

Return : 'return';

Super : 'super';

Static : 'static';

Struct : 'struct';

Self : 'self';

SelfCaps : 'Self';

True : 'true';

Type : 'type';

Trait : 'trait';

Impl : 'impl';

Union : 'union';

Unsafe : 'unsafe';

Use : 'use';

Where : 'where';

While : 'while';

Mut : 'mut';

AttrPrefix : '#[';

AssignShiftLeft : '<<=';

AssignShiftRight : '>>=';

ByteLiteralPrefix : 'b\'';

ByteStringPrefix : 'b"';

TripleDot : '...';

InnerAttrPrefix : '#![';

DotDotEquals : '..=';

Fn : 'fn';

In : 'in';

HexLiteralPrefix : '0x';

OctalLiteralPrefix : '0o';

BitsLiteralPrefix : '0b';

BlockCommentPrefix : '/*';

LineCommentPrefix : '//';

DoubleAmpersand : '&&';

DoubleColon : '::';

DoubleEquals : '==';

DoublePipe : '||';

DoubleBackslash : '\\\\';

Arrow : '->';

FatArrow : '=>';

NotEquals : '!=';

LessThanOrEquals : '<=';

GreaterThanOrEquals : '>=';

ShiftLeft : '<<';

ShiftRight : '>>';

AssignMultiply : '*=';

AssignDivide : '/=';

AssignMod : '%=';

AssignPlus : '+=';

AssignMinus : '-=';

AssignAnd : '&=';

AssignXor : '^=';

AssignOr : '|=';

Newline : '\n';

CarriageReturn : '\r';

Ampersand : '&';

Quote : '"';

SingleQuote : '\'';

Backslash : '\\';

Bang : '!';

Colon : ':';

Comma : ',';

Dot : '.';

DotDot : '..';

LeftParen : '(';

RightParen : ')';

LeftBrace : '{';

RightBrace : '}';

LeftBracket : '[';

RightBracket : ']';

Equals : '=';

Percent : '%';

LeftAngleBracket : '<';

RightAngleBracket : '>';

Hash : '#';

Plus : '+';

Minus : '-';

Slash : '/';

Asterisk : '*';

Underscore : '_';

QuestionMark : '?';

AtSymbol : '@';

Pipe : '|';

Circumflex : '^';

Space : ' ';

DollarSign : '$';

Semicolon : ';';

Ident : IDENT;

CharLiteral : SingleQuote ( CHAR | Quote) SingleQuote;

StringLiteral : Quote STRING_ELEMENT* Quote
              | 'r' RAW_STRING_BODY;

ByteLiteral : ByteLiteralPrefix ( BYTE | Quote) SingleQuote;

ByteStringLiteral : ByteStringPrefix BYTE_STRING_ELEMENT* Quote
                  | 'br' RAW_BYTE_STRING_BODY;
 // BareIntLiteral and FullIntLiteral both match '123'; BareIntLiteral wins by virtue of
 // appearing first in the file. (This comment is to point out the dependency on
 // a less-than-obvious ANTLR rule.)
BareIntLiteral : DEC_DIGITS;

FullIntLiteral : DEC_DIGITS INT_SUFFIX?
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
FloatLiteral : DEC_DIGITS Dot [0-9] [0-9_]* EXPONENT?
             | DEC_DIGITS ( Dot {/* dot followed by another dot is a range, not a float */
                                        _input.LA(1) != Dot &&
                                        /* dot followed by an identifier is an integer with a function call, not a float */
                                        _input.LA(1) != Underscore &&
                                        !(_input.LA(1) >= 'a' && _input.LA(1) <= 'z') &&
                                        !(_input.LA(1) >= 'A' && _input.LA(1) <= 'Z')
 }?)
             | DEC_DIGITS EXPONENT
             | DEC_DIGITS; // Fragments used for literals below here
fragment SIMPLE_ESCAPE : Backslash [0nrt'"\\];

fragment CHAR : ~ ['"\r\n\\\ud800-\udfff] // a single BMP character other than a backslash, newline, or quote
              | [\ud800-\udbff] [\udc00-\udfff] // a single non-BMP character (hack for Java)
              | SIMPLE_ESCAPE
              | '\\x' [0-7] [0-9a-fA-F]
              | '\\u{'[0-9a-fA-F]+ RightBrace;

fragment OTHER_STRING_ELEMENT : SingleQuote
                              | Backslash CarriageReturn? Newline [ \t]*
                              | CarriageReturn
                              | Newline;

fragment STRING_ELEMENT : CHAR
                        | OTHER_STRING_ELEMENT;

fragment RAW_CHAR : ~ [\ud800-\udfff] // any BMP character
                  | [\ud800-\udbff] [\udc00-\udfff]; // any non-BMP character (hack for Java)
 // Here we use a non-greedy match to implement the
 // (non-regular) rules about raw string syntax.
fragment RAW_STRING_BODY : Quote RAW_CHAR*? Quote
                         | Hash RAW_STRING_BODY Hash;

fragment BYTE : Space // any ASCII character from 32 (space) to 126 (`~`),
              | Bang // except 34 (double quote), 39 (single quote), and 92 (backslash)
              | [#-&]
              | [(-[]
              | RightBracket
              | Circumflex
              | [_-~]
              | SIMPLE_ESCAPE
              | '\\x' [0-9a-fA-F] [0-9a-fA-F];

fragment BYTE_STRING_ELEMENT : BYTE
                             | OTHER_STRING_ELEMENT;

fragment RAW_BYTE_STRING_BODY : Quote [\t\r\n -~]*? Quote
                              | Hash RAW_BYTE_STRING_BODY Hash;

fragment DEC_DIGITS : [0-9] [0-9_]*;

fragment INT_SUFFIX : [ui] ('8Pipe16Pipe32Pipe64Pipesize');

fragment EXPONENT : [Ee] [+-]? Underscore* [0-9] [0-9_]*;

fragment FLOAT_SUFFIX : F32
                      | F64;

fragment IDENT : XID_Start XID_Continue*; 