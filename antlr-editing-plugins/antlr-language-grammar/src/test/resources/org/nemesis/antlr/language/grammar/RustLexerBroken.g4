lexer grammar RustLexerBroken;

import xidstart, xidcontinue, keywords, symbols, types;

@members {
int delimDepth;
char macroOpen;
boolean nextWordIs(String word) {
    int cursor = -1;
    for(int i=1;;i++) {
        int val = _input.LA(i);
        if (val == CharStream.EOF) {
            return cursor == word.length()-1;
        }
        char c = (char) val;
        switch(cursor) {
            case -1:
                switch(c) {
                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                        continue;
                    default :
                        cursor = 0;
                        // double fallthrough
                }
            default :
                if (cursor >= word.length()) {
                    return true;
                }
                if (c != word.charAt(cursor++)) {
                    return false;
                }
        }
    }
}

boolean nextNonWhitespace(char c, boolean match) {
    for(int i=1;;i++) {
        int val = _input.LA(i);
        if (val == CharStream.EOF) {
            return false;
        }
        char curr = (char) val;
        if (!Character.isWhitespace(curr)) {
            return match ? c == curr : c != curr;
        }
    }
}

boolean maybeEnterMacro() {
    for(int i=1;;i++) {
        int val = _input.LA(i);
        if (val == CharStream.EOF) {
            return false;
        }
        char curr = (char) val;
        switch(curr) {
            case ' ':
            case '\b':
            case '\t':
            case '\r':
            case '\n':
                continue;
            case '[':
            case '(':
            case '{':
              echo("Enter macro: " + curr);
              macroOpen = curr;
              delimDepth = 0;
              return true;
            default :
              return false;
        }
    }
}

boolean exitingMacro(char type) {
    echo("Exiting macro? " + type + " with delim depth " + delimDepth + " and macroOpen " + macroOpen);
    if (macroOpen == type) {
        macroOpen = 0;
        delimDepth = 0;
        echo("Really exiting macro.");
        return true;
    }
    return false;
}

void echo(String msg) {
    System.out.println(msg);
}

int incDelimDepth() {
    echo("Inc delim depth to " + (delimDepth+1) + " (ret " + delimDepth + ")");
    return delimDepth++;
}

int decDelimDepth() {
    echo("Dec delim depth to " + (delimDepth-1));
    return --delimDepth;
}
}

Placeholder : '^`````t```^';

DollarsCrate
    : '$crate';

SelfCaps
    : KW_SELFTYPE;

SelfLower
    : KW_SELFVALUE;

Super
    : KW_SUPER;

Crate
    : KW_CRATE;

As
    : KW_AS;

Fn
    : KW_FN;

For
    : KW_FOR;

Dyn
    : KW_DYN;

Let
    : KW_LET;

Pub
    : KW_PUB;

In
    : KW_IN;

Module
    : KW_MOD;

LeftBrace
    : L_BRACE;

RightBrace
    : R_BRACE;

Struct
    : KW_STRUCT;

Enumeration
    : KW_ENUM;

Union
    : KW_UNION;

Static
    : KW_STATIC;

Unsafe
    : KW_UNSAFE;

Trait
    : KW_TRAIT;

Async
    : KW_ASYNC;

AtSymbol
    : AT;

Comma
    : COMMA;

Impl : KW_IMPL;

Ref : KW_REF;

BooleanLiteral
    : BOOLEAN_LITERAL;

Question
    : QUESTION;

Minus
    : MINUS;

Match : KW_MATCH;

FatArrow : FAT_ARROW;

DotDotDot : DOT_DOT_DOT;

While : KW_WHILE;

If : KW_IF;

Else : KW_ELSE;

Await : KW_AWAIT;

Dot : DOT;

Equals
    : EQUALS;

RightArrow
    : RIGHT_ARROW;

LeftBracket
    : LEFT_BRACKET;

RightBracket
    : RIGHT_BRACKET;

Semicolon
    : SEMI;

IntegerLiteral
    : INT_LITERAL;

FloatLiteral
    : FLOAT_LITERAL;

//Foo : (COMMA | LINE_COMMENT_PREFIX) {};
BlockComment
    : BLOCK_COMMENT_PREFIX ~[*!] -> more, pushMode ( BlockComments );

