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

package de.unijena.cheminf.mortar.model.fragmentation.algorithm;

import de.unijena.cheminf.mortar.gui.util.GuiUtil;
import de.unijena.cheminf.mortar.message.Message;
import de.unijena.cheminf.mortar.model.io.Importer;
import de.unijena.cheminf.mortar.model.util.BasicDefinitions;
import de.unijena.cheminf.mortar.model.util.CollectionUtil;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

import org.openscience.cdk.fragment.CircularFragmenter;
import org.openscience.cdk.interfaces.IAtomContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

//TODO: add aromaticity detection as optional preprocessing (setting!)
/**
 * Wrapper class that makes the CDK {@link CircularFragmenter} available in MORTAR.
 * The fragmenter extracts atom-centered circular / spherical fragments from a molecule,
 * analogous to HOSE codes, circular Morgan-type fingerprints, and Molecular Signatures.
 * For every atom in the input molecule, the neighborhood up to a user-defined radius
 * (number of bonds) is collected by a breadth-first expansion and returned as an
 * independent {@link IAtomContainer}. Note that the resulting fragments are not
 * deduplicated.
 *
 * @author Jonas Schaub
 * @version 1.0.0.0
 */
public class CDKCircularFragmenter implements IMoleculeFragmenter {
    //<editor-fold desc="Public static final constants">
    /**
     * Name of the algorithm used in this fragmenter.
     */
    public static final String ALGORITHM_NAME = "CDK Circular Fragmenter";

    /**
     * Default radius setting value (number of bonds), taken from {@link CircularFragmenter#DEFAULT_RADIUS}.
     */
    public static final int RADIUS_SETTING_DEFAULT = CircularFragmenter.DEFAULT_RADIUS;

    /**
     * Default preserve stereo setting value, taken from {@link CircularFragmenter#DEFAULT_PRESERVE_STEREO}.
     */
    public static final boolean PRESERVE_STEREO_SETTING_DEFAULT = CircularFragmenter.DEFAULT_PRESERVE_STEREO;

    /**
     * Default mark attachments setting value, taken from {@link CircularFragmenter#DEFAULT_MARK_ATTACHMENTS}.
     */
    public static final boolean MARK_ATTACHMENTS_SETTING_DEFAULT = CircularFragmenter.DEFAULT_MARK_ATTACHMENTS;
    //</editor-fold>
    //
    //<editor-fold desc="Private final variables">
    /**
     * Instance of the CDK CircularFragmenter class that is used for fragmentation.
     */
    private final CircularFragmenter circularFragmenterInstance;
    //note: since Java 21, the javadoc build complains about "double comments" when there is a comment
    // for the get() method of the property and the private property itself as well
    private final SimpleIntegerProperty radiusSetting;

    private final SimpleBooleanProperty preserveStereoSetting;

    private final SimpleBooleanProperty markAttachmentsSetting;

    /**
     * All settings of this fragmenter, encapsulated in JavaFX properties for binding in GUI.
     */
    private final List<Property<?>> settings;

    /**
     * Map to store pairs of {@literal <setting name, tooltip text>}.
     */
    private final HashMap<String, String> settingNameToTooltipTextMap;

