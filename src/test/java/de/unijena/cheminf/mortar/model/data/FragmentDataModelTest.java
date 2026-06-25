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

import javafx.scene.image.ImageView;

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
    //<editor-fold desc="Parent-molecule tracking test methods" defaultstate="collapsed">
    /**
     * Tests the empty-parent-Set state of a freshly built fragment: {@code getFirstParentMolecule()} is null,
     * {@code getParentMoleculeName()} is the empty string, and {@code getParentMoleculeStructure()} returns the
     * non-null "No parent molecules" error {@code ImageView} (headless-safe). Uses a fresh fragment so no cached
     * first-parent leaks in from another state.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testParentTrackingEmptySetState() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertNull(tmpFragment.getFirstParentMolecule());
        Assertions.assertEquals("", tmpFragment.getParentMoleculeName());
        ImageView tmpStructure = tmpFragment.getParentMoleculeStructure();
        Assertions.assertNotNull(tmpStructure);
    }
    //
    /**
     * Tests the Set-populated state: a parent added through the live mutable Set returned by
     * {@code getParentMolecules()} is the molecule reported by {@code getFirstParentMolecule()} (the lazy first-parent
     * cache resolves to it), and {@code getParentMoleculeName()} equals the parent's name. Uses a fresh fragment so the
     * lazy cache starts empty.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testParentTrackingSetPopulatedViaGetter() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        MoleculeDataModel tmpParent = FragmentDataModelTest.buildParent("CCO", "Ethanol");
        tmpFragment.getParentMolecules().add(tmpParent);
        Assertions.assertSame(tmpParent, tmpFragment.getFirstParentMolecule());
        Assertions.assertEquals(tmpParent.getName(), tmpFragment.getParentMoleculeName());
    }
    //
    /**
     * Tests that {@code setParentMolecule(...)} registers a parent that is then used by
     * {@code getFirstParentMolecule()}. Note that the explicit setter populates the cached first-parent field but not
     * the parent Set, so {@code getFirstParentMolecule()} (whose empty-Set guard short-circuits to null) is only
     * reached here because the parent Set is also populated via the live getter; a fresh fragment is used.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetParentMoleculeUsedByFirstParent() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        MoleculeDataModel tmpParent = FragmentDataModelTest.buildParent("CCO", "Ethanol");
        tmpFragment.getParentMolecules().add(tmpParent);
        tmpFragment.setParentMolecule(tmpParent);
        Assertions.assertSame(tmpParent, tmpFragment.getFirstParentMolecule());
    }
    //
    /**
     * Tests that {@code setParentMolecule(null)} throws a {@code NullPointerException} (the
     * {@code Objects.requireNonNull} null-guard).
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testSetParentMoleculeRejectsNull() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        Assertions.assertThrows(NullPointerException.class, () -> tmpFragment.setParentMolecule(null));
    }
    //
    /**
     * Tests the valid-parent branch of {@code getParentMoleculeStructure()}: with a parent built from a valid SMILES
     * registered, the accessor returns a non-null {@code ImageView} (the depicted-structure branch), verified headless.
     * No golden-pixel assertions are made. Uses a fresh fragment.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testGetParentMoleculeStructureValidParentBranch() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        MoleculeDataModel tmpParent = FragmentDataModelTest.buildParent("CCO", "Ethanol");
        tmpFragment.getParentMolecules().add(tmpParent);
        ImageView tmpStructure = tmpFragment.getParentMoleculeStructure();
        Assertions.assertNotNull(tmpStructure);
    }
    //
    /**
     * Tests the bad-parent branch of {@code getParentMoleculeStructure()}: with a parent built from an unparseable
     * SMILES registered, the atom-container retrieval fails and the accessor still returns a non-null error-image
     * {@code ImageView} (the catch branch), verified headless. Uses a fresh fragment.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testGetParentMoleculeStructureBadParentBranch() throws Exception {
        FragmentDataModel tmpFragment = FragmentDataModelTest.buildFragment("c1ccccc1");
        MoleculeDataModel tmpBadParent = new MoleculeDataModel("not_a_valid_smiles", "Invalid", new HashMap<>());
        tmpFragment.getParentMolecules().add(tmpBadParent);
        ImageView tmpStructure = tmpFragment.getParentMoleculeStructure();
        Assertions.assertNotNull(tmpStructure);
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
    //
    /**
     * Builds a parent {@link MoleculeDataModel} from the given valid SMILES via the string constructor so the parent
     * carries a deterministic, explicitly supplied name (used to assert {@code getParentMoleculeName()}).
     *
     * @param aSmiles unique SMILES string of the parent molecule
     * @param aName name of the parent molecule
     * @return a parent molecule data model
     */
    private static MoleculeDataModel buildParent(String aSmiles, String aName) {
        return new MoleculeDataModel(aSmiles, aName, new HashMap<>());
    }
    //</editor-fold>
}