InnerBlockDocComment
    : INNER_BLOCK_DOC_COMMENT_PREFIX -> more, pushMode ( BlockComments );

InnerLineDocComment
    : INNER_LINE_DOC_COMMENT_PREFIX -> more, pushMode ( LineComments );

OuterBlockDocComment
    : OUTER_BLOCK_DOC_COMMENT_PREFIX -> more, pushMode ( BlockComments );

OuterLineDocComment
    : OUTER_LINE_DOC_COMMENT_PREFIX -> more, pushMode ( LineComments );

LineCommentPrefix
    : LINE_COMMENT_PREFIX -> more, pushMode ( LineComments );

Extern
    : KW_EXTERN { !nextWordIs("crate") }?;

ExternCrateStatement
    : KW_EXTERN -> pushMode ( ExternCrate );

UseStatement
    : KW_USE -> pushMode ( Use );

Whitespace
    : WHITESPACE -> channel ( 1 );

AttrPrefix
    : '#[' -> pushMode ( Attribute );

InnerAttrPrefix
    : '#![' -> pushMode ( Attribute );

LeftParen
    : LEFT_PAREN;

RightParen
    : RIGHT_PAREN;

BooleanType
    : BOOL;

CharType
    : CHAR;

Type
    : KW_TYPE;

Where
    : KW_WHERE;

Not
    : NOT;

Mut
    : KW_MUT;

Const
    : KW_CONST;

Star
    : STAR;

Slash
    : SLASH;

And
    : AND;

AndAnd
    : AND_AND;

Colon
    : COLON;

Plus
    : PLUS;

Percent
    : PERCENT;

Or
    : OR;

Caret
    : CARET;

ShiftLeft
    : SHIFT_LEFT;

ShiftRight
    : SHIFT_RIGHT;

DoubleEquals
    : EQUALS_EQUALS;

NotEquals
    : NOT_EQUALS;

GreaterThan
    : GREATER_THAN;

LessThan
    : LESS_THAN;

GreaterThanOrEquals
    : GREATER_THAN_OR_EQUALS;

LessThanOrEquals
    : LESS_THAN_OR_EQUALS;

OrOr
    : OR_OR;

PlusEquals
    : PLUS_EQ;

MinusEquals
    : MINUS_EQ;

TimesEquals
    : STAR_EQ;

DivEquals
    : SLASH_EQ;

ModEquals
    : PERCENT_EQ;

AndEquals
    : AND_EQ;

OrEquals
    : OR_EQ;

XorEquals
    : CARET_EQ;

ShiftLeftEquals
    : SHIFT_LEFT_EQ;

ShiftRightEquals
    : SHIFT_RIGHT_EQ;

DoubleColon
    : DOUBLE_COLON;

Underscore
    : UNDERSCORE;

DotDot
    : DOT_DOT;

DotDotEquals
    : DOT_DOT_EQ;

Move
    : KW_MOVE;

Continue
    : KW_CONTINUE;

Break : KW_BREAK;

Return : KW_RETURN;

Loop : KW_LOOP;

LifetimeToken
    : LIFETIME;

LifetimeStatic
    : '\'static';

LifetimeWildcard
    : '\'_';

StringLiteral
    : STRING_LITERAL;

ProceduralMacroInvocation
    : (IDENT DOUBLE_COLON)* MACRO_INVOCATION { maybeEnterMacro() }? -> pushMode(Macro);

RawStringLiteral
    : RAW_STRING_LITERAL;

CharLiteral
    : SINGLE_QUOTE CHAR_LITERAL SINGLE_QUOTE;

ByteLiteral
    : BYTE_LITERAL;

ByteStringLiteral
    : BYTE_STRING_LITERAL;

RawByteStringLiteral
    : RAW_BYTE_STRING_LITERAL;

SignedIntegerType
    : I8
    | I16
    | I32
    | I64
    | I128
    | ISIZE;

UnsignedIntegerType
    : U8
    | U16
    | U32
    | U64
    | U128
    | USIZE;

FloatType
    : F32
    | F64;

StringType
    : STR;

Identifier
    : IDENT;


mode Macro;

MacroWhitespace : WHITESPACE -> channel(2);

