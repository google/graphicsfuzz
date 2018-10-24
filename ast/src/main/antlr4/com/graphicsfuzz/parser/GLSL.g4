/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

grammar GLSL;

@lexer::members {
   boolean ignoreNewLine = true;
}

translation_unit:
   version_statement extension_statement_list external_declaration_list
   ;

version_statement:
   /* blank - no #version specified: defaults are already set */
   | VERSION INTCONSTANT EOL
   | VERSION INTCONSTANT IDENTIFIER EOL
   ;

pragma_statement:
   PRAGMA_DEBUG_ON EOL
   | PRAGMA_DEBUG_OFF EOL
   | PRAGMA_OPTIMIZE_ON EOL
   | PRAGMA_OPTIMIZE_OFF EOL
   | PRAGMA_INVARIANT_ALL EOL
   ;

extension_statement_list:
   | extension_statement_list extension_statement
   ;

extension_statement:
   EXTENSION extension_name=IDENTIFIER COLON extension_status=IDENTIFIER EOL
   ;

external_declaration_list:
   single=external_declaration
   | prefix=external_declaration_list lastDecl=external_declaration
   | prefix=external_declaration_list lastExtension=extension_statement
   ;

variable_identifier:
   IDENTIFIER
   | IDENTIFIER
   ;

primary_expression:
   variable_identifier
   {
      // NEW VARIABLE DECLARATION
   }
   | INTCONSTANT
   | UINTCONSTANT
   | FLOATCONSTANT
   | BOOLCONSTANT
   | LPAREN expression RPAREN
   ;

postfix_expression:
   primary_expression
   | postfix_expression LBRACKET integer_expression RBRACKET
   | postfix_expression DOT method_call_generic
   | postfix_expression DOT IDENTIFIER
   | postfix_expression INC_OP
   | postfix_expression DEC_OP
   | function_call_generic
   ;

integer_expression:
   expression
   ;

function_call_generic:
   function_call_header_with_parameters RPAREN
   | function_call_header_no_parameters RPAREN
   ;

function_call_header_no_parameters:
   function_call_header VOID_TOK
   | function_call_header
   ;

function_call_header_with_parameters:
   function_call_header assignment_expression
   | function_call_header_with_parameters COMMA assignment_expression
   ;

function_call_header:
   function_identifier array_specifier? LPAREN // The array_specifier is there to allow for vector type constructors, e.g. int[2](1, 2)
   ;

function_identifier:
   builtin_type_specifier_nonarray // constructor
   | variable_identifier
   | FIELD_SELECTION
   ;

method_call_generic:
   method_call_header_with_parameters RPAREN
   | method_call_header_no_parameters RPAREN
   ;

method_call_header_no_parameters:
   method_call_header VOID_TOK
   | method_call_header
   ;

method_call_header_with_parameters:
   method_call_header assignment_expression
   | method_call_header_with_parameters COMMA assignment_expression
   ;

   // Grammar Note: Constructors look like methods, but lexical
   // analysis recognized most of them as keywords. They are now
   // recognized through "type_specifier".
method_call_header:
   variable_identifier LPAREN
   ;

   // Grammar Note: No traditional style type casts.
unary_expression:
   postfix_expression
   | INC_OP unary_expression
   | DEC_OP unary_expression
   | unary_operator unary_expression
   ;

   // Grammar Note: No '*' or '&' unary ops. Pointers are not supported.
unary_operator:
   PLUS_OP
   | MINUS_OP
   | NOT_OP
   | BNEG_OP
   ;

multiplicative_expression:
   operands+=unary_expression ((operators+=TIMES_OP | operators+=DIV_OP | operators+=MOD_OP) operands+=unary_expression)*
   ;

additive_expression:
   operands+=multiplicative_expression ((operators+=PLUS_OP | operators+=MINUS_OP) operands+=multiplicative_expression)*
   ;

shift_expression:
   operands+=additive_expression ((operators+=LEFT_OP | operators+=RIGHT_OP) operands+=additive_expression)*
   ;

relational_expression:
   operands+=shift_expression ((operators+=LT_OP | operators+=GT_OP | operators+=LE_OP | operators+=GE_OP) operands+=shift_expression)*
   ;

equality_expression:
   operands+=relational_expression ((operators+=EQ_OP | operators+=NE_OP) operands+=relational_expression)*
   ;

and_expression:
   operands+=equality_expression (operators+=BAND_OP operands+=equality_expression)*
   ;

exclusive_or_expression:
   operands+=and_expression (operators+=BXOR_OP operands+=and_expression)*
   ;

