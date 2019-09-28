package org.nemesis.antlrformatting.api;

import com.mastfrog.function.IntBiPredicate;
import com.mastfrog.util.collections.IntList;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.search.Bias;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import org.antlr.v4.runtime.Token;

/**
 * Performance - one of the most expensive operations we do is scanning forward
 * and backward for token counts which are not used on all tokens. So, maintain
 * a few lightweight int arrays with all offsets of interest, and use binary
 * search to find what we need.
 *
 * @author Tim Boudreau
 */
final class TokenCountCache {

    private final Map<IntPredicate, CacheEntry> countEntries = new IdentityHashMap<>(30);
    private IntList sharedWhitespaceList;

    void close() {
        countEntries.clear();
        sharedWhitespaceList = null;
    }

    private CacheEntry getOrBuildCacheEntry(EverythingTokenStream stream, IntPredicate targetType) {
        CacheEntry ce = countEntries.get(notNull("targetType", targetType));
        if (ce == null) {
            ce = new CacheEntry(targetType);
            countEntries.put(targetType, ce);
            for (int i = 0; i < stream.size(); i++) {
                Token t = stream.get(i);
                if (targetType.test(t.getType())) {
                    ce.matchSeen(t);
                }
            }
        }
        return ce;
    }

    private IntList getOrBuildWhitespaceList(EverythingTokenStream stream, IntPredicate whitespace) {
        if (sharedWhitespaceList == null) {
            sharedWhitespaceList = IntList.create((stream.size() / 2) - 1);
            for (int i = 0; i < stream.size() - 1; i++) {
                if (whitespace.test(stream.get(i).getType())) {
                    sharedWhitespaceList.add(i);
                }
            }
        }
        return sharedWhitespaceList;
    }

    public int tokenCountToPreceding(EverythingTokenStream stream, IntPredicate whitespace, boolean ignoreWhitespace, IntPredicate targetType) {
        CacheEntry ce = getOrBuildCacheEntry(stream, targetType);
        IntList ws = ignoreWhitespace ? getOrBuildWhitespaceList(stream, whitespace) : null;
        return ce.tokenCountToPrev(stream.get(stream.cursor), ignoreWhitespace, ws);
    }

    public int tokenCountToNext(EverythingTokenStream stream, IntPredicate whitespace, boolean ignoreWhitespace, IntPredicate targetType) {
        CacheEntry ce = getOrBuildCacheEntry(stream, targetType);
        IntList ws = ignoreWhitespace ? getOrBuildWhitespaceList(stream, whitespace) : null;
        return ce.tokenCountToNext(stream.get(stream.cursor), ignoreWhitespace, ws);
    }

    public int countForwardOccurrencesUntilNext(EverythingTokenStream stream, IntPredicate toCount, IntPredicate stopType) {
        CacheEntry counts = getOrBuildCacheEntry(stream, toCount);
        if (counts.matchedTokens.isEmpty()) {
            return 0;
        }
        CacheEntry stops = getOrBuildCacheEntry(stream, stopType);
//        System.out.println("COUNT FWD " + toCount + " until " + stopType + " for " + stream.get(stream.cursor)
//                + " counts " + counts
//                + " stops " + stops
//        );
//        int startPoint = counts.next(stream.cursor);
        int startPoint = stream.cursor;
        if (startPoint == -1) {
//            System.out.println(" bail a for " + stream.cursor + " with " + counts);
            return 0;
        } else if (startPoint == stream.cursor) {
//            System.out.println("  inc start");
            startPoint++;
        }
        int stopPoint = stops.next(startPoint);
        if (startPoint > stopPoint) {
//            System.out.println("  bail b with " + startPoint + " > " + stopPoint + " at " + stream.cursor + " with " + stops
//                    + " and " + counts);
//            return 0;
            if (stopPoint == -1) {
                stopPoint = stream.size();
            } else {
                return 0;
            }
        }
        if (stopPoint == -1) {
//            System.out.println("no stop point, use stream remainder");
            stopPoint = stream.size();
        }
//        System.out.println("START " + startPoint + " stop " + stopPoint
//                + " values ");
        return Math.max(0, counts.itemCountBetween(startPoint, stopPoint, Bias.FORWARD));
    }

