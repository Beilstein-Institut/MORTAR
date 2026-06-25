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

import de.unijena.cheminf.mortar.model.util.ChemUtil;

import javafx.scene.image.ImageView;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Direct, headless unit tests for {@link MoleculeDataModel}. All fixtures are built from real CDK
 * {@code IAtomContainer}s parsed from SMILES (no Mockito); the JavaFX {@code ImageView}/{@code BooleanProperty}
 * accessors are verified to construct under {@code java.awt.headless=true} without a started toolkit.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class MoleculeDataModelTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB so any message-bundle strings resolved by the image
     * accessors are deterministic.
     */
    public MoleculeDataModelTest() {
        Locale.setDefault(Locale.of("en", "GB"));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Constructor and identity-contract test methods" defaultstate="collapsed">
    /**
     * Tests that the atom-container constructor derives the unique SMILES from the supplied container: the value
     * returned by {@code getUniqueSmiles()} must equal an independent {@code ChemUtil.createUniqueSmiles(ac, false)}
     * call on the same container, and successive calls must return the same (stable) string. Asserts against the
     * runtime-computed SMILES rather than a hard-coded literal because CDK is a moving 2.12-SNAPSHOT.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testAtomContainerConstructorUniqueSmilesIdentityContract() throws Exception {
        IAtomContainer tmpAtomContainer = MoleculeDataModelTest.buildAtomContainer("c1ccccc1");
        String tmpExpectedSmiles = ChemUtil.createUniqueSmiles(tmpAtomContainer, false);
        MoleculeDataModel tmpMolecule = new MoleculeDataModel(tmpAtomContainer, false);
        Assertions.assertNotNull(tmpMolecule.getUniqueSmiles());
        Assertions.assertEquals(tmpExpectedSmiles, tmpMolecule.getUniqueSmiles());
        //stability: the field is final, so two reads must return the identical string
        Assertions.assertSame(tmpMolecule.getUniqueSmiles(), tmpMolecule.getUniqueSmiles());
    }
    //
    /**
     * Tests that the atom-container constructor retains the container (keepAtomContainer becomes true) so that
     * later coverage of the kept-container path is exercised, using an aliphatic fixture.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testAtomContainerConstructorKeepsContainer() throws Exception {
        IAtomContainer tmpAtomContainer = MoleculeDataModelTest.buildAtomContainer("O=C(O)CCCC(=O)O");
        MoleculeDataModel tmpMolecule = new MoleculeDataModel(tmpAtomContainer, false);
        Assertions.assertTrue(tmpMolecule.isKeepAtomContainer());
        Assertions.assertEquals(ChemUtil.createUniqueSmiles(tmpAtomContainer, false), tmpMolecule.getUniqueSmiles());
    }
    //
    /**
     * Tests that the string constructor returns the supplied unique SMILES and name verbatim and does not keep an
     * atom container.
     */
    @Test
    public void testStringConstructorReturnsSuppliedValues() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        Assertions.assertEquals("c1ccccc1", tmpMolecule.getUniqueSmiles());
        Assertions.assertEquals("Benzene", tmpMolecule.getName());
        Assertions.assertFalse(tmpMolecule.isKeepAtomContainer());
    }
    //
    /**
     * Tests the side-effecting "NoName" defaulting of {@code getName()} for a model constructed with a null name
     * (RESEARCH Pitfall 3): the getter must return "NoName".
     */
    @Test
    public void testGetNameDefaultsToNoNameForNullName() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", null, new HashMap<>());
        Assertions.assertEquals("NoName", tmpMolecule.getName());
    }
    //
    /**
     * Tests the side-effecting "NoName" defaulting of {@code getName()} for a model constructed with an
     * empty-string name (the second branch of the null/empty guard): the getter must return "NoName".
     */
    @Test
    public void testGetNameDefaultsToNoNameForEmptyName() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "", new HashMap<>());
        Assertions.assertEquals("NoName", tmpMolecule.getName());
    }
    //
    /**
     * Tests that {@code getName()} returns the supplied name when it is neither null nor empty, and that
     * {@code setName(...)} round-trips through {@code getName()}.
     */
    @Test
    public void testGetAndSetName() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Original", new HashMap<>());
        Assertions.assertEquals("Original", tmpMolecule.getName());
        tmpMolecule.setName("X");
        Assertions.assertEquals("X", tmpMolecule.getName());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Atom-container, property, map, image, and size test methods" defaultstate="collapsed">
    /**
     * Tests that a model built from the atom-container constructor returns its stored, non-null container directly
     * from {@code getAtomContainer()} (the kept-container short-circuit path).
     *
     * @throws Exception if SMILES parsing or atom-container retrieval fails
     */
    @Test
    public void testGetAtomContainerReturnsStoredContainerForAtomContainerModel() throws Exception {
        IAtomContainer tmpAtomContainer = MoleculeDataModelTest.buildAtomContainer("c1ccccc1");
        MoleculeDataModel tmpMolecule = new MoleculeDataModel(tmpAtomContainer, false);
        IAtomContainer tmpReturned = tmpMolecule.getAtomContainer();
        Assertions.assertNotNull(tmpReturned);
        Assertions.assertTrue(tmpReturned.getAtomCount() > 0);
    }
    //
    /**
     * Tests that a string-constructor model lazily parses its unique SMILES into a non-null container (exercising the
     * kekulize-try / catch-fallback parse path) for a valid aromatic SMILES. Asserts structural non-emptiness rather
     * than a golden atom count due to CDK 2.12-SNAPSHOT drift.
     *
     * @throws Exception if atom-container retrieval fails
     */
    @Test
    public void testGetAtomContainerLazilyParsesForStringModel() throws Exception {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        IAtomContainer tmpReturned = tmpMolecule.getAtomContainer();
        Assertions.assertNotNull(tmpReturned);
        Assertions.assertTrue(tmpReturned.getAtomCount() > 0);
    }
    //
    /**
     * Tests that {@code setKeepAtomContainer(true)} followed by {@code getAtomContainer()} caches a non-null
     * container and that {@code isKeepAtomContainer()} reports true.
     *
     * @throws Exception if atom-container retrieval fails
     */
    @Test
    public void testSetKeepAtomContainerTrueCachesContainer() throws Exception {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        tmpMolecule.setKeepAtomContainer(true);
        Assertions.assertTrue(tmpMolecule.isKeepAtomContainer());
        IAtomContainer tmpFirst = tmpMolecule.getAtomContainer();
        Assertions.assertNotNull(tmpFirst);
        //second call must return the cached instance
        Assertions.assertSame(tmpFirst, tmpMolecule.getAtomContainer());
    }
    //
    /**
     * Tests that {@code setKeepAtomContainer(false)} nulls the cached container and reports false, after which
     * {@code getAtomContainer()} re-parses to a fresh non-null container.
     *
     * @throws Exception if SMILES parsing or atom-container retrieval fails
     */
    @Test
    public void testSetKeepAtomContainerFalseClearsCache() throws Exception {
        IAtomContainer tmpAtomContainer = MoleculeDataModelTest.buildAtomContainer("c1ccccc1");
        MoleculeDataModel tmpMolecule = new MoleculeDataModel(tmpAtomContainer, false);
        tmpMolecule.setKeepAtomContainer(false);
        Assertions.assertFalse(tmpMolecule.isKeepAtomContainer());
        Assertions.assertNotNull(tmpMolecule.getAtomContainer());
    }
    //
    /**
     * Tests the selection {@code BooleanProperty}: the property is non-null, the default selection state is true (set
     * in the constructor), and {@code setSelection(...)} round-trips through both {@code isSelected()} and the
     * property value.
     */
    @Test
    public void testSelectionPropertyRoundTrip() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        Assertions.assertNotNull(tmpMolecule.selectionProperty());
        Assertions.assertTrue(tmpMolecule.isSelected());
        tmpMolecule.setSelection(false);
        Assertions.assertFalse(tmpMolecule.isSelected());
        Assertions.assertFalse(tmpMolecule.selectionProperty().get());
        tmpMolecule.setSelection(true);
        Assertions.assertTrue(tmpMolecule.isSelected());
        Assertions.assertTrue(tmpMolecule.selectionProperty().get());
    }
    //
    /**
     * Tests the fragment and frequency maps: a fragment list and a frequency map put under a fragmentation name are
     * read back via the specific-fragmentation getters, and {@code hasMoleculeUndergoneSpecificFragmentation(...)} is
     * true for the populated name and false for an absent name.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testFragmentAndFrequencyMaps() throws Exception {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        String tmpFragmentationName = "TestFragmentation";
        IAtomContainer tmpFragmentContainer = MoleculeDataModelTest.buildAtomContainer("c1ccccc1");
        FragmentDataModel tmpFragment = new FragmentDataModel(tmpFragmentContainer, false);
        List<FragmentDataModel> tmpFragmentList = new ArrayList<>();
        tmpFragmentList.add(tmpFragment);
        tmpMolecule.getAllFragments().put(tmpFragmentationName, tmpFragmentList);
        Map<String, Integer> tmpFrequencyMap = new HashMap<>();
        tmpFrequencyMap.put(tmpFragment.getUniqueSmiles(), 1);
        tmpMolecule.getFragmentFrequencies().put(tmpFragmentationName, tmpFrequencyMap);
        Assertions.assertSame(tmpFragmentList, tmpMolecule.getFragmentsOfSpecificFragmentation(tmpFragmentationName));
        Assertions.assertSame(tmpFrequencyMap, tmpMolecule.getFragmentFrequencyOfSpecificFragmentation(tmpFragmentationName));
        Assertions.assertTrue(tmpMolecule.hasMoleculeUndergoneSpecificFragmentation(tmpFragmentationName));
        Assertions.assertFalse(tmpMolecule.hasMoleculeUndergoneSpecificFragmentation("AbsentFragmentation"));
    }
    //
    /**
     * Tests that {@code getStructure()} and {@code getStructureWithText(...)} return non-null {@code ImageView}s for a
     * valid-SMILES model (the success branch), verified headless. No golden-pixel assertions are made.
     */
    @Test
    public void testStructureImageAccessorsValidModelSuccessBranch() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        ImageView tmpStructure = tmpMolecule.getStructure();
        Assertions.assertNotNull(tmpStructure);
        ImageView tmpStructureWithText = tmpMolecule.getStructureWithText("caption");
        Assertions.assertNotNull(tmpStructureWithText);
    }
    //
    /**
     * Tests that {@code getStructure()} and {@code getStructureWithText(...)} both return a non-null {@code ImageView}
     * for an invalid-SMILES model, i.e. the error-image branch via {@code DepictionUtil.depictErrorImage(...)} (CDK
     * parse failure caught) in each accessor, verified headless.
     */
    @Test
    public void testStructureImageAccessorErrorBranchForInvalidSmiles() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("not_a_valid_smiles", "Invalid", new HashMap<>());
        Assertions.assertNotNull(tmpMolecule.getStructure());
        Assertions.assertNotNull(tmpMolecule.getStructureWithText("caption"));
    }
    //
    /**
     * Tests the {@code 0.0 ⇒ default} branch and the positive round-trip of the structure-image height and width
     * accessors: setting 0.0 yields the non-zero default, and setting a positive value yields that value back.
     */
    @Test
    public void testStructureImageSizeDefaultsAndRoundTrip() {
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", new HashMap<>());
        tmpMolecule.setStructureImageHeight(0.0);
        tmpMolecule.setStructureImageWidth(0.0);
        double tmpDefaultHeight = tmpMolecule.getStructureImageHeight();
        double tmpDefaultWidth = tmpMolecule.getStructureImageWidth();
        Assertions.assertTrue(tmpDefaultHeight > 0.0);
        Assertions.assertTrue(tmpDefaultWidth > 0.0);
        tmpMolecule.setStructureImageHeight(123.0);
        tmpMolecule.setStructureImageWidth(456.0);
        Assertions.assertEquals(123.0, tmpMolecule.getStructureImageHeight());
        Assertions.assertEquals(456.0, tmpMolecule.getStructureImageWidth());
    }
    //
    /**
     * Tests that {@code getProperties()} returns the same property map instance supplied to the string constructor.
     */
    @Test
    public void testGetPropertiesReturnsSuppliedMap() {
        Map<Object, Object> tmpProperties = new HashMap<>();
        tmpProperties.put("key", "value");
        MoleculeDataModel tmpMolecule = new MoleculeDataModel("c1ccccc1", "Benzene", tmpProperties);
        Assertions.assertSame(tmpProperties, tmpMolecule.getProperties());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Parses the given SMILES into a raw CDK atom container using a silent builder, so callers can both wrap it in a
     * {@link MoleculeDataModel} and independently compute {@code ChemUtil.createUniqueSmiles(ac, false)} on it.
     *
     * @param aSmiles SMILES string to parse
     * @return the parsed atom container
     * @throws Exception if SMILES parsing fails
     */
    private static IAtomContainer buildAtomContainer(String aSmiles) throws Exception {
        SmilesParser tmpParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        return tmpParser.parseSmiles(aSmiles);
    }
    //</editor-fold>
}