inclusive_or_expression:
   operands+=exclusive_or_expression (operators+=BOR_OP operands+=exclusive_or_expression)*
   ;

logical_and_expression:
   operands+=inclusive_or_expression (operators+=AND_OP operands+=inclusive_or_expression)*
   ;

logical_xor_expression:
   operands+=logical_and_expression (operators+=XOR_OP operands+=logical_and_expression)*
   ;

logical_or_expression:
   operands+=logical_xor_expression (operators+=OR_OP operands+=logical_xor_expression)*
   ;

conditional_expression:
   logical_or_expression (QUERY_OP expression COLON assignment_expression)*
   ;

assignment_expression:
   conditional_expression
   | unary_expression assignment_operator assignment_expression
   ;

assignment_operator:
   ASSIGN_OP
   | MUL_ASSIGN
   | DIV_ASSIGN
   | MOD_ASSIGN
   | ADD_ASSIGN
   | SUB_ASSIGN
   | LEFT_ASSIGN
   | RIGHT_ASSIGN
   | AND_ASSIGN
   | XOR_ASSIGN
   | OR_ASSIGN
   ;

expression:
   operands+=assignment_expression (operators+=COMMA operands+=assignment_expression)*
   ;

constant_expression:
   conditional_expression
   ;

declaration:
   function_prototype SEMICOLON
   | init_declarator_list SEMICOLON
   | PRECISION precision_qualifier type_specifier SEMICOLON
   | interface_block
   ;

function_prototype:
   function_declarator RPAREN
   ;

function_declarator:
   function_header
   | function_header_with_parameters
   ;

function_header_with_parameters:
   function_header parameter_declaration
   | function_header_with_parameters COMMA parameter_declaration
   ;

function_header:
   fully_specified_type variable_identifier LPAREN
   ;

parameter_declarator:
   type_specifier IDENTIFIER
   | type_specifier IDENTIFIER array_specifier
   ;

parameter_declaration:
   parameter_qualifier parameter_declarator
   | parameter_qualifier parameter_type_specifier
   ;

parameter_qualifier:
   // empty
   | CONST_TOK parameter_qualifier
   | PRECISE parameter_qualifier
   | parameter_direction_qualifier parameter_qualifier
   | precision_qualifier parameter_qualifier
   ;

parameter_direction_qualifier:
   IN_TOK
   | OUT_TOK
   | INOUT_TOK
   ;

parameter_type_specifier:
   type_specifier
   ;

init_declarator_list:
   single_declaration
   | init_declarator_list COMMA IDENTIFIER
   | init_declarator_list COMMA IDENTIFIER array_specifier
   | init_declarator_list COMMA IDENTIFIER array_specifier ASSIGN_OP initializer
   | init_declarator_list COMMA IDENTIFIER ASSIGN_OP initializer
   ;

   // Grammar Note: No 'enum', or 'typedef'.
single_declaration:
   fully_specified_type
   | fully_specified_type IDENTIFIER
   | fully_specified_type IDENTIFIER array_specifier
   | fully_specified_type IDENTIFIER array_specifier ASSIGN_OP initializer
   | fully_specified_type IDENTIFIER ASSIGN_OP initializer
   | INVARIANT variable_identifier
   | PRECISE variable_identifier
   ;

fully_specified_type:
   type_specifier
   | type_qualifier type_specifier
   ;

layout_qualifier:
   LAYOUT_TOK LPAREN layout_qualifier_id_list RPAREN
   ;

layout_qualifier_id_list:
   layout_qualifier_id
   | layout_qualifier_id_list COMMA layout_qualifier_id
   ;

integer_constant:
   INTCONSTANT
   | UINTCONSTANT
   ;

layout_qualifier_id:
   IDENTIFIER
   | IDENTIFIER ASSIGN_OP integer_constant
   | interface_block_layout_qualifier
   ;

// This is a separate language rule because we parse these as tokens
// (due to them being reserved keywords) instead of identifiers like
// most qualifiers.  See the IDENTIFIER path of
// layout_qualifier_id for the others.
//
// Note that since layout qualifiers are case-insensitive in desktop
// GLSL, all of these qualifiers need to be handled as identifiers as
// well.
//
interface_block_layout_qualifier:
   ROW_MAJOR
   | PACKED_TOK
   ;

interpolation_qualifier:
   SMOOTH
   | FLAT
   | NOPERSPECTIVE
   ;

