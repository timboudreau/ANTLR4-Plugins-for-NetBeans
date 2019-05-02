package org.nemesis.antlr.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Vocabulary;
import org.nemesis.antlr.completion.TokenTriggersBuilder.Any;
import com.mastfrog.util.collections.IntList;

/**
 *
 * @author Tim Boudreau
 */
public class TokenPatternBuilder {

    private final List<TokenPosition> l = new ArrayList<>(5);

    TokenPatternBuilder() {
        OneTokenBuilder otb = new OneTokenBuilder(':')
                .optional().followedBy('(').deleteIfPresent()
                .followedByTarget(8888).deleteIfPresent().optional()
                .followedBy(')').deleteIfPresent().optional()
                .followedBy(';').optional();

    }

    private static final class ArraysBuilder {

        private final List<IntList> lists = new ArrayList<>();

        ArraysBuilder() {
            lists.add(IntList.create());
        }

        int size() {
            return lists.size();
        }

        int[][] toIntsArray() {
            int[][] result = new int[lists.size()][];
            for (int i = 0; i < lists.size(); i++) {
                result[i] = lists.get(i).toIntArray();
            }
            return result;
        }

        void addInt(int val) {
            for (IntList a : lists) {
                a.add(val);
            }
        }

        List<IntList> copies() {
            return copies(lists);
        }

        private static List<IntList> copies(List<IntList> orig) {
            List<IntList> result = new ArrayList<>(orig.size());
            for (IntList i : orig) {
                result.add(i.copy());
            }
            return result;
        }

        private static void addToAll(int val, List<IntList> lists) {
            for (IntList i : lists) {
                i.add(val);
            }
        }

        void add(OneTokenBuilder b) {
            List<IntList> before = b.optional || b.tokenType.length > 0 ? copies() : null;
            List<List<IntList>> added = new ArrayList<>();
            if (b.optional) {
                added.add(before);
            }
            for (int i = 0; i < b.tokenType.length; i++) {
                if (i == 0) {
                    addToAll(b.tokenType[i], lists);
                } else {
                    List<IntList> copy = copies(before);
                    added.add(copy);
                    addToAll(b.tokenType[i], copy);
                }
            }
            for (List<IntList> stuff : added) {
                lists.addAll(stuff);
            }
        }

        void multiply(int size) {
            for (int i = 0; i < size; i++) {
                lists.addAll(copies(lists));
            }
        }
    }

    void doIt(OneTokenBuilder tail) {
        int ix = tail.index();
        if (ix < 0) {
            throw new IllegalStateException("No target token was set");
        }
        List<OneTokenBuilder> all = tail.list();
        ArraysBuilder befores = new ArraysBuilder();
        ArraysBuilder afters = new ArraysBuilder();
        IntPredicate target = Any.ANY;
        for (OneTokenBuilder b : all) {
            int index = b.index();
            if (index < 0) {
                befores.add(b);
            } else if (index == 0) {
                target = b.toPredicate();
            } else {
                afters.add(b);
            }
        }
        int oldBeforeSize = befores.size();
        int oldAfterSize = afters.size();
        befores.multiply(oldAfterSize);
        afters.multiply(oldBeforeSize);

        int[][] finalBefores = befores.toIntsArray();
        int[][] finalAfters = afters.toIntsArray();
        
    }

    public static final class OneTokenBuilder {

        private final int[] tokenType;
        private boolean optional;
        private boolean deleteIfPresent;
        private final boolean target;
        OneTokenBuilder previous;

        OneTokenBuilder(int... tokenType) {
            this(false, tokenType);
        }

        OneTokenBuilder(boolean target, int... tokenType) {
            assert tokenType.length >= 1;
            this.tokenType = tokenType;
            this.target = target;
        }

        public IntPredicate toPredicate() {
            return (i) -> {
                if (optional && i == 0) {
                    return true;
                }
                if (tokenType.length == 0) {
                    return true;
                }
                for (int j = 0; j < tokenType.length; j++) {
                    if (i == tokenType[j]) {
                        return true;
                    }
                }
                return false;
            };
        }

        public OneTokenBuilder deleteIfPresent() {
            deleteIfPresent = true;
            return this;
        }

        public OneTokenBuilder optional() {
            optional = true;
            return this;
        }

        public OneTokenBuilder followedBy(int... tokenType) {
            OneTokenBuilder result = new OneTokenBuilder(false, tokenType);
            resetIndexes();
            result.previous = this;
            return result;
        }

        public OneTokenBuilder followedByTarget(int... tokenType) {
            OneTokenBuilder result = new OneTokenBuilder(true, tokenType);
            if (distanceToTarget() != Integer.MIN_VALUE) {
                throw new IllegalStateException("More than one target not allowed");
            }
            resetIndexes();
            result.previous = this;
            return result;
        }

