/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.data;

import com.mastfrog.range.IntRange;
import com.mastfrog.range.Range;
import com.mastfrog.range.RangeRelation;
import com.mastfrog.util.collections.IntSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.nemesis.data.SemanticRegions.SemanticRegionsBuilder;

/**
 *
 * @author Tim Boudreau
 */
public class SemanticRegionsBugsTest {

    SemanticRegions<AlternativeKey> searchBug;

    @Test
    public void test() {
        // Did not find alts {1, 2} in 'expression'  within rule bounds 4052:9608
        assertAllFound(4052, 9608, "expression", 1, 2);

        // Did not find alts {1, 2} in 'typeExpression'  within rule bounds 20976:21069
        assertAllFound(20976, 21069, "typeExpression", 1, 2);

        // Did not find alts {1, 2} in 'statements'  within rule bounds 2870:2911
//        assertAllFound(2870, 2911, "statements", 1, 2);
        // Did not find alts {2, 3} in 'traitImplItem'  within rule bounds 17360:17574
        assertAllFound(17360, 17574, "traitImplItem", 2, 3);

        // Did not find alts {1, 2} in 'pathInExpression'  within rule bounds 9710:9795
        assertAllFound(9710, 9795, "pathInExpression", 1, 2);

        // Did not find alts {1, 2} in 'matchArmContent'  within rule bounds 15246:15674
        assertAllFound(15246, 15674, "matchArmContent", 1, 2);

        // Did not find alts {1, 2} in 'pattern'  within rule bounds 11953:12596
        assertAllFound(11953, 12596, "pattern", 1, 2);

        // Did not find alts {1, 2} in 'usePath'  within rule bounds 18975:19064
        assertAllFound(18975, 19064, "usePath", 1, 2);
    }

    private void assertAllFound(int start, int end, String name,
            int... alternativeIndices) {
        assert end > start : "bogus range " + start + ":" + end;
        IntSet ks = IntSet.of(alternativeIndices);
        Set<AlternativeKey> tested = new TreeSet<>();
        List<? extends SemanticRegion<AlternativeKey>> found = search(name,
                start, end, tested, alternativeIndices);

        Set<AlternativeKey> foundKeys = new HashSet<>();
        found.forEach(reg -> foundKeys.add(reg.key()));
        if (found.size() != alternativeIndices.length) {
            List<? extends SemanticRegion<AlternativeKey>> shouldHaveBeen
                    = searchAll(name, alternativeIndices);
            if (shouldHaveBeen.size() != ks.size()) {
                IntSet present = IntSet.create(alternativeIndices.length);
                IntRange<? extends IntRange<?>> r = Range.of(start, end);

                Map<SemanticRegion<AlternativeKey>, RangeRelation> presentButNotInRequestedBounds
                        = new HashMap<>();

                Set<SemanticRegion<AlternativeKey>> inRangeButNotSeen
                        = new HashSet<>();

                shouldHaveBeen.forEach(item -> {
                    present.add(item.key().alternativeIndex());
                    ks.remove(item.key().alternativeIndex());
                    if (!foundKeys.contains(item.key())) {
                        RangeRelation rel = r.relationTo(item);
                        switch (rel) {
                            case AFTER:
                            case BEFORE:
                            case STRADDLES_START:
                            case STRADDLES_END:
                                presentButNotInRequestedBounds.put(item, rel);
                                break;
                            default:
                                inRangeButNotSeen.add(item);
                                break;
                        }
                    }
                });
                assertEquals(found, shouldHaveBeen);
                IntSet ks2 = IntSet.of(alternativeIndices);
                fail("Test bug: Some items not present in source data.  Missing"
                        + " for '" + name + "' " + ks + ". Present: \n  " + present
                        + " present items: \nShould have gotten " + shouldHaveBeen
                        + "\nBut got instead " + found
                        + ".\n\nSearched \n  " + tested + "\n\nPresent but out of range: \n  "
                        + presentButNotInRequestedBounds + "\n\nIn Range but not included:\n  "
                        + inRangeButNotSeen + "\nshouldHaveBeen size " + shouldHaveBeen.size()
                        + "\nkeys size " + ks2.size() + " " + " requesting " + IntSet.of(alternativeIndices));
            }

            IntSet present = IntSet.create(alternativeIndices.length);
            found.forEach(item -> {
                present.add(item.key().alternativeIndex());
                ks.remove(item.key().alternativeIndex());
            });
            fail("\nTEST BUG: some items not present at all.  No items for " + ks
                    + ". Present: " + present + " present items: \n" + found
                    + "\n\nShould have found:\n" + shouldHaveBeen + ".\nSearched: "
                    + tested + "\n");
        } else {
            IntSet present = IntSet.create(alternativeIndices.length);
            found.forEach(item -> {
                present.add(item.key().alternativeIndex());
                ks.remove(item.key().alternativeIndex());
            });
            // xxx the bitsets currently wrong in the input data.
            List<? extends SemanticRegion<AlternativeKey>> shouldHaveBeen
                    = searchAll(name, alternativeIndices);
            assertEquals(found, shouldHaveBeen);
            assertEquals("Got a different set of alternatives than requested. Should have gotten:\n" + shouldHaveBeen
                    + "\n But got " + found + "\nSearched: " + tested,
                    IntSet.of(alternativeIndices), present);
        }
    }

    private List<? extends SemanticRegion<AlternativeKey>> search(String name,
            int start, int end, Set<AlternativeKey> tested,
            int... alternativeIndices) {
        IntSet ks = IntSet.of(alternativeIndices);
        return searchBug.collectBetween(start, end, (AlternativeKey key) -> {
            tested.add(key);
            return ks.contains(key.alternativeIndex()) && key.rule().equals(name);
        });
    }

    private List<? extends SemanticRegion<AlternativeKey>> searchAll(String name,
            int... alternativeIndices) {
        IntSet ks = IntSet.of(alternativeIndices);
        return searchBug.collect((AlternativeKey key) -> {
            return ks.contains(key.alternativeIndex) && name.equals(key.rule());
        });
    }

