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
