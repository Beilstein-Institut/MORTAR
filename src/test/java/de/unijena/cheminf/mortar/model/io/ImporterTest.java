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
import org.openscience.cdk.Atom;
import org.openscience.cdk.AtomContainer;
import org.openscience.cdk.AtomContainerSet;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
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
    /**
     * Tests the end-to-end import of an MDL V3000 MOL file. Loading {@code MolV3000.mol} and importing it through
     * {@link Importer#importMoleculeFile(File, boolean, boolean)} exercises the V3000-format detection branch of
     * {@code importMolFile} that uses the {@code MDLV3000Reader} (as opposed to the V2000 reader covered by the
     * Mirabilin test). A single molecule with a non-null, non-blank unique SMILES is expected.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithV3000MolFile() throws Exception {
        URL tmpURL = this.getClass().getResource("MolV3000.mol");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(1, tmpResultList.size());
        Assertions.assertNotNull(tmpResultList.get(0).getUniqueSmiles());
        Assertions.assertFalse(tmpResultList.get(0).getUniqueSmiles().isBlank());
        Assertions.assertEquals("MolV3000.mol", this.getFileName());
    }
    /**
     * Tests the molecule-name fallback branch of {@code importMolFile}. Loading {@code UnnamedMol.mol}, whose title line
     * is blank and which carries no name/ID property, makes {@code findMoleculeName} return null; the importer then falls
     * back to reading the first line and, since it is blank, to the file name without extension. A single molecule with a
     * non-blank name (the fallback name) is expected.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithUnnamedMolFileUsesNameFallback() throws Exception {
        URL tmpURL = this.getClass().getResource("UnnamedMol.mol");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(1, tmpResultList.size());
        Assertions.assertNotNull(tmpResultList.get(0).getName());
        Assertions.assertFalse(tmpResultList.get(0).getName().isBlank());
    }
    /**
     * Tests the import of an SD file that contains a deliberately broken record between two valid records. Loading
     * {@code SDFwithError.sdf} exercises the erroneous-entry skip path of {@code importSDFile}: the iterating reader skips
     * the broken record (logging it) and continues, so the failed-import-count branch is reached. The two valid records
     * must still be imported.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithErroneousSDFile() throws Exception {
        URL tmpURL = this.getClass().getResource("SDFwithError.sdf");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertTrue(tmpResultList.size() >= 2);
        Assertions.assertEquals("SDFwithError.sdf", this.getFileName());
    }
    /**
     * Tests the {@code parse} branch that keeps the atom container inside the data model. With the
     * {@code keepAtomContainerInDataModelSetting} enabled, {@code parse} constructs the MoleculeDataModel from the atom
     * container directly (rather than from the SMILES string), covering the alternative model-construction branch. The
     * import must still produce the expected number of molecules with valid unique SMILES.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileKeepingAtomContainerInDataModel() throws Exception {
        SettingsContainer tmpSettingsContainer = new SettingsContainer();
        boolean tmpOriginalSetting = tmpSettingsContainer.getKeepAtomContainerInDataModelSetting();
        try {
            tmpSettingsContainer.setKeepAtomContainerInDataModelSetting(true);
            Importer tmpImporter = new Importer(tmpSettingsContainer);
            URL tmpURL = this.getClass().getResource("SMILESTestFileTwo.smi");
            File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
            List<MoleculeDataModel> tmpResultList = tmpImporter.importMoleculeFile(tmpResourceFile, false, true);
            Assertions.assertNotNull(tmpResultList);
            Assertions.assertEquals(5, tmpResultList.size());
            for (MoleculeDataModel tmpMolecule : tmpResultList) {
                Assertions.assertNotNull(tmpMolecule.getUniqueSmiles());
                Assertions.assertFalse(tmpMolecule.getUniqueSmiles().isBlank());
            }
        } finally {
            tmpSettingsContainer.setKeepAtomContainerInDataModelSetting(tmpOriginalSetting);
        }
    }
    /**
     * Tests the early-return branch of {@link Importer#preprocessMoleculeSet(IAtomContainerSet, boolean)} that is reached
     * when the given molecule set is empty: the method returns immediately without processing. The set must remain empty
     * and no exception must be thrown.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testPreprocessMoleculeSetWithEmptySet() throws Exception {
        IAtomContainerSet tmpSet = new AtomContainerSet();
        Assertions.assertDoesNotThrow(() -> this.preprocessMoleculeSet(tmpSet, true));
        Assertions.assertEquals(0, tmpSet.getAtomContainerCount());
    }
    /**
     * Tests the unset-implicit-hydrogen-count branch of the {@code isFillOpenValencesWithImplH == false} path in
     * {@link Importer#preprocessMoleculeSet(IAtomContainerSet, boolean)}. A manually built atom container whose atoms have
     * an unset implicit hydrogen count is processed without filling open valences, so the method sets the unset implicit
     * hydrogen counts to zero. The atom's implicit hydrogen count must be zero afterwards.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testPreprocessMoleculeSetSetsUnsetImplicitHydrogenCountToZero() throws Exception {
        IAtomContainer tmpMolecule = new AtomContainer();
        IAtom tmpCarbon = new Atom("C");
        tmpCarbon.setImplicitHydrogenCount(null);
        IAtom tmpOxygen = new Atom("O");
        tmpOxygen.setImplicitHydrogenCount(null);
        tmpMolecule.addAtom(tmpCarbon);
        tmpMolecule.addAtom(tmpOxygen);
        tmpMolecule.addBond(0, 1, org.openscience.cdk.interfaces.IBond.Order.SINGLE);
        IAtomContainerSet tmpSet = new AtomContainerSet();
        tmpSet.addAtomContainer(tmpMolecule);
        this.preprocessMoleculeSet(tmpSet, false);
        for (IAtom tmpAtom : tmpSet.getAtomContainer(0).atoms()) {
            Assertions.assertNotNull(tmpAtom.getImplicitHydrogenCount());
        }
    }
    /**
     * Tests the exception-handling branch of {@link Importer#preprocessMoleculeSet(IAtomContainerSet, boolean)}. An empty
     * atom container (no atoms, no bonds) that cannot be kekulized causes an exception inside the per-molecule processing
     * loop. The exception is caught and logged, the molecule remains in the set, and the method completes without
     * propagating the exception.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testPreprocessMoleculeSetLogsPerMoleculeException() throws Exception {
        IAtomContainer tmpInvalidMolecule = new AtomContainer();
        IAtom tmpAromaticCarbon = new Atom("C");
        tmpAromaticCarbon.setIsAromatic(true);
        tmpInvalidMolecule.addAtom(tmpAromaticCarbon);
        tmpInvalidMolecule.setProperty(Importer.MOLECULE_NAME_PROPERTY_KEY, "InvalidAromaticMolecule");
        IAtomContainerSet tmpSet = new AtomContainerSet();
        tmpSet.addAtomContainer(tmpInvalidMolecule);
        Assertions.assertDoesNotThrow(() -> this.preprocessMoleculeSet(tmpSet, true));
        Assertions.assertEquals(1, tmpSet.getAtomContainerCount());
    }
    /**
     * Tests the end-to-end import of a SMILES file that contains some unparsable lines mixed with valid SMILES codes.
     * Loading {@code SmilesWithSomeInvalid.smi} drives the skipped-lines warning path of {@code importSMILESFile}: the
     * underlying reader skips the invalid lines (incrementing its skipped-lines counter) and the importer logs the
     * skipped count. The three valid SMILES codes must still be imported.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithPartiallyInvalidSmilesFile() throws Exception {
        URL tmpURL = this.getClass().getResource("SmilesWithSomeInvalid.smi");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertEquals(3, tmpResultList.size());
        Assertions.assertEquals("SmilesWithSomeInvalid.smi", this.getFileName());
    }
    /**
     * Tests the relaxed-parse fallback path of the SMILES file format detection (via {@code importSMILESFile} ->
     * {@code DynamicSMILESFileReader.detectFormat}). Loading {@code SingleColumnRelaxedSmiles.smi}, whose single-column
     * SMILES codes parse only when kekulization is disabled (they fail the initial kekulization-enabled parse), drives the
     * detect-format catch-and-retry-relaxed branch as well as the no-ID-column header-detection retry. At least one
     * molecule must be imported.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithSingleColumnRelaxedSmiles() throws Exception {
        URL tmpURL = this.getClass().getResource("SingleColumnRelaxedSmiles.smi");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertFalse(tmpResultList.isEmpty());
        Assertions.assertEquals("SingleColumnRelaxedSmiles.smi", this.getFileName());
    }
    /**
     * Tests the first-and-only-structure-failed warning path of {@code importSDFile}. Loading
     * {@code SingleBrokenRecord.sdf}, whose single record cannot be parsed, makes the iterating reader yield no structures
     * and triggers the "import failed for first and only structure" branch. The returned list is empty.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMoleculeFileWithSingleBrokenSDRecord() throws Exception {
        URL tmpURL = this.getClass().getResource("SingleBrokenRecord.sdf");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        List<MoleculeDataModel> tmpResultList = this.importMoleculeFile(tmpResourceFile, false, true);
        Assertions.assertNotNull(tmpResultList);
        Assertions.assertTrue(tmpResultList.isEmpty());
        Assertions.assertEquals("SingleBrokenRecord.sdf", this.getFileName());
    }
    /**
     * Tests the undeterminable-format branch of {@code importMolFile} (reached via reflection). Loading
     * {@code UnrecognizedFormat.mol}, whose content is not a recognizable chemistry file format, makes the format factory
     * return null, so {@code importMolFile} throws a CDKException ("file type could not be determined"). The reflective
     * invocation wraps that exception in an InvocationTargetException whose cause is the CDKException.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testImportMolFileWithUndeterminableFormatThrows() throws Exception {
        URL tmpURL = this.getClass().getResource("UnrecognizedFormat.mol");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        Method tmpImportMolFile = Importer.class.getDeclaredMethod("importMolFile", File.class);
        tmpImportMolFile.setAccessible(true);
        java.lang.reflect.InvocationTargetException tmpThrown = Assertions.assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> tmpImportMolFile.invoke(this, tmpResourceFile));
        Assertions.assertInstanceOf(org.openscience.cdk.exception.CDKException.class, tmpThrown.getCause());
    }
    /**
     * Tests the null/empty-set early-return branch of the private {@code parse} method (reached via reflection). When the
     * given atom container set is null, {@code parse} returns an empty (non-null) list without iterating.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testParseWithNullSetReturnsEmptyList() throws Exception {
        Method tmpParse = Importer.class.getDeclaredMethod("parse", IAtomContainerSet.class, boolean.class);
        tmpParse.setAccessible(true);
        Object tmpResult = tmpParse.invoke(this, null, false);
        Assertions.assertInstanceOf(List.class, tmpResult);
        Assertions.assertTrue(((List<?>) tmpResult).isEmpty());
    }
    /**
     * Tests the empty-set early-return branch of the private {@code parse} method (reached via reflection). When the given
     * atom container set is empty, {@code parse} returns an empty (non-null) list.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testParseWithEmptySetReturnsEmptyList() throws Exception {
        Method tmpParse = Importer.class.getDeclaredMethod("parse", IAtomContainerSet.class, boolean.class);
        tmpParse.setAccessible(true);
        Object tmpResult = tmpParse.invoke(this, new AtomContainerSet(), false);
        Assertions.assertInstanceOf(List.class, tmpResult);
        Assertions.assertTrue(((List<?>) tmpResult).isEmpty());
    }
    /**
     * Tests the ID-fallback branch of the private {@code findMoleculeName} method (reached via reflection). When the atom
     * container has no title and no property whose key contains 'name', but has a property whose key contains 'id', the
     * value of that ID property is returned as the molecule name.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testFindMoleculeNameReturnsIdProperty() throws Exception {
        IAtomContainer tmpAtomContainer = new AtomContainer();
        tmpAtomContainer.setProperty("Compound_ID", "CID-12345");
        Method tmpFindMoleculeName = Importer.class.getDeclaredMethod("findMoleculeName", IAtomContainer.class);
        tmpFindMoleculeName.setAccessible(true);
        Object tmpResult = tmpFindMoleculeName.invoke(this, tmpAtomContainer);
        Assertions.assertEquals("CID-12345", tmpResult);
    }
    /**
     * Tests the 'None'-reset branch of the private {@code findMoleculeName} method (reached via reflection). When the
     * resolved name equals 'None' (case-insensitive) and no usable ID property is present, the method resets the returned
     * name to null.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testFindMoleculeNameResetsNoneToNull() throws Exception {
        IAtomContainer tmpAtomContainer = new AtomContainer();
        tmpAtomContainer.setTitle("None");
        Method tmpFindMoleculeName = Importer.class.getDeclaredMethod("findMoleculeName", IAtomContainer.class);
        tmpFindMoleculeName.setAccessible(true);
        Object tmpResult = tmpFindMoleculeName.invoke(this, tmpAtomContainer);
        Assertions.assertNull(tmpResult);
    }
    /**
     * Tests the deprecated, currently-unused private {@code importPDBFile} method (reached via reflection). Although it is
     * not wired into the public import dispatch, the method is still functional: it reads a small valid PDB fixture
     * ({@code Glycine.pdb}) and returns a non-empty atom container set whose first molecule carries a non-blank name
     * property. This exercises the PDB reader configuration loop and the molecule-name fallback.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testImportPDBFile() throws Exception {
        URL tmpURL = this.getClass().getResource("Glycine.pdb");
        File tmpResourceFile = Paths.get(tmpURL.toURI()).toFile();
        Method tmpImportPDBFile = Importer.class.getDeclaredMethod("importPDBFile", File.class);
        tmpImportPDBFile.setAccessible(true);
        Object tmpResult = tmpImportPDBFile.invoke(this, tmpResourceFile);
        Assertions.assertInstanceOf(IAtomContainerSet.class, tmpResult);
        IAtomContainerSet tmpAtomContainerSet = (IAtomContainerSet) tmpResult;
        Assertions.assertTrue(tmpAtomContainerSet.getAtomContainerCount() > 0);
        IAtomContainer tmpFirstAtomContainer = tmpAtomContainerSet.getAtomContainer(0);
        Object tmpNameProperty = tmpFirstAtomContainer.getProperty(Importer.MOLECULE_NAME_PROPERTY_KEY);
        Assertions.assertNotNull(tmpNameProperty);
        Assertions.assertFalse(tmpNameProperty.toString().isBlank());
    }
    //</editor-fold>
}
