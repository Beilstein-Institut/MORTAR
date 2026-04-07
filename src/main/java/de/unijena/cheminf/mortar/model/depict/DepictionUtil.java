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

import de.unijena.cheminf.mortar.model.util.BasicDefinitions;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Util class for depiction.
 *
 * @author Felix Baensch
 * @author Jonas Schaub
 * @version 1.0.0.0
 */
public class DepictionUtil {
    //<editor-fold desc="Decimal format patterns enum">
    /**
     * Progressively shorter scientific-notation {@link DecimalFormat} patterns used by
     * {@link #fitIntegerDisplayToImageWidth(double, int)} to shrink an integer label until it fits
     * within the target image width.
     * <br>The constants are ordered from most precise to least precise; iteration over
     * {@link #values()} therefore tries the most detailed representation first.
     * <br>To avoid sharing mutable and non-thread-safe {@link DecimalFormat} instances across
     * threads, only the pattern strings are stored here.
     */
    public enum IntegerFormatPattern {
        /** Three decimal places in scientific notation, e.g. {@code 1.235E3}. */
        THREE_DECIMALS_SCIENTIFIC("0.000E0"),
        /** Two decimal places in scientific notation, e.g. {@code 1.24E3}. */
        TWO_DECIMALS_SCIENTIFIC("0.00E0"),
        /** One decimal place in scientific notation, e.g. {@code 1.2E3}. */
        ONE_DECIMAL_SCIENTIFIC("0.0E0"),
        /** No decimal places in scientific notation, e.g. {@code 1E3}. */
        ZERO_DECIMALS_SCIENTIFIC("0E0");

        /** The {@link DecimalFormat} pattern string represented by this constant. */
        private final String pattern;

        /**
         * Creates the constant with the given pattern string.
         *
         * @param aPattern the {@link DecimalFormat} pattern string
         */
        IntegerFormatPattern(String aPattern) {
            this.pattern = aPattern;
        }

