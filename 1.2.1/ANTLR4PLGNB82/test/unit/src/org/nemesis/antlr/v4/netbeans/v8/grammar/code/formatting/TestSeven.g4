/*
Leading comment followed by blank line */

parser grammar TestSix;

 options { tokenVocab = TestFour; 
           tokenVocab = TestThree; 
            // a line comment
           tokenVocab = TestFive; 
    }

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

word : Word { System.out.println(ctx); };

thing : Word Word 
{
     // a comment
     System.out.println("foo");
     if (true) {
         int x = 23;
     }
};
