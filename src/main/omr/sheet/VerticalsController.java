//----------------------------------------------------------------------------//
//                                                                            //
//                   V e r t i c a l s C o n t r o l l e r                    //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet;

import omr.check.CheckBoard;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.GlyphsModel;
import omr.glyph.Nest;
import omr.glyph.facets.Glyph;
import omr.glyph.ui.GlyphBoard;
import omr.glyph.ui.GlyphsController;
import omr.glyph.ui.NestView;

import omr.lag.Lag;
import omr.lag.ui.SectionBoard;

import omr.log.Logger;

import omr.run.RunBoard;

import omr.selection.GlyphEvent;
import omr.selection.MouseMovement;
import omr.selection.SelectionService;
import omr.selection.UserEvent;

import omr.sheet.ui.PixelBoard;
import omr.sheet.ui.SheetPainter;

import omr.step.Step;
import omr.step.Steps;

import omr.ui.BoardsPane;
import omr.ui.view.ScrollView;

import java.awt.Graphics2D;
import java.util.Arrays;

/**
 * Class <code>VerticalsController</code> is in charge of handling assignment
 * and deassignment of vertical entities (stems) at sheet level
 *
 * @author Hervé Bitteur
 */
public class VerticalsController
    extends GlyphsController
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
        VerticalsController.class);

    /** Events this entity is interested in */
    private static final Class[] eventClasses = new Class[] { GlyphEvent.class };

    //~ Instance fields --------------------------------------------------------

    /** Related user display if any */
    private MyView view;

    //~ Constructors -----------------------------------------------------------

    //---------------------//
    // VerticalsController //
    //---------------------//
    /**
     * Creates a new VerticalsController object.
     *
     * @param sheet the related sheet
     */
    public VerticalsController (Sheet sheet)
    {
        // We work with the sheet vertical lag
        super(
            new GlyphsModel(
                sheet,
                sheet.getNest(),
                Steps.valueOf(Steps.VERTICALS)));
    }

    //~ Methods ----------------------------------------------------------------

    //---------//
    // refresh //
    //---------//
    /**
     * Refresh the display if any, with proper colors for sections
     */
    public void refresh ()
    {
        if ((view == null) && constants.displayFrame.getValue()) {
            displayFrame();
        } else if (view != null) {
            view.refresh();
        }
    }

    //--------------//
    // displayFrame //
    //--------------//
    private void displayFrame ()
    {
        Lag vLag = sheet.getVerticalLag();

        // Specific rubber display
        view = new MyView(sheet.getNest());

        // Create a hosting frame for the view
        final String unit = sheet.getId() + ":VerticalsBuilder";

        sheet.getAssembly()
             .addViewTab(
            Step.VERTICALS_TAB,
            new ScrollView(view),
            new BoardsPane(
                new PixelBoard(unit, sheet),
                new RunBoard(unit, vLag),
                new SectionBoard(unit, vLag),
                new GlyphBoard(unit, this, true),
                new MyCheckBoard(
                    unit,
                    sheet.getNest().getGlyphService(),
                    eventClasses)));
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        Constant.Boolean displayFrame = new Constant.Boolean(
            true,
            "Should we display a frame on the stem sticks");
    }

    //--------------//
    // MyCheckBoard //
    //--------------//
    private class MyCheckBoard
        extends CheckBoard<Glyph>
    {
        //~ Constructors -------------------------------------------------------

        public MyCheckBoard (String           unit,
                             SelectionService eventService,
                             Class[]          eventList)
        {
            super(unit, null, eventService, eventList);
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public void onEvent (UserEvent event)
        {
            try {
                // Ignore RELEASING
                if (event.movement == MouseMovement.RELEASING) {
                    return;
                }

                if (event instanceof GlyphEvent) {
                    GlyphEvent glyphEvent = (GlyphEvent) event;
                    Glyph      glyph = glyphEvent.getData();

                    if (glyph instanceof Glyph) {
                        try {
                            Glyph      stick = (Glyph) glyph;
                            SystemInfo system = sheet.getSystemOf(stick);
                            // Get a fresh suite
                            setSuite(system.createStemCheckSuite(true));
                            tellObject(stick);
                        } catch (Exception ex) {
                            logger.warning("Glyph cannot be processed", ex);
                        }
                    } else {
                        tellObject(null);
                    }
                }
            } catch (Exception ex) {
                logger.warning(getClass().getName() + " onEvent error", ex);
            }
        }
    }

    //--------//
    // MyView //
    //--------//
    private final class MyView
        extends NestView
    {
        //~ Constructors -------------------------------------------------------

        public MyView (Nest nest)
        {
            super(
                nest,
                VerticalsController.this,
                Arrays.asList(sheet.getHorizontalLag(), sheet.getVerticalLag()));

            setLocationService(sheet.getLocationService());

            setName("VerticalsBuilder-MyView");
        }

        //~ Methods ------------------------------------------------------------

        //-------------//
        // renderItems //
        //-------------//
        @Override
        public void renderItems (Graphics2D g)
        {
            // Render all physical info known so far
            sheet.getPage()
                 .accept(new SheetPainter(g, false));

            super.renderItems(g);
        }
    }
}
