/*
 * MORTAR - MOlecule fRagmenTAtion fRamework
 * Copyright (C) 2026  Felix Baensch, Jonas Schaub (felix.j.baensch@gmail.com, jonas.schaub@uni-jena.de)
 *
 * Source code is available at <https://github.com/FelixBaensch/MORTAR>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.unijena.cheminf.mortar.model.depict;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Tests for {@link DepictionUtil}.
 *
 * @author Jonas Schaub
 * @version 1.0.0.0
 */
class DepictionUtilTest {
    //<editor-fold desc="Tests">
    /**
     * Illustrates the effect of each format pattern defined in {@link DepictionUtil.IntegerFormatPattern}
     * enum on selected large integer values. The patterns are applied progressively (most detail → the least detail)
     * by {@link DepictionUtil#fitIntegerDisplayToImageWidth(double, int)} until the resulting text fits the given
     * image width.
     *
     * <p>The patterns and their intended output for the example value {@code 1,234,567,890}:</p>
     * <pre>
     *   "0.000E0"  →  "1.235E9"   (three decimal places)
     *   "0.00E0"   →  "1.23E9"    (two decimal places)
     *   "0.0E0"    →  "1.2E9"     (one decimal place)
     *   "0E0"      →  "1E9"       (no decimal places, shortest)
     * </pre>
     *
     * <p>Note that rounding can cause the leading digit to increment.  For instance,
     * {@code 9,876,543} formatted with {@code "0E0"} rounds up to {@code "1E7"} rather than {@code "9E6"}.</p>
     */
    @Test
    void testFormatPatternsIntIllustration() {
        DecimalFormatSymbols tmpUsSymbols = DecimalFormatSymbols.getInstance(Locale.US);

        // --- Example 1: 1,234,567,890 ---
        int tmpValue1 = 1_234_567_890;
        String[] tmpExpected1 = {
            "1.235E9",  // 1.2345... rounded at the 4th decimal (digit = 5) → rounds up
            "1.23E9",   // 1.234...  rounded at the 3rd decimal (digit = 4) → rounds down
            "1.2E9",    // 1.23...   rounded at the 2nd decimal (digit = 3) → rounds down
            "1E9"       // 1.2...    rounded at the 1st decimal (digit = 2) → rounds down
        };
        for (int i = 0; i < DepictionUtil.IntegerFormatPattern.values().length; i++) {
            DecimalFormat tmpFmt = new DecimalFormat(DepictionUtil.IntegerFormatPattern.values()[i].getPattern(), tmpUsSymbols);
            String tmpResult = tmpFmt.format(tmpValue1);
            Assertions.assertEquals(
                    tmpExpected1[i], tmpResult,
                    "Pattern \"" + DepictionUtil.IntegerFormatPattern.values()[i].getPattern() + "\" applied to " + tmpValue1);
        }

        // --- Example 2: 9,876,543 – demonstrates carry-propagation on rounding ---
        int tmpValue2 = 9_876_543;
        String[] tmpExpected2 = {
            "9.877E6",  // 9.876543 rounded at the 4th decimal (digit = 5) → rounds up
            "9.88E6",   // 9.8765   rounded at the 3rd decimal (digit = 6) → rounds up
            "9.9E6",    // 9.876    rounded at the 2nd decimal (digit = 7) → rounds up
            "1E7"       // 9.9      rounded at the 1st decimal (digit = 9) → carry: 10 → 1×10^7
        };
        for (int i = 0; i < DepictionUtil.IntegerFormatPattern.values().length; i++) {
            DecimalFormat tmpFmt = new DecimalFormat(DepictionUtil.IntegerFormatPattern.values()[i].getPattern(), tmpUsSymbols);
            String tmpResult = tmpFmt.format(tmpValue2);
            Assertions.assertEquals(
                    tmpExpected2[i], tmpResult,
                    "Pattern \"" + DepictionUtil.IntegerFormatPattern.values()[i].getPattern() + "\" applied to " + tmpValue2);
        }

        // --- Example 3: Integer.MAX_VALUE (2,147,483,647) ---
        int tmpValue3 = Integer.MAX_VALUE;
        String[] tmpExpected3 = {
            "2.147E9",  // 2.147483... rounded at the 4th decimal (digit = 4) → rounds down
            "2.15E9",   // 2.1474...   rounded at the 3rd decimal (digit = 7) → rounds up
            "2.1E9",    // 2.147...    rounded at the 2nd decimal (digit = 4) → rounds down
            "2E9"       // 2.1...      rounded at the 1st decimal (digit = 1) → rounds down
        };
        for (int i = 0; i < DepictionUtil.IntegerFormatPattern.values().length; i++) {
            DecimalFormat tmpFmt = new DecimalFormat(DepictionUtil.IntegerFormatPattern.values()[i].getPattern(), tmpUsSymbols);
            String tmpResult = tmpFmt.format(tmpValue3);
            Assertions.assertEquals(
                    tmpExpected3[i], tmpResult,
                    "Pattern \"" + DepictionUtil.IntegerFormatPattern.values()[i].getPattern() + "\" applied to " + tmpValue3);
        }
    }
    //
    /**
     * Tests that {@link DepictionUtil#fitIntegerDisplayToImageWidth(double, int)} produces a compressed
     * (shorter) representation when the image is too narrow to display the plain integer string.
     * A width of 2 pixels is far too narrow for any text, so the method must fall back to the shortest
     * available scientific-notation format.
     */
    @Test
    void testFitIntegerDisplayToImageWidthCompressesForNarrowImage() {
        // 2 pixels is too narrow for any multi-character text → compression must kick in
        double tmpVeryNarrowImage = 2.0;
        int[] tmpLargeValues = {1_234_567_890, 9_876_543, Integer.MAX_VALUE, 100_000};
        for (int tmpValue : tmpLargeValues) {
            String tmpPlain = String.valueOf(tmpValue);
            String tmpCompressed = DepictionUtil.fitIntegerDisplayToImageWidth(tmpVeryNarrowImage, tmpValue);
            Assertions.assertNotEquals(
                    tmpPlain, tmpCompressed,
                    "Value " + tmpValue + " should be reformatted for a 2-pixel-wide image");
        }
    }
    //</editor-fold>
}