    public int countBackwardOccurrencesUntilPrevious(EverythingTokenStream stream, IntPredicate toCount, IntPredicate stopType) {
        CacheEntry counts = getOrBuildCacheEntry(stream, toCount);
        if (counts.matchedTokens.isEmpty()) {
            return 0;
        }
        CacheEntry stops = getOrBuildCacheEntry(stream, stopType);
        int stopPoint = stream.cursor - 1;

        int startPoint = stops.prev(stopPoint);
//        System.out.println("SEARCH " + startPoint + " to " + stopPoint + " with " + toCount + " items " + counts + " stops " + stops);
        if (startPoint < 0) {
//            System.out.println("  Start is < 0 - no stops");
            int res = counts.matchedTokens.nearestIndexToPresumingSorted(stream.cursor - 1, Bias.BACKWARD);
//            System.out.println("nearest index backward in counts is " + res);
            // XXX should this be exclusive - check matchedTokens.get(res) and if == stream.cursor, subtract one?
            return res + 1;

//            startPoint = 0;
        }
        if (startPoint > stopPoint) {
//            System.out.println("  start > stop " + startPoint + " > " + stopPoint + " - no go");
            return 0;
        }
//        System.out.println("START " + startPoint + " stop " + stopPoint
//                + " startValue " + stops.get(stopPoint) + " stopValue " + counts.get(stopPoint));
//        System.out.println("  COUNTS: " + counts);
//        System.out.println("  STOPS: " + stops);
        int result = counts.itemCountBetween(startPoint, stopPoint, Bias.BACKWARD);
//        System.out.println("  RESULT " + result);
        return Math.max(0, result);
    }

    enum SingleRangeTests implements IntBiPredicate {
        MIN_EXCLUSIVE,
        MIN_INCLUSIVE,
        MAX_EXCLUSIVE,
        MAX_INCLUSIVE;

        @Override
        public boolean test(int value, int limit) {
            switch (this) {
                case MIN_EXCLUSIVE:
                    return value > limit;
                case MIN_INCLUSIVE:
                    return value >= limit;
                case MAX_EXCLUSIVE:
                    return value < limit;
                case MAX_INCLUSIVE:
                    return value <= limit;
                default:
                    throw new AssertionError(this);
            }
        }

        boolean isExclusive() {
            return this == MIN_EXCLUSIVE || this == MAX_EXCLUSIVE;
        }

        int adjustment(int value, int limit) {
            if (isExclusive() && !test(value, limit)) {
                switch (this) {
                    case MAX_EXCLUSIVE:
                        return -1;
                    case MIN_EXCLUSIVE:
                        return 1;
                }
            }
            return 0;
        }
    }