type_qualifier:
   // Single qualifiers
   INVARIANT
   | PRECISE
   | auxiliary_storage_qualifier
   | storage_qualifier
   | interpolation_qualifier
   | layout_qualifier
   | precision_qualifier

   // Multiple qualifiers:
    // In GLSL 4.20, these can be specified in any order.  In earlier versions,
    // they appear in this order (see GLSL 1.50 section 4.7 & comments below):
    //
    //    invariant interpolation auxiliary storage precision  ...or...
    //    layout storage precision
    //
    // Each qualifier's rule ensures that the accumulated qualifiers on the right
    // side don't contain any that must appear on the left hand side.
    // For example, when processing a storage qualifier, we check that there are
    // no auxiliary, interpolation, layout, invariant, or precise qualifiers to the right.
    ///
   | PRECISE type_qualifier
   | INVARIANT type_qualifier
   | interpolation_qualifier type_qualifier
   | layout_qualifier type_qualifier
   | auxiliary_storage_qualifier type_qualifier
   | storage_qualifier type_qualifier
   | precision_qualifier type_qualifier
   ;

auxiliary_storage_qualifier:
   CENTROID
   | SAMPLE
   ;

storage_qualifier:
   CONST_TOK
   | ATTRIBUTE
   | VARYING
   | IN_TOK
   | OUT_TOK
   | UNIFORM
   | COHERENT
   | VOLATILE
   | RESTRICT
   | READONLY
   | WRITEONLY
   ;

array_specifier:
   LBRACKET RBRACKET
   | LBRACKET constant_expression RBRACKET
   | array_specifier LBRACKET RBRACKET
   | array_specifier LBRACKET constant_expression RBRACKET
   ;

type_specifier:
   type_specifier_nonarray
   | type_specifier_nonarray array_specifier
   ;

type_specifier_nonarray:
   builtin_type_specifier_nonarray
   | struct_specifier
   | IDENTIFIER
   ;

builtin_type_specifier_nonarray:
   VOID_TOK
   | FLOAT_TOK
   | INT_TOK
   | UINT_TOK
   | BOOL_TOK
   | VEC2
   | VEC3
   | VEC4
   | BVEC2
   | BVEC3
   | BVEC4
   | IVEC2
   | IVEC3
   | IVEC4
   | UVEC2
   | UVEC3
   | UVEC4
   | MAT2X2
   | MAT2X3
   | MAT2X4
   | MAT3X2
   | MAT3X3
   | MAT3X4
   | MAT4X2
   | MAT4X3
   | MAT4X4
   | SAMPLER1D
   | SAMPLER2D
   | SAMPLER2DRECT
   | SAMPLER3D
   | SAMPLERCUBE
   | SAMPLEREXTERNALOES
   | SAMPLER1DSHADOW
   | SAMPLER2DSHADOW
   | SAMPLER2DRECTSHADOW
   | SAMPLERCUBESHADOW
   | SAMPLER1DARRAY
   | SAMPLER2DARRAY
   | SAMPLER1DARRAYSHADOW
   | SAMPLER2DARRAYSHADOW
   | SAMPLERBUFFER
   | SAMPLERCUBEARRAY
   | SAMPLERCUBEARRAYSHADOW
   | ISAMPLER1D
   | ISAMPLER2D
   | ISAMPLER2DRECT
   | ISAMPLER3D
   | ISAMPLERCUBE
   | ISAMPLER1DARRAY
   | ISAMPLER2DARRAY
   | ISAMPLERBUFFER
   | ISAMPLERCUBEARRAY
   | USAMPLER1D
   | USAMPLER2D
   | USAMPLER2DRECT
   | USAMPLER3D
   | USAMPLERCUBE
   | USAMPLER1DARRAY
   | USAMPLER2DARRAY
   | USAMPLERBUFFER
   | USAMPLERCUBEARRAY
   | SAMPLER2DMS
   | ISAMPLER2DMS
   | USAMPLER2DMS
   | SAMPLER2DMSARRAY
   | ISAMPLER2DMSARRAY
   | USAMPLER2DMSARRAY
   | IMAGE1D
   | IMAGE2D
   | IMAGE3D
   | IMAGE2DRECT
   | IMAGECUBE
   | IMAGEBUFFER
   | IMAGE1DARRAY
   | IMAGE2DARRAY
   | IMAGECUBEARRAY
   | IMAGE2DMS
   | IMAGE2DMSARRAY
   | IIMAGE1D
   | IIMAGE2D
   | IIMAGE3D
   | IIMAGE2DRECT
   | IIMAGECUBE
   | IIMAGEBUFFER
   | IIMAGE1DARRAY
   | IIMAGE2DARRAY
   | IIMAGECUBEARRAY
   | IIMAGE2DMS
   | IIMAGE2DMSARRAY
   | UIMAGE1D
   | UIMAGE2D
   | UIMAGE3D
   | UIMAGE2DRECT
   | UIMAGECUBE
   | UIMAGEBUFFER
   | UIMAGE1DARRAY
   | UIMAGE2DARRAY
   | UIMAGECUBEARRAY
   | UIMAGE2DMS
   | UIMAGE2DMSARRAY
   | ATOMIC_UINT
   ;

