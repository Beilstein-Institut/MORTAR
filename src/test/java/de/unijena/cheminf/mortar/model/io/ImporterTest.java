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

package de.unijena.cheminf.mortar.model.io;

import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.settings.SettingsContainer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openscience.cdk.AtomContainerSet;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Tests some functionalities of the {@link Importer} class.
 *
 * @author Jonas Schaub
 */
public class ImporterTest extends Importer {
    //<editor-fold desc="static initializer">
    /**
     * Sets the default locale to British English.
     */
    static {
        Locale.setDefault(Locale.of("en", "GB"));
    }
    //</editor-fold>
    //
    // <editor-fold desc="constructor">
    /**
     * Default constructor for the ImporterTest class.
     */
    public ImporterTest() {
        super(new SettingsContainer());
    }
    //</editor-fold>
    //
    // <editor-fold desc="test methods">
    /**
     * Test importing a MOL file containing a molecules with radicals and testing whether these are fixed correctly.
     * This is basically an integration test for the ChemUtil functionality used internally by the Importer.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testHydrogenSaturationOnMOLfile() throws Exception {
        URL tmpURL = this.getClass().getResource("Mirabilin_B.mol");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        MDLV2000Reader tmpReader = new MDLV2000Reader(new FileReader(tmpResourceFile));
        IAtomContainer tmpMolecule = tmpReader.read(SilentChemObjectBuilder.getInstance().newAtomContainer());
        tmpReader.close();
        IAtomContainerSet tmpSet = new AtomContainerSet();
        tmpSet.addAtomContainer(tmpMolecule);
        this.preprocessMoleculeSet(tmpSet, true);
        SmilesGenerator smiGen = new SmilesGenerator(SmiFlavor.Canonical);
        Assertions.assertEquals("N=C1N=C2C3=C(N1)CCC3CC(C)C2CCCC", smiGen.create(tmpMolecule));
    }
    /**
     * Test importing a SMILES string containing a molecule with some explicit incomplete valences and testing whether
     * these are fixed correctly.
     * This is NOT an integration test for underlying ChemUtil functionalities because these explicit valences do not
     * create single electrons in the generated atom container. Hence, the importer method that saturates these "simple"
     * open valences is tested directly.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testHydrogenSaturationOnSMILES() throws Exception {
        SmilesParser tmpSmiPar = new SmilesParser(SilentChemObjectBuilder.getInstance());
        IAtomContainer tmpMolecule = tmpSmiPar.parseSmiles("[CH2]CCCC([CH])CCC");
        IAtomContainerSet tmpSet = new AtomContainerSet();
        tmpSet.addAtomContainer(tmpMolecule);
        this.preprocessMoleculeSet(tmpSet, true);
        SmilesGenerator smiGen = new SmilesGenerator(SmiFlavor.Canonical);
        Assertions.assertEquals("CCCCC(C)CCC", smiGen.create(tmpMolecule));
    }
    /**
     * Tests the end-to-end import dispatch for a SMILES (.smi) file. Loading {@code SMILESTestFileTwo.smi} and importing
     * it through {@link Importer#importMoleculeFile(File, boolean, boolean)} exercises the extension dispatch to
     * {@code importSMILESFile} as well as {@code parse}, {@code findMoleculeName}, and {@code getFileName} transitively.
     * The fixture contains five SMILES codes, so a list with five molecules is expected and the stored file name must
     * match the imported file's base name.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithSMILESFile() throws Exception {
        URL tmpURL = this.getClass().getResource("SMILESTestFileTwo.smi");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(5, tmpResultList.size());
        Assertions.assertEquals("SMILESTestFileTwo.smi", this.getFileName());
        for (MoleculeDataModel tmpMolecule : tmpResultList) {
            Assertions.assertNotNull(tmpMolecule.getUniqueSmiles());
            Assertions.assertFalse(tmpMolecule.getUniqueSmiles().isBlank());
        }
    }
    /**
     * Tests the end-to-end import dispatch for a single MOL (.mol) file. Loading {@code Mirabilin_B.mol} and importing it
     * through {@link Importer#importMoleculeFile(File, boolean, boolean)} exercises the extension dispatch to
     * {@code importMolFile}. A single molecule with a non-null, non-blank unique SMILES is expected.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithMOLFile() throws Exception {
        URL tmpURL = this.getClass().getResource("Mirabilin_B.mol");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(1, tmpResultList.size());
        Assertions.assertNotNull(tmpResultList.get(0).getUniqueSmiles());
        Assertions.assertFalse(tmpResultList.get(0).getUniqueSmiles().isBlank());
        Assertions.assertEquals("Mirabilin_B.mol", this.getFileName());
    }
    /**
     * Tests the end-to-end import dispatch for a multi-record SD (.sdf) file. Loading {@code MultiRecord.sdf} and
     * importing it through {@link Importer#importMoleculeFile(File, boolean, boolean)} exercises the extension dispatch
     * to {@code importSDFile}, the iterating SDF reader, and the molecule-name fallback for records lacking a title. The
     * fixture contains three valid records, so a list with three molecules is expected.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithSDFile() throws Exception {
        URL tmpURL = this.getClass().getResource("MultiRecord.sdf");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(3, tmpResultList.size());
        Assertions.assertEquals("MultiRecord.sdf", this.getFileName());
        for (MoleculeDataModel tmpMolecule : tmpResultList) {
            Assertions.assertNotNull(tmpMolecule.getName());
            Assertions.assertFalse(tmpMolecule.getName().isBlank());
        }
    }
    /**
     * Tests that importing a file with an unsupported extension returns null. This exercises the
     * {@code tmpInputFileType == null} branch in {@link Importer#importMoleculeFile(File, boolean, boolean)} that is
     * reached when the file extension does not match any of the valid import file types.
     *
     * @param aTempDir temporary directory provided by JUnit; auto-deleted after the test
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithUnknownExtensionReturnsNull(@TempDir Path aTempDir) throws Exception {
        Path tmpUnknownFilePath = aTempDir.resolve("unsupported.xyz");
        Files.writeString(tmpUnknownFilePath, "C\n");
        File tmpUnknownFile = tmpUnknownFilePath.toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpUnknownFile, false, true);
        Assertions.assertNull(tmpResultList);
    }
    /**
     * Tests the else-branch of {@link Importer#preprocessMoleculeSet(IAtomContainerSet, boolean)} that is reached when
     * {@code isFillOpenValencesWithImplH} is false. In this case open valences are not saturated; instead unset implicit
     * hydrogen counts are set to zero. The existing preprocessing tests only cover the {@code true} path. The method must
     * complete without throwing and leave the molecule in the set.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testPreprocessMoleculeSetWithoutFillingOpenValences() throws Exception {
        SmilesParser tmpSmiPar = new SmilesParser(SilentChemObjectBuilder.getInstance());
        IAtomContainer tmpMolecule = tmpSmiPar.parseSmiles("[CH2]CCCC([CH])CCC");
        IAtomContainerSet tmpSet = new AtomContainerSet();
        tmpSet.addAtomContainer(tmpMolecule);
        Assertions.assertDoesNotThrow(() -> this.preprocessMoleculeSet(tmpSet, false));
        Assertions.assertEquals(1, tmpSet.getAtomContainerCount());
    }
    //</editor-fold>
}
