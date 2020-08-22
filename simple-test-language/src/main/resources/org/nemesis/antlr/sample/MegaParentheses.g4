grammar MegaParentheses;

compilation_unit
    : (stuff | things | other | withLineComments | simple otherOrs | withLabels)+ EOF;

// this is what happens when you invent a grammar for a test
// while playing rhyming games with a five year old.
mat : 'mat';
bat : 'bat';
cat : 'cat';
flat : 'flat';
gnat : 'gnat';
sat : 'sat';
ladybug : 'ladybug';
bug : 'bug';
rug : 'rug';
hug : 'hug';
mug : 'mug';
lug : 'lug';
tug : 'tug';
pug : 'pug';
dug : 'dug';
wig : 'wig';
wham : 'wham';
bam : 'bam';

ring : 'ring';
ding : 'ding';
lava : 'the floor is lava';
orange : 'orange';
yellow : 'yellow';
black : 'black';
yessir : 'yes sir';

stuff : mat | black 
    ( 
        (wig | wham | bam)*? dug cat | gnat (bug | lug | tug
    (ding | ring ding | lava (yessir lava ring 
    (wig |wham |bam (ladybug | flat sat (hug | lug (orange | yellow )* )+?  )+ )+) lava )* ) tug bug )+;

things :
   bat cat (mat | gnat)* (ring | ding)+ (hug (bug | rug)* lug (tug pug)+);

other : 
    (ring | ding (wig | bam)* )+ ;

withLineComments : 
    (ring | ding // whatevs
    ( // hello
    wig | bam)* ) //uh
    + ;

simple
    : yessir ( yellow
        | orange );

otherOrs : 
    (attrs=mug* lifetimes=yellow? mv=lava? mat params=wham? mat bam? (body=ladybug | expr=orange))
    | (attrs=mug* mv=lava? dug pug? (body=ladybug | expr=orange))
    | (attrs=mug* mv=lava? cat cat gnat? (body=ladybug | expr=orange));

withLabels 
    : (first=mug( flat more=ladybug )*
    orange
            ( ring
            | ding
            | mat )) #MultiCaseLiteral
    |
        ( range=lava yellow
            ( black
            | hug
            | bug )) #ExclusiveRangeCase
    |
        ( range=lava dug
            ( flat
            | cat
            | ding )) #InclusiveRangeCase