precision_qualifier:
   HIGHP
   | MEDIUMP
   | LOWP
   ;

struct_specifier:
   STRUCT IDENTIFIER? LBRACE member_list RBRACE
   ;

member_list:
   member_declaration
   | member_list member_declaration
   ;

member_declaration:
   fully_specified_type struct_declarator_list SEMICOLON
   ;

struct_declarator_list:
   struct_declarator
   | struct_declarator_list COMMA struct_declarator
   ;

struct_declarator:
   IDENTIFIER
   | IDENTIFIER array_specifier
   ;

initializer:
   assignment_expression
   | LBRACE initializer_list RBRACE
   | LBRACE initializer_list COMMA RBRACE
   ;

initializer_list:
   initializer
   | initializer_list COMMA initializer
   ;

declaration_statement:
   declaration
   ;

   // Grammar Note: labeled statements for SWITCH only; 'goto' is not
   // supported.
statement:
   compound_statement
   | simple_statement
   ;

simple_statement:
   expression_statement
   | declaration_statement
   | selection_statement
   | switch_statement
   | iteration_statement
   | jump_statement
   ;

compound_statement:
   LBRACE RBRACE
   | LBRACE
   statement_list RBRACE
   ;

statement_no_new_scope:
   compound_statement_no_new_scope
   | simple_statement
   ;

compound_statement_no_new_scope:
   LBRACE RBRACE
   | LBRACE statement_list RBRACE
   ;

statement_list:
   statement
   | statement_list statement
   ;

expression_statement:
   SEMICOLON
   | expression SEMICOLON
   ;

selection_statement:
   IF LPAREN expression RPAREN selection_rest_statement
   ;

selection_rest_statement:
   statement ELSE statement
   | statement // REVISIT: binding issue with conditionals
   ;

condition:
   expression
   | fully_specified_type IDENTIFIER ASSIGN_OP initializer
   ;

///
 // switch_statement grammar is based on the syntax described in the body
 // of the GLSL spec, not in it's appendix!!!
 ///
switch_statement:
   SWITCH LPAREN expression RPAREN switch_body
   ;

switch_body:
   LBRACE RBRACE
   | LBRACE case_statement_list RBRACE
   ;

case_label:
   CASE expression COLON
   | DEFAULT COLON
   ;

case_label_list:
   case_label
   | case_label_list case_label
   ;

case_statement:
   case_label_list statement
   | case_statement statement
   ;

case_statement_list:
   case_statement
   | case_statement_list case_statement
   ;

iteration_statement:
   WHILE LPAREN condition RPAREN statement_no_new_scope
   | DO statement WHILE LPAREN expression RPAREN SEMICOLON
   | FOR LPAREN for_init_statement for_rest_statement RPAREN statement_no_new_scope
   ;

for_init_statement:
   expression_statement
   | declaration_statement
   ;

conditionopt:
   condition
   | /// empty ///
   ;

for_rest_statement:
   conditionopt SEMICOLON
   | conditionopt SEMICOLON expression
   ;

   // Grammar Note: No 'goto'. Gotos are not supported.
jump_statement:
   CONTINUE SEMICOLON
   | BREAK SEMICOLON
   | RETURN SEMICOLON
   | RETURN expression SEMICOLON
   | DISCARD SEMICOLON // Fragment shader only.
   ;

external_declaration:
   function_definition
   | declaration
   | pragma_statement
   | layout_defaults
   ;

function_definition:
   function_prototype compound_statement_no_new_scope
   ;

/// layout_qualifieropt is packed into this rule ///
interface_block:
   basic_interface_block
   | layout_qualifier basic_interface_block
   ;

basic_interface_block:
   interface_qualifier IDENTIFIER LBRACE member_list RBRACE instance_name_opt SEMICOLON
   ;

interface_qualifier:
   IN_TOK
   | OUT_TOK
   | UNIFORM
   | BUFFER
   ;

instance_name_opt:
   /// empty ///
   | IDENTIFIER
   | IDENTIFIER array_specifier
   ;

layout_defaults:
   layout_qualifier UNIFORM SEMICOLON
   | layout_qualifier IN_TOK SEMICOLON
   | layout_qualifier OUT_TOK SEMICOLON
   | layout_qualifier BUFFER SEMICOLON
;