    @Before
    public void setup() {
        // Interesting - if we make this all one chained call,
        // JDK 14's javac throws a StackOverflowError.
        SemanticRegionsBuilder<AlternativeKey> bldr = SemanticRegions.builder(AlternativeKey.class, 645);
        bldr
                .add(new AlternativeKey("compilation_unit", 1, "1"), 172, 181)
                .add(new AlternativeKey("item", 1, "1"), 195, 209)
                .add(new AlternativeKey("item", 2, "2"), 216, 230)
                .add(new AlternativeKey("item", 3, "3"), 237, 244)
                .add(new AlternativeKey("item", 4, "4"), 251, 260)
                .add(new AlternativeKey("visItem", 1, "1"), 277, 555)
                .add(new AlternativeKey("visItem", 2, "2"), 299, 305)
                .add(new AlternativeKey("visItem", 3, "3"), 316, 327)
                .add(new AlternativeKey("visItem", 4, "4"), 338, 352)
                .add(new AlternativeKey("visItem", 5, "5"), 363, 371)
                .add(new AlternativeKey("visItem", 6, "6"), 382, 391)
                .add(new AlternativeKey("visItem", 7, "7"), 402, 408)
                .add(new AlternativeKey("visItem", 8, "8"), 419, 430)
                .add(new AlternativeKey("visItem", 9, "9"), 441, 446)
                .add(new AlternativeKey("visItem", 10, "10"), 457, 469)
                .add(new AlternativeKey("visItem", 11, "11"), 480, 490)
                .add(new AlternativeKey("visItem", 12, "12"), 501, 506)
                .add(new AlternativeKey("visItem", 13, "13"), 517, 531)
                .add(new AlternativeKey("visItem", 14, "14"), 542, 553)
                .add(new AlternativeKey("visibility", 1, "1"), 575, 605)
                .add(new AlternativeKey("visibility", 2, "2"), 612, 646)
                .add(new AlternativeKey("visibility", 3, "3"), 653, 683)
                .add(new AlternativeKey("visibility", 4, "4"), 690, 728)
                .add(new AlternativeKey("visibility", 5, "5"), 735, 738)
                .add(new AlternativeKey("visibility", 6, "6"), 745, 750)
                .add(new AlternativeKey("macroInvocation", 1, "1"), 775, 815)
                .add(new AlternativeKey("function", 1, "1"), 833, 960)
                .add(new AlternativeKey("identifier", 1, "1"), 976, 986)
                .add(new AlternativeKey("module", 1, "1"), 1002, 1029)
                .add(new AlternativeKey("module", 2, "2"), 1036, 1096)
                .add(new AlternativeKey("typeAlias", 1, "1"), 1115, 1175)
                .add(new AlternativeKey("struct", 1, "1"), 1191, 1203)
                .add(new AlternativeKey("struct", 2, "2"), 1210, 1221)
                .add(new AlternativeKey("structStruct", 1, "1"), 1243, 1349)
                .add(new AlternativeKey("structStruct", 2, "2"), 1294, 1327)
                .add(new AlternativeKey("structStruct", 3, "3"), 1338, 1347)
                .add(new AlternativeKey("structFields", 1, "1"), 1371, 1412)
                .add(new AlternativeKey("structFields", 2, "2"), 1385, 1402)
                .add(new AlternativeKey("structField", 1, "1"), 1433, 1482)
                .add(new AlternativeKey("tupleStruct", 1, "1"), 1503, 1591)
                .add(new AlternativeKey("tupleFields", 1, "1"), 1612, 1651)
                .add(new AlternativeKey("tupleFields", 2, "2"), 1625, 1641)
                .add(new AlternativeKey("tupleField", 1, "1"), 1671, 1703)
                .add(new AlternativeKey("enumeration", 1, "1"), 1724, 1805)
                .add(new AlternativeKey("enumItems", 1, "1"), 1824, 1859)
                .add(new AlternativeKey("enumItems", 2, "2"), 1835, 1849)
                .add(new AlternativeKey("enumItem", 1, "1"), 1877, 2006)
                .add(new AlternativeKey("enumItem", 2, "2"), 1926, 1939)
                .add(new AlternativeKey("enumItem", 3, "3"), 1950, 1964);

        // We do not generate a single chain of calls because
        // with several hundred, javac will throw a StackOverflowException
        bldr
                .add(new AlternativeKey("enumItem", 4, "4"), 1975, 2003)
                .add(new AlternativeKey("enumItemTuple", 1, "1"), 2029, 2062)
                .add(new AlternativeKey("enumItemStruct", 1, "1"), 2086, 2120)
                .add(new AlternativeKey("enumItemDiscriminant", 1, "1"), 2150, 2174)
                .add(new AlternativeKey("union", 1, "1"), 2189, 2262)
                .add(new AlternativeKey("constantItem", 1, "1"), 2284, 2400)
                .add(new AlternativeKey("constantItem", 2, "2"), 2300, 2310)
                .add(new AlternativeKey("constantItem", 3, "3"), 2321, 2331)
                .add(new AlternativeKey("constantItem", 4, "4"), 2378, 2388)
                .add(new AlternativeKey("staticItem", 1, "1"), 2421, 2496)
                .add(new AlternativeKey("trait", 1, "1"), 2511, 2622)
                .add(new AlternativeKey("trait", 2, "2"), 2548, 2570)
                .add(new AlternativeKey("traitItem", 1, "1"), 2641, 2783)
                .add(new AlternativeKey("traitItem", 2, "2"), 2679, 2688)
                .add(new AlternativeKey("traitItem", 3, "3"), 2699, 2710)
                .add(new AlternativeKey("traitItem", 4, "4"), 2721, 2731)
                .add(new AlternativeKey("traitItem", 5, "5"), 2742, 2751)
                .add(new AlternativeKey("traitItem", 6, "6"), 2762, 2781)
                .add(new AlternativeKey("traitFunc", 1, "1"), 2802, 2867)
                .add(new AlternativeKey("traitFunc", 2, "2"), 2830, 2839)
                .add(new AlternativeKey("traitFunc", 3, "3"), 2850, 2865)
                .add(new AlternativeKey("statements", 1, "1"), 2887, 2910)
                .add(new AlternativeKey("statement", 1, "1"), 2930, 2939)
                .add(new AlternativeKey("statement", 2, "2"), 2946, 2950)
                .add(new AlternativeKey("statement", 3, "3"), 2957, 2969)
                .add(new AlternativeKey("statement", 4, "4"), 2976, 2991)
                .add(new AlternativeKey("statement", 5, "5"), 2998, 3018)
                .add(new AlternativeKey("statement", 6, "6"), 3025, 3060)
                .add(new AlternativeKey("statement", 7, "7"), 3067, 3086)
                .add(new AlternativeKey("letStatement", 1, "1"), 3108, 3202)
                .add(new AlternativeKey("letStatement", 2, "2"), 3138, 3148)
                .add(new AlternativeKey("letStatement", 3, "3"), 3154, 3189)
                .add(new AlternativeKey("loopControlStatement", 1, "1"), 3232, 3271)
                .add(new AlternativeKey("loopControlStatement", 2, "2"), 3233, 3241)
                .add(new AlternativeKey("loopControlStatement", 3, "3"), 3244, 3249)
                .add(new AlternativeKey("blockExpression", 1, "1"), 3296, 3344)
                .add(new AlternativeKey("unsafeExpression", 1, "1"), 3370, 3392)
                .add(new AlternativeKey("loopExpression", 1, "1"), 3416, 3569)
                .add(new AlternativeKey("loopExpression", 2, "2"), 3437, 3459)
                .add(new AlternativeKey("loopExpression", 3, "3"), 3470, 3493)
                .add(new AlternativeKey("loopExpression", 4, "4"), 3504, 3534)
                .add(new AlternativeKey("loopExpression", 5, "5"), 3545, 3567)
                .add(new AlternativeKey("ifExpression", 1, "1"), 3591, 3740)
                .add(new AlternativeKey("ifExpression", 2, "2"), 3645, 3738)
                .add(new AlternativeKey("ifExpression", 3, "3"), 3664, 3679)
                .add(new AlternativeKey("ifExpression", 4, "4"), 3694, 3706)
                .add(new AlternativeKey("ifExpression", 5, "5"), 3721, 3736)
                .add(new AlternativeKey("ifLetExpression", 1, "1"), 3765, 3942)
                .add(new AlternativeKey("ifLetExpression", 2, "2"), 3847, 3940)
                .add(new AlternativeKey("ifLetExpression", 3, "3"), 3866, 3881);
        bldr
                .add(new AlternativeKey("ifLetExpression", 4, "4"), 3896, 3908)
                .add(new AlternativeKey("ifLetExpression", 5, "5"), 3923, 3938)
                .add(new AlternativeKey("matchExpression", 1, "1"), 3967, 4049)
                .add(new AlternativeKey("expression", 1, "BlockBlockExpression"), 4069, 4084)
                .add(new AlternativeKey("expression", 2, "AsyncBlockExpression"), 4113, 4140)
                .add(new AlternativeKey("expression", 3, "UnsafeBlockExpression"), 4169, 4185)
                .add(new AlternativeKey("expression", 4, "LoopBlockExpressions"), 4215, 4229)
                .add(new AlternativeKey("expression", 5, "IfBlockExpression"), 4258, 4270)
                .add(new AlternativeKey("expression", 6, "IfLetBlockExpression"), 4296, 4311)
                .add(new AlternativeKey("expression", 7, "MatchBlockExpression"), 4340, 4360)
                .add(new AlternativeKey("expression", 8, "CharLiteralExpression"), 4389, 4405)
                .add(new AlternativeKey("expression", 9, "StringLiteralExpression"), 4435, 4453)
                .add(new AlternativeKey("expression", 10, "RawStringLiteralExpression"), 4485, 4506)
                .add(new AlternativeKey("expression", 11, "ByteLiteralExpression"), 4541, 4557)
                .add(new AlternativeKey("expression", 12, "ByteStringLiteralExpression"), 4587, 4609)
                .add(new AlternativeKey("expression", 13, "RawByteStringLiteralExpression"), 4645, 4670)
                .add(new AlternativeKey("expression", 14, "IntegerLiteralExpression"), 4709, 4728)
                .add(new AlternativeKey("expression", 15, "FloatLiteralExpression"), 4761, 4778)
                .add(new AlternativeKey("expression", 16, "BooleanLiteralExpression"), 4809, 4828)
                .add(new AlternativeKey("expression", 17, "PathExpression"), 4861, 4882)
                .add(new AlternativeKey("expression", 18, "StaticExpression"), 4905, 4911)
                .add(new AlternativeKey("expression", 19, "QualifiedPathExpression"), 5039, 5069)
                .add(new AlternativeKey("expression", 20, "OperatorBorrowExpression"), 5101, 5107)
                .add(new AlternativeKey("expression", 21, "OperatorDereferenceExpression"), 5140, 5161)
                .add(new AlternativeKey("expression", 22, "OperatorErrorPropagationExpression"), 5199, 5218)
                .add(new AlternativeKey("expression", 23, "OperatorNegationExpression"), 5261, 5279)
                .add(new AlternativeKey("expression", 24, "OperatorPlusExpression"), 5314, 5340)
                .add(new AlternativeKey("expression", 25, "OperatorMinusExpression"), 5371, 5398)
                .add(new AlternativeKey("expression", 26, "OperatorTimesExpression"), 5430, 5456)
                .add(new AlternativeKey("expression", 27, "OperatorDivisionExpression"), 5488, 5515)
                .add(new AlternativeKey("expression", 28, "OperatorModExpression"), 5550, 5579)
                .add(new AlternativeKey("expression", 29, "OrOperatorExpression"), 5609, 5633)
                .add(new AlternativeKey("expression", 30, "ShiftLeftOperatorExpression"), 5662, 5693)
                .add(new AlternativeKey("expression", 31, "ShiftRightOperatorExpression"), 5729, 5761)
                .add(new AlternativeKey("expression", 32, "EqualsOperatorExpression"), 5798, 5832)
                .add(new AlternativeKey("expression", 33, "NotEqualsOperatorExpression"), 5865, 5896)
                .add(new AlternativeKey("expression", 34, "GreaterThanOperatorExpression"), 5932, 5965)
                .add(new AlternativeKey("expression", 35, "LessThanOperatorExpression"), 6003, 6033)
                .add(new AlternativeKey("expression", 36, "GreaterThanOrEqualsOperatorExpression"), 6068, 6109)
                .add(new AlternativeKey("expression", 37, "LessThanOrEqualsOperatorExpression"), 6155, 6193)
                .add(new AlternativeKey("expression", 38, "BooleanOrOperatorExpression"), 6236, 6262)
                .add(new AlternativeKey("expression", 39, "BooleanAndOperatorExpression"), 6298, 6326)
                .add(new AlternativeKey("expression", 40, "ArithmeticOrOperatorExpression"), 6363, 6387)
                .add(new AlternativeKey("expression", 41, "ArithmeticAndOperatorExpression"), 6426, 6451)
                .add(new AlternativeKey("expression", 42, "TypeCastOperatorExpression"), 6491, 6517)
                .add(new AlternativeKey("expression", 43, "AssignmentOperatorExpression"), 6552, 6580)
                .add(new AlternativeKey("expression", 44, "AdditionCompoundAssignmentOperatorExpression"), 6617, 6649)
                .add(new AlternativeKey("expression", 45, "SubtractionCompoundAssignmentOperatorExpression"), 6702, 6735)
                .add(new AlternativeKey("expression", 46, "MultiplicationCompoundAssignmentOperatorExpression"), 6791, 6824)
                .add(new AlternativeKey("expression", 47, "DivisionCompoundAssignmentOperatorExpression"), 6883, 6914);
        bldr
                .add(new AlternativeKey("expression", 48, "ModCompoundAssignmentOperatorExpression"), 6967, 6998)
                .add(new AlternativeKey("expression", 49, "AndCompoundAssignmentOperatorExpression"), 7046, 7077)
                .add(new AlternativeKey("expression", 50, "OrCompoundAssignmentOperatorExpression"), 7125, 7155)
                .add(new AlternativeKey("expression", 51, "XorCompoundAssignmentOperatorExpression"), 7202, 7233)
                .add(new AlternativeKey("expression", 52, "ShiftLeftCompoundAssignmentOperatorExpression"), 7281, 7318)
                .add(new AlternativeKey("expression", 53, "ShiftRightCompoundAssignmentOperatorExpression"), 7372, 7410)
                .add(new AlternativeKey("expression", 54, "DoubleColonCompoundAssignmentOperatorExpression"), 7465, 7498)
                .add(new AlternativeKey("expression", 55, "UnderscoreCompoundAssignmentOperatorExpression"), 7554, 7586)
                .add(new AlternativeKey("expression", 56, "RawMutOrConstExpression"), 7641, 7672)
                .add(new AlternativeKey("expression", 57, "57"), 7649, 7654)
                .add(new AlternativeKey("expression", 58, "58"), 7657, 7660)
                .add(new AlternativeKey("expression", 59, "EmptyArrayPointer"), 7728, 7756)
                .add(new AlternativeKey("expression", 60, "GroupedExpression"), 7836, 7883)
                .add(new AlternativeKey("expression", 61, "ArrayExpression"), 7909, 7963)
                .add(new AlternativeKey("expression", 62, "AwaitExpression"), 7987, 8007)
                .add(new AlternativeKey("expression", 63, "IndexExpression"), 8031, 8077)
                .add(new AlternativeKey("expression", 64, "TupleExpression"), 8101, 8211)
                .add(new AlternativeKey("expression", 65, "65"), 8137, 8147)
                .add(new AlternativeKey("expression", 66, "66"), 8158, 8190)
                .add(new AlternativeKey("expression", 67, "67"), 8171, 8187)
                .add(new AlternativeKey("expression", 68, "EmptyTupleExpression"), 8235, 8245)
                .add(new AlternativeKey("expression", 69, "TupleIndexExpression"), 8274, 8299)
                .add(new AlternativeKey("expression", 70, "DotExpression"), 8328, 8407)
                .add(new AlternativeKey("expression", 71, "71"), 8355, 8369)
                .add(new AlternativeKey("expression", 72, "72"), 8373, 8405)
                .add(new AlternativeKey("expression", 73, "StructStructExpression"), 8430, 8446)
                .add(new AlternativeKey("expression", 74, "StructTupleExpression"), 8477, 8492)
                .add(new AlternativeKey("expression", 75, "StructUnitExpression"), 8522, 8536)
                .add(new AlternativeKey("expression", 76, "StructureEnumerationVariantExpression"), 8565, 8579)
                .add(new AlternativeKey("expression", 77, "TupleEnumerationVariantExpression"), 8625, 8638)
                .add(new AlternativeKey("expression", 78, "FieldlessEnumerationVariantExpression"), 8680, 8697)
                .add(new AlternativeKey("expression", 79, "CallExpression"), 8743, 8786)
                .add(new AlternativeKey("expression", 80, "MethodCallExpression"), 8809, 8872)
                .add(new AlternativeKey("expression", 81, "FieldExpression"), 8901, 8926)
                .add(new AlternativeKey("expression", 82, "ClosureExpression"), 8950, 9080)
                .add(new AlternativeKey("expression", 83, "83"), 8966, 8970)
                .add(new AlternativeKey("expression", 84, "84"), 8981, 9005)
                .add(new AlternativeKey("expression", 85, "85"), 9018, 9028)
                .add(new AlternativeKey("expression", 86, "86"), 9039, 9078)
                .add(new AlternativeKey("expression", 87, "ContinueExpression"), 9106, 9123)
                .add(new AlternativeKey("expression", 88, "BreakExpression"), 9150, 9175)
                .add(new AlternativeKey("expression", 89, "RangeExpression"), 9199, 9244)
                .add(new AlternativeKey("expression", 90, "90"), 9211, 9217)
                .add(new AlternativeKey("expression", 91, "91"), 9220, 9232)
                .add(new AlternativeKey("expression", 92, "RangeFromExpression"), 9268, 9285)
                .add(new AlternativeKey("expression", 93, "RangeToExpression"), 9313, 9330)
                .add(new AlternativeKey("expression", 94, "RangeFullExpression"), 9356, 9369)
                .add(new AlternativeKey("expression", 95, "RangeInclusiveExpression"), 9397, 9431)
                .add(new AlternativeKey("expression", 96, "RangeToInclusiveExpression"), 9464, 9487)
                .add(new AlternativeKey("expression", 97, "ReturnExpression"), 9522, 9540);
        bldr
                .add(new AlternativeKey("expression", 98, "MacroInvocationExpression"), 9565, 9580)
                .add(new AlternativeKey("emptyTuple", 1, "1"), 9627, 9647)
                .add(new AlternativeKey("expressionWithAttributes", 1, "1"), 9681, 9707)
                .add(new AlternativeKey("pathInExpression", 1, "1"), 9733, 9794)
                .add(new AlternativeKey("pathInExpression", 2, "2"), 9764, 9791)
                .add(new AlternativeKey("pathExprSegment", 1, "1"), 9819, 9864)
                .add(new AlternativeKey("pathExprSegment", 2, "2"), 9838, 9861)
                .add(new AlternativeKey("qualifiedPathInExpression", 1, "1"), 9899, 9949)
                .add(new AlternativeKey("qualifiedPathInExpression", 2, "2"), 9919, 9946)
                .add(new AlternativeKey("borrow", 1, "1"), 9965, 10022)
                .add(new AlternativeKey("borrow", 2, "2"), 9975, 9978)
                .add(new AlternativeKey("borrow", 3, "3"), 9989, 9995)
                .add(new AlternativeKey("borrow", 4, "4"), 10029, 10082)
                .add(new AlternativeKey("borrow", 5, "5"), 10031, 10034)
                .add(new AlternativeKey("borrow", 6, "6"), 10045, 10051)
                .add(new AlternativeKey("dereferenceExpression", 1, "1"), 10113, 10142)
                .add(new AlternativeKey("negationExpression", 1, "1"), 10170, 10200)
                .add(new AlternativeKey("negationExpression", 2, "2"), 10207, 10235)
                .add(new AlternativeKey("arrayElements", 1, "1"), 10258, 10325)
                .add(new AlternativeKey("arrayElements", 2, "2"), 10285, 10315)
                .add(new AlternativeKey("arrayElements", 3, "3"), 10414, 10473)
                .add(new AlternativeKey("tupleIndex", 1, "1"), 10581, 10617)
                .add(new AlternativeKey("tupleIndex", 2, "2"), 10597, 10615)
                .add(new AlternativeKey("structExprStruct", 1, "1"), 10643, 10747)
                .add(new AlternativeKey("structExprStruct", 2, "2"), 10696, 10712)
                .add(new AlternativeKey("structExprStruct", 3, "3"), 10723, 10733)
                .add(new AlternativeKey("structExprFields", 1, "1"), 10773, 10861)
                .add(new AlternativeKey("structExprFields", 2, "2"), 10791, 10812)
                .add(new AlternativeKey("structExprFields", 3, "3"), 10826, 10842)
                .add(new AlternativeKey("structExprFields", 4, "4"), 10853, 10859)
                .add(new AlternativeKey("structExprField", 1, "1"), 10886, 10896)
                .add(new AlternativeKey("structExprField", 2, "2"), 10903, 10969)
                .add(new AlternativeKey("structExprField", 3, "3"), 10905, 10915)
                .add(new AlternativeKey("structExprField", 4, "4"), 10926, 10936)
                .add(new AlternativeKey("structBase", 1, "1"), 10989, 11020)
                .add(new AlternativeKey("structExprTuple", 1, "1"), 11045, 11087)
                .add(new AlternativeKey("structExprTuple", 2, "2"), 11094, 11208)
                .add(new AlternativeKey("structExprTuple", 3, "3"), 11164, 11198)
                .add(new AlternativeKey("structExprUnit", 1, "1"), 11232, 11248)
                .add(new AlternativeKey("enumExprStruct", 1, "1"), 11272, 11325)
                .add(new AlternativeKey("enumExprFields", 1, "1"), 11349, 11387)
                .add(new AlternativeKey("enumExprFields", 2, "2"), 11365, 11384)
                .add(new AlternativeKey("enumExprField", 1, "1"), 11410, 11420)
                .add(new AlternativeKey("enumExprField", 2, "2"), 11427, 11493)
                .add(new AlternativeKey("enumExprField", 3, "3"), 11429, 11439)
                .add(new AlternativeKey("enumExprField", 4, "4"), 11450, 11460)
                .add(new AlternativeKey("enumExprTuple", 1, "1"), 11516, 11620)
                .add(new AlternativeKey("enumExprTuple", 2, "2"), 11627, 11689)
                .add(new AlternativeKey("enumExprFieldless", 1, "1"), 11716, 11732)
                .add(new AlternativeKey("callParams", 1, "1"), 11752, 11819);
        bldr
                .add(new AlternativeKey("callParams", 2, "2"), 11779, 11809)
                .add(new AlternativeKey("closureParameters", 1, "1"), 11846, 11889)
                .add(new AlternativeKey("closureParameters", 2, "2"), 11861, 11879)
                .add(new AlternativeKey("closureParam", 1, "1"), 11911, 11950)
                .add(new AlternativeKey("closureParam", 2, "2"), 11937, 11947)
                .add(new AlternativeKey("pattern", 1, "LitPattern"), 11967, 11981)
                .add(new AlternativeKey("pattern", 2, "IdPattern"), 12000, 12017)
                .add(new AlternativeKey("pattern", 3, "WildPattern"), 12035, 12050)
                .add(new AlternativeKey("pattern", 4, "RngPattern"), 12070, 12082)
                .add(new AlternativeKey("pattern", 5, "RefPattern"), 12101, 12117)
                .add(new AlternativeKey("pattern", 6, "StrucPattern"), 12136, 12149)
                .add(new AlternativeKey("pattern", 7, "TupleStPattern"), 12170, 12188)
                .add(new AlternativeKey("pattern", 8, "TupleTrailingCommaPattern"), 12211, 12224)
                .add(new AlternativeKey("pattern", 9, "TupleMultiPattern"), 12258, 12291)
                .add(new AlternativeKey("pattern", 10, "10"), 12268, 12281)
                .add(new AlternativeKey("pattern", 11, "TupleLeadingRangePattern"), 12317, 12349)
                .add(new AlternativeKey("pattern", 12, "12"), 12326, 12339)
                .add(new AlternativeKey("pattern", 13, "TupleRangePattern"), 12382, 12447)
                .add(new AlternativeKey("pattern", 14, "14"), 12398, 12411)
                .add(new AlternativeKey("pattern", 15, "15"), 12424, 12437)
                .add(new AlternativeKey("pattern", 16, "GrpPattern"), 12473, 12487)
                .add(new AlternativeKey("pattern", 17, "SlPattern"), 12506, 12518)
                .add(new AlternativeKey("pattern", 18, "PthPattern"), 12536, 12547)
                .add(new AlternativeKey("pattern", 19, "MacroPattern"), 12566, 12581)
                .add(new AlternativeKey("literalPattern", 1, "1"), 12619, 12633)
                .add(new AlternativeKey("literalPattern", 2, "2"), 12640, 12651)
                .add(new AlternativeKey("literalPattern", 3, "3"), 12658, 12669)
                .add(new AlternativeKey("literalPattern", 4, "4"), 12676, 12689)
                .add(new AlternativeKey("literalPattern", 5, "5"), 12696, 12712)
                .add(new AlternativeKey("literalPattern", 6, "6"), 12719, 12736)
                .add(new AlternativeKey("literalPattern", 7, "7"), 12743, 12763)
                .add(new AlternativeKey("literalPattern", 8, "8"), 12770, 12796)
                .add(new AlternativeKey("literalPattern", 9, "9"), 12803, 12827)
                .add(new AlternativeKey("identifierPattern", 1, "1"), 12854, 12921)
                .add(new AlternativeKey("identifierPattern", 2, "2"), 12865, 12875)
                .add(new AlternativeKey("identifierPattern", 3, "3"), 12878, 12887)
                .add(new AlternativeKey("identifierPattern", 4, "4"), 12890, 12898)
                .add(new AlternativeKey("identifierPattern", 5, "5"), 12902, 12918)
                .add(new AlternativeKey("wildcardPattern", 1, "1"), 12946, 12956)
                .add(new AlternativeKey("rangePattern", 1, "1"), 12978, 13037)
                .add(new AlternativeKey("rangePattern", 2, "2"), 12997, 13003)
                .add(new AlternativeKey("rangePattern", 3, "3"), 13006, 13018)
                .add(new AlternativeKey("rangePattern", 4, "4"), 13044, 13089)
                .add(new AlternativeKey("rangePatternBound", 1, "1"), 13116, 13127)
                .add(new AlternativeKey("rangePatternBound", 2, "2"), 13134, 13145)
                .add(new AlternativeKey("rangePatternBound", 3, "3"), 13152, 13178)
                .add(new AlternativeKey("rangePatternBound", 4, "4"), 13185, 13209)
                .add(new AlternativeKey("rangePatternBound", 5, "5"), 13216, 13232)
                .add(new AlternativeKey("rangePatternBound", 6, "6"), 13239, 13264)
                .add(new AlternativeKey("referencePattern", 1, "1"), 13290, 13335);
        bldr
                .add(new AlternativeKey("referencePattern", 2, "2"), 13300, 13303)
                .add(new AlternativeKey("referencePattern", 3, "3"), 13314, 13320)
                .add(new AlternativeKey("structPattern", 1, "1"), 13358, 13418)
                .add(new AlternativeKey("structPatternElements", 1, "1"), 13449, 13525)
                .add(new AlternativeKey("structPatternElements", 2, "2"), 13479, 13484)
                .add(new AlternativeKey("structPatternElements", 3, "3"), 13495, 13522)
                .add(new AlternativeKey("structPatternElements", 4, "4"), 13532, 13553)
                .add(new AlternativeKey("structPatternFields", 1, "1"), 13582, 13630)
                .add(new AlternativeKey("structPatternFields", 2, "2"), 13603, 13627)
                .add(new AlternativeKey("structPatternField", 1, "1"), 13658, 13698)
                .add(new AlternativeKey("structPatternField", 2, "2"), 13705, 13729)
                .add(new AlternativeKey("structPatternField", 3, "3"), 13736, 13756)
                .add(new AlternativeKey("structPatternEtCetera", 1, "1"), 13787, 13809)
                .add(new AlternativeKey("tupleStructPattern", 1, "1"), 13837, 13892)
                .add(new AlternativeKey("tupleStructPattern", 2, "2"), 13899, 13919)
                .add(new AlternativeKey("tupleStructItems", 1, "1"), 14008, 14041)
                .add(new AlternativeKey("tupleStructItems", 2, "2"), 14018, 14031)
                .add(new AlternativeKey("tupleStructItems", 3, "3"), 14048, 14099)
                .add(new AlternativeKey("tupleStructItems", 4, "4"), 14050, 14063)
                .add(new AlternativeKey("tupleStructItems", 5, "5"), 14076, 14089)
                .add(new AlternativeKey("groupedPattern", 1, "1"), 14123, 14151)
                .add(new AlternativeKey("slicePattern", 1, "1"), 14173, 14231)
                .add(new AlternativeKey("slicePattern", 2, "2"), 14195, 14208)
                .add(new AlternativeKey("pathPattern", 1, "1"), 14252, 14268)
                .add(new AlternativeKey("pathPattern", 2, "2"), 14275, 14300)
                .add(new AlternativeKey("delimTokenTree", 1, "1"), 14324, 14355)
                .add(new AlternativeKey("delimTokenTree", 2, "2"), 14362, 14393)
                .add(new AlternativeKey("delimTokenTree", 3, "3"), 14400, 14435)
                .add(new AlternativeKey("tokenTree", 1, "1"), 14454, 14543)
                .add(new AlternativeKey("tokenTree", 2, "2"), 14455, 14465)
                .add(new AlternativeKey("tokenTree", 3, "3"), 14468, 14483)
                .add(new AlternativeKey("tokenTree", 4, "4"), 14486, 14505)
                .add(new AlternativeKey("tokenTree", 5, "5"), 14508, 14531)
                .add(new AlternativeKey("tokenTree", 6, "6"), 14534, 14542)
                .add(new AlternativeKey("tokenTree", 7, "7"), 14546, 14560)
                .add(new AlternativeKey("macroInvocationSemi", 1, "1"), 14589, 14615)
                .add(new AlternativeKey("rangeFullExpr", 1, "1"), 14638, 14644)
                .add(new AlternativeKey("loopLabel", 1, "1"), 14663, 14672)
                .add(new AlternativeKey("infiniteLoopExpression", 1, "1"), 14704, 14724)
                .add(new AlternativeKey("gurb", 1, "1"), 14734, 14764)
                .add(new AlternativeKey("predicateLoopExpression", 1, "1"), 14934, 14980)
                .add(new AlternativeKey("predicatePatternLoopExpression", 1, "1"), 15020, 15094)
                .add(new AlternativeKey("iteratorLoopExpression", 1, "1"), 15126, 15181)
                .add(new AlternativeKey("matchArms", 1, "1"), 15196, 15243)
                .add(new AlternativeKey("matchArms", 2, "2"), 15213, 15234)
                .add(new AlternativeKey("matchArmContent", 1, "1"), 15268, 15615)
                .add(new AlternativeKey("matchArmContent", 2, "2"), 15345, 15361)
                .add(new AlternativeKey("matchArmContent", 3, "3"), 15380, 15394)
                .add(new AlternativeKey("matchArmContent", 4, "4"), 15413, 15425)
                .add(new AlternativeKey("matchArmContent", 5, "5"), 15444, 15459);
        bldr
                .add(new AlternativeKey("matchArmContent", 6, "6"), 15478, 15493)
                .add(new AlternativeKey("matchArmContent", 7, "7"), 15513, 15528)
                .add(new AlternativeKey("matchArmContent", 8, "8"), 15547, 15574)
                .add(new AlternativeKey("matchArmContent", 9, "9"), 15593, 15601)
                .add(new AlternativeKey("matchArmContent", 10, "10"), 15622, 15673)
                .add(new AlternativeKey("matchArm", 1, "1"), 15691, 15738)
                .add(new AlternativeKey("matchArmPatterns", 1, "1"), 15764, 15791)
                .add(new AlternativeKey("matchArmPatterns", 2, "2"), 15778, 15788)
                .add(new AlternativeKey("matchArmGuard", 1, "1"), 15814, 15841)
                .add(new AlternativeKey("traitFunctionDecl", 1, "1"), 15868, 15984)
                .add(new AlternativeKey("functionReturnType", 1, "1"), 16012, 16027)
                .add(new AlternativeKey("traitFunctionParameters", 1, "1"), 16060, 16115)
                .add(new AlternativeKey("traitFunctionParameters", 2, "2"), 16081, 16105)
                .add(new AlternativeKey("traitFunctionParam", 1, "1"), 16143, 16182)
                .add(new AlternativeKey("traitFunctionParam", 2, "2"), 16161, 16174)
                .add(new AlternativeKey("functionHead", 1, "1"), 16204, 16253)
                .add(new AlternativeKey("functionHead", 2, "2"), 16236, 16247)
                .add(new AlternativeKey("functionParameters", 1, "1"), 16281, 16326)
                .add(new AlternativeKey("functionParameters", 2, "2"), 16297, 16316)
                .add(new AlternativeKey("functionParam", 1, "1"), 16349, 16383)
                .add(new AlternativeKey("abi", 1, "1"), 16396, 16409)
                .add(new AlternativeKey("abi", 2, "2"), 16416, 16432)
                .add(new AlternativeKey("traitMethod", 1, "1"), 16453, 16516)
                .add(new AlternativeKey("traitMethod", 2, "2"), 16479, 16488)
                .add(new AlternativeKey("traitMethod", 3, "3"), 16499, 16514)
                .add(new AlternativeKey("traitMethodDecl", 1, "1"), 16541, 16679)
                .add(new AlternativeKey("traitMethodDecl", 2, "2"), 16597, 16625)
                .add(new AlternativeKey("traitType", 1, "1"), 16698, 16751)
                .add(new AlternativeKey("traitType", 2, "2"), 16716, 16738)
                .add(new AlternativeKey("traitConst", 1, "1"), 16771, 16845)
                .add(new AlternativeKey("traitConst", 2, "2"), 16801, 16832)
                .add(new AlternativeKey("asyncConstQualifiers", 1, "1"), 16875, 16880)
                .add(new AlternativeKey("asyncConstQualifiers", 2, "2"), 16887, 16892)
                .add(new AlternativeKey("implementation", 1, "1"), 16916, 16928)
                .add(new AlternativeKey("implementation", 2, "2"), 16935, 16944)
                .add(new AlternativeKey("inherentImpl", 1, "1"), 16966, 17057)
                .add(new AlternativeKey("inherentImplItem", 1, "1"), 17083, 17224)
                .add(new AlternativeKey("inherentImplItem", 2, "2"), 17109, 17128)
                .add(new AlternativeKey("inherentImplItem", 3, "3"), 17139, 17223)
                .add(new AlternativeKey("inherentImplItem", 4, "4"), 17165, 17177)
                .add(new AlternativeKey("inherentImplItem", 5, "5"), 17192, 17200)
                .add(new AlternativeKey("inherentImplItem", 6, "6"), 17215, 17221)
                .add(new AlternativeKey("traitImpl", 1, "1"), 17243, 17357)
                .add(new AlternativeKey("traitImplItem", 1, "1"), 17380, 17573)
                .add(new AlternativeKey("traitImplItem", 2, "2"), 17406, 17425)
                .add(new AlternativeKey("traitImplItem", 3, "3"), 17428, 17437)
                .add(new AlternativeKey("traitImplItem", 4, "4"), 17448, 17572)
                .add(new AlternativeKey("traitImplItem", 5, "5"), 17478, 17487)
                .add(new AlternativeKey("traitImplItem", 6, "6"), 17506, 17518)
                .add(new AlternativeKey("traitImplItem", 7, "7"), 17537, 17545);
        bldr
                .add(new AlternativeKey("traitImplItem", 8, "8"), 17564, 17570)
                .add(new AlternativeKey("method", 1, "1"), 17589, 17738)
                .add(new AlternativeKey("method", 2, "2"), 17645, 17664)
                .add(new AlternativeKey("selfParam", 1, "1"), 17757, 17818)
                .add(new AlternativeKey("selfParam", 2, "2"), 17783, 17796)
                .add(new AlternativeKey("selfParam", 3, "3"), 17807, 17816)
                .add(new AlternativeKey("shorthandSelf", 1, "1"), 17841, 17895)
                .add(new AlternativeKey("shorthandSelf", 2, "2"), 17851, 17854)
                .add(new AlternativeKey("shorthandSelf", 3, "3"), 17865, 17877)
                .add(new AlternativeKey("typedSelf", 1, "1"), 17914, 17939)
                .add(new AlternativeKey("externBlock", 1, "1"), 17960, 18022)
                .add(new AlternativeKey("externalItem", 1, "1"), 18044, 18170)
                .add(new AlternativeKey("externalItem", 2, "2"), 18070, 18089)
                .add(new AlternativeKey("externalItem", 3, "3"), 18100, 18169)
                .add(new AlternativeKey("externalItem", 4, "4"), 18114, 18132)
                .add(new AlternativeKey("externalItem", 5, "5"), 18147, 18167)
                .add(new AlternativeKey("externalStaticItem", 1, "1"), 18198, 18241)
                .add(new AlternativeKey("externalFunctionItem", 1, "1"), 18271, 18446)
                .add(new AlternativeKey("externalFunctionItem", 2, "2"), 18315, 18338)
                .add(new AlternativeKey("externalFunctionItem", 3, "3"), 18349, 18385)
                .add(new AlternativeKey("namedFunctionParameters", 1, "1"), 18479, 18534)
                .add(new AlternativeKey("namedFunctionParameters", 2, "2"), 18500, 18524)
                .add(new AlternativeKey("namedFunctionParam", 1, "1"), 18562, 18632)
                .add(new AlternativeKey("namedFunctionParam", 2, "2"), 18588, 18598)
                .add(new AlternativeKey("namedFunctionParam", 3, "3"), 18609, 18619)
                .add(new AlternativeKey("namedFunctionParametersWithVariadics", 1, "1"), 18678, 18762)
                .add(new AlternativeKey("namedFunctionParametersWithVariadics", 2, "2"), 18680, 18704)
                .add(new AlternativeKey("macroItem", 1, "1"), 18781, 18800)
                .add(new AlternativeKey("macroItem", 2, "2"), 18807, 18838)
                .add(new AlternativeKey("useDeclaration", 1, "1"), 18862, 18972)
                .add(new AlternativeKey("useDeclaration", 2, "2"), 18885, 18897)
                .add(new AlternativeKey("useDeclaration", 3, "3"), 18908, 18925)
                .add(new AlternativeKey("useDeclaration", 4, "4"), 18947, 18960)
                .add(new AlternativeKey("usePath", 1, "1"), 18989, 19063)
                .add(new AlternativeKey("usePath", 2, "2"), 19002, 19060)
                .add(new AlternativeKey("usePath", 3, "3"), 19016, 19038)
                .add(new AlternativeKey("useTarget", 1, "UseTargetMux"), 19082, 19094)
                .add(new AlternativeKey("useTarget", 2, "UseTargetSimple"), 19115, 19125)
                .add(new AlternativeKey("useTarget", 3, "UseTargetWildcard"), 19149, 19153)
                .add(new AlternativeKey("useMuxArg", 1, "1"), 19191, 19225)
                .add(new AlternativeKey("useMuxArg", 2, "2"), 19203, 19223)
                .add(new AlternativeKey("useMuxTarget", 1, "1"), 19243, 19334)
                .add(new AlternativeKey("useMuxTarget", 2, "2"), 19264, 19277)
                .add(new AlternativeKey("useMuxTarget", 3, "3"), 19282, 19314)
                .add(new AlternativeKey("useMuxTarget", 4, "4"), 19299, 19312)
                .add(new AlternativeKey("outerAttribute", 1, "1"), 19358, 19386)
                .add(new AlternativeKey("innerAttribute", 1, "1"), 19410, 19443)
                .add(new AlternativeKey("attr", 1, "1"), 19457, 19475)
                .add(new AlternativeKey("generics", 1, "1"), 19493, 19527)
                .add(new AlternativeKey("genericParams", 1, "1"), 19550, 19564);
        bldr
                .add(new AlternativeKey("genericParams", 2, "2"), 19571, 19606)
                .add(new AlternativeKey("genericParams", 3, "3"), 19573, 19592)
                .add(new AlternativeKey("genericArgs", 1, "1"), 19982, 20002)
                .add(new AlternativeKey("genericArgs", 2, "2"), 20009, 20057)
                .add(new AlternativeKey("genericArgs", 3, "3"), 20064, 20108)
                .add(new AlternativeKey("genericArgs", 4, "4"), 20115, 20162)
                .add(new AlternativeKey("genericArgs", 5, "5"), 20169, 20239)
                .add(new AlternativeKey("genericArgs", 6, "6"), 20246, 20317)
                .add(new AlternativeKey("genericArgs", 7, "7"), 20324, 20398)
                .add(new AlternativeKey("genericArgs", 8, "8"), 20405, 20502)
                .add(new AlternativeKey("genericArgsBindings", 1, "1"), 20531, 20579)
                .add(new AlternativeKey("genericArgsBindings", 2, "2"), 20552, 20576)
                .add(new AlternativeKey("genericArgsBinding", 1, "1"), 20607, 20629)
                .add(new AlternativeKey("genericArgsLifetimes", 1, "1"), 20659, 20694)
                .add(new AlternativeKey("genericArgsLifetimes", 2, "2"), 20670, 20684)
                .add(new AlternativeKey("genericArgsTypes", 1, "1"), 20720, 20767)
                .add(new AlternativeKey("genericArgsTypes", 2, "2"), 20726, 20736)
                .add(new AlternativeKey("genericArgsTypes", 3, "3"), 20739, 20759)
                .add(new AlternativeKey("genericArgsTypes", 4, "4"), 20746, 20756)
                .add(new AlternativeKey("type", 1, "1"), 20781, 20831)
                .add(new AlternativeKey("type", 2, "2"), 20797, 20829)
                .add(new AlternativeKey("type", 3, "3"), 20803, 20817)
                .add(new AlternativeKey("type", 4, "4"), 20820, 20828)
                .add(new AlternativeKey("primitiveType", 1, "1"), 20854, 20871)
                .add(new AlternativeKey("primitiveType", 2, "2"), 20878, 20897)
                .add(new AlternativeKey("primitiveType", 3, "3"), 20904, 20912)
                .add(new AlternativeKey("primitiveType", 4, "4"), 20919, 20928)
                .add(new AlternativeKey("primitiveType", 5, "5"), 20935, 20945)
                .add(new AlternativeKey("primitiveType", 6, "6"), 20952, 20963)
                .add(new AlternativeKey("primitiveType", 7, "7"), 20970, 20973)
                .add(new AlternativeKey("typeExpression", 1, "1"), 20997, 21009)
                .add(new AlternativeKey("typeExpression", 2, "2"), 21016, 21029)
                .add(new AlternativeKey("typeExpression", 3, "3"), 21036, 21051)
                .add(new AlternativeKey("typeExpression", 4, "4"), 21058, 21068)
                .add(new AlternativeKey("typeNoBounds", 1, "1"), 21090, 21107)
                .add(new AlternativeKey("typeNoBounds", 2, "2"), 21114, 21135)
                .add(new AlternativeKey("typeNoBounds", 3, "3"), 21142, 21165)
                .add(new AlternativeKey("typeNoBounds", 4, "4"), 21172, 21180)
                .add(new AlternativeKey("typeNoBounds", 5, "5"), 21187, 21196)
                .add(new AlternativeKey("typeNoBounds", 6, "6"), 21203, 21212)
                .add(new AlternativeKey("typeNoBounds", 7, "7"), 21219, 21233)
                .add(new AlternativeKey("typeNoBounds", 8, "8"), 21240, 21253)
                .add(new AlternativeKey("typeNoBounds", 9, "9"), 21260, 21269)
                .add(new AlternativeKey("typeNoBounds", 10, "10"), 21276, 21285)
                .add(new AlternativeKey("typeNoBounds", 11, "11"), 21292, 21304)
                .add(new AlternativeKey("typeNoBounds", 12, "12"), 21311, 21330)
                .add(new AlternativeKey("typeNoBounds", 13, "13"), 21337, 21353)
                .add(new AlternativeKey("typeNoBounds", 14, "14"), 21360, 21375)
                .add(new AlternativeKey("typeNoBounds", 15, "15"), 21382, 21395)
                .add(new AlternativeKey("implTraitType", 1, "1"), 21418, 21438);
        bldr
                .add(new AlternativeKey("traitObjectType", 1, "1"), 21463, 21483)
                .add(new AlternativeKey("parenthesizedType", 1, "1"), 21510, 21540)
                .add(new AlternativeKey("implTraitTypeOneBound", 1, "1"), 21571, 21586)
                .add(new AlternativeKey("traitObjectTypeOneBound", 1, "1"), 21619, 21634)
                .add(new AlternativeKey("typePath", 1, "1"), 21736, 21818)
                .add(new AlternativeKey("typePath", 2, "2"), 21750, 21798)
                .add(new AlternativeKey("typePath", 3, "3"), 21768, 21795)
                .add(new AlternativeKey("typePath", 4, "4"), 21801, 21817)
                .add(new AlternativeKey("typePathSegment", 1, "1"), 21843, 21992)
                .add(new AlternativeKey("typePathSegment", 2, "2"), 21957, 21968)
                .add(new AlternativeKey("typePathSegment", 3, "3"), 21979, 21989)
                .add(new AlternativeKey("typePathFn", 1, "1"), 22012, 22071)
                .add(new AlternativeKey("typePathFn", 2, "2"), 22053, 22068)
                .add(new AlternativeKey("typePathFnInputs", 1, "1"), 22097, 22117)
                .add(new AlternativeKey("typePathFnInputs", 2, "2"), 22104, 22114)
                .add(new AlternativeKey("pathIdentSegment", 1, "1"), 22143, 22153)
                .add(new AlternativeKey("pathIdentSegment", 2, "2"), 22160, 22165)
                .add(new AlternativeKey("pathIdentSegment", 3, "3"), 22172, 22181)
                .add(new AlternativeKey("pathIdentSegment", 4, "4"), 22188, 22196)
                .add(new AlternativeKey("pathIdentSegment", 5, "5"), 22203, 22208)
                .add(new AlternativeKey("pathIdentSegment", 6, "6"), 22215, 22227)
                .add(new AlternativeKey("simplePath", 1, "1"), 22247, 22312)
                .add(new AlternativeKey("simplePath", 2, "2"), 22280, 22309)
                .add(new AlternativeKey("simplePathSegment", 1, "1"), 22339, 22349)
                .add(new AlternativeKey("simplePathSegment", 2, "2"), 22356, 22361)
                .add(new AlternativeKey("simplePathSegment", 3, "3"), 22368, 22377)
                .add(new AlternativeKey("simplePathSegment", 4, "4"), 22384, 22396)
                .add(new AlternativeKey("tupleType", 1, "1"), 22415, 22435)
                .add(new AlternativeKey("tupleType", 2, "2"), 22442, 22484)
                .add(new AlternativeKey("tupleType", 3, "3"), 22454, 22464)
                .add(new AlternativeKey("neverType", 1, "1"), 22503, 22506)
                .add(new AlternativeKey("rawPointerType", 1, "1"), 22530, 22579)
                .add(new AlternativeKey("rawPointerType", 2, "2"), 22545, 22548)
                .add(new AlternativeKey("rawPointerType", 3, "3"), 22559, 22564)
                .add(new AlternativeKey("referenceType", 1, "1"), 22602, 22625)
                .add(new AlternativeKey("forLifetimes", 1, "1"), 22647, 22686)
                .add(new AlternativeKey("lifetimeParams", 1, "1"), 22710, 22748)
                .add(new AlternativeKey("lifetimeParams", 2, "2"), 22712, 22731)
                .add(new AlternativeKey("lifetimeParam", 1, "1"), 22771, 22827)
                .add(new AlternativeKey("lifetimeParam", 2, "2"), 22804, 22824)
                .add(new AlternativeKey("typeParams", 1, "1"), 22847, 22878)
                .add(new AlternativeKey("typeParams", 2, "2"), 22849, 22864)
                .add(new AlternativeKey("typeParam", 1, "1"), 22897, 22968)
                .add(new AlternativeKey("typeParam", 2, "2"), 22926, 22948)
                .add(new AlternativeKey("typeParam", 3, "3"), 22954, 22965)
                .add(new AlternativeKey("typeParamBounds", 1, "1"), 22993, 23036)
                .add(new AlternativeKey("typeParamBounds", 2, "2"), 23009, 23028)
                .add(new AlternativeKey("typeParamBound", 1, "1"), 23060, 23074)
                .add(new AlternativeKey("typeParamBound", 2, "2"), 23081, 23091)
                .add(new AlternativeKey("whereClause", 1, "1"), 23112, 23161);
        bldr.add(new AlternativeKey("whereClause", 2, "2"), 23120, 23141)
                .add(new AlternativeKey("whereClauseItem", 1, "1"), 23186, 23209)
                .add(new AlternativeKey("whereClauseItem", 2, "2"), 23216, 23240)
                .add(new AlternativeKey("lifetimeWhereClauseItem", 1, "1"), 23273, 23302)
                .add(new AlternativeKey("lifetimeBounds", 1, "1"), 23326, 23353)
                .add(new AlternativeKey("lifetimeBounds", 2, "2"), 23337, 23350)
                .add(new AlternativeKey("typeBoundWhereClauseItem", 1, "1"), 23387, 23428)
                .add(new AlternativeKey("traitBound", 1, "1"), 23548, 23580)
                .add(new AlternativeKey("traitBound", 2, "2"), 23587, 23640)
                .add(new AlternativeKey("lifetime", 1, "1"), 23658, 23671)
                .add(new AlternativeKey("lifetime", 2, "2"), 23679, 23692)
                .add(new AlternativeKey("lifetime", 3, "3"), 23699, 23713)
                .add(new AlternativeKey("lifetime", 4, "4"), 23720, 23736)
                .add(new AlternativeKey("arrayType", 1, "1"), 23755, 23819)
                .add(new AlternativeKey("sliceType", 1, "1"), 23838, 23867)
                .add(new AlternativeKey("inferredType", 1, "1"), 23889, 23899)
                .add(new AlternativeKey("qualifiedPathInType", 1, "1"), 23928, 23978)
                .add(new AlternativeKey("qualifiedPathInType", 2, "2"), 23948, 23975)
                .add(new AlternativeKey("qualifiedPathType", 1, "1"), 24005, 24047)
                .add(new AlternativeKey("qualifiedPathType", 2, "2"), 24021, 24032)
                .add(new AlternativeKey("bareFunctionType", 1, "1"), 24073, 24186)
                .add(new AlternativeKey("bareFunctionReturnType", 1, "1"), 24218, 24241)
                .add(new AlternativeKey("functionParametersMaybeNamedVariadic", 1, "1"), 24287, 24315)
                .add(new AlternativeKey("functionParametersMaybeNamedVariadic", 2, "2"), 24322, 24358)
                .add(new AlternativeKey("maybeNamedFunctionParameters", 1, "1"), 24396, 24445)
                .add(new AlternativeKey("maybeNamedFunctionParameters", 2, "2"), 24414, 24435)
                .add(new AlternativeKey("maybeNamedParam", 1, "1"), 24470, 24556)
                .add(new AlternativeKey("maybeNamedParam", 2, "2"), 24496, 24548)
                .add(new AlternativeKey("maybeNamedParam", 3, "3"), 24498, 24508)
                .add(new AlternativeKey("maybeNamedParam", 4, "4"), 24523, 24533)
                .add(new AlternativeKey("maybeNamedFunctionParametersVariadic", 1, "1"), 24602, 24676)
                .add(new AlternativeKey("maybeNamedFunctionParametersVariadic", 2, "2"), 24604, 24625)
                .add(new AlternativeKey("externCrate", 1, "1"), 24697, 24804)
                .add(new AlternativeKey("externCrate", 2, "2"), 24742, 24752)
                .add(new AlternativeKey("externCrate", 3, "3"), 24763, 24771)
                .add(new AlternativeKey("externCrate", 4, "4"), 24775, 24791)
                .add(new AlternativeKey("metaItem", 1, "1"), 24822, 24833)
                .add(new AlternativeKey("metaItem", 2, "2"), 24841, 24854)
                .add(new AlternativeKey("metaItem", 3, "3"), 24862, 24878)
                .add(new AlternativeKey("metaItem", 4, "4"), 24886, 24897)
                .add(new AlternativeKey("metaItem", 5, "5"), 24905, 24922)
                .add(new AlternativeKey("metaItem", 6, "6"), 24930, 24950)
                .add(new AlternativeKey("metaItem", 7, "7"), 24958, 24972)
                .add(new AlternativeKey("metaItem", 8, "8"), 24980, 24992)
                .add(new AlternativeKey("metaItem", 9, "9"), 25000, 25014)
                .add(new AlternativeKey("metaItem", 10, "10"), 25022, 25234)
                .add(new AlternativeKey("metaItem", 11, "11"), 25043, 25054)
                .add(new AlternativeKey("metaItem", 12, "12"), 25070, 25083)
                .add(new AlternativeKey("metaItem", 13, "13"), 25099, 25110)
                .add(new AlternativeKey("metaItem", 14, "14"), 25126, 25143);
        bldr.add(new AlternativeKey("metaItem", 15, "15"), 25159, 25169)
                .add(new AlternativeKey("metaItem", 16, "16"), 25185, 25197)
                .add(new AlternativeKey("metaItem", 17, "17"), 25213, 25227)
                .add(new AlternativeKey("metaItem", 18, "18"), 25241, 25255)
                .add(new AlternativeKey("stringLiteral", 1, "1"), 26494, 26511)
                .add(new AlternativeKey("stringLiteral", 2, "2"), 26518, 26538)
                .add(new AlternativeKey("intLiteral", 1, "1"), 26558, 26572)
                .add(new AlternativeKey("intLiteral", 2, "2"), 26579, 26596)
                .add(new AlternativeKey("byteStringLiteral", 1, "1"), 26623, 26644)
                .add(new AlternativeKey("byteStringLiteral", 2, "2"), 26651, 26675)
                .add(new AlternativeKey("booleanLiteral", 1, "1"), 26699, 26717)
                .add(new AlternativeKey("booleanLiteral", 2, "2"), 26724, 26745)
                .add(new AlternativeKey("charLiteral", 1, "1"), 26766, 26781)
                .add(new AlternativeKey("charLiteral", 2, "2"), 26788, 26806)
                .add(new AlternativeKey("floatLiteral", 1, "1"), 26828, 26844)
                .add(new AlternativeKey("floatLiteral", 2, "2"), 26851, 26870)
                .add(new AlternativeKey("byteLiteral", 1, "1"), 26891, 26906)
                .add(new AlternativeKey("byteLiteral", 2, "2"), 26913, 26931)
                .add(new AlternativeKey("attrPath", 1, "1"), 26949, 26988)
                .add(new AlternativeKey("attrPath", 2, "2"), 26950, 26959)
                .add(new AlternativeKey("attrPath", 3, "3"), 26962, 26975)
                .add(new AlternativeKey("attrPath", 4, "4"), 26995, 27043)
                .add(new AlternativeKey("attrPath", 5, "5"), 26996, 27008)
                .add(new AlternativeKey("attrPath", 6, "6"), 27011, 27027)
                .add(new AlternativeKey("macroRulesDefinition", 1, "1"), 27073, 27108)
                .add(new AlternativeKey("macroRulesDefinition", 2, "2"), 27115, 27192)
                .add(new AlternativeKey("macroKeywordArgs", 1, "1"), 27214, 27224)
                .add(new AlternativeKey("macroRulesDef", 1, "1"), 27247, 27288)
                .add(new AlternativeKey("macroRulesDef", 2, "2"), 27295, 27340)
                .add(new AlternativeKey("macroRulesDef", 3, "3"), 27347, 27378)
                .add(new AlternativeKey("macroRules", 1, "1"), 27394, 27438)
                .add(new AlternativeKey("macroRules", 2, "2"), 27406, 27425)
                .add(new AlternativeKey("macroRule", 1, "1"), 27453, 27491)
                .add(new AlternativeKey("macroMatcher", 1, "1"), 27514, 27546)
                .add(new AlternativeKey("macroMatcher", 2, "2"), 27553, 27585)
                .add(new AlternativeKey("macroMatcher", 3, "3"), 27593, 27629)
                .add(new AlternativeKey("macroMatch", 1, "1"), 27650, 27873)
                .add(new AlternativeKey("macroMatch", 2, "2"), 27651, 27666)
                .add(new AlternativeKey("macroMatch", 3, "3"), 27674, 27686)
                .add(new AlternativeKey("macroMatch", 4, "4"), 27694, 27781)
                .add(new AlternativeKey("macroMatch", 5, "5"), 27695, 27714)
                .add(new AlternativeKey("macroMatch", 6, "6"), 27717, 27732)
                .add(new AlternativeKey("macroMatch", 7, "7"), 27789, 27865)
                .add(new AlternativeKey("macroRepSep", 1, "1"), 27890, 27905)
                .add(new AlternativeKey("macroRepSep", 2, "2"), 27908, 27920)
                .add(new AlternativeKey("macroTranscriber", 1, "1"), 27942, 27957);
        searchBug = bldr.build();
    }

    static final class AlternativeKey implements Comparable<AlternativeKey> {

        private final String ruleName;
        private final short alternativeIndex;
        private final String label;

        AlternativeKey(String ruleName, int alternativeIndex, String label) {
            this.ruleName = ruleName;
            this.alternativeIndex = (short) alternativeIndex;
            this.label = label;
        }

        public String rule() {
            return ruleName;
        }

        public int alternativeIndex() {
            return alternativeIndex;
        }

        public String label() {
            return label.isEmpty() ? Integer.toString(alternativeIndex) : label;
        }

        @Override
        public int hashCode() {
            return ruleName.hashCode() * 101 * alternativeIndex;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final AlternativeKey other = (AlternativeKey) obj;
            if (this.alternativeIndex != other.alternativeIndex) {
                return false;
            }
            return Objects.equals(this.ruleName, other.ruleName);
        }

        @Override
        public String toString() {
            return ruleName + ":" + alternativeIndex + (label.length() > 0 ? ":" + label : "");
        }

        @Override
        public int compareTo(AlternativeKey o) {
            return Integer.compare(alternativeIndex, o.alternativeIndex);
        }
    }
}