    static int countValuesBetween(int minValue, boolean minInclusive, int maxValue, boolean maxInclusive, IntList list) {
        // Okay, we want to know the count of list elements where the value is > / >= minValue,
        // and the value is < / <= maxValue.
        // We are not interested in what those values are, just the count of them.
        if (list.isEmpty()) {
            // If the list is empty, or the range is zero length, we're done
            return 0;
        }
        if (minValue == maxValue) {
            if (minInclusive || maxInclusive) {
                return list.indexOfPresumingSorted(maxValue) >= 0 ? 1 : 0;
            } else {
                return 0;
            }
        }
        SingleRangeTests minTest = minInclusive ? SingleRangeTests.MIN_INCLUSIVE : SingleRangeTests.MIN_EXCLUSIVE;
        SingleRangeTests maxTest = maxInclusive ? SingleRangeTests.MAX_INCLUSIVE : SingleRangeTests.MAX_EXCLUSIVE;
//        System.out.println("RANGE BETWEEN " + minValue + " " + minTest + " and " + maxValue + " " + maxTest + " in [" + list + "]");
        if (list.size() == 1) {
            int single = list.first();

            boolean minOk = minTest.test(single, minValue);
            boolean maxOk = maxTest.test(single, maxValue);
//            System.out.println("Single element list of " + single
//                    + " minOK? " + minOk + " maxOK " + maxOk + " " + minTest + " " + maxTest
//                    + " will return " + (minOk || maxOk));
            return minOk ? maxOk ? 1 : 0 : 0;

        }
        // Looking forward, find the first value greater or equal to than minValue
        int least = list.nearestIndexToPresumingSorted(minValue, Bias.FORWARD);
        // Looking backward, find the first value less than or equal to maxValue
        int most = list.nearestIndexToPresumingSorted(maxValue, Bias.BACKWARD);

        // If nothing was found, we're done
        if (least == -1 && most == -1) {
            return 0;
        }

        // Boundary condition:  We found the last element only.
        if (least == list.size() - 1) {
            // Test just that, against both conditions.
            // Either condition being exclusive excludes it from
            // being counted (or should it OR them?)
            int single = list.last();
            boolean minOk = minTest.test(single, minValue);
            boolean maxOk = maxTest.test(single, maxValue);
//            System.out.println("  least is last: " + single
//                    + " minOK? " + minOk + " maxOK " + maxOk + " " + minTest + " " + maxTest
//                    + " will return " + (minOk || maxOk));
            return minOk ? maxOk ? 1 : 0 : 0;
//            return minTest.test(single, minValue)
//                    && maxTest.test(single, maxValue) ? 1 : 0;
        } else if (most == 0) {
            //Boundary condition:  We found the first element only.
            int single = list.first();
//            System.out.println("  most is first - " + single);
            // Test just that, against both conditions.
            // Either condition being exclusive excludes it from
            // being counted (or should it OR them?)
            boolean minOk = minTest.test(single, minValue);
            boolean maxOk = maxTest.test(single, maxValue);
//            System.out.println("  most is first: " + single
//                    + " minOK? " + minOk + " maxOK " + maxOk + " " + minTest + " " + maxTest
//                    + " will return " + (maxOk && minOk));
            return maxOk ? minOk ? 1 : 0 : 0;
//            return minTest.test(single, minValue)
//                    && maxTest.test(single, maxValue) ? 1 : 0;
        }

//        System.out.println("rangeBetweenValues " + minValue + ":" + minTest + " " + maxValue + ":" + maxTest
//                + " starting from indices " + least + ":" + most);
//
        // Boundary condition:  There are no elements > maxValue
        if (most < 0) {
            // If there are no elements at all < maxValue,
            // then there also cannot be any elements > minValue,
            // since they would also be < maxValue
//            System.out.println("  most < 0, ret 0");
            return 0;
        }
        // Boundary condition: Also no elements
        if (least < 0) {
//            System.out.println("  least < 0, ret 0");
            return 0;
        }
        // Now adjust what we're looking at to compensate for
        // inclusive / exclusive
        int leastVal = list.getAsInt(least);
//        System.out.println("   leastVal " + leastVal);
        int leastAdjust = minTest.adjustment(leastVal, minValue);
        if (leastAdjust != 0) {
            if (least + leastAdjust < list.size() && least + leastAdjust >= 0) {
                least += leastAdjust;
                leastVal = list.getAsInt(least);
//                System.out.println("  adjust least by " + leastAdjust + " to " + leastVal);
//            } else {
//                System.out.println("    can't adjust " + least + " by " + leastAdjust + " at end of list");
            }
        }
        int mostVal = list.getAsInt(most);
//        System.out.println("   mostVal " + mostVal);
        int mostAdjust = maxTest.adjustment(mostVal, maxValue);
        if (mostAdjust != 0) {
            if (most + mostAdjust < list.size() && most + mostAdjust >= 0) {
                most += mostAdjust;
                mostVal = list.getAsInt(most);
//                System.out.println("    adjust most by " + mostAdjust + " to " + mostVal);
//            } else {
//                System.out.println("    can't adjust " + most + " by " + mostAdjust + " at end of list");
            }
        }
        // Boundary condition: Both tests are exclusive, and
        // both matched the same element, so moving backward
        // got us an inverted range.  That means we didn't match
        // anything.
        if (least > most) {
//            System.out.println("  backwards range, return 0");
            return 0;
        }
        // If least and most both matched the same element, then
        // we did match exactly one element
        if (leastVal != -1 && mostVal != -1 && leastVal == mostVal) {
            return 1;
        }

        int result = (most - least) + 1;
//        System.out.println("   result " + result);
        return result;
    }

    private static class CacheEntry {

        private final IntList matchedTokens = IntList.create(30);
        private final String stringValue;

        CacheEntry(IntPredicate p) {
            stringValue = p.toString();
        }

        void matchSeen(Token tok) {
            matchedTokens.add(tok.getTokenIndex());
        }

        @Override
        public String toString() {
            return "matched=" + matchedTokens + " for " + stringValue;
        }

        int get(int index) {
            if (index < 0) {
                return -1;
            }
            return matchedTokens.get(index);
        }

        int itemCountBetween(int start, int end, Bias bias) {
            assert end >= start : start + " > " + end;
            switch (bias) {
                case FORWARD:
                    return TokenCountCache.countValuesBetween(start, true, end, true, matchedTokens);
                case BACKWARD:
                    return TokenCountCache.countValuesBetween(start, true, end, true, matchedTokens);
                default:
                    throw new AssertionError(bias);
            }
        }

