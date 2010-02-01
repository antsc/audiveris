//----------------------------------------------------------------------------//
//                                                                            //
//                                 B o a r d                                  //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.ui;

import omr.log.Logger;

import omr.selection.SelectionService;
import omr.selection.UserEvent;

import omr.ui.util.Panel;

import omr.util.ClassUtil;

import org.bushe.swing.event.EventSubscriber;

import java.awt.*;
import java.util.Collection;

import javax.swing.*;

/**
 * Class <code>Board</code> defines the common properties of any user board
 * such as PixelBoard, SectionBoard, and the like.
 *
 * <p>By default, any board can have a related SelectionService, used for
 * subscribe (input) and publish (output). When {@link #connect} is called, the
 * board instance is subscribed to its SelectionService for a specific
 * collection of event classes. Similarly, {@link #disconnect} unsubscribes the
 * Board instance from the same event classes..
 *
 * <p>This is still an abstract class, since the onEvent() method must be
 * provided by every subclass.
 *
 * @author Hervé Bitteur
 */
public abstract class Board
    implements EventSubscriber<UserEvent>
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(Board.class);

    //~ Instance fields --------------------------------------------------------

    /** The swing component of the Board instance */
    protected final Panel component;

    /** The event service this board interacts with */
    protected final SelectionService selectionService;

    /** The collection of event classes to be observed */
    protected final Collection<Class<?extends UserEvent>> eventList;

    /** The Board instance name */
    protected String name;

    //~ Constructors -----------------------------------------------------------

    //-------//
    // Board //
    //-------//
    /**
     * Create a board
     *
     * @param name a name assigned to the board, for debug reason
     * @param selectionService the related selection service (for input & output)
     * @param eventList the collection of event classes to observe
     */
    public Board (String                                name,
                  SelectionService                      selectionService,
                  Collection<Class<?extends UserEvent>> eventList)
    {
        this.name = name;
        this.selectionService = selectionService;
        this.eventList = eventList;

        component = new Panel();
        component.setNoInsets();
    }

    //~ Methods ----------------------------------------------------------------

    //-------------//
    // emptyFields //
    //-------------//
    /**
     * Empty all the text fields of a given JComponent
     *
     * @param component the component to "blank".
     */
    public static void emptyFields (JComponent component)
    {
        for (Component comp : component.getComponents()) {
            if (comp instanceof JTextField) {
                ((JTextField) comp).setText("");
            }
        }
    }

    //--------------//
    // getComponent //
    //--------------//
    /**
     * Report the UI component
     *
     * @return the concrete component
     */
    public JPanel getComponent ()
    {
        return component;
    }

    //---------//
    // getName //
    //---------//
    /**
     * Report a distinct name for this board instance
     *
     * @return an instance name
     */
    public String getName ()
    {
        return name;
    }

    //---------//
    // connect //
    //---------//
    /**
     * Invoked when the board has been made visible, to connect to input
     * selections.
     */
    public void connect ()
    {
        ///logger.info("+Board " + tag + " Shown");
        if (eventList != null) {
            for (Class eventClass : eventList) {
                selectionService.subscribeStrongly(eventClass, this);
            }
        }
    }

    //------------//
    // disconnect //
    //------------//
    /**
     * Invoked when the board has been made invisible, to disconnect from input
     * selections.
     */
    public void disconnect ()
    {
        ///logger.info("-Board " + tag + " Hidden");
        if (eventList != null) {
            for (Class eventClass : eventList) {
                selectionService.unsubscribe(eventClass, this);
            }
        }
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        return ClassUtil.nameOf(this);
    }
}
