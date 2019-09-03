package org.nemesis.antlr.project.spi;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class GlobFilterTest {

    @Test
    public void testSomeMethod() {
        Path base = Paths.get("/home/joe/work/projects/myproject/src");
        Path a = base.resolve("com/foo/bar/SomeGrammar.g4");
        Path b = base.resolve("com/foo/bar/SomeGrammar.java");
        Path c = base.resolve("org/baz/bar/SomeGrammar.g4");
        Path d = base.resolve("org/baz/bug/SomeGrammar.g4");
        Path e = base.resolve("org/baz/bar/woohoo/OtherGrammar.g4");
        Path f = base.resolve("org/foo/bar/woohoo/OtherGrammar.g4");
        Path g = base.resolve("org/foo/bar/woohoo/wunk/OtherGrammar.g4");
        Path h = base.resolve("com/foo/bar/.g4");

        GlobFilter glob = new GlobFilter("**/*.g4");

        Predicate<Path> pred = glob.forBaseDir(base);
        assertTrue(pred.test(a), a::toString);
        assertFalse(pred.test(b), b::toString);
        for (Path p : new Path[]{a, c, d, e, f, g, h}) {
            assertTrue(pred.test(p), "Should match '" + p + "' - " + glob);
        }

        glob = new GlobFilter("**/?.g4");
        pred = glob.forBaseDir(base);
        for (Path p : new Path[]{a, c, d, e, f, g}) {
            assertTrue(pred.test(p), "Should match '" + p + "' - " + glob);
        }
        assertFalse(pred.test(h), "Should not match " + h + "' - " + glob);

        glob = new GlobFilter("*/baz/**/*.g4");
        pred = glob.forBaseDir(base);

        assertFalse(pred.test(a), a::toString);
        assertFalse(pred.test(b), b::toString);
        assertTrue(pred.test(c), c::toString);
        assertTrue(pred.test(d), d::toString);
        assertTrue(pred.test(e), e::toString);
        assertFalse(pred.test(f), f::toString);
        glob = new GlobFilter("*/f*/**/*.g4");
        pred = glob.forBaseDir(base);
        assertFalse(pred.test(e));
        assertTrue(pred.test(f));
        assertTrue(pred.test(g));
    }

}
