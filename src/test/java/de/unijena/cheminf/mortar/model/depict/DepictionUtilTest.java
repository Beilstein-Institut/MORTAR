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

package de.unijena.cheminf.mortar.model.depict;

import javafx.scene.image.Image;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.lang.reflect.Constructor;
import java.util.Locale;

/**
 * Direct, headless unit tests for {@link DepictionUtil}. All fixtures are built from real CDK
 * {@code IAtomContainer}s parsed from SMILES (no Mockito); the JavaFX {@code Image} outputs are verified
 * to construct under {@code java.awt.headless=true} without a started toolkit.
 *
 * @author Felix Baensch
 * @version 1.0.0.0
 */
public class DepictionUtilTest {
    //<editor-fold desc="Constructor" defaultstate="collapsed">
    /**
     * Constructor that sets the default locale to en-GB so any message-bundle strings resolved during
     * image rendering are deterministic.
     */
    public DepictionUtilTest() {
        Locale.setDefault(Locale.of("en", "GB"));
    }
    //</editor-fold>
    //
    //<editor-fold desc="Success-path overload test methods" defaultstate="collapsed">
    /**
     * Tests that {@code depictImage(...)} returns a non-null Image for a valid container, verified headless.
     * No golden-pixel assertions are made.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImage(tmpAtomContainer, 200.0, 200.0);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithHeight(...)} returns a non-null Image for a valid container, using the
     * default width. Verified headless with no golden-pixel assertions.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithHeightValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithHeight(tmpAtomContainer, 200.0);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithWidth(...)} returns a non-null Image for a valid container, using the
     * default height. Verified headless with no golden-pixel assertions.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithWidthValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithWidth(tmpAtomContainer, 200.0);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that the two-argument {@code depictImageWithZoom(...)} overload (using the BasicDefinitions default
     * width and height) returns a non-null Image for a valid container, verified headless.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithZoomTwoArgValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithZoom(tmpAtomContainer, 1.0);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that the four-argument {@code depictImageWithZoom(...)} overload returns a non-null Image for a
     * valid container with explicit width and height, verified headless.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithZoomFourArgValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithZoom(tmpAtomContainer, 1.0, 200.0, 200.0);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithZoomAndFillToFit(...)} with {@code fillToFit=true} returns a non-null
     * Image for a valid container, exercising the fill-to-fit branch and the success body. Verified headless.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithZoomAndFillToFitValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithZoomAndFillToFit(tmpAtomContainer, 1.0, 250.0, 250.0, true);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithText(...)} returns a non-null Image for a valid container with a caption,
     * verified headless. A valid container is required because this method's catch handles only
     * {@code CDKException}; a null container would escape as an uncaught {@code NullPointerException}.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithTextValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithText(tmpAtomContainer, 1.0, 250.0, 250.0, "caption");
        Assertions.assertNotNull(tmpImage);
    }
    //</editor-fold>
    //
    //<editor-fold desc="White-background, null-input catch, and error-image branch test methods" defaultstate="collapsed">
    /**
     * Tests that {@code depictImageWithZoomAndFillToFitAndWhiteBackground(...)} with
     * {@code isBackgroundWhite=true} returns a non-null Image for a valid container, exercising the
     * white-background branch and the otherwise-uncalled success body. Verified headless.
     *
     * @throws Exception if SMILES parsing fails
     */
    @Test
    public void testDepictImageWithZoomAndFillToFitAndWhiteBackgroundValidContainerReturnsNonNullImage() throws Exception {
        IAtomContainer tmpAtomContainer = DepictionUtilTest.buildAtomContainer("c1ccccc1");
        Image tmpImage = DepictionUtil.depictImageWithZoomAndFillToFitAndWhiteBackground(tmpAtomContainer, 1.0, 250.0, 250.0, false, true);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithZoomAndFillToFit(...)} routes a null container through the
     * {@code catch (CDKException | NullPointerException)} branch to {@code depictErrorImage(...)} and still
     * returns a non-null Image.
     */
    @Test
    public void testDepictImageWithZoomAndFillToFitNullContainerReturnsErrorImage() {
        Image tmpImage = DepictionUtil.depictImageWithZoomAndFillToFit(null, 1.0, 250.0, 250.0, false);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictImageWithZoomAndFillToFitAndWhiteBackground(...)} routes a null container through
     * its {@code catch (CDKException | NullPointerException)} branch to {@code depictErrorImage(...)} and still
     * returns a non-null Image.
     */
    @Test
    public void testDepictImageWithZoomAndFillToFitAndWhiteBackgroundNullContainerReturnsErrorImage() {
        Image tmpImage = DepictionUtil.depictImageWithZoomAndFillToFitAndWhiteBackground(null, 1.0, 250.0, 250.0, false, false);
        Assertions.assertNotNull(tmpImage);
    }
    //
    /**
     * Tests that {@code depictErrorImage(...)} with a null message returns a non-null Image, exercising the
     * {@code Objects.requireNonNullElse(aMessage, "Error")} default-message branch.
     */
    @Test
    public void testDepictErrorImageNullMessageReturnsNonNullImage() {
        Image tmpImage = DepictionUtil.depictErrorImage(null, 250, 250);
        Assertions.assertNotNull(tmpImage);
        Assertions.assertEquals(250.0, tmpImage.getWidth());
        Assertions.assertEquals(250.0, tmpImage.getHeight());
    }
    //
    /**
     * Tests that {@code depictErrorImage(...)} with an explicit message returns a non-null Image, exercising the
     * non-null message path.
     */
    @Test
    public void testDepictErrorImageWithMessageReturnsNonNullImage() {
        Image tmpImage = DepictionUtil.depictErrorImage("explicit error", 250, 250);
        Assertions.assertNotNull(tmpImage);
        Assertions.assertEquals(250.0, tmpImage.getWidth());
        Assertions.assertEquals(250.0, tmpImage.getHeight());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Reflective constructor test method" defaultstate="collapsed">
    /**
     * Exercises the private no-argument constructor of the utility class DepictionUtil via reflection to
     * cover the otherwise-unreachable constructor line.
     *
     * @throws Exception if reflective instantiation fails
     */
    @Test
    public void testPrivateConstructor() throws Exception {
        Constructor<DepictionUtil> tmpConstructor = DepictionUtil.class.getDeclaredConstructor();
        tmpConstructor.setAccessible(true);
        Assertions.assertNotNull(tmpConstructor.newInstance());
    }
    //</editor-fold>
    //
    //<editor-fold desc="Private methods" defaultstate="collapsed">
    /**
     * Parses the given SMILES into a raw CDK atom container using a silent builder.
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