PRAGMA_DEBUG_ON:      { ignoreNewLine = false; } [ \t]*'#'[ \t]*'pragma'[ \t]+'debug'[ \t]*'('[ \t]*'on'[ \t]*')' ;
PRAGMA_DEBUG_OFF:     { ignoreNewLine = false; } [ \t]*'#'[ \t]*'pragma'[ \t]+'debug'[ \t]*'('[ \t]*'off'[ \t]*')'     ;
PRAGMA_OPTIMIZE_ON:   { ignoreNewLine = false; } [ \t]*'#'[ \t]*'pragma'[ \t]+'optimize'[ \t]*'('[ \t]*'on'[ \t]*')'   ;
PRAGMA_OPTIMIZE_OFF:  { ignoreNewLine = false; } [ \t]*'#'[ \t]*'pragma'[ \t]+'optimize'[ \t]*'('[ \t]*'off'[ \t]*')'  ;
PRAGMA_INVARIANT_ALL: { ignoreNewLine = false; } [ \t]*'#'[ \t]*'pragma'[ \t]+'invariant'[ \t]*'('[ \t]*'all'[ \t]*')' ;
EXTENSION: { ignoreNewLine = false; } [ \t]*'#'[ \t]*'extension' ;
COLON: ':' ;
UNIFORM: 'uniform' ;
BUFFER: 'buffer' ;
IN_TOK: 'in' ;
OUT_TOK: 'out' ;
INOUT_TOK: 'inout' ;
HIGHP: 'highp' ;
MEDIUMP: 'mediump' ;
LOWP: 'lowp' ;
PRECISION: 'precision' ;
VERSION: { ignoreNewLine = false; } [ \t]*'#'[ \t]*'version' ;
INTCONSTANT: DIGIT+ ; // REVISIT

