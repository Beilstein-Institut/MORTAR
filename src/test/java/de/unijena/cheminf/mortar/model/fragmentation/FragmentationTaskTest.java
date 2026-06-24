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

package de.unijena.cheminf.mortar.model.fragmentation;

import de.unijena.cheminf.mortar.configuration.Configuration;
import de.unijena.cheminf.mortar.model.data.FragmentDataModel;
import de.unijena.cheminf.mortar.model.data.MoleculeDataModel;
import de.unijena.cheminf.mortar.model.fragmentation.algorithm.IMoleculeFragmenter;
import de.unijena.cheminf.mortar.model.util.ChemUtil;

import javafx.beans.property.Property;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct, headless unit tests for {@link FragmentationTask}, targeting the per-molecule exception-counter branch
 * (the generic {@code catch} in {@link FragmentationTask#call()}) that the {@link FragmentationService} happy path does
 * not reach. The branch is driven with a test-local {@link ThrowingFragmenter} — a <b>real</b> class implementing
 * {@link IMoleculeFragmenter} whose {@code fragmentMolecule} throws — not a Mockito mock (no Mockito is used anywhere in
 * MORTAR's tests). The task's {@code call()} blocks and returns the exception count synchronously, so the assertion
 * follows directly with no sleep/latch. The happy-path, filter-skip and preprocessing branches are covered transitively
 * via {@code FragmentationServiceTest} (plan 04-01); this test covers only the exception counter.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class FragmentationTaskTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB (for deterministic, message-bundle-resolved strings) and
     * bootstraps the Configuration singleton from the classpath (no data directory is touched by this).
     *
     * @throws Exception if the Configuration singleton cannot be initialized
     */
    public FragmentationTaskTest() throws Exception {
        Locale.setDefault(Locale.of("en", "GB"));
        Configuration.getInstance();
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Drives {@link FragmentationTask#call()} over a real molecule list with the test-local {@link ThrowingFragmenter}
     * (whose {@code fragmentMolecule} throws a {@link NullPointerException} for every molecule), and asserts the returned
     * exception count is {@literal >} 0 — one per molecule that threw — confirming the per-molecule generic catch branch
     * is exercised and the batch is not aborted by a single failure.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void exceptionCounterTest() throws Exception {
        List<MoleculeDataModel> tmpMols = new ArrayList<>(2);
        tmpMols.add(FragmentationTaskTest.buildMDM("c1ccccc1"));
        tmpMols.add(FragmentationTaskTest.buildMDM("O=C(O)CCCC(=O)O"));
        Map<String, FragmentDataModel> tmpFragmentMap = new ConcurrentHashMap<>();
        FragmentationTask tmpTask = new FragmentationTask(
                tmpMols, new ThrowingFragmenter(), tmpFragmentMap, "TaskTest", false);
        Integer tmpExceptionCount = tmpTask.call();
        Assertions.assertNotNull(tmpExceptionCount);
        Assertions.assertTrue(tmpExceptionCount > 0);
        Assertions.assertEquals(tmpMols.size(), tmpExceptionCount.intValue());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private static methods" defaultstate="collapsed">
    /**
     * Builds a {@link MoleculeDataModel} from the given SMILES string using {@link ChemUtil#parseSmilesToAtomContainer}
     * and the (IAtomContainer, boolean) constructor without stereochemistry encoding.
     *
     * @param aSmiles SMILES string to parse
     * @return a MoleculeDataModel wrapping the parsed atom container
     * @throws Exception if parsing or unique-SMILES creation fails
     */
    private static MoleculeDataModel buildMDM(String aSmiles) throws Exception {
        IAtomContainer tmpAtomContainer = ChemUtil.parseSmilesToAtomContainer(aSmiles, false, false);
        return new MoleculeDataModel(tmpAtomContainer, false);
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test-local fragmenter" defaultstate="collapsed">
    /**
     * Real (non-mock) test-local {@link IMoleculeFragmenter} whose {@link #fragmentMolecule(IAtomContainer)} always
     * throws a {@link NullPointerException}, used to drive the exception-counter branch of {@link FragmentationTask}.
     * Every other interface method is implemented minimally so the task reaches {@code fragmentMolecule}: the molecule is
     * neither filtered nor preprocessed and is considered fragmentable.
     */
    private static final class ThrowingFragmenter implements IMoleculeFragmenter {
        /**
         * Constructor (no state).
         */
        private ThrowingFragmenter() {
        }
        //
        @Override
        public List<Property<?>> settingsProperties() {
            return new ArrayList<>(0);
        }
        //
        @Override
        public Map<String, String> getSettingNameToTooltipTextMap() {
            return new HashMap<>(0);
        }
        //
        @Override
        public Map<String, String> getSettingNameToDisplayNameMap() {
            return new HashMap<>(0);
        }
        //
        @Override
        public String getFragmentationAlgorithmName() {
            return "ThrowingFragmenter";
        }
        //
        @Override
        public String getFragmentationAlgorithmDisplayName() {
            return "Throwing Fragmenter";
        }
        //
        @Override
        public IMoleculeFragmenter copy() {
            return new ThrowingFragmenter();
        }
        //
        @Override
        public void restoreDefaultSettings() {
            //no-op: this test fragmenter has no settings
        }
        //
        @Override
        public List<IAtomContainer> fragmentMolecule(IAtomContainer aMolecule)
                throws NullPointerException, IllegalArgumentException, CloneNotSupportedException {
            throw new NullPointerException("ThrowingFragmenter always throws to exercise the exception-counter branch");
        }
        //
        @Override
        public boolean shouldBeFiltered(IAtomContainer aMolecule) {
            return false;
        }
        //
        @Override
        public boolean shouldBePreprocessed(IAtomContainer aMolecule) throws NullPointerException {
            return false;
        }
        //
        @Override
        public boolean canBeFragmented(IAtomContainer aMolecule) throws NullPointerException {
            return true;
        }
        //
        @Override
        public IAtomContainer applyPreprocessing(IAtomContainer aMolecule)
                throws NullPointerException, IllegalArgumentException, CloneNotSupportedException {
            return aMolecule;
        }
    }
    //</editor-fold>
}