        /**
         * Returns the {@link DecimalFormat} pattern string.
         *
         * @return pattern string, never {@code null}
         */
        public String getPattern() {
            return this.pattern;
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="private static final class variables" defaultstate="collapsed">
    /**
     * Logger of this class.
     */
    private static final Logger LOGGER = Logger.getLogger(DepictionUtil.class.getName());
    //
    /**
     * Standard font name for depicting structure images with text.
     */
    private static final String FONT_NAME_FOR_IMAGE_WITH_TEXT = "Calibri";
    //
    /**
     * Standard font size for depicting structure images with text.
     */
    private static final int FONT_SIZE_FOR_IMAGE_WITH_TEXT = 20;
    //
    /**
     * Standard font style for depicting structure images with text.
     */
    private static final int FONT_STYLE_FOR_IMAGE_WITH_TEXT = Font.BOLD;
    //</editor-fold>
    //
    //<editor-fold desc="private constructor">
    /**
     * Private parameter-less constructor.
     * Introduced because Javadoc build complained about classes without declared default constructor.
     */
    private DepictionUtil() {
    }
    //</editor-fold>
    //
    //<editor-fold desc="public static methods" defaultstate="collapsed">
    /**
     * Creates and returns an Image of the given AtomContainer. Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aWidth double for image width
     * @param aHeight  double for image height
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImage(IAtomContainer anAtomContainer, double aWidth, double aHeight) {
        return DepictionUtil.depictImageWithZoom(anAtomContainer, 1.0, aWidth, aHeight);
    }
    //
    /**
     * Creates and returns an Image of the AtomContainer with given height and default width (250.0). Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aHeight double
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithHeight(IAtomContainer anAtomContainer, double aHeight) {
        return DepictionUtil.depictImageWithZoom(anAtomContainer, 1.0, BasicDefinitions.DEFAULT_IMAGE_WIDTH_DEFAULT, aHeight);
    }
    //
    /**
     * Creates and returns an Image of the AtomContainer with given width and default height (250.0). Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aWidth double
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithWidth(IAtomContainer anAtomContainer, double aWidth) {
        return DepictionUtil.depictImageWithZoom(anAtomContainer, 1.0, aWidth, BasicDefinitions.DEFAULT_IMAGE_HEIGHT_DEFAULT);
    }
    //
    /**
     * Creates and returns an Image of the AtomContainer with any zoom factor and default width (250.0) and height (250.0).
     * Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithZoom(IAtomContainer anAtomContainer, double aZoom) {
        return DepictionUtil.depictImageWithZoom(anAtomContainer, aZoom, BasicDefinitions.DEFAULT_IMAGE_WIDTH_DEFAULT, BasicDefinitions.DEFAULT_IMAGE_HEIGHT_DEFAULT);
    }
    /**
     * Creates and returns an Image of the AtomContainer with any zoom factor and given width and height.
     * Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithZoom(IAtomContainer anAtomContainer, double aZoom, double aWidth, double aHeight) {
        return DepictionUtil.depictImageWithZoomAndFillToFit(anAtomContainer, aZoom, aWidth, aHeight, false);
    }
    //
    /**
     * Creates and returns an Image of the AtomContainer with any zoom factor and given width and height and fill to fit.
     * Background will be transparent.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @param fillToFit boolean Resize depictions to fill all available space (only if a size is specified)
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithZoomAndFillToFit(IAtomContainer anAtomContainer, double aZoom, double aWidth, double aHeight, boolean fillToFit) {
        try {
            BufferedImage tmpBufferedImage = DepictionUtil.depictBufferedImageWithZoom(anAtomContainer, aZoom, aWidth, aHeight, fillToFit, false);
            return SwingFXUtils.toFXImage(tmpBufferedImage, null);
        } catch (CDKException | NullPointerException anException) {
            DepictionUtil.LOGGER.log(Level.SEVERE, anException.toString(), anException);
            return DepictionUtil.depictErrorImage(anException.getMessage(), 250,250);
        }
    }
    //
    /**
     * Creates and returns an Image of the AtomContainer with any zoom factor and given width and height and fill to fit
     * and a white background.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @param fillToFit boolean Resize depictions to fill all available space (only if a size is specified)
     * @param isBackgroundWhite true if the image should have an opaque white background; false if the background
     *                          should be transparent
     * @return Image of 2D structure of IAtomContainer
     */
    public static Image depictImageWithZoomAndFillToFitAndWhiteBackground(IAtomContainer anAtomContainer, double aZoom, double aWidth, double aHeight, boolean fillToFit, boolean isBackgroundWhite) {
        try {
            BufferedImage tmpBufferedImage = DepictionUtil.depictBufferedImageWithZoom(anAtomContainer, aZoom, aWidth, aHeight, fillToFit, isBackgroundWhite);
            return SwingFXUtils.toFXImage(tmpBufferedImage, null);
        } catch (CDKException | NullPointerException anException) {
            DepictionUtil.LOGGER.log(Level.SEVERE, anException.toString(), anException);
            return DepictionUtil.depictErrorImage(anException.getMessage(), 250,250);
        }
    }
    //
    /**
     * Creates and returns an image of the given message.
     *
     * @param aMessage String
     * @param aWidth int
     * @param aHeight int
     * @return Image of given String
     */
    public static Image depictErrorImage(String aMessage, int aWidth, int aHeight) {
        String tmpMessage = Objects.requireNonNullElse(aMessage, "Error");
        BufferedImage tmpBufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tmpGraphic = tmpBufferedImage.createGraphics();
        Font tmpFont = new Font("Arial", Font.PLAIN, 20);
        tmpGraphic.setFont(tmpFont);
        tmpGraphic.dispose();
        tmpBufferedImage = new BufferedImage(aWidth, aHeight, BufferedImage.TYPE_INT_ARGB);
        tmpGraphic = tmpBufferedImage.createGraphics();
        tmpGraphic.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        tmpGraphic.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        tmpGraphic.setFont(tmpFont);
        FontMetrics tmpFontMetrics = tmpGraphic.getFontMetrics();
        tmpGraphic.setColor(Color.BLACK);
        tmpGraphic.drawString(tmpMessage, 0, tmpFontMetrics.getAscent());
        tmpGraphic.dispose();
        tmpGraphic.drawImage(tmpBufferedImage, 0, 0, null);
        return SwingFXUtils.toFXImage(tmpBufferedImage, null);
    }
    //
    /**
     * Checks whether the given text would be wider than the image if both are used with depictImageWithText().
     *
     * @param anImageWidth the width of the image that is intended to be generated
     * @param aText the text that is supposed to be included in the image
     * @return true if the text would be wider than the image; false otherwise
     */
    public static boolean isTextWiderThanImage(double anImageWidth, String aText) {
        //the image is only created to obtain the Graphics2D instance; its width does not matter
        BufferedImage tmpBufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D tmpGraphics2d = tmpBufferedImage.createGraphics();
        try {
            DepictionUtil.configureGraphics2D(tmpGraphics2d);
            Font tmpFont = new Font(
                    DepictionUtil.FONT_NAME_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_STYLE_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_SIZE_FOR_IMAGE_WITH_TEXT);
            tmpGraphics2d.setFont(tmpFont);
            FontMetrics tmpFontMetrics = tmpGraphics2d.getFontMetrics();
            int tmpTextWidth = tmpFontMetrics.stringWidth(aText);
            return tmpTextWidth > anImageWidth;
        } finally {
            tmpGraphics2d.dispose();
        }
    }
    //
    /**
     * Formats the given integer value using progressively shorter decimal formats until the resulting text is not
     * wider than the given image width (using the default text font). If all formats are too wide, scientific notation
     * with no decimals will be used as a last resort. Note that no initial check is performed whether the given value
     * in standard String notation is actually wider than the given image width. This must be done by the calling code!
     *
     * @param anImageWidth the width of the image that is intended to be generated
     * @param aValue the value that is supposed to be included in the image
     * @return a String representation of the given value that is not wider than the given image width when using the default text font
     */
    public static String fitIntegerDisplayToImageWidth(double anImageWidth, int aValue) {
        String tmpResult = String.valueOf(aValue);
        Locale tmpDefaultLocale = Locale.getDefault();
        DecimalFormatSymbols tmpSymbols = new DecimalFormatSymbols(tmpDefaultLocale);
        //the image is only created to obtain the Graphics2D instance; its width does not matter
        BufferedImage tmpBufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D tmpGraphics2d = tmpBufferedImage.createGraphics();
        try {
            DepictionUtil.configureGraphics2D(tmpGraphics2d);
            Font tmpFont = new Font(DepictionUtil.FONT_NAME_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_STYLE_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_SIZE_FOR_IMAGE_WITH_TEXT);
            tmpGraphics2d.setFont(tmpFont);
            FontMetrics tmpFontMetrics = tmpGraphics2d.getFontMetrics();
            for (IntegerFormatPattern tmpFormatPattern : IntegerFormatPattern.values()) {
                DecimalFormat tmpDecimalFormat = new DecimalFormat(tmpFormatPattern.getPattern(), tmpSymbols);
                tmpResult = tmpDecimalFormat.format(aValue);
                int tmpTextWidth = tmpFontMetrics.stringWidth(tmpResult);
                //not using isTextWiderThanImage() here to not create new graphics every iteration
                if (tmpTextWidth < anImageWidth) {
                    return tmpResult;
                }
            }
            // Last resort: return the formatted string produced last, since it is the shortest one possible
            return tmpResult;
        } finally {
            tmpGraphics2d.dispose();
        }
    }
    //
    /**
     * Depicts and returns an image with the given text below. Background will be transparent and fill to fit turned off.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @param aString String
     * @return Image of 2D structure of IAtomContainer with given String below
     */
    public static Image depictImageWithText(
            IAtomContainer anAtomContainer,
            double aZoom,
            double aWidth,
            double aHeight,
            String aString
    ) {
        return DepictionUtil.depictImageWithText(anAtomContainer, aZoom, aWidth, aHeight, aString, false, false);
    }
    //
    /**
     * Depicts and returns an image with the given text below.
     *
     * @param anAtomContainer the molecule to depict
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @param aText text label
     * @param aFillToFit if true, resize depictions to fill all available space
     * @param anIsBackgroundWhite true if the image should have an opaque white background; false if the background should be transparent
     * @return Image of 2D structure of IAtomContainer with given text below
     */
    public static Image depictImageWithText(
            IAtomContainer anAtomContainer,
            double aZoom,
            double aWidth,
            double aHeight,
            String aText,
            boolean aFillToFit,
            boolean anIsBackgroundWhite
    ) {
        try {
            BufferedImage tmpMolBufferedImage = DepictionUtil.depictBufferedImageWithZoom(
                    anAtomContainer,
                    aZoom,
                    aWidth,
                    aHeight - BasicDefinitions.DEFAULT_IMAGE_TEXT_LABEL_HEIGHT,
                    aFillToFit,
                    anIsBackgroundWhite);
            BufferedImage tmpBufferedImage = new BufferedImage(
                    tmpMolBufferedImage.getWidth(),
                    tmpMolBufferedImage.getHeight() + BasicDefinitions.DEFAULT_IMAGE_TEXT_LABEL_HEIGHT,
                    anIsBackgroundWhite ? Transparency.OPAQUE : Transparency.TRANSLUCENT);
            Graphics2D tmpGraphics2d = tmpBufferedImage.createGraphics();
            DepictionUtil.configureGraphics2D(tmpGraphics2d);
            if (anIsBackgroundWhite) {
                tmpGraphics2d.setColor(Color.WHITE);
                tmpGraphics2d.fillRect(0, 0, tmpBufferedImage.getWidth(), tmpBufferedImage.getHeight());
            }
            tmpGraphics2d.drawImage(tmpMolBufferedImage, 0, 0,null);
            tmpGraphics2d.setColor(Color.BLACK);
            tmpGraphics2d.setFont(new Font(DepictionUtil.FONT_NAME_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_STYLE_FOR_IMAGE_WITH_TEXT,
                    DepictionUtil.FONT_SIZE_FOR_IMAGE_WITH_TEXT));
            FontMetrics tmpFontMetric = tmpGraphics2d.getFontMetrics();
            int tmpTextWidth = tmpFontMetric.stringWidth(aText);
            tmpGraphics2d.drawString(
                    aText,
                    (tmpBufferedImage.getWidth() / 2) - tmpTextWidth / 2,
                    // - 7 magic number to align text, i.e. add a spacing between the bottom of the image and the text
                    tmpBufferedImage.getHeight() - 7);
            tmpGraphics2d.dispose();
            return SwingFXUtils.toFXImage(tmpBufferedImage, null);
        } catch (CDKException anException) {
            DepictionUtil.LOGGER.log(Level.SEVERE, anException.toString(), anException);
            return DepictionUtil.depictErrorImage(anException.getMessage(), 250,250);
        }
    }
    //</editor-fold>
    //
    //<editor-fold desc="private static methods" defaultstate="collapsed">
    /**
     * Creates and returns a BufferedImage of given IAtomContainer.
     *
     * @param anAtomContainer IAtomContainer
     * @param aZoom double
     * @param aWidth double
     * @param aHeight double
     * @param fillToFit boolean
     * @param isBackgroundWhite true if the image should have an opaque white background; false if the background
     *                          should be transparent
     * @return BufferedImage of given IAtomContainer
     * @throws CDKException if a depiction cannot be generated
     */
    private static BufferedImage depictBufferedImageWithZoom(IAtomContainer anAtomContainer, double aZoom, double aWidth, double aHeight, boolean fillToFit, boolean isBackgroundWhite) throws CDKException {
        DepictionGenerator tmpGenerator = new DepictionGenerator()
                .withAtomColors()
                .withAromaticDisplay()
                .withSize(aWidth, aHeight)
                .withZoom(aZoom)
                //color is white by sRGB values and fourth param alpha decides over opaque (1.0f) and transparent (0.0f)
                .withBackgroundColor(new Color(1.0f, 1.0f, 1.0f, isBackgroundWhite? 1.0f : 0.0f));
        if (fillToFit) {
            tmpGenerator = tmpGenerator.withFillToFit();
        }
        return tmpGenerator.depict(anAtomContainer).toImg();
    }
    //
    /**
     * Configures the given Graphics2D instance according to the MORTAR standard.
     *
     * @param aGraphics2d Graphics2D instance
     */
    private static void configureGraphics2D(Graphics2D aGraphics2d) {
        aGraphics2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        aGraphics2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        aGraphics2d.addRenderingHints(new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON));
    }
    //</editor-fold>
}