MacroToken : 
     PLUS | 
     MINUS | 
     STAR | 
     SLASH | 
     PERCENT | 
     CARET | 
     NOT | 
     AND | 
     OR | 
     AND_AND |  
     OR_OR |  
     SHIFT_LEFT |  
     SHIFT_RIGHT |  
     PLUS_EQ |  
     MINUS_EQ |  
     STAR_EQ |  
     SLASH_EQ |  
     PERCENT_EQ |  
     CARET_EQ |  
     AND_EQ |  
     OR_EQ |  
     SHIFT_LEFT_EQ |  
     SHIFT_RIGHT_EQ |  
     EQUALS |  
     EQUALS_EQUALS |  
     NOT_EQUALS |  
     GREATER_THAN |  
     LESS_THAN |  
     GREATER_THAN_OR_EQUALS |  
     LESS_THAN_OR_EQUALS |  
     AT |  
     UNDERSCORE |  
     DOT |  
     DOT_DOT |  
     DOT_DOT_DOT |  
     DOT_DOT_EQ |  
     COMMA |  
     SEMI |  
     COLON |  
     PATH_SEP |  
     RIGHT_ARROW |  
     FAT_ARROW |  
     POUND |  
     DOLLAR |  
     QUESTION |  
     SINGLE_QUOTE CHAR_LITERAL SINGLE_QUOTE | STRING_LITERAL | BYTE_STRING_LITERAL
     | BYTE_LITERAL
     | SEMI
     | INT_LITERAL
     | FLOAT_LITERAL
     | ~('(' | ')' | '{' | '}' | '[' | ']' | ' ' | '\r' | '\n' | '\t')+
;

MacroOpenBrace : L_BRACE { incDelimDepth();};

MacroOpenBracket : LEFT_BRACKET {incDelimDepth();};

MacroOpenParen : LEFT_PAREN {incDelimDepth();};

MacroCloseBrace : R_BRACE { decDelimDepth() > 0}?;

MacroCloseBracket : RIGHT_BRACKET {decDelimDepth() > 0}?;

MacroCloseParen : RIGHT_PAREN {decDelimDepth() > 0}?;

MacroExitParen : RIGHT_PAREN { delimDepth == 0 && exitingMacro('(') }? -> popMode;

MacroExitBrace : R_BRACE { delimDepth == 0 && exitingMacro('\\u007D') }? -> popMode;

MacroExitBracket : RIGHT_BRACKET { delimDepth == 0 && exitingMacro('[') }? -> popMode;

mode ExternCrate;

EcCrate
    : KW_CRATE;

EcSemi
    : SEMI -> popMode;

EcSelf
    : KW_SELFTYPE;

EcAs
    : KW_AS;

EcIdentifier
    : IDENT;

EcWhitespace
    : WHITESPACE -> channel ( 1 );

mode Attribute;

AttrBooleanLiteral
    : BOOLEAN_LITERAL;

AttrByteLiteral
    : BYTE_LITERAL;

AttrByteStringLiteral
    : BYTE_STRING_LITERAL;

AttrClose
    : RIGHT_BRACKET -> popMode;

AttrComma
    : COMMA;

AttrCharLiteral
    : SINGLE_QUOTE CHAR_LITERAL SINGLE_QUOTE;

AttrEquals
    : EQUALS;

AttrFloatLiteral
    : FLOAT_LITERAL;

AttrIdent
    : IDENT;

AttrIntLiteral
    : INT_LITERAL;

AttrLeftParen
    : LEFT_PAREN -> pushMode ( AttributeArgList );

AttrPathItem
    : PATH_IDENT DOUBLE_COLON;

AttrStringLiteral
    : STRING_LITERAL;

AttrWhitespace
    : WHITESPACE -> channel ( 1 );


mode AttributeArgList;

AttrArgBooleanLiteral
    : BOOLEAN_LITERAL;

AttrArgByteLiteral
    : BYTE_LITERAL;

AttrArgByteStringLiteral
    : BYTE_STRING_LITERAL;

AttrArgComma
    : COMMA;

AttrArgCharLiteral
    : SINGLE_QUOTE CHAR_LITERAL SINGLE_QUOTE;

AttrArgEquals
    : EQUALS;

AttrArgFloatLiteral
    : FLOAT_LITERAL;

AttrArgIdent
    : IDENT;

AttrArgIntLiteral
    : INT_LITERAL;

AttrArgStringLiteral
    : STRING_LITERAL;

