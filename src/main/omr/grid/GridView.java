//----------------------------------------------------------------------------//
//                                                                            //
//                              G r i d V i e w                               //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.grid;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.Nest;
import omr.glyph.ui.GlyphsController;
import omr.glyph.ui.NestView;

import omr.lag.Lag;

import omr.log.Logger;

import omr.ui.Colors;
import omr.ui.util.UIUtilities;

import java.awt.Graphics2D;
import java.awt.Stroke;
import java.util.Arrays;

/**
 * Class {@code GridView} is a special {@link NestView}, meant as a
 * companion of {@link GridBuilder} with its 2 lags (horizontal & vertical).
 * <p>We paint on the same display the vertical and horizontal sections.
 *
 * @author Hervé Bitteur
 */
public class GridView
    extends NestView
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(GridBuilder.class);

    //~ Instance fields --------------------------------------------------------

    // Companion for horizontals (staff lines)
    private final LinesRetriever linesRetriever;

    // Companion for verticals (barlines)
    private final BarsRetriever barsRetriever;

    //~ Constructors -----------------------------------------------------------

    //----------//
    // GridView //
    //----------//
    /**
     * Creates a new GridView object.
     * @param nest the related nest instance
     * @param linesRetriever the related lines retriever
     * @param hLag horizontal lag
     * @param barsRetriever the related bars retriever
     * @param vLag vertical lag
     * @param controller glyphs controller
     */
    public GridView (Nest             nest,
                     LinesRetriever   linesRetriever,
                     Lag              hLag,
                     BarsRetriever    barsRetriever,
                     Lag              vLag,
                     GlyphsController controller)
    {
        super(nest, controller, Arrays.asList(hLag, vLag));

        setName("Grid-View");
        this.linesRetriever = linesRetriever;
        this.barsRetriever = barsRetriever;
    }

    //~ Methods ----------------------------------------------------------------

    //-------------//
    // renderItems //
    //-------------//
    @Override
    protected void renderItems (Graphics2D g)
    {
        final boolean showTangents = constants.showTangents.getValue();
        final boolean showCombs = constants.showCombs.getValue();
        final Stroke  oldStroke = UIUtilities.setAbsoluteStroke(g, 1f);
        g.setColor(Colors.ENTITY_MINOR);

        // Horizontal items
        linesRetriever.renderItems(g, showTangents, showCombs);

        // Vertical items
        barsRetriever.renderItems(g, showTangents);

        g.setStroke(oldStroke);
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        Constant.Boolean showTangents = new Constant.Boolean(
            false,
            "Should we show filament ending tangents?");
        Constant.Boolean showCombs = new Constant.Boolean(
            false,
            "Should we show staff lines combs?");
    }
}
