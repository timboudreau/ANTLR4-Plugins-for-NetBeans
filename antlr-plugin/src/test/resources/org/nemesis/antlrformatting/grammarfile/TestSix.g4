/*
Leading comment followed by blank line */

parser grammar TestSix;

 options { tokenVocab = TestFour; 
           tokenVocab = TestThree; 
            // a line comment
           tokenVocab = TestFive; 
    }


   tokens { FOO, BAR, BAZ }

tokens { ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN, ELEVEN, TWELVE, THIRTEEN, FOURTEEN, FIFTEEN, SIXTEEN, SEVENTEEN, EIGHTEEN, NINETEEN, TWENTY, TWENTYONE, TWENTY_TWO, TWENTY_THREE, TWENTY_FOUR }


@parser::header {
    import java.util.*;
}

@parser::members {
        Set<String> importedTypes = new HashSet<>();
    Set<String> referencedTypes = new HashSet<>();
      Set<String> definedTypes = new HashSet<>();
    Map<String,String> aliasedTypes = new HashMap<>();
}

bug : Word;

/** A doc 
 * comment */
word : Word { System.out.println(ctx); };

thing : Word Word 
{
     // a comment
     System.out.println("foo");
     if (true) {
         int x = 23;
     }
};

whunk : FOO | BAR | BAZ;

bubba : {istype()}? BAZ | {isfunc()}? BAR;

add[int x] returns [int result] : '+=' FOO { $result = $x + $FOO.int };

throwingThing : FOO BAR+
;
    catch [RecognitionException e] { throw e; }
    finally { System.out.println("that's all, folks");
            // how about a comment and more stuff
            assert 2 + 2 == 4;
 }


otherThing : FOO+ {
    // these braces should stay commented
    // if (true) {
    //    System.out.println("Hey");
    // }
    // otherwise we make a mess
}