CONST_TOK: 'const' ;
PRECISE: 'precise' ;
INVARIANT: 'invariant' ;
SMOOTH: 'smooth' ;
FLAT: 'flat' ;
NOPERSPECTIVE: 'noperspective' ;
CENTROID: 'centroid' ;
SAMPLE: 'sample' ;
ATTRIBUTE: 'attribute' ;
COHERENT: 'coherent' ;
VOLATILE: 'volatile' ;
RESTRICT: 'restrict' ;
VARYING: 'varying' ;
READONLY: 'readonly' ;
WRITEONLY: 'writeonly' ;
LAYOUT_TOK: 'layout' ; // REVISIT
UINTCONSTANT: DIGIT+ 'u' ; // REVISIT
ROW_MAJOR: 'row_major' ;
PACKED_TOK: 'packed' ;
FLOATCONSTANT: ((DIGIT+ ('.' DIGIT*)?) | ('.' DIGIT+)) (('e' | 'E') ('+' | '-')? DIGIT*)?; // REVISIT
BOOLCONSTANT: 'true' | 'false' ;
INC_OP: '++' ;
DEC_OP: '--' ;
VOID_TOK: 'void' ;
FIELD_SELECTION: 'C_TODO' ; // REVISIT
LEFT_OP: '<<' ;
RIGHT_OP: '>>' ;
LE_OP: '<=' ;
GE_OP: '>=' ;
EQ_OP: '==' ;
NE_OP: '!=' ;
AND_OP: '&&' ;
XOR_OP: '^^' ;
OR_OP: '||' ;
MUL_ASSIGN: '*=' ;
DIV_ASSIGN: '/=' ;
MOD_ASSIGN: '%=' ;
ADD_ASSIGN: '+=' ;
SUB_ASSIGN: '-=' ;
LEFT_ASSIGN: '<<=' ;
RIGHT_ASSIGN: '>>=' ;
AND_ASSIGN: '&=' ;
XOR_ASSIGN: '^=' ;
OR_ASSIGN: '|=' ;
FLOAT_TOK: 'float' ;
INT_TOK: 'int' ;
UINT_TOK: 'uint' ;
BOOL_TOK: 'bool' ;
// VEC: 'TODO' ;
// BVEC: 'TODO' ;
// IVEC: 'TODO' ;
// UVEC: 'TODO' ;
// MATX: 'TODO' ;
// SAMPLERD: 'TODO' ;
// SAMPLERDRECT: 'TODO' ;
SAMPLERCUBE: 'samplerCube' ;
SAMPLEREXTERNALOES: 'samplerExternalOES' ; // REVISIT
// SAMPLERDSHADOW: 'TODO' ;
// SAMPLERDRECTSHADOW: 'TODO' ;
SAMPLERCUBESHADOW: 'samplerCubeShadow' ;
// SAMPLERDARRAY: 'TODO' ;
// SAMPLERDARRAYSHADOW: 'TODO' ;
SAMPLERBUFFER: 'samplerBuffer' ;
SAMPLERCUBEARRAY: 'samplerCubeArray' ;
SAMPLERCUBEARRAYSHADOW: 'samplerCubeArrayShadow' ;
// ISAMPLERD: 'TODO' ;
// ISAMPLERDRECT: 'TODO' ;
ISAMPLERCUBE: 'isamplerCube' ;
// ISAMPLERDARRAY: 'TODO' ;
ISAMPLERBUFFER: 'isamplerBuffer' ;
ISAMPLERCUBEARRAY: 'isamplerCubeArray' ;
// USAMPLERD: 'TODO' ;
// USAMPLERDRECT: 'TODO' ;
USAMPLERCUBE: 'usamplerCube' ;
// USAMPLERDARRAY: 'TODO' ;
USAMPLERBUFFER: 'usamplerBuffer' ;
USAMPLERCUBEARRAY: 'usamplerCubeArray' ;
// SAMPLERDMS: 'TODO' ;
// ISAMPLERDMS: 'TODO' ;
// USAMPLERDMS: 'TODO' ;
// SAMPLERDMSARRAY: 'TODO' ;
// ISAMPLERDMSARRAY: 'TODO' ;
// USAMPLERDMSARRAY: 'TODO' ;
// IMAGED: 'TODO' ;
// IMAGEDRECT: 'TODO' ;
IMAGECUBE: 'imageCube' ;
IMAGEBUFFER: 'imageBuffer' ;
// IMAGEDARRAY: 'TODO' ;
IMAGECUBEARRAY: 'imageCubeArray' ;
// IMAGEDMS: 'TODO' ;
// IMAGEDMSARRAY: 'TODO' ;
// IIMAGED: 'TODO' ;
// IIMAGEDRECT: 'TODO' ;
IIMAGECUBE: 'iimageCube' ;
IIMAGEBUFFER: 'iimageBuffer' ;
// IIMAGEDARRAY: 'TODO' ;
IIMAGECUBEARRAY: 'iimageCubeArray' ;
// IIMAGEDMS: 'TODO' ;
// IIMAGEDMSARRAY: 'TODO' ;
// UIMAGED: 'TODO' ;
// UIMAGEDRECT: 'TODO' ;
UIMAGECUBE: 'uimageCube' ;
UIMAGEBUFFER: 'uimageBuffer' ;
// UIMAGEDARRAY: 'TODO' ;
UIMAGECUBEARRAY: 'uimageCubeArray' ;
// UIMAGEDMS: 'TODO' ;
// UIMAGEDMSARRAY: 'TODO' ;
ATOMIC_UINT: 'atomic_uint' ;
STRUCT: 'struct' ;
IF: 'if' ;
ELSE: 'else' ;
SWITCH: 'switch' ;
CASE: 'case' ;
DEFAULT: 'default' ;
WHILE: 'while' ;
DO: 'do' ;
FOR: 'for' ;
CONTINUE: 'continue' ;
BREAK: 'break' ;
RETURN: 'return' ;
DISCARD: 'discard' ;
VEC2: 'vec2' ;
VEC3: 'vec3' ;
VEC4: 'vec4' ;
BVEC2: 'bvec2' ;
BVEC3: 'bvec3' ;
BVEC4: 'bvec4' ;
IVEC2: 'ivec2' ;
IVEC3: 'ivec3' ;
IVEC4: 'ivec4' ;
UVEC2: 'uvec2' ;
UVEC3: 'uvec3' ;
UVEC4: 'uvec4' ;
MAT2X2: 'mat2' | 'mat2x2' ;
MAT2X3: 'mat2x3' ;
MAT2X4: 'mat2x4' ;
MAT3X2: 'mat3x2' ;
MAT3X3: 'mat3' | 'mat3x3' ;
MAT3X4: 'mat3x4' ;
MAT4X2: 'mat4x2' ;
MAT4X3: 'mat4x3' ;
MAT4X4: 'mat4' | 'mat4x4' ;
IMAGE1D: 'image1D' ;
IMAGE2D: 'image2D' ;
IMAGE3D: 'image3D' ;
UIMAGE1D: 'uimage1D' ;
UIMAGE2D: 'uimage2D' ;
UIMAGE3D: 'uimage3D' ;
IIMAGE1D: 'iimage1D' ;
IIMAGE2D: 'iimage2D' ;
IIMAGE3D: 'iimage3D' ;
SAMPLER1D: 'sampler1D' ;
SAMPLER2D: 'sampler2D' ;
SAMPLER3D: 'sampler3D' ;
SAMPLER2DRECT: 'sampler2DRect' ;