AttrArgWhitespace
    : WHITESPACE -> channel ( 1 );

AttrRightParen
    : RIGHT_PAREN -> popMode;

AttrArgPathItem
    : PATH_IDENT DOUBLE_COLON;


mode BlockComments;

BlockCommentBody
    : ( [*] ~[/] | ~[*] )+ -> channel ( 2 );

BlockCommentSuffix
    : CLOSE_COMMENT -> channel ( 2 ), popMode;


mode LineComments;

LineComment
    : ( [ \r\t] | ~[ \r\t\n] )* NEWLINE -> channel ( 2 ), popMode;


mode SimplePath;

PathElementIdent
    : ( DOUBLE_COLON PATH_IDENT )+ -> popMode;


mode Use;

UseDoubleColon
    : DOUBLE_COLON;

UseExit
    : SEMI -> popMode;

UseIdent
    : PATH_IDENT;

UseMuxOpen
    : L_BRACE -> pushMode ( UseMux );

UseWhitespace
    : WHITESPACE -> channel ( 2 );

UseWildcard
    : STAR;


mode UseMux;

UseMuxClose
    : R_BRACE -> popMode;

UseMuxComma
    : COMMA;

UseMuxIdent
    : IDENT | STAR;

UseMuxWhitespace
    : WHITESPACE -> channel ( 1 );

fragment FLOAT_SUFFIX
    : F32
    | F64;

fragment INT_SUFFIX
    : I8
    | I16
    | I32
    | I64
    | I128
    | ISIZE
    | U8
    | U16
    | U32
    | U64
    | U128
    | USIZE;

fragment BACKSLASH
    : '\\';

fragment BANG
    : '!';

fragment RAW_BYTE_STRING_BODY
    : QUOTE [\t\r\n -~]*? QUOTE
    | HASH RAW_BYTE_STRING_BODY HASH;

fragment L_BRACE
    : '{';

fragment R_BRACE
    : '}';

fragment LEFT_BRACKET
    : '[';

fragment RAW_CHAR
    : ~[\ud800-\udfff]

// any BMP character
    | [\ud800-\udbff] [\udc00-\udfff]; // any non-BMP character (hack for Java)
// Here we use a non-greedy match to implement the
// (non-regular) rules about raw string syntax.
fragment RAW_STRING_BODY
    : QUOTE RAW_CHAR*? QUOTE
    | HASH RAW_STRING_BODY HASH;

fragment RIGHT_BRACKET
    : ']';

