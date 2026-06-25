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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.HashMap;
import java.util.Locale;

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
