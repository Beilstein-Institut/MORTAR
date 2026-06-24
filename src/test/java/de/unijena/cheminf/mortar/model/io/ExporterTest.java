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

import de.unijena.cheminf.mortar.controller.TabNames;
import de.unijena.cheminf.mortar.message.Message;
import de.unijena.cheminf.mortar.model.data.FragmentDataModel;
import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.settings.SettingsContainer;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tests the file-taking export methods of the {@link Exporter} class directly against a temporary output directory and
 * real CDK-derived data models. The GUI file-chooser methods ({@code openFileChooserForExportFileOrDir},
 * {@code chooseFile}, {@code chooseDirectory}) are intentionally left uncovered (no Stage in a unit test). The PDF
 * export path is exercised headlessly (CDK depiction renders to an AWT BufferedImage; JavaFX appears only as a thin
 * pixel-copy round trip), asserted by magic bytes only. The en-GB locale guard is load-bearing because the CSV/PDF
 * headers come from {@link Message}.
 *
 * @author Felix Baensch
 */
public class ExporterTest {
    //<editor-fold desc="static initializer">
    /**
     * Sets the default locale to British English so the {@link Message}-resolved CSV/PDF headers are deterministic.
     */
    static {
        Locale.setDefault(Locale.of("en", "GB"));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private final variables" defaultstate="collapsed">
    /**
     * Exporter instance under test, constructed with a real settings container.
     */
    private final Exporter exporter;
    //</editor-fold>
    //
    //<editor-fold desc="Constructor">
    /**
     * Constructor. Re-asserts the en-GB locale (load-bearing for the golden header assertions) and builds the Exporter
     * with a real, classpath-configured SettingsContainer.
     */
    public ExporterTest() {
        Locale.setDefault(Locale.of("en", "GB"));
        this.exporter = new Exporter(new SettingsContainer());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Smoke test (written and run FIRST): a one-fragment FRAGMENTS-tab PDF export to a temporary file must succeed
     * headlessly and produce a file whose first four bytes are the {@code %PDF} magic bytes. This validates the research
     * headless verdict (A1) that the CDK depiction + OpenPDF render path needs no live JavaFX toolkit. If this throws a
     * headless/toolkit error, the PDF coverage path is not reachable and the rest of the PDF work must stop.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportPdfFileFragmentsTabSmoke(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        ObservableList<MoleculeDataModel> tmpMolecules = FXCollections.observableArrayList(tmpFragments);
        File tmpOut = aTempDir.resolve("smoke.pdf").toFile();
        List<String> tmpFailed = this.exporter.exportPdfFile(
                tmpOut, tmpFragments, tmpMolecules, "ErtlFG", "input.smi", TabNames.FRAGMENTS);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        Assertions.assertTrue(tmpOut.length() > 0);
        byte[] tmpHead = Arrays.copyOf(Files.readAllBytes(tmpOut.toPath()), 4);
        Assertions.assertEquals("%PDF", new String(tmpHead, StandardCharsets.US_ASCII));
    }
    //
    /**
     * Tests that exporting the FRAGMENTS tab as a CSV file with a list of real FragmentDataModel instances succeeds
     * (empty failed-list), the file content starts with the en-GB golden header built from the five fragmentation-tab
     * Message keys, and the chosen separator character is present.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportCsvFileFragmentsTab(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        File tmpOut = aTempDir.resolve("frags.csv").toFile();
        List<String> tmpFailed = this.exporter.exportCsvFile(tmpOut, tmpFragments, "ErtlFG", ',', TabNames.FRAGMENTS);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        String tmpContent = Files.readString(tmpOut.toPath());
        String tmpExpectedHeader =
                Message.get("Exporter.fragmentationTab.csvHeader.smiles") + ',' +
                Message.get("Exporter.fragmentationTab.csvHeader.frequency") + ',' +
                Message.get("Exporter.fragmentationTab.csvHeader.percentage") + ',' +
                Message.get("Exporter.fragmentationTab.csvHeader.moleculeFrequency") + ',' +
                Message.get("Exporter.fragmentationTab.csvHeader.moleculePercentage");
        Assertions.assertTrue(tmpContent.startsWith(tmpExpectedHeader));
        Assertions.assertTrue(tmpContent.contains(","));
    }
    //
    /**
     * Tests that exporting the ITEMIZATION tab as a CSV file with a parent molecule carrying a populated fragments map
     * and frequency map succeeds (empty failed-list) and the file content starts with the four-column en-GB itemization
     * header.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportCsvFileItemizationTab(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpMolecules = new ArrayList<>();
        tmpMolecules.add(ExporterTest.buildMoleculeWithFragments("ErtlFG"));
        File tmpOut = aTempDir.resolve("items.csv").toFile();
        List<String> tmpFailed = this.exporter.exportCsvFile(tmpOut, tmpMolecules, "ErtlFG", ',', TabNames.ITEMIZATION);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        String tmpContent = Files.readString(tmpOut.toPath());
        String tmpExpectedHeader =
                Message.get("Exporter.itemsTab.csvHeader.moleculeName") + ',' +
                Message.get("Exporter.itemsTab.csvHeader.smilesOfStructure") + ',' +
                Message.get("Exporter.itemsTab.csvHeader.smilesOfFragment") + ',' +
                Message.get("Exporter.itemsTab.csvHeader.frequencyOfFragment");
        Assertions.assertTrue(tmpContent.startsWith(tmpExpectedHeader));
    }
    //
    /**
     * Tests the wrong-type guard: passing a plain MoleculeDataModel (not a FragmentDataModel) into the FRAGMENTS-tab
     * CSV export triggers the internal cast failure so the molecule's SMILES lands in the returned failed-export list
     * (non-empty).
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportCsvFileFragmentsTabWrongTypeGuard(@TempDir Path aTempDir) throws Exception {
        SmilesParser tmpParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        IAtomContainer tmpAtomContainer = tmpParser.parseSmiles("CCO");
        MoleculeDataModel tmpPlainMolecule = new MoleculeDataModel(tmpAtomContainer, false);
        List<MoleculeDataModel> tmpList = new ArrayList<>();
        tmpList.add(tmpPlainMolecule);
        File tmpOut = aTempDir.resolve("wrongtype.csv").toFile();
        List<String> tmpFailed = this.exporter.exportCsvFile(tmpOut, tmpList, "ErtlFG", ',', TabNames.FRAGMENTS);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertFalse(tmpFailed.isEmpty());
    }
    //
    /**
     * Tests that a single SD-file export of a list of FragmentDataModel instances (with 2D-coordinate generation
     * enabled, the common case for SMILES-derived fragments) succeeds with an empty failed-export list and writes a
     * file whose content contains the MDL record terminator {@code $$$$}, proving a valid SDF was written.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFileSingleSdfGenerate2d(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        File tmpOut = aTempDir.resolve("single.sdf").toFile();
        List<String> tmpFailed = this.exporter.exportFragmentsAsChemicalFile(
                tmpOut, tmpFragments, ChemFileTypes.SDF, true, true);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        Assertions.assertTrue(tmpOut.length() > 0);
        String tmpContent = Files.readString(tmpOut.toPath());
        Assertions.assertTrue(tmpContent.contains("$$$$"));
        int tmpRecordTerminatorCount =
                tmpContent.split("\\$\\$\\$\\$", -1).length - 1;
        Assertions.assertEquals(tmpFragments.size(), tmpRecordTerminatorCount);
    }
    //
    /**
     * Tests the single SD-file export with 2D-coordinate generation disabled, driving the zero-3D-coordinate branch of
     * {@code handleFragmentWithNo3dInformationAvailable}. The export still succeeds (empty failed-list) and writes a
     * non-empty file containing the MDL record terminator {@code $$$$}.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFileSingleSdfNoGenerate2d(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        File tmpOut = aTempDir.resolve("single_no2d.sdf").toFile();
        List<String> tmpFailed = this.exporter.exportFragmentsAsChemicalFile(
                tmpOut, tmpFragments, ChemFileTypes.SDF, false, true);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        Assertions.assertTrue(tmpOut.length() > 0);
        Assertions.assertTrue(Files.readString(tmpOut.toPath()).contains("$$$$"));
    }
    //
    /**
     * Tests the single SD-file export with the {@code alwaysMDLV3000FormatAtExport} setting enabled, exercising the
     * {@code SDFWriter.setAlwaysV3000(true)} branch. A local SettingsContainer and Exporter are used so the global
     * instance under test is not mutated; the setting is restored in a {@code finally} block (FileUtilTest idiom). The
     * file is written and contains the V3000 connection-table marker.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFileSingleSdfV3000(@TempDir Path aTempDir) throws Exception {
        SettingsContainer tmpContainer = new SettingsContainer();
        boolean tmpOriginalSetting = tmpContainer.getAlwaysMDLV3000FormatAtExportSetting();
        try {
            tmpContainer.setAlwaysMDLV3000FormatAtExportSetting(true);
            Exporter tmpLocalExporter = new Exporter(tmpContainer);
            List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
            File tmpOut = aTempDir.resolve("single_v3000.sdf").toFile();
            List<String> tmpFailed = tmpLocalExporter.exportFragmentsAsChemicalFile(
                    tmpOut, tmpFragments, ChemFileTypes.SDF, true, true);
            Assertions.assertNotNull(tmpFailed);
            Assertions.assertTrue(tmpFailed.isEmpty());
            Assertions.assertTrue(tmpOut.length() > 0);
            String tmpContent = Files.readString(tmpOut.toPath());
            Assertions.assertTrue(tmpContent.contains("$$$$"));
            Assertions.assertTrue(tmpContent.contains("V3000"));
        } finally {
            tmpContainer.setAlwaysMDLV3000FormatAtExportSetting(tmpOriginalSetting);
        }
    }
    //
    /**
     * Tests the separate SD-files export ({@code isSingleExport=false}): the method creates a sub-directory inside the
     * passed @TempDir directory and writes one {@code .sdf} file per fragment into it. Asserts an empty failed-list, that
     * a sub-directory was created, and that it contains at least one {@code .sdf} file whose content carries the MDL
     * record terminator {@code $$$$}.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFileSeparateSdf(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        File tmpDir = aTempDir.toFile();
        List<String> tmpFailed = this.exporter.exportFragmentsAsChemicalFile(
                tmpDir, tmpFragments, ChemFileTypes.SDF, true, false);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        File[] tmpSubDirs = tmpDir.listFiles(File::isDirectory);
        Assertions.assertNotNull(tmpSubDirs);
        Assertions.assertEquals(1, tmpSubDirs.length);
        File[] tmpSdfFiles = tmpSubDirs[0].listFiles((aDir, aName) -> aName.endsWith(".sdf"));
        Assertions.assertNotNull(tmpSdfFiles);
        Assertions.assertTrue(tmpSdfFiles.length > 0);
        Assertions.assertTrue(Files.readString(tmpSdfFiles[0].toPath()).contains("$$$$"));
    }
    //
    /**
     * Tests the PDB export: the method creates a sub-directory inside the passed @TempDir directory and writes one
     * {@code .pdb} file per fragment into it. Asserts an empty failed-list, that a sub-directory was created, and that it
     * contains at least one non-empty {@code .pdb} file.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFilePdb(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        File tmpDir = aTempDir.toFile();
        List<String> tmpFailed = this.exporter.exportFragmentsAsChemicalFile(
                tmpDir, tmpFragments, ChemFileTypes.PDB, true);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        File[] tmpSubDirs = tmpDir.listFiles(File::isDirectory);
        Assertions.assertNotNull(tmpSubDirs);
        Assertions.assertEquals(1, tmpSubDirs.length);
        File[] tmpPdbFiles = tmpSubDirs[0].listFiles((aDir, aName) -> aName.endsWith(".pdb"));
        Assertions.assertNotNull(tmpPdbFiles);
        Assertions.assertTrue(tmpPdbFiles.length > 0);
        Assertions.assertTrue(tmpPdbFiles[0].length() > 0);
    }
    //
    /**
     * Tests the null-file guard of {@code exportFragmentsAsChemicalFile}: passing a {@code null} file returns
     * {@code null} (no file written).
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportFragmentsAsChemicalFileNullFileGuard() throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        List<String> tmpFailed = this.exporter.exportFragmentsAsChemicalFile(
                null, tmpFragments, ChemFileTypes.SDF, true, true);
        Assertions.assertNull(tmpFailed);
    }
    //
    /**
     * Tests the full FRAGMENTS-tab PDF export with multiple real FragmentDataModel instances (a fuller variant of the
     * headless smoke test). Asserts an empty failed-list, a non-empty file, and the {@code %PDF} magic bytes.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportPdfFileFragmentsTabFull(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        ObservableList<MoleculeDataModel> tmpMolecules = FXCollections.observableArrayList(tmpFragments);
        File tmpOut = aTempDir.resolve("fragments_full.pdf").toFile();
        List<String> tmpFailed = this.exporter.exportPdfFile(
                tmpOut, tmpFragments, tmpMolecules, "ErtlFG", "input.smi", TabNames.FRAGMENTS);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        Assertions.assertTrue(tmpOut.length() > 0);
        byte[] tmpHead = Arrays.copyOf(Files.readAllBytes(tmpOut.toPath()), 4);
        Assertions.assertEquals("%PDF", new String(tmpHead, StandardCharsets.US_ASCII));
    }
    //
    /**
     * Tests the ITEMIZATION-tab PDF export, the largest single Exporter block. A non-empty observable molecule list
     * (a parent molecule with attached fragments), a non-empty fragmentation name, and a non-empty imported-file name
     * are all required (the three guards return {@code null} if any is empty). Asserts an empty failed-list and the
     * {@code %PDF} magic bytes.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportPdfFileItemizationTab(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        ObservableList<MoleculeDataModel> tmpMolecules =
                FXCollections.observableArrayList(ExporterTest.buildMoleculeWithFragments("ErtlFG"));
        File tmpOut = aTempDir.resolve("items.pdf").toFile();
        List<String> tmpFailed = this.exporter.exportPdfFile(
                tmpOut, tmpFragments, tmpMolecules, "ErtlFG", "input.smi", TabNames.ITEMIZATION);
        Assertions.assertNotNull(tmpFailed);
        Assertions.assertTrue(tmpFailed.isEmpty());
        Assertions.assertTrue(tmpOut.length() > 0);
        byte[] tmpHead = Arrays.copyOf(Files.readAllBytes(tmpOut.toPath()), 4);
        Assertions.assertEquals("%PDF", new String(tmpHead, StandardCharsets.US_ASCII));
    }
    //
    /**
     * Tests the ITEMIZATION-tab PDF null-guard: passing an empty imported-file name makes the export return
     * {@code null} (no file written), per the empty-name guard of {@code createItemizationTabPdfFile}.
     *
     * @param aTempDir per-test temporary directory (auto-deleted)
     * @throws Exception if anything goes wrong
     */
    @Test
    public void testExportPdfFileItemizationTabEmptyNameGuard(@TempDir Path aTempDir) throws Exception {
        List<MoleculeDataModel> tmpFragments = ExporterTest.buildFragmentList();
        ObservableList<MoleculeDataModel> tmpMolecules =
                FXCollections.observableArrayList(ExporterTest.buildMoleculeWithFragments("ErtlFG"));
        File tmpOut = aTempDir.resolve("items_guard.pdf").toFile();
        List<String> tmpFailed = this.exporter.exportPdfFile(
                tmpOut, tmpFragments, tmpMolecules, "ErtlFG", "", TabNames.ITEMIZATION);
        Assertions.assertNull(tmpFailed);
    }
    //
    /**
     * Tests that the nested enums of the Exporter ({@code ExportTypes}, {@code FileExtension}, {@code CSVSeparator})
     * load and expose non-null accessors, loading their static initializers for coverage.
     */
    @Test
    public void testNestedEnumsSmoke() {
        Assertions.assertTrue(Exporter.ExportTypes.values().length > 0);
        for (Exporter.ExportTypes tmpType : Exporter.ExportTypes.values()) {
            Assertions.assertNotNull(tmpType.name());
        }
        Assertions.assertTrue(Exporter.FileExtension.values().length > 0);
        for (Exporter.FileExtension tmpExtension : Exporter.FileExtension.values()) {
            Assertions.assertNotNull(tmpExtension.toString());
        }
        Assertions.assertTrue(Exporter.CSVSeparator.values().length > 0);
        for (Exporter.CSVSeparator tmpSeparator : Exporter.CSVSeparator.values()) {
            Assertions.assertNotNull(tmpSeparator.getDisplayName());
            Assertions.assertNotNull(tmpSeparator.getTooltipText());
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private static helper methods" defaultstate="collapsed">
    /**
     * Builds a FRAGMENTS-tab export input: a list of real FragmentDataModel instances (parsed from three SMILES) with
     * their frequency/percentage fields populated. The list is typed as List of MoleculeDataModel (the export method
     * signature) but actually holds FragmentDataModel instances (required by the FRAGMENTS branch).
     *
     * @return list of FragmentDataModel instances usable as a FRAGMENTS-tab export input
     * @throws Exception if SMILES parsing fails
     */
    private static List<MoleculeDataModel> buildFragmentList() throws Exception {
        SmilesParser tmpParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        List<MoleculeDataModel> tmpList = new ArrayList<>();
        String[] tmpSmilesCodes = {"c1ccccc1", "CCO", "O=CO"};
        for (String tmpSmilesCode : tmpSmilesCodes) {
            IAtomContainer tmpAtomContainer = tmpParser.parseSmiles(tmpSmilesCode);
            FragmentDataModel tmpFragment = new FragmentDataModel(tmpAtomContainer, false);
            tmpFragment.setAbsoluteFrequency(3);
            tmpFragment.setAbsolutePercentage(0.25);
            tmpFragment.setMoleculeFrequency(2);
            tmpFragment.setMoleculePercentage(0.20);
            tmpList.add(tmpFragment);
        }
        return tmpList;
    }
    //
    /**
     * Builds an ITEMIZATION-tab export input: a parent MoleculeDataModel carrying a populated fragments map and a
     * matching fragment-frequency map (keyed by the fragment unique SMILES) under the given fragmentation name. No
     * fragmenter is involved; the maps are populated directly via the public API.
     *
     * @param aFragmentationName fragmentation name under which the fragments are registered
     * @return parent molecule with attached fragments for the given fragmentation name
     * @throws Exception if SMILES parsing fails
     */
    private static MoleculeDataModel buildMoleculeWithFragments(String aFragmentationName) throws Exception {
        SmilesParser tmpParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
        IAtomContainer tmpParentAtomContainer = tmpParser.parseSmiles("c1ccccc1CCO");
        MoleculeDataModel tmpMolecule = new MoleculeDataModel(tmpParentAtomContainer, false);
        FragmentDataModel tmpFragment = new FragmentDataModel(tmpParser.parseSmiles("c1ccccc1"), false);
        tmpFragment.setAbsoluteFrequency(1);
        List<FragmentDataModel> tmpFragments = new ArrayList<>(List.of(tmpFragment));
        tmpMolecule.getAllFragments().put(aFragmentationName, tmpFragments);
        Map<String, Integer> tmpFrequencies = new HashMap<>();
        tmpFrequencies.put(tmpFragment.getUniqueSmiles(), 1);
        tmpMolecule.getFragmentFrequencies().put(aFragmentationName, tmpFrequencies);
        return tmpMolecule;
    }
    //</editor-fold>
}