fragment BYTE
    : SPACE// any ASCII character from 32 (space) to 126 (`~`),
    | BANG// except 34 (double quote), 39 (single quote), and 92 (backslash)
    | [#-&]
    | [(-[]
    | RIGHT_BRACKET
    | CIRCUMFLEX
    | [_-~]
    | SIMPLE_ESCAPE
    | BACKSLASH_X [0-9a-fA-F] [0-9a-fA-F];

fragment SELF_CAPS
    : 'Self';

fragment CHAR_LITERAL
    : ~['"\r\n\\\ud800-\udfff]

// a single BMP character other than a backslash, newline, or quote
    | [\ud800-\udbff] [\udc00-\udfff]// a single non-BMP character (hack for Java)
    | SIMPLE_ESCAPE
    | '\\x' [0-7] [0-9a-fA-F]
    | '\\u{' [0-9a-fA-F]+ R_BRACE;

fragment CIRCUMFLEX
    : '^';

fragment DOUBLE_COLON
    : '::';

fragment CLOSE_COMMENT
    : '*/';

fragment SELF_CLOSING_BLOCK_COMMENT
    : '/**/'
    | '/***/';

fragment CRATE
    : 'crate';

fragment DOLLARS_CRATE
    : '$crate';

fragment DEC_DIGITS
    : [0-9] [0-9_]*;

fragment EXPONENT
    : [Ee] [+-]? UNDERSCORE* [0-9] [0-9_]*;

fragment BYTE_LITERAL
    : BYTE_LITERAL_PREFIX ( BYTE | QUOTE ) SINGLE_QUOTE;

fragment CHAR_LIT
    : SINGLE_QUOTE ( STRING_CHAR_ELEMENT | QUOTE ) SINGLE_QUOTE;

fragment FLOAT_LITERAL
    : DEC_DIGITS DOT [0-9] [0-9_]* EXPONENT? FLOAT_SUFFIX?
    | DEC_DIGITS ( DOT {
        /* dot followed by another dot is a range, not a float */
        _input.LA(1) != '.' &&
        /* dot followed by an identifier is an integer with a function call, not a float */
        _input.LA(1) != '_' &&
        !(_input.LA(1) >= 'a' && _input.LA(1) <= 'z') &&
        !(_input.LA(1) >= 'A' && _input.LA(1) <= 'Z')
    }? )
    | DEC_DIGITS EXPONENT
    | DEC_DIGITS;

fragment BYTE_LITERAL_PREFIX
    : 'b\'';

fragment BYTE_STRING_ELEMENT
    : BYTE
    | OTHER_STRING_ELEMENT;

fragment OTHER_STRING_ELEMENT
    : SINGLE_QUOTE
    | BACKSLASH CARRIAGE_RETURN? NEWLINE [ \t]*
    | CARRIAGE_RETURN
    | NEWLINE;

fragment STRING_ELEMENT
    : STRING_CHAR_ELEMENT
    | OTHER_STRING_ELEMENT;

fragment STRING_CHAR_ELEMENT :
    ~['\ud800-\udfff];

fragment LINE_END
    : '\r\n'
    | '\n';

// Fragments used for literals below here
fragment SIMPLE_ESCAPE
    : BACKSLASH [0nrt'"\\];

fragment MACRO_INVOCATION
    : IDENT '!';

fragment LIFETIME
    : SINGLE_QUOTE IDENT;

fragment IDENT
    : XID_START XID_CONTINUE*
    | RAW_IDENTIFIER_PREFIX XID_CONTINUE*;

fragment PATH_IDENT
    : IDENT
    | KW_SELFVALUE
    | KW_SELFTYPE
    | KW_CRATE
    | DOLLARS_CRATE;

fragment BOOLEAN_LITERAL
    : TRUE
    | FALSE;

fragment BYTE_STRING_LITERAL
    : BYTE_STRING_PREFIX BYTE_STRING_ELEMENT* QUOTE;

fragment RAW_BYTE_STRING_LITERAL
    : RAW_BYTE_STRING_PREFIX RAW_BYTE_STRING_BODY;

fragment INT_LITERAL
    : MINUS? DEC_DIGITS INT_SUFFIX?
    | HEX_LITERAL_PREFIX UNDERSCORE* [0-9a-fA-F] [0-9a-fA-F_]*
    | OCTAL_LITERAL_PREFIX UNDERSCORE* [0-7] [0-7_]*
    | BITS_LITERAL_PREFIX UNDERSCORE* [01] [01_]*;

fragment STRING_LITERAL
    : QUOTE STRING_ELEMENT*? QUOTE;

fragment RAW_STRING_LITERAL
    : 'r' RAW_STRING_BODY;

fragment NEWLINE
    : '\n';

fragment LEFT_PAREN
    : '(';

fragment RIGHT_PAREN
    : ')';

fragment BACKSLASH_X
    : '\\x';

fragment BITS_LITERAL_PREFIX
    : '0b';

fragment BLOCK_COMMENT_PREFIX
    : '/*';

fragment BYTE_STRING_PREFIX
    : 'b"';

fragment HEX_LITERAL_PREFIX
    : '0x';

fragment INNER_BLOCK_DOC_COMMENT_PREFIX
    : '/*!';

fragment INNER_LINE_DOC_COMMENT_PREFIX
    : '//!';

fragment LINE_COMMENT_PREFIX
    : '//';

fragment OCTAL_LITERAL_PREFIX
    : '0o';

fragment OUTER_BLOCK_DOC_COMMENT_PREFIX
    : '/**';

fragment OUTER_LINE_DOC_COMMENT_PREFIX
    : '///';

fragment RAW_BYTE_STRING_PREFIX
    : 'br';

fragment RAW_IDENTIFIER_PREFIX
    : 'r#';

fragment QUOTE
    : '"';

fragment SINGLE_QUOTE
    : '\'';

fragment CARRIAGE_RETURN
    : '\r';

fragment SPACE
    : ' ';

fragment SUPER
    : 'super';

fragment WHITESPACE
    : [ \t\r\n]+;

