//----------------------------------------------------------------------------//
//                                                                            //
//                           C h e c k R e s u l t                            //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.check;


/**
 * Class <code>CheckResult</code> encapsulates the <b>result</b> of a check,
 * composed of a value (double) and a flag which can be RED, YELLOW or GREEN.
 *
 * @author Herv&eacute; Bitteur
 * @version $Id$
 */
public class CheckResult
{
    //~ Instance fields --------------------------------------------------------

    /** Numerical result value */
    public double value;

    /** Flag the result (RED, YELLOW, GREEN) */
    public int flag;
}
