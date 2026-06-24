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
import de.unijena.cheminf.mortar.model.util.FileUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openscience.cdk.interfaces.IAtomContainer;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.LogManager;

/**
 * Direct, headless unit tests for {@link FragmentationService}. The service orchestrates single and pipeline
 * fragmentation and persists/reloads its settings. All fragmentation drives here are synchronous: the
 * {@code startSingleFragmentation}/{@code startPipelineFragmentation}/{@code startPipelineFragmentationMolByMol}
 * methods block (via {@code ExecutorService.invokeAll} + {@code Future.get}) and return with the fragment map fully
 * populated, so assertions are made directly after the call with no sleep, latch, or {@code Platform.runLater} wait.
 * The persist/reload round-trip is isolated to a JUnit {@link TempDir} by redirecting the {@code user.home} system
 * property and reflectively resetting the private static {@code appDirPath} cache of {@link FileUtil}, with the
 * original state always restored in a finally block, so the real {@code ~/MORTAR} directory is never touched. Only
 * real CDK objects and real fragmenters are used (no mocking).
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class FragmentationServiceTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB (so the fragmenter settings tooltips and display names, which
     * are resolved from the message.properties file during fragmenter instantiation, are deterministic) and bootstraps
     * the Configuration singleton from the classpath (the service reads config; no data directory is touched by this).
     *
     * @throws Exception if the Configuration singleton cannot be initialized
     */
    public FragmentationServiceTest() throws Exception {
        Locale.setDefault(Locale.of("en", "GB"));
        Configuration.getInstance();
    }
    //</editor-fold>
    //
    //<editor-fold desc="Test methods" defaultstate="collapsed">
    /**
     * Tests single-algorithm fragmentation: after {@code startSingleFragmentation} returns, the fragment map is
     * non-null and non-empty, every fragment has an absolute frequency {@literal >=} 1 and an absolute percentage in
     * (0.0, 1.0], and the current fragmentation name is set.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void singleFragmentationTest() throws Exception {
        FragmentationService tmpService = new FragmentationService();
        List<MoleculeDataModel> tmpMols = new ArrayList<>(2);
        tmpMols.add(FragmentationServiceTest.buildMDM("c1ccccc1"));
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCCC(=O)O"));
        tmpService.setSelectedFragmenter(tmpService.getFragmenters()[0].getFragmentationAlgorithmDisplayName());
        tmpService.startSingleFragmentation(tmpMols, 1, false);
        Map<String, FragmentDataModel> tmpFragments = tmpService.getFragments();
        Assertions.assertNotNull(tmpFragments);
        Assertions.assertFalse(tmpFragments.isEmpty());
        for (FragmentDataModel tmpFragment : tmpFragments.values()) {
            Assertions.assertTrue(tmpFragment.getAbsoluteFrequency() >= 1);
            Assertions.assertTrue(tmpFragment.getAbsolutePercentage() > 0.0 && tmpFragment.getAbsolutePercentage() <= 1.0);
        }
        Assertions.assertNotNull(tmpService.getCurrentFragmentationName());
        Assertions.assertFalse(tmpService.getCurrentFragmentationName().isEmpty());
    }
    //
    /**
     * Tests the task-split logic of the service by driving four (molecule count, task count) combinations that exercise
     * the even-split, modulo (remainder {@literal >} 0) and clamp (more tasks than molecules) branches in the private
     * {@code startFragmentation} method. Each drive must complete without throwing and populate a non-empty fragment
     * map, and the result must be shaped identically regardless of the task count (single task and two tasks over the
     * same four molecules yield the same number of distinct fragments).
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void multiTaskSplitTest() throws Exception {
        List<MoleculeDataModel> tmpFourMols = new ArrayList<>(4);
        tmpFourMols.add(FragmentationServiceTest.buildMDM("O=C(O)CC"));
        tmpFourMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCC"));
        tmpFourMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCCC"));
        tmpFourMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCCCC"));
        //size=4, tasks=1 (single task)
        int tmpDistinctSingleTask = this.runSingleAndCountFragments(tmpFourMols, 1);
        Assertions.assertTrue(tmpDistinctSingleTask > 0);
        //size=4, tasks=2 (even split)
        int tmpDistinctTwoTasks = this.runSingleAndCountFragments(tmpFourMols, 2);
        Assertions.assertTrue(tmpDistinctTwoTasks > 0);
        //identical-shaped result regardless of task count
        Assertions.assertEquals(tmpDistinctSingleTask, tmpDistinctTwoTasks);
        //size=3, tasks=2 (modulo remainder > 0)
        List<MoleculeDataModel> tmpThreeMols = new ArrayList<>(tmpFourMols.subList(0, 3));
        Assertions.assertTrue(this.runSingleAndCountFragments(tmpThreeMols, 2) > 0);
        //size=2, tasks=4 (clamp: more tasks than molecules)
        List<MoleculeDataModel> tmpTwoMols = new ArrayList<>(tmpFourMols.subList(0, 2));
        Assertions.assertTrue(this.runSingleAndCountFragments(tmpTwoMols, 4) > 0);
    }
    //
    /**
     * Tests the duplicate-fragment merge/aggregation path (the concurrency-sensitive part of the service) with two
     * tasks. A molecule set is built where two distinct molecules share a fragment (both carry a carboxylic-acid
     * functional group fragmented by the Ertl fragmenter). Invariants are asserted rather than a hard-coded fragment
     * SMILES key: the maximum-absolute-frequency fragment must have a molecule frequency equal to the number of
     * distinct parent molecules and an absolute frequency {@literal >=} 2, and the sum of absolute percentages across
     * all fragments must be approximately 1.0.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void mergeAggregationTest() throws Exception {
        FragmentationService tmpService = new FragmentationService();
        List<MoleculeDataModel> tmpMols = new ArrayList<>(2);
        //two different molecules that both bear a carboxylic acid group -> shared Ertl functional group fragment
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCC"));
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCCCCC"));
        //select the Ertl functional groups finder fragmenter (the default, first registered)
        tmpService.setSelectedFragmenter(tmpService.getFragmenters()[0].getFragmentationAlgorithmDisplayName());
        tmpService.startSingleFragmentation(tmpMols, 2, false);
        Map<String, FragmentDataModel> tmpFragments = tmpService.getFragments();
        Assertions.assertNotNull(tmpFragments);
        Assertions.assertFalse(tmpFragments.isEmpty());
        FragmentDataModel tmpMaxFrequencyFragment = FragmentationServiceTest.findMaxAbsoluteFrequencyFragment(tmpFragments);
        Assertions.assertNotNull(tmpMaxFrequencyFragment);
        //the shared fragment appears in both molecules: molecule frequency == number of distinct parent molecules
        Assertions.assertEquals(tmpMaxFrequencyFragment.getParentMolecules().size(), tmpMaxFrequencyFragment.getMoleculeFrequency());
        Assertions.assertEquals(2, tmpMaxFrequencyFragment.getMoleculeFrequency());
        Assertions.assertTrue(tmpMaxFrequencyFragment.getAbsoluteFrequency() >= 2);
        //percentages across all fragments must sum to approximately 1.0
        double tmpPercentageSum = 0.0;
        for (FragmentDataModel tmpFragment : tmpFragments.values()) {
            tmpPercentageSum += tmpFragment.getAbsolutePercentage();
        }
        Assertions.assertEquals(1.0, tmpPercentageSum, 1e-9);
    }
    //
    /**
     * Tests pipeline fragmentation: a two-fragmenter pipeline (two independent copies of the default fragmenter) is
     * configured and {@code startPipelineFragmentation} is driven on a small molecule set; the resulting fragment map
     * is non-empty with valid frequency/percentage values and the current fragmentation name equals the configured
     * pipeline name. The deprecated {@code startPipelineFragmentationMolByMol} is then driven on a fresh service over
     * the same input and must complete without throwing.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void pipelineFragmentationTest() throws Exception {
        FragmentationService tmpService = new FragmentationService();
        tmpService.setPipelineFragmenter(new IMoleculeFragmenter[] {
                tmpService.getFragmenters()[0].copy(),
                tmpService.getFragmenters()[0].copy()
        });
        String tmpPipelineName = "TestPipeline";
        tmpService.setPipeliningFragmentationName(tmpPipelineName);
        List<MoleculeDataModel> tmpMols = new ArrayList<>(2);
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCC"));
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCCCCC"));
        tmpService.startPipelineFragmentation(tmpMols, 1, false, false);
        Map<String, FragmentDataModel> tmpFragments = tmpService.getFragments();
        Assertions.assertNotNull(tmpFragments);
        Assertions.assertFalse(tmpFragments.isEmpty());
        for (FragmentDataModel tmpFragment : tmpFragments.values()) {
            Assertions.assertTrue(tmpFragment.getAbsoluteFrequency() >= 1);
            Assertions.assertTrue(tmpFragment.getAbsolutePercentage() > 0.0 && tmpFragment.getAbsolutePercentage() <= 1.0);
        }
        Assertions.assertEquals(tmpPipelineName, tmpService.getCurrentFragmentationName());
        //drive the deprecated mol-by-mol pipeline on a fresh service over the same input
        FragmentationService tmpMolByMolService = new FragmentationService();
        tmpMolByMolService.setPipelineFragmenter(new IMoleculeFragmenter[] {
                tmpMolByMolService.getFragmenters()[0].copy(),
                tmpMolByMolService.getFragmenters()[0].copy()
        });
        tmpMolByMolService.setPipeliningFragmentationName("MolByMolPipeline");
        List<MoleculeDataModel> tmpMolsForMolByMol = new ArrayList<>(2);
        tmpMolsForMolByMol.add(FragmentationServiceTest.buildMDM("O=C(O)CCC"));
        tmpMolsForMolByMol.add(FragmentationServiceTest.buildMDM("O=C(O)CCCCCC"));
        Assertions.assertDoesNotThrow(() -> tmpMolByMolService.startPipelineFragmentationMolByMol(tmpMolsForMolByMol, 1, false));
        Assertions.assertNotNull(tmpMolByMolService.getFragments());
    }
    //
    /**
     * Tests the trivial getters/setters and cache management methods: the accessors return/round-trip sensibly,
     * {@code createNewFragmenterObjectByAlgorithmName} returns a fragmenter for a valid algorithm name and throws an
     * IllegalArgumentException for a bogus name, running two single fragmentations exercises the duplicate-name append
     * branch in the private {@code createAndCheckFragmentationName}, and {@code clearCache} as well as the happy-path
     * {@code abortExecutor} do not throw.
     *
     * @throws Exception if anything goes wrong
     */
    @Test
    public void gettersAndCacheTest() throws Exception {
        FragmentationService tmpService = new FragmentationService();
        Assertions.assertNotNull(tmpService.getFragmenters());
        Assertions.assertEquals(4, tmpService.getFragmenters().length);
        Assertions.assertNotNull(tmpService.getSelectedFragmenter());
        Assertions.assertNotNull(tmpService.getPipelineFragmenter());
        Assertions.assertNotNull(tmpService.getPipeliningFragmentationName());
        Assertions.assertNotNull(tmpService.selectedFragmenterDisplayNameProperty());
        //setPipeliningFragmentationName round-trip
        tmpService.setPipeliningFragmentationName("CustomName");
        Assertions.assertEquals("CustomName", tmpService.getPipeliningFragmentationName());
        //setSelectedFragmenter + display-name property round-trip
        String tmpSecondDisplayName = tmpService.getFragmenters()[1].getFragmentationAlgorithmDisplayName();
        tmpService.setSelectedFragmenter(tmpSecondDisplayName);
        Assertions.assertEquals(tmpService.getFragmenters()[1].getFragmentationAlgorithmName(),
                tmpService.getSelectedFragmenter().getFragmentationAlgorithmName());
        tmpService.setSelectedFragmenterDisplayName(tmpSecondDisplayName);
        Assertions.assertEquals(tmpSecondDisplayName, tmpService.getSelectedFragmenterDisplayName());
        //createNewFragmenterObjectByAlgorithmName: valid name returns a fragmenter
        String tmpValidAlgorithmName = tmpService.getFragmenters()[0].getFragmentationAlgorithmName();
        IMoleculeFragmenter tmpNewFragmenter = tmpService.createNewFragmenterObjectByAlgorithmName(tmpValidAlgorithmName);
        Assertions.assertNotNull(tmpNewFragmenter);
        Assertions.assertEquals(tmpValidAlgorithmName, tmpNewFragmenter.getFragmentationAlgorithmName());
        //createNewFragmenterObjectByAlgorithmName: bogus name throws IllegalArgumentException
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> tmpService.createNewFragmenterObjectByAlgorithmName("this-is-not-a-real-algorithm-name"));
        //two single fragmentations -> duplicate-name append branch in createAndCheckFragmentationName
        tmpService.setSelectedFragmenter(tmpService.getFragmenters()[0].getFragmentationAlgorithmDisplayName());
        List<MoleculeDataModel> tmpMols = new ArrayList<>(1);
        tmpMols.add(FragmentationServiceTest.buildMDM("O=C(O)CCC"));
        tmpService.startSingleFragmentation(tmpMols, 1, false);
        String tmpFirstName = tmpService.getCurrentFragmentationName();
        List<MoleculeDataModel> tmpMols2 = new ArrayList<>(1);
        tmpMols2.add(FragmentationServiceTest.buildMDM("O=C(O)CCCC"));
        tmpService.startSingleFragmentation(tmpMols2, 1, false);
        String tmpSecondName = tmpService.getCurrentFragmentationName();
        Assertions.assertNotEquals(tmpFirstName, tmpSecondName);
        //clearCache and happy-path abortExecutor do not throw
        Assertions.assertDoesNotThrow(tmpService::abortExecutor);
        Assertions.assertDoesNotThrow(tmpService::clearCache);
        Assertions.assertNull(tmpService.getFragments());
        Assertions.assertNull(tmpService.getCurrentFragmentationName());
    }
    //
    /**
     * Tests the settings persist/reload round-trip under {@link TempDir} isolation: the {@code user.home} system
     * property is redirected to a temporary directory and the private static {@code appDirPath} cache of
     * {@link FileUtil} is reflectively reset so that the settings directory resolves under the temporary directory. A
     * service mutates its selected fragmenter and pipeline name, persists them, and a fresh service reloads them; the
     * reloaded selected-fragmenter algorithm name and pipelining name must match. The original {@code user.home} and
     * the {@code appDirPath} cache are always restored and the log manager is reset in a finally block, so the real
     * {@code ~/MORTAR} directory is never touched and no logger handler leaks into sibling tests.
     *
     * @param aTempHome temporary directory used as a fake user home
     * @throws Exception if anything goes wrong
     */
    @Test
    public void persistAndReloadRoundTrip(@TempDir Path aTempHome) throws Exception {
        String tmpOldHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", aTempHome.toString());
            this.resetAppDirPathCache();
            FragmentationService tmpService = new FragmentationService();
            //mutate the selected fragmenter to a non-default one and set a pipeline + name
            String tmpSelectedDisplayName = tmpService.getFragmenters()[1].getFragmentationAlgorithmDisplayName();
            tmpService.setSelectedFragmenter(tmpSelectedDisplayName);
            tmpService.setPipelineFragmenter(new IMoleculeFragmenter[] {
                    tmpService.getFragmenters()[0].copy(),
                    tmpService.getFragmenters()[1].copy()
            });
            tmpService.setPipeliningFragmentationName("PersistedPipeline");
            tmpService.persistFragmenterSettings();
            tmpService.persistSelectedFragmenterAndPipeline();
            //fresh service reloads from the temp-dir-isolated settings directory
            FragmentationService tmpReloaded = new FragmentationService();
            tmpReloaded.reloadFragmenterSettings();
            tmpReloaded.reloadActiveFragmenterAndPipeline();
            Assertions.assertEquals(tmpService.getSelectedFragmenter().getFragmentationAlgorithmName(),
                    tmpReloaded.getSelectedFragmenter().getFragmentationAlgorithmName());
            Assertions.assertEquals("PersistedPipeline", tmpReloaded.getPipeliningFragmentationName());
        } finally {
            System.setProperty("user.home", tmpOldHome);
            this.resetAppDirPathCache();
            LogManager.getLogManager().reset();
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Builds a MoleculeDataModel from a SMILES string using the verified (IAtomContainer, boolean) constructor.
     *
     * @param aSmiles SMILES string of the molecule
     * @return MoleculeDataModel for the given SMILES
     * @throws Exception if the SMILES cannot be parsed
     */
    private static MoleculeDataModel buildMDM(String aSmiles) throws Exception {
        IAtomContainer tmpAtomContainer = ChemUtil.parseSmilesToAtomContainer(aSmiles, false, false);
        return new MoleculeDataModel(tmpAtomContainer, false);
    }
    //
    /**
     * Returns the fragment with the maximum absolute frequency from the given fragment map, or null if the map is
     * empty.
     *
     * @param aFragmentsMap map of unique SMILES to FragmentDataModel
     * @return the FragmentDataModel with the highest absolute frequency
     */
    private static FragmentDataModel findMaxAbsoluteFrequencyFragment(Map<String, FragmentDataModel> aFragmentsMap) {
        FragmentDataModel tmpMaxFragment = null;
        for (FragmentDataModel tmpFragment : aFragmentsMap.values()) {
            if (tmpMaxFragment == null || tmpFragment.getAbsoluteFrequency() > tmpMaxFragment.getAbsoluteFrequency()) {
                tmpMaxFragment = tmpFragment;
            }
        }
        return tmpMaxFragment;
    }
    //
    /**
     * Runs a single fragmentation on a fresh service over the given molecules with the given number of tasks and
     * returns the number of distinct fragments produced.
     *
     * @param aListOfMolecules molecules to fragment
     * @param aNumberOfTasks number of parallel tasks
     * @return number of distinct fragments in the result map
     * @throws Exception if anything goes wrong
     */
    private int runSingleAndCountFragments(List<MoleculeDataModel> aListOfMolecules, int aNumberOfTasks) throws Exception {
        FragmentationService tmpService = new FragmentationService();
        tmpService.setSelectedFragmenter(tmpService.getFragmenters()[0].getFragmentationAlgorithmDisplayName());
        //fresh molecule data models per drive so previous fragmentation state does not interfere
        List<MoleculeDataModel> tmpFreshMols = new ArrayList<>(aListOfMolecules.size());
        for (MoleculeDataModel tmpMolecule : aListOfMolecules) {
            tmpFreshMols.add(FragmentationServiceTest.buildMDM(tmpMolecule.getUniqueSmiles()));
        }
        tmpService.startSingleFragmentation(tmpFreshMols, aNumberOfTasks, false);
        Map<String, FragmentDataModel> tmpFragments = tmpService.getFragments();
        Assertions.assertNotNull(tmpFragments);
        Assertions.assertFalse(tmpFragments.isEmpty());
        return tmpFragments.size();
    }
    //
    /**
     * Reflectively resets the private static {@code appDirPath} cache of FileUtil to null, so the next call to
     * getAppDirPath re-resolves the data directory from the current {@code user.home} system property.
     *
     * @throws Exception if the field cannot be accessed
     */
    private void resetAppDirPathCache() throws Exception {
        Field tmpField = FileUtil.class.getDeclaredField("appDirPath");
        tmpField.setAccessible(true);
        tmpField.set(null, null);
    }
    //</editor-fold>
}