        private void resetIndexes() {
            visitWithParents(otb -> {
                otb.index = Integer.MIN_VALUE;
            });
        }

        OneTokenBuilder first() {
            if (previous != null) {
                return previous.first();
            }
            return this;
        }

        int index = Integer.MIN_VALUE;

        int index() {
            if (target) {
                return 0;
            }
            if (index != Integer.MIN_VALUE) {
                return index;
            }
            int dist = distanceToTarget();
            if (dist != Integer.MIN_VALUE) {
                OneTokenBuilder otb = this;
                while (otb != null) {
                    otb.index = dist--;
                    otb = otb.previous;
                }

            } else {
                noTargetIndex(-1);
            }
            return index;
        }

        private int noTargetIndex(int val) {
            index = val;
            if (previous != null) {
                previous.noTargetIndex(val - 1);
            }
            return index;
        }

        int size() {
            OneTokenBuilder otb = this;
            int result = 0;
            while (otb != null) {
                result++;
                otb = otb.previous;
            }
            return result;
        }

        int distanceToTarget() {
            int result = Integer.MIN_VALUE;
            if (target) {
                return 0;
            }
            if (previous != null) {
                OneTokenBuilder b = previous;
                int temp = 0;
                while (b != null) {
                    temp++;
                    if (b.target) {
                        break;
                    }
                    b = b.previous;
                }
                if (temp > 0) {
                    return -temp;
                }
            }
            return result;
        }

        List<OneTokenBuilder> list() {
            LinkedList<OneTokenBuilder> ll = new LinkedList<>();
            visitWithParents(ll::addFirst);
            return ll;
        }

        void visitWithParents(Consumer<OneTokenBuilder> c) {
            c.accept(this);
            if (previous != null) {
                previous.visitWithParents(c);
            }
        }
    }

    public TokenPatternBuilder addBefores(int[] tokenIds, boolean[] optional, boolean[] deleteIfPresent) {
        assert tokenIds.length == optional.length;
        assert tokenIds.length == deleteIfPresent.length;
        for (int i = 0; i < tokenIds.length; i++) {
            int ix = -(tokenIds.length - i);
            TokenPosition tp = find(ix);
            TokenInfo ti = new TokenInfo(tokenIds[i], optional[i], deleteIfPresent[i]);
            tp.add(ti);
        }
        return this;
    }

    private TokenPosition find(int pos) {
        for (TokenPosition p : l) {
            if (p.index == pos) {
                return p;
            }
        }
        TokenPosition nue = new TokenPosition(pos);
        l.add(nue);
        return nue;
    }

    static class TokenPosition implements Comparable<TokenPosition> {

        private final int index;

        private final Set<TokenInfo> tokensAtPosition = new HashSet<>();

        public TokenPosition(int index) {
            this.index = index;
        }

        public TokenPosition(int index, TokenInfo tok) {
            this.index = index;
            tokensAtPosition.add(tok);
        }

        void add(TokenInfo info) {
            for (TokenInfo t : tokensAtPosition) {
                if (t.tokenType == info.tokenType && t.optional != info.optional) {
                    throw new IllegalArgumentException(t
                            + " cannot be both optional and non-optional at "
                            + "position " + index);
                }
            }
        }

        @Override
        public int compareTo(TokenPosition o) {
            int a = index;
            int b = o.index;
            return a > b ? 1 : a == b ? 0 : -1;
        }
    }

    static class TokenInfo {

        private final int tokenType;
        private final boolean optional;
        private final boolean deleteIfPresent;

        public TokenInfo(int tokenType, boolean optional, boolean deleteIfPresent) {
            this.tokenType = tokenType;
            this.optional = optional;
            this.deleteIfPresent = deleteIfPresent;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(10);
            if (deleteIfPresent) {
                sb.append('<');
            } else {
                sb.append('(');
            }
            sb.append(tokenType);
            if (optional) {
                sb.append('?');
            }
            if (deleteIfPresent) {
                sb.append('>');
            } else {
                sb.append(')');
            }
            return sb.toString();
        }

        public String toString(Vocabulary vocabulary) {
            StringBuilder sb = new StringBuilder(10);
            if (deleteIfPresent) {
                sb.append('<');
            } else {
                sb.append('(');
            }
            sb.append(vocabulary.getSymbolicName(tokenType));
            if (optional) {
                sb.append('?');
            }
            if (deleteIfPresent) {
                sb.append('>');
            } else {
                sb.append(')');
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 79 * hash + this.tokenType;
            hash = 79 * hash + (this.optional ? 1 : 0);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TokenInfo other = (TokenInfo) obj;
            if (this.tokenType != other.tokenType) {
                return false;
            }
            return this.optional == other.optional;
        }

    }
}
