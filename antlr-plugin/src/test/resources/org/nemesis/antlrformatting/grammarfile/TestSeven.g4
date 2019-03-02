parser grammar TestSeven;

tokens { FOO, BAR, /*inline-comment*/ BAZ }

otherThing : FOO+ {
    // these braces should stay commented
      // if (true) {
    //    System.out.println("Hey");
     // }
    // otherwise we make a mess
}
;
    // these comments
    // should stay
    // indented

            /* This is a block comment hanging out in the ether */

// hmm, hmm

                                                       /* This is a block comment further out which is decidedly 
too long for the space available, and just goes on and on and on and on.  What's with this thing? */

use_statement : outer_attribute* Pub? Use use_path Semicolon { 
//    if (_localctx.path() != null) {
//        if (_localctx.path().end != null) {
//            importedTypes.add(_localctx.path().getText());
//            aliasedtypes.put(_localctx.path().end, localctx.path());
//        }
//    }
};

    /*
        This line comment should get wrapped on line breaks where needed, so it does not run too long.

            It should also respect multiple line breaks.


It should also convert more than two line breaks into a double line break,
        but ignore single line breaks when it reflows the words.
    */

whatzit : BAZ {
    if (foo == bar) {
        doSomething();
        doSomethingElse();
        // tee hee
        if (you.equals(me)) {
            // oops!
            System.exit(23);
        }
    }
}