SAMPLER1DSHADOW: 'sampler1DShadow' ;
SAMPLER2DSHADOW: 'sampler2DShadow' ;
SAMPLER2DRECTSHADOW: 'sampler2DRectShadow' ;
SAMPLER1DARRAY: 'sampler1DArray' ;
SAMPLER2DARRAY: 'sampler2DArray' ;
SAMPLER1DARRAYSHADOW: 'sampler1DArrayShadow' ;
SAMPLER2DARRAYSHADOW: 'sampler2DArrayShadow' ;
ISAMPLER1D: 'isampler1D' ;
ISAMPLER2D: 'isampler2D' ;
ISAMPLER2DRECT: 'isampler2DRect' ;
ISAMPLER3D: 'isampler3D' ;
ISAMPLER1DARRAY: 'isampler1DArray' ;
ISAMPLER2DARRAY: 'isampler2DArray' ;
USAMPLER1D: 'usampler1D' ;
USAMPLER2D: 'usampler2D' ;
USAMPLER2DRECT: 'usampler2DRect' ;
USAMPLER3D: 'usampler3D' ;
USAMPLER1DARRAY: 'usampler1DArray' ;
USAMPLER2DARRAY: 'usampler2DArray' ;
SAMPLER2DMS: 'sampler2DMS' ;
ISAMPLER2DMS: 'isampler2DMS' ;
USAMPLER2DMS: 'usampler2DMS' ;
SAMPLER2DMSARRAY: 'sampler2DMSArray' ;
ISAMPLER2DMSARRAY: 'isampler2DMSArray' ;
USAMPLER2DMSARRAY: 'usampler2DMSArray' ;
IMAGE2DRECT: 'image2DRect' ;
IMAGE1DARRAY: 'image1DArray' ;
IMAGE2DARRAY: 'image2DArray' ;
IMAGE2DMS: 'image2DMS' ;
IMAGE2DMSARRAY: 'image2DMSArray' ;
IIMAGE2DRECT: 'iimage2DRect' ;
IIMAGE1DARRAY: 'iimage1DArray' ;
IIMAGE2DARRAY: 'iimage2DArray' ;
IIMAGE2DMS: 'iimage2DMS' ;
IIMAGE2DMSARRAY: 'iimage2DMSArray' ;
UIMAGE2DRECT: 'uimage2DRect' ;
UIMAGE1DARRAY: 'uimage1DArray' ;
UIMAGE2DARRAY: 'uimage2DArray' ;
UIMAGE2DMS: 'uimage2DMS' ;
UIMAGE2DMSARRAY: 'uimage2DMSArray' ;

LPAREN: '(' ;
RPAREN: ')' ;
LBRACE: '{' ;
RBRACE: '}' ;
SEMICOLON: ';' ;
LBRACKET: '[' ;
RBRACKET: ']' ;
COMMA: ',' ;

DOT: '.' ;
PLUS_OP: '+' ;
MINUS_OP: '-' ;
NOT_OP: '!' ;
BNEG_OP: '~' ;
TIMES_OP: '*' ;
DIV_OP: '/' ;
MOD_OP: '%' ;
LT_OP: '<' ;
GT_OP: '>' ;
BAND_OP: '&' ;
BOR_OP: '|' ;
BXOR_OP: '^' ;
QUERY_OP: '?' ;
ASSIGN_OP: '=' ;

IDENTIFIER: ('a'..'z' | 'A'..'Z' | '_') (DIGIT | 'a'..'z' | 'A'..'Z' | '_')*;
fragment DIGIT  : '0'..'9';


COMMENT: ('//' ~('\n'|'\r')* '\r'? '\n' |   '/*' (.)*? '*/') -> skip ;

WS: [\t\r\u000C ]+ { skip(); } ;

EOL: '\n' { if(ignoreNewLine) { skip(); } ignoreNewLine = true; } ;