        int xitemCountBetween(int start, int end, Bias bias) {
            if (start == end) {
                return 0;
            }
            assert end > start : start + " > " + end;
//            System.out.println("   item count between " + start + " and "
//                    + end + " start " + start + " end " + end + " on " + this + " " + bias);
            if (bias == Bias.BACKWARD) {
                end--;
            }
            int aResult = matchedTokens.nearestIndexToPresumingSorted(start, bias);
            if (aResult == start && bias == Bias.FORWARD) {
//                System.out.println("increase aResult");
                aResult++;
            }
            int bResult = matchedTokens.nearestIndexToPresumingSorted(end, bias.inverse());
            if (bias == Bias.FORWARD) {
//                System.out.println("increase bResult to " + (bResult + 1) + " end is " + end);
                bResult++;
            }
//            System.out.println("NEAREST INDEX TO " + end + " with " + bias.inverse() + " is " + bResult + " in " + this);
//            System.out.println("    aResult " + aResult + " " + bResult + " bResult");
            if (aResult != -1 && bResult != -1 && aResult == bResult) {
//                System.out.println("     results match, one item");
                return 1;
            }
            if ((aResult == -1 && bResult != -1) || (aResult != -1 && bResult == -1) || (aResult != -1 && bResult != -1 && aResult == bResult)) {
//                System.out.println("   one item");
                return 1;
            } else if (aResult == -1 && bResult == -1) {
                return 0;
            }
//            System.out.println(" DIST " + (bResult - aResult) + " " + bias + " between " + bResult + " - " + aResult);
            int result = bResult - aResult;
            switch (bias) {
                case BACKWARD:
                    if (bResult == end) {
//                        System.out.println("adjust bResult downward");
                        result--;
                    }
                case FORWARD:
                    if (aResult == start) {
//                        System.out.println("ADJUST DOWNWARD");
                        result--;
                    }
            }
            return result;
        }

        int next(int ix) {
            int result = matchedTokens.nearestIndexToPresumingSorted(ix, Bias.FORWARD);
//            System.out.println("NEXT from " + ix + " is " + result + " in " + matchedTokens + " with " + Bias.FORWARD);
//            System.out.println("  res " + result + " in " + matchedTokens);
//            if (result >= 0 && matchedTokens.getAsInt(result) == ix) {
//                System.out.println("  try " + ix + 1);
//                return next(ix + 1);
//            }
            return result == -1 ? -1 : matchedTokens.getAsInt(result);
        }

        int prev(int ix) {
            if (ix <= 0) {
                return -1;
            }
            int result = matchedTokens.nearestIndexToPresumingSorted(ix, Bias.BACKWARD);
//            System.out.println("  PREV " + ix + " gets " + result
//                    + " is " + (result < 0 ? "none " : matchedTokens.getAsInt(result)));

            if (result >= 0 && matchedTokens.getAsInt(result) == ix) {
//                System.out.println("  exact match, move to previous");
                return next(result - 1);
            }
            return result == -1 ? -1 : matchedTokens.getAsInt(result);
        }

        int tokenCountToNext(Token t, boolean ignoreWhitespace, IntList whitespace) {
            int ix = t.getTokenIndex();
            if (t.getType() == -1) {
                return -1;
            }
            int lastKnownValue = matchedTokens.last();
            if (ix > lastKnownValue) {
                return -1;
            } else if (ix == lastKnownValue) {
                return -1;
            }
            int matchIndex = matchedTokens.nearestIndexToPresumingSorted(ix, Bias.FORWARD);
            if (matchIndex != -1 && matchedTokens.getAsInt(matchIndex) == ix) {
                // if we hit the target exactly, we are looking for the preceding
                // one
                matchIndex++;
                if (matchIndex == matchedTokens.size()) {
                    return -1;
                }
            } else if (matchIndex == -1) {
                return -1;
            }
            int target = matchedTokens.getAsInt(matchIndex);
            int toSubtract = 0;
            if (ignoreWhitespace) {
                int first = whitespace.nearestIndexToPresumingSorted(ix, Bias.FORWARD);
                int last = whitespace.nearestIndexToPresumingSorted(target, Bias.BACKWARD);
                if ((first == -1 && last != -1) || (first != -1 && last == -1) || (first != -1 && last != -1 && first == last)) {
                    toSubtract = 1;
                } else {
                    toSubtract = (last - first) + 1;
                }
                target -= toSubtract;
            }
            int distance = target - (ix + 1);
            return distance;
        }

        int tokenCountToPrev(Token t, boolean ignoreWhitespace, IntList whitespace) {
            int ix = t.getTokenIndex();
            if (ix <= 0) {
                return -1;
            }
            int matchIndex = matchedTokens.nearestIndexToPresumingSorted(ix, Bias.BACKWARD);
            if (matchIndex != -1 && matchedTokens.getAsInt(matchIndex) == ix) {
                // if we hit the target exactly, we are looking for the preceding
                // one
                if (matchIndex > 0) {
                    matchIndex--;
                } else {
                    return -1;
                }
            }
            if (matchIndex == -1) {
                return -1;
            }
            int target = matchedTokens.getAsInt(matchIndex);
            int toSubtract = 0;
            if (ignoreWhitespace) {
                int first = whitespace.nearestIndexToPresumingSorted(target, Bias.FORWARD);
                int last = whitespace.nearestIndexToPresumingSorted(ix, Bias.BACKWARD);
                if ((first == -1 && last != -1) || (first != -1 && last == -1) || (first != -1 && last != -1 && first == last)) {
                    toSubtract = 1;
                } else {
                    toSubtract = (last - first) + 1;
                }
                ix -= toSubtract;
            }
            int distance = (ix - 1) - target;
            return distance;
        }
    }
}