    /**
     * Map to store pairs of {@literal <setting name, display name>}.
     */
    private final HashMap<String, String> settingNameToDisplayNameMap;

    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(CDKCircularFragmenter.class.getName());
    //</editor-fold>
    //
    //<editor-fold desc="Constructor">
    /**
     * Constructor, all settings are initialized with their default values as declared in the respective public constants.
     */
    public CDKCircularFragmenter() {
        this.circularFragmenterInstance = new CircularFragmenter(
                CDKCircularFragmenter.RADIUS_SETTING_DEFAULT,
                CDKCircularFragmenter.PRESERVE_STEREO_SETTING_DEFAULT,
                CDKCircularFragmenter.MARK_ATTACHMENTS_SETTING_DEFAULT
        );
        int tmpNumberOfSettings = 3;
        this.settings = new ArrayList<>(tmpNumberOfSettings);
        int tmpInitialCapacityForSettingNameTooltipTextMap = CollectionUtil.calculateInitialHashCollectionCapacity(
                tmpNumberOfSettings,
                BasicDefinitions.DEFAULT_HASH_COLLECTION_LOAD_FACTOR);
        this.settingNameToTooltipTextMap = new HashMap<>(tmpInitialCapacityForSettingNameTooltipTextMap, BasicDefinitions.DEFAULT_HASH_COLLECTION_LOAD_FACTOR);
        this.settingNameToDisplayNameMap = new HashMap<>(tmpInitialCapacityForSettingNameTooltipTextMap, BasicDefinitions.DEFAULT_HASH_COLLECTION_LOAD_FACTOR);

        this.radiusSetting = new SimpleIntegerProperty(this, "Radius setting",
                CDKCircularFragmenter.RADIUS_SETTING_DEFAULT) {
            @Override
            public void set(int newValue) throws IllegalArgumentException {
                if (newValue < 0) {
                    IllegalArgumentException tmpException = new IllegalArgumentException(
                            "Radius must be >= 0, got: " + newValue);
                    CDKCircularFragmenter.LOGGER.log(Level.WARNING, tmpException.toString(), tmpException);
                    GuiUtil.guiExceptionAlert(Message.get("Fragmenter.IllegalSettingValue.Title"),
                            Message.get("Fragmenter.IllegalSettingValue.Header"),
                            tmpException.toString(),
                            tmpException);
                    //re-throws the exception to properly reset the binding
                    throw tmpException;
                }
                super.set(newValue);
                CDKCircularFragmenter.this.circularFragmenterInstance.setRadius(newValue);
            }
        };
        this.settings.add(this.radiusSetting);
        this.settingNameToTooltipTextMap.put(this.radiusSetting.getName(),
                Message.get("CDKCircularFragmenter.radiusSetting.tooltip"));
        this.settingNameToDisplayNameMap.put(this.radiusSetting.getName(),
                Message.get("CDKCircularFragmenter.radiusSetting.displayName"));

        this.preserveStereoSetting = new SimpleBooleanProperty(this,
                "Preserve stereo setting",
                CDKCircularFragmenter.PRESERVE_STEREO_SETTING_DEFAULT) {
            @Override
            public void set(boolean newValue) {
                super.set(newValue);
                CDKCircularFragmenter.this.circularFragmenterInstance.setPreserveStereo(newValue);
            }
        };
        this.settings.add(this.preserveStereoSetting);
        this.settingNameToTooltipTextMap.put(this.preserveStereoSetting.getName(),
                Message.get("CDKCircularFragmenter.preserveStereoSetting.tooltip"));
        this.settingNameToDisplayNameMap.put(this.preserveStereoSetting.getName(),
                Message.get("CDKCircularFragmenter.preserveStereoSetting.displayName"));

        this.markAttachmentsSetting = new SimpleBooleanProperty(this,
                "Mark attachments setting",
                CDKCircularFragmenter.MARK_ATTACHMENTS_SETTING_DEFAULT) {
            @Override
            public void set(boolean newValue) {
                super.set(newValue);
                CDKCircularFragmenter.this.circularFragmenterInstance.setMarkAttachments(newValue);
            }
        };
        this.settings.add(this.markAttachmentsSetting);
        this.settingNameToTooltipTextMap.put(this.markAttachmentsSetting.getName(),
                Message.get("CDKCircularFragmenter.markAttachmentsSetting.tooltip"));
        this.settingNameToDisplayNameMap.put(this.markAttachmentsSetting.getName(),
                Message.get("CDKCircularFragmenter.markAttachmentsSetting.displayName"));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public properties get">
    /**
     * Returns the currently set radius for the circular neighborhood extraction (number of bonds).
     *
     * @return current radius setting value
     */
    public int getRadiusSetting() {
        return this.radiusSetting.get();
    }

    /**
     * Returns the property object of the radius setting that can be used to configure this setting.
     *
     * @return property object of the radius setting
     */
    public SimpleIntegerProperty radiusSettingProperty() {
        return this.radiusSetting;
    }

    /**
     * Returns the current state of the preserve stereo setting.
     *
     * @return true if stereochemistry annotations should be preserved in fragments
     */
    public boolean getPreserveStereoSetting() {
        return this.preserveStereoSetting.get();
    }

    /**
     * Returns the property object of the preserve stereo setting that can be used to configure this setting.
     *
     * @return property object of the preserve stereo setting
     */
    public SimpleBooleanProperty preserveStereoSettingProperty() {
        return this.preserveStereoSetting;
    }

    /**
     * Returns the current state of the mark attachments setting.
     *
     * @return true if attachment points of broken bonds should be marked with pseudo atoms
     */
    public boolean getMarkAttachmentsSetting() {
        return this.markAttachmentsSetting.get();
    }

    /**
     * Returns the property object of the mark attachments setting that can be used to configure this setting.
     *
     * @return property object of the mark attachments setting
     */
    public SimpleBooleanProperty markAttachmentsSettingProperty() {
        return this.markAttachmentsSetting;
    }
    //</editor-fold>
    //
    //<editor-fold desc="Public properties set">
    /**
     * Sets the radius for the circular neighborhood extraction (number of bonds).
     *
     * @param aRadius the new radius; must be >= 0
     * @throws IllegalArgumentException if the given radius is negative
     */
    public void setRadiusSetting(int aRadius) throws IllegalArgumentException {
        //parameter test done in overridden set() method of the property
        this.radiusSetting.set(aRadius);
    }

    /**
     * Sets the preserve stereo setting, defining whether stereochemistry annotations should be
     * preserved in the circular fragments.
     *
     * @param aBoolean true if stereo annotations should be preserved
     */
    public void setPreserveStereoSetting(boolean aBoolean) {
        this.preserveStereoSetting.set(aBoolean);
    }

    /**
     * Sets the mark attachments setting, defining whether attachment points of broken bonds
     * should be marked with pseudo ("R") atoms in the fragments.
     *
     * @param aBoolean true if attachment points should be marked with pseudo atoms
     */
    public void setMarkAttachmentsSetting(boolean aBoolean) {
        this.markAttachmentsSetting.set(aBoolean);
    }
    //</editor-fold>
    //
    //<editor-fold desc="IMoleculeFragmenter methods">
    //without the empty line, the code folding does not work properly here...

    @Override
    public List<Property<?>> settingsProperties() {
        return this.settings;
    }

    @Override
    public Map<String, String> getSettingNameToTooltipTextMap() {
        return this.settingNameToTooltipTextMap;
    }

    @Override
    public Map<String, String> getSettingNameToDisplayNameMap() {
        return this.settingNameToDisplayNameMap;
    }

    @Override
    public String getFragmentationAlgorithmName() {
        return CDKCircularFragmenter.ALGORITHM_NAME;
    }

    @Override
    public String getFragmentationAlgorithmDisplayName() {
        return Message.get("CDKCircularFragmenter.displayName");
    }

    @Override
    public IMoleculeFragmenter copy() {
        CDKCircularFragmenter tmpCopy = new CDKCircularFragmenter();
        tmpCopy.setRadiusSetting(this.radiusSetting.get());
        tmpCopy.setPreserveStereoSetting(this.preserveStereoSetting.get());
        tmpCopy.setMarkAttachmentsSetting(this.markAttachmentsSetting.get());
        return tmpCopy;
    }

    @Override
    public void restoreDefaultSettings() {
        this.radiusSetting.set(CDKCircularFragmenter.RADIUS_SETTING_DEFAULT);
        this.preserveStereoSetting.set(CDKCircularFragmenter.PRESERVE_STEREO_SETTING_DEFAULT);
        this.markAttachmentsSetting.set(CDKCircularFragmenter.MARK_ATTACHMENTS_SETTING_DEFAULT);
    }

    @Override
    public List<IAtomContainer> fragmentMolecule(IAtomContainer aMolecule)
            throws NullPointerException, IllegalArgumentException, CloneNotSupportedException {
        Objects.requireNonNull(aMolecule, "Given molecule is null.");
        boolean tmpCanBeFragmented = this.canBeFragmented(aMolecule);
        if (!tmpCanBeFragmented) {
            throw new IllegalArgumentException("Given molecule cannot be fragmented but should be filtered or preprocessed first.");
        }
        List<IAtomContainer> tmpFragments;
        try {
            tmpFragments = this.circularFragmenterInstance.getCircularFragments(aMolecule);
        } catch (Exception anException) {
            throw new IllegalArgumentException("An error occurred during fragmentation: " + anException.toString()
                    + " Molecule Name: " + aMolecule.getProperty(Importer.MOLECULE_NAME_PROPERTY_KEY));
        }
        return tmpFragments;
    }

    @Override
    public boolean shouldBeFiltered(IAtomContainer aMolecule) {
        return (Objects.isNull(aMolecule) || aMolecule.isEmpty());
    }

    @Override
    public boolean shouldBePreprocessed(IAtomContainer aMolecule) throws NullPointerException {
        Objects.requireNonNull(aMolecule, "Given molecule is null.");
        return false;
    }

    @Override
    public boolean canBeFragmented(IAtomContainer aMolecule) throws NullPointerException {
        Objects.requireNonNull(aMolecule, "Given molecule is null.");
        boolean tmpShouldBeFiltered = this.shouldBeFiltered(aMolecule);
        boolean tmpShouldBePreprocessed = this.shouldBePreprocessed(aMolecule);
        return !(tmpShouldBeFiltered || tmpShouldBePreprocessed);
    }

    @Override
    public IAtomContainer applyPreprocessing(IAtomContainer aMolecule)
            throws NullPointerException, IllegalArgumentException, CloneNotSupportedException {
        Objects.requireNonNull(aMolecule, "Given molecule is null.");
        boolean tmpShouldBeFiltered = this.shouldBeFiltered(aMolecule);
        if (tmpShouldBeFiltered) {
            throw new IllegalArgumentException("The given molecule cannot be preprocessed but should be filtered.");
        }
        return aMolecule.clone();
    }
    //</editor-fold>
}
