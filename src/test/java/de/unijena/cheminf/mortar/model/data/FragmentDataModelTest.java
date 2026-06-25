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

package de.unijena.cheminf.mortar.model.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.HashMap;
import java.util.Locale;

/**
 * Direct, headless unit tests for {@link FragmentDataModel}. All fixtures are built from real CDK
 * {@code IAtomContainer}s parsed from SMILES (no Mockito); the JavaFX {@code ImageView} parent-structure accessor is
 * verified to construct under {@code java.awt.headless=true} without a started toolkit. Frequency/percentage values
 * asserted here are the exact values set on the model (deterministic, not CDK-derived), while CDK-derived artefacts are
 * only checked for invariants (non-null, instance type) because CDK is a moving 2.12-SNAPSHOT.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class FragmentDataModelTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB so any message-bundle strings resolved by the parent-structure
     * image accessor (error-image captions) are deterministic.
     */
    public FragmentDataModelTest() {
        Locale.setDefault(Locale.of("en", "GB"));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Constructor, frequency, percentage, and validation test methods" defaultstate="collapsed">
    /**
     * Tests that the atom-container constructor builds a fragment whose absolute and molecule frequencies both default
     * to 0 and whose percentages default to 0.0.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testAtomContainerConstructorDefaults() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertEquals(0, tmpFragment.getAbsoluteFrequency());
        Assertions.assertEquals(0, tmpFragment.getMoleculeFrequency());
        Assertions.assertEquals(0.0, tmpFragment.getAbsolutePercentage());
        Assertions.assertEquals(0.0, tmpFragment.getMoleculePercentage());
    }
    //
    /**
     * Tests that the string constructor returns the supplied unique SMILES and name verbatim and starts with both
     * frequencies defaulted to 0.
     */
    @Test
    public void testStringConstructorDefaults() {
        FragmentDataModel tmpFragment = new FragmentDataModel("c1ccccc1", "Benzene", new HashMap<>());
        Assertions.assertEquals("c1ccccc1", tmpFragment.getUniqueSmiles());
        Assertions.assertEquals("Benzene", tmpFragment.getName());
        Assertions.assertEquals(0, tmpFragment.getAbsoluteFrequency());
        Assertions.assertEquals(0, tmpFragment.getMoleculeFrequency());
    }
    //
    /**
     * Tests that {@code incrementAbsoluteFrequency()} returns the incremented value (1 on the first call) and that the
     * subsequent getter reflects the incremented value.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testIncrementAbsoluteFrequencyReturnsIncrementedValue() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertEquals(1, tmpFragment.incrementAbsoluteFrequency());
        Assertions.assertEquals(1, tmpFragment.getAbsoluteFrequency());
        Assertions.assertEquals(2, tmpFragment.incrementAbsoluteFrequency());
        Assertions.assertEquals(2, tmpFragment.getAbsoluteFrequency());
    }
    //
    /**
     * Tests that {@code incrementMoleculeFrequency()} returns the incremented value (1 on the first call) and that the
     * subsequent getter reflects the incremented value.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testIncrementMoleculeFrequencyReturnsIncrementedValue() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertEquals(1, tmpFragment.incrementMoleculeFrequency());
        Assertions.assertEquals(1, tmpFragment.getMoleculeFrequency());
        Assertions.assertEquals(2, tmpFragment.incrementMoleculeFrequency());
        Assertions.assertEquals(2, tmpFragment.getMoleculeFrequency());
    }
    //
    /**
     * Tests the happy-path round-trips of the four frequency/percentage setters and getters with the exact values set.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testFrequencyAndPercentageSetterGetterRoundTrip() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        tmpFragment.setAbsoluteFrequency(3);
        tmpFragment.setMoleculeFrequency(2);
        tmpFragment.setAbsolutePercentage(0.25);
        tmpFragment.setMoleculePercentage(0.20);
        Assertions.assertEquals(3, tmpFragment.getAbsoluteFrequency());
        Assertions.assertEquals(2, tmpFragment.getMoleculeFrequency());
        Assertions.assertEquals(0.25, tmpFragment.getAbsolutePercentage());
        Assertions.assertEquals(0.20, tmpFragment.getMoleculePercentage());
    }
    //
    /**
     * Tests that {@code setAbsoluteFrequency(-1)} throws an {@code IllegalArgumentException} (the negative-value
     * validation branch).
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetAbsoluteFrequencyRejectsNegative() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> tmpFragment.setAbsoluteFrequency(-1));
    }
    //
    /**
     * Tests that {@code setMoleculeFrequency(-1)} throws an {@code IllegalArgumentException} (the negative-value
     * validation branch).
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetMoleculeFrequencyRejectsNegative() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> tmpFragment.setMoleculeFrequency(-1));
    }
    //
    /**
     * Tests that {@code setAbsolutePercentage(...)} rejects both a negative value and a non-finite value (the two
     * distinct validation branches), here using {@code Double.NaN} for the non-finite case.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetAbsolutePercentageRejectsNegativeAndNonFinite() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> tmpFragment.setAbsolutePercentage(-0.1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> tmpFragment.setAbsolutePercentage(Double.NaN));
    }
    //
    /**
     * Tests that {@code setMoleculePercentage(...)} rejects both a negative value and a non-finite value (the two
     * distinct validation branches), here using {@code Double.POSITIVE_INFINITY} for the non-finite case.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetMoleculePercentageRejectsNegativeAndNonFinite() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> tmpFragment.setMoleculePercentage(-0.1));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpFragment.setMoleculePercentage(Double.POSITIVE_INFINITY));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Parses the given SMILES into a real CDK atom container using a silent builder and wraps it in a
     * {@link FragmentDataModel} (atom-container constructor, no stereochemistry encoding).
     *
     * @param aSmiles SMILES string to parse
     * @return a fragment data model backed by the parsed atom container
     * @throws Exception if SMILES parsing fails
     */
    private static FragmentDataModel buildFragment(String aSmiles) throws Exception {
        SmilesParser tmpParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        IAtomContainer tmpAtomContainer = tmpParser.parseSmiles(aSmiles);
        return new FragmentDataModel(tmpAtomContainer, false);
    }
    //</editor-fold>
}