/*

%token ATTRIBUTE CONST_TOK BOOL_TOK FLOAT_TOK INT_TOK UINT_TOK
%token BREAK CONTINUE DO ELSE FOR IF DISCARD RETURN SWITCH CASE DEFAULT
%token BVEC2 BVEC3 BVEC4 IVEC2 IVEC3 IVEC4 UVEC2 UVEC3 UVEC4 VEC2 VEC3 VEC4
%token CENTROID IN_TOK OUT_TOK INOUT_TOK UNIFORM VARYING SAMPLE
%token NOPERSPECTIVE FLAT SMOOTH
%token MAT2X2 MAT2X3 MAT2X4
%token MAT3X2 MAT3X3 MAT3X4
%token MAT4X2 MAT4X3 MAT4X4
%token SAMPLER1D SAMPLER2D SAMPLER3D SAMPLERCUBE SAMPLER1DSHADOW SAMPLER2DSHADOW
%token SAMPLERCUBESHADOW SAMPLER1DARRAY SAMPLER2DARRAY SAMPLER1DARRAYSHADOW
%token SAMPLER2DARRAYSHADOW SAMPLERCUBEARRAY SAMPLERCUBEARRAYSHADOW
%token ISAMPLER1D ISAMPLER2D ISAMPLER3D ISAMPLERCUBE
%token ISAMPLER1DARRAY ISAMPLER2DARRAY ISAMPLERCUBEARRAY
%token USAMPLER1D USAMPLER2D USAMPLER3D USAMPLERCUBE USAMPLER1DARRAY
%token USAMPLER2DARRAY USAMPLERCUBEARRAY
%token SAMPLER2DRECT ISAMPLER2DRECT USAMPLER2DRECT SAMPLER2DRECTSHADOW
%token SAMPLERBUFFER ISAMPLERBUFFER USAMPLERBUFFER
%token SAMPLER2DMS ISAMPLER2DMS USAMPLER2DMS
%token SAMPLER2DMSARRAY ISAMPLER2DMSARRAY USAMPLER2DMSARRAY
%token SAMPLEREXTERNALOES
%token IMAGE1D IMAGE2D IMAGE3D IMAGE2DRECT IMAGECUBE IMAGEBUFFER
%token IMAGE1DARRAY IMAGE2DARRAY IMAGECUBEARRAY IMAGE2DMS IMAGE2DMSARRAY
%token IIMAGE1D IIMAGE2D IIMAGE3D IIMAGE2DRECT IIMAGECUBE IIMAGEBUFFER
%token IIMAGE1DARRAY IIMAGE2DARRAY IIMAGECUBEARRAY IIMAGE2DMS IIMAGE2DMSARRAY
%token UIMAGE1D UIMAGE2D UIMAGE3D UIMAGE2DRECT UIMAGECUBE UIMAGEBUFFER
%token UIMAGE1DARRAY UIMAGE2DARRAY UIMAGECUBEARRAY UIMAGE2DMS UIMAGE2DMSARRAY
%token IMAGE1DSHADOW IMAGE2DSHADOW IMAGE1DARRAYSHADOW IMAGE2DARRAYSHADOW
%token COHERENT VOLATILE RESTRICT READONLY WRITEONLY
%token ATOMIC_UINT
%token STRUCT VOID_TOK WHILE
%token IDENTIFIER TYPE_IDENTIFIER IDENTIFIER
%token FLOATCONSTANT
%token INTCONSTANT UINTCONSTANT BOOLCONSTANT
%token FIELD_SELECTION
%token LEFT_OP RIGHT_OP
%token INC_OP DEC_OP LE_OP GE_OP EQ_OP NE_OP
%token AND_OP OR_OP XOR_OP MUL_ASSIGN DIV_ASSIGN ADD_ASSIGN
%token MOD_ASSIGN LEFT_ASSIGN RIGHT_ASSIGN AND_ASSIGN XOR_ASSIGN OR_ASSIGN
%token SUB_ASSIGN
%token INVARIANT PRECISE
%token LOWP MEDIUMP HIGHP SUPERP PRECISION

%token VERSION EXTENSION LINE COLON EOL INTERFACE OUTPUT
%token PRAGMA_DEBUG_ON PRAGMA_DEBUG_OFF
%token PRAGMA_OPTIMIZE_ON PRAGMA_OPTIMIZE_OFF
%token PRAGMA_INVARIANT_ALL
%token LAYOUT_TOK

// Reserved words that are not actually used in the grammar.
%token ASM CLASS UNION ENUM TYPEDEF TEMPLATE THIS PACKED_TOK GOTO
%token INLINE_TOK NOINLINE PUBLIC_TOK STATIC EXTERN EXTERNAL
%token LONG_TOK SHORT_TOK DOUBLE_TOK HALF FIXED_TOK UNSIGNED INPUT_TOK
%token HVEC2 HVEC3 HVEC4 DVEC2 DVEC3 DVEC4 FVEC2 FVEC3 FVEC4
%token SAMPLER3DRECT
%token SIZEOF CAST NAMESPACE USING
%token RESOURCE PATCH
%token SUBROUTINE

%token ERROR_TOK

%token COMMON PARTITION ACTIVE FILTER ROW_MAJOR

%right THEN ELSE
%%

*/
