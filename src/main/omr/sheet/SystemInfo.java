//----------------------------------------------------------------------------//
//                                                                            //
//                            S y s t e m I n f o                             //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet;

import omr.check.CheckSuite;

import omr.glyph.CompoundBuilder;
import omr.glyph.GlyphInspector;
import omr.glyph.Glyphs;
import omr.glyph.GlyphsBuilder;
import omr.glyph.facets.Glyph;
import omr.glyph.pattern.PatternsChecker;
import omr.glyph.pattern.SlurInspector;
import omr.glyph.text.Sentence;

import omr.grid.BarAlignment;
import omr.grid.BarInfo;
import omr.grid.LineInfo;
import omr.grid.StaffInfo;

import omr.lag.Section;

import omr.log.Logger;

import omr.score.SystemTranslator;
import omr.score.common.PixelDimension;
import omr.score.common.PixelPoint;
import omr.score.common.PixelRectangle;
import omr.score.entity.ScoreSystem;
import omr.score.entity.Staff;
import omr.score.entity.SystemPart;

import omr.step.StepException;

import omr.util.HorizontalSide;
import static omr.util.HorizontalSide.*;
import omr.util.Navigable;
import omr.util.Predicate;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Class <code>SystemInfo</code> gathers information from the original picture
 * about a retrieved system. Most of the physical processing is done in parallel
 * at system level, and thus is handled from this SystemInfo object.
 *
 * <p>Many processing tasks are actually handled by companion classes, but
 * SystemInfo is the interface of choice, with delegation to the proper
 * companion.
 *
 * <p>Nota: All measurements are assumed to be in pixels.
 *
 * @author Hervé Bitteur
 */
public class SystemInfo
    implements Comparable<SystemInfo>
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(SystemInfo.class);

    //~ Instance fields --------------------------------------------------------

    /** Related sheet */
    @Navigable(false)
    private final Sheet sheet;

    /** Dedicated measure builder */
    private final MeasuresBuilder measuresBuilder;

    /** Dedicated glyph builder */
    private final GlyphsBuilder glyphsBuilder;

    /** Dedicated compound builder */
    private final CompoundBuilder compoundBuilder;

    /** Dedicated verticals builder */
    private final VerticalsBuilder verticalsBuilder;

    /** Dedicated horizontals builder */
    private final HorizontalsBuilder horizontalsBuilder;

    /** Dedicated glyph inspector */
    private final GlyphInspector glyphInspector;

    /** Dedicated slur inspector */
    private final SlurInspector slurInspector;

    /** Dedicated system translator */
    private final SystemTranslator translator;

    /** Staves of this system */
    private List<StaffInfo> staves = new ArrayList<StaffInfo>();

    /** Parts in this system */
    private final List<PartInfo> parts = new ArrayList<PartInfo>();

    /** Related System in Score hierarchy */
    private ScoreSystem scoreSystem;

    /** Left system bar, if any */
    private BarInfo leftBar;

    /** Right system bar, if any */
    private BarInfo rightBar;

    /** Left system limit  (a filament or a straight line) */
    private Object leftLimit;

    /** Right system limit  (a filament or a straight line) */
    private Object rightLimit;

    /** Bar alignments for this system */
    private List<BarAlignment> barAlignments;

    ///   HORIZONTALS   ////////////////////////////////////////////////////////

    /** Horizontal sections, assigned once for all to this system */
    private final List<Section> hSections = new ArrayList<Section>();

    /** Unmodifiable view of the horizontal section collection */
    private final Collection<Section> hSectionsView = Collections.unmodifiableCollection(
        hSections);

    /** Retrieved ledgers in this system */
    private final List<Glyph> ledgers = new ArrayList<Glyph>();

    /** Retrieved tenuto signs in this system */
    private final List<Glyph> tenutos = new ArrayList<Glyph>();

    /** Retrieved endings in this system */
    private final List<Glyph> endings = new ArrayList<Glyph>();

    ///   VERTICALS   //////////////////////////////////////////////////////////

    /** Vertical sections, assigned once for all to this system */
    private final List<Section> vSections = new ArrayList<Section>();

    /** Unmodifiable view of the vertical section collection */
    private final Collection<Section> vSectionsView = Collections.unmodifiableCollection(
        vSections);

    /** Collection of (active?) glyphs in this system */
    private final SortedSet<Glyph> glyphs = new ConcurrentSkipListSet<Glyph>(
        Glyph.abscissaComparator);

    /** Unmodifiable view of the glyphs collection */
    private final SortedSet<Glyph> glyphsView = Collections.unmodifiableSortedSet(
        glyphs);

    /** Set of sentences made of text glyphs */
    private Set<Sentence> sentences = new LinkedHashSet<Sentence>();

    /** Used to assign a unique ID to system sentences */
    private int sentenceCount = 0;

    ////////////////////////////////////////////////////////////////////////////

    /** Unique Id for this system (in the sheet) */
    private final int id;

    /** Boundary that encloses all items of this system */
    private SystemBoundary boundary;

    /** Ordinate of bottom of last staff of the system. */
    private int bottom;

    /** Delta ordinate between first line of first staff & first line of last
       staff. */
    private int deltaY;

    /** Abscissa of beginning of system. */
    private int left;

    /** Ordinate of top of first staff of the system. */
    private int top;

    /** Width of the system. */
    private int width = -1;

    //~ Constructors -----------------------------------------------------------

    //------------//
    // SystemInfo //
    //------------//
    /**
     * Create a SystemInfo entity, to register the provided parameters
     *
     * @param id the unique identity
     * @param sheet the containing sheet
     * @param staves the (initial) sequence of staves
     */
    public SystemInfo (int             id,
                       Sheet           sheet,
                       List<StaffInfo> staves)
    {
        this.id = id;
        this.sheet = sheet;
        this.staves = staves;

        updateCoordinates();

        measuresBuilder = new MeasuresBuilder(this);
        glyphsBuilder = new GlyphsBuilder(this);
        compoundBuilder = new CompoundBuilder(this);
        verticalsBuilder = new VerticalsBuilder(this);
        horizontalsBuilder = new HorizontalsBuilder(this);
        glyphInspector = new GlyphInspector(this);
        slurInspector = new SlurInspector(this);
        translator = new SystemTranslator(this);
    }

    //~ Methods ----------------------------------------------------------------

    //--------//
    // setBar //
    //--------//
    /**
     * @param side proper horizontal side
     * @param bar the bar to set
     */
    public void setBar (HorizontalSide side,
                        BarInfo        bar)
    {
        if (side == HorizontalSide.LEFT) {
            this.leftBar = bar;
        } else {
            this.rightBar = bar;
        }
    }

    //--------//
    // getBar //
    //--------//
    /**
     * @param side proper horizontal side
     * @return the system bar on this side, or null
     */
    public BarInfo getBar (HorizontalSide side)
    {
        if (side == HorizontalSide.LEFT) {
            return leftBar;
        } else {
            return rightBar;
        }
    }

    //------------------//
    // setBarAlignments //
    //------------------//
    /**
     * @param barAlignments the barAlignments to set
     */
    public void setBarAlignments (List<BarAlignment> barAlignments)
    {
        this.barAlignments = barAlignments;
    }

    //------------------//
    // getBarAlignments //
    //------------------//
    /**
     * @return the barAlignments
     */
    public List<BarAlignment> getBarAlignments ()
    {
        return barAlignments;
    }

    //-----------//
    // getBottom //
    //-----------//
    /**
     * Report the ordinate of the bottom of the system, which is the ordinate of
     * the last line of the last staff of this system
     *
     * @return the system bottom, in pixels
     */
    public int getBottom ()
    {
        return bottom;
    }

    //-------------//
    // setBoundary //
    //-------------//
    /**
     * Define the precise boundary of this system
     * @param boundary the (new) boundary
     */
    public void setBoundary (SystemBoundary boundary)
    {
        this.boundary = boundary;
    }

    //-------------//
    // getBoundary //
    //-------------//
    /**
     * Report the precise boundary of this system
     * @return the precise system boundary
     */
    public SystemBoundary getBoundary ()
    {
        return boundary;
    }

    //-----------//
    // getBounds //
    //-----------//
    /**
     * Report the rectangular bounds that enclose this system
     * @return the system rectangular bounds
     */
    public PixelRectangle getBounds ()
    {
        if (boundary != null) {
            return new PixelRectangle(boundary.getBounds());
        } else {
            return null;
        }
    }

    //--------------------//
    // getCompoundBuilder //
    //--------------------//
    public CompoundBuilder getCompoundBuilder ()
    {
        return compoundBuilder;
    }

    //-----------//
    // getDeltaY //
    //-----------//
    /**
     * Report the deltaY of the system, that is the difference in ordinate
     * between first and last staves of the system. This deltaY is of course 0
     * for a one-staff system.
     *
     * @return the deltaY value, expressed in pixels
     */
    public int getDeltaY ()
    {
        return deltaY;
    }

    //------------//
    // getEndings //
    //------------//
    /**
     * Report the collection of endings found
     * @return the endings collection
     */
    public List<Glyph> getEndings ()
    {
        return endings;
    }

    //---------------//
    // getFirstStaff //
    //---------------//
    /**
     * @return the first staff
     */
    public StaffInfo getFirstStaff ()
    {
        return staves.get(0);
    }

    //-----------//
    // getGlyphs //
    //-----------//
    /**
     * Report the unmodifiable collection of glyphs within the system area
     *
     * @return the unmodifiable collection of glyphs
     */
    public SortedSet<Glyph> getGlyphs ()
    {
        return glyphsView;
    }

    //-----------------------//
    // getHorizontalSections //
    //-----------------------//
    /**
     * Report the (unmodifiable) collection of horizontal sections in the system
     * related area
     *
     * @return the area horizontal sections
     */
    public Collection<Section> getHorizontalSections ()
    {
        return hSectionsView;
    }

    //-------//
    // getId //
    //-------//
    /**
     * Report the id (debugging info) of the system info
     *
     * @return the id
     */
    public int getId ()
    {
        return id;
    }

    //--------------//
    // getLastStaff //
    //--------------//
    /**
     * @return the lastStaff
     */
    public StaffInfo getLastStaff ()
    {
        return staves.get(staves.size() - 1);
    }

    //------------//
    // getLedgers //
    //------------//
    /**
     * Report the collection of ledgers found
     * @return the ledger collection
     */
    public List<Glyph> getLedgers ()
    {
        return ledgers;
    }

    //---------//
    // getLeft //
    //---------//
    /**
     * Report the left abscissa
     *
     * @return the left abscissa value, expressed in pixels
     */
    public int getLeft ()
    {
        return left;
    }

    //----------//
    // setLimit //
    //----------//
    /**
     * @param side proper horizontal side
     * @param limit the limit to set
     */
    public void setLimit (HorizontalSide side,
                          Object         limit)
    {
        if (side == HorizontalSide.LEFT) {
            this.leftLimit = limit;
        } else {
            this.rightLimit = limit;
        }
    }

    //---------//
    // getLimit //
    //---------//
    /**
     * @param side proper horizontal side
     * @return the leftBar
     */
    public Object getLimit (HorizontalSide side)
    {
        if (side == HorizontalSide.LEFT) {
            return leftLimit;
        } else {
            return rightLimit;
        }
    }

    //--------------//
    // getLogPrefix //
    //--------------//
    /**
     * Report the proper prefix to use when logging a message
     * @return the proper prefix
     */
    public String getLogPrefix ()
    {
        StringBuilder sb = new StringBuilder(sheet.getLogPrefix());

        if (sb.length() > 1) {
            sb.insert(sb.length() - 1, "S" + id);
        } else {
            sb.append("S")
              .append(id)
              .append(" ");
        }

        return sb.toString();
    }

    //------------------------------//
    // getMutableHorizontalSections //
    //------------------------------//
    /**
     * Report the (modifiable) collection of horizontal sections in the system
     * related area
     *
     * @return the area vertical sections
     */
    public Collection<Section> getMutableHorizontalSections ()
    {
        return hSections;
    }

    //----------------------------//
    // getMutableVerticalSections //
    //----------------------------//
    /**
     * Report the (modifiable) collection of vertical sections in the system
     * related area
     *
     * @return the area vertical sections
     */
    public Collection<Section> getMutableVerticalSections ()
    {
        return vSections;
    }

    //------------------//
    // getNewSentenceId //
    //------------------//
    /**
     * Report the id for a new sentence
     * @return the next id
     */
    public int getNewSentenceId ()
    {
        return ++sentenceCount;
    }

    //----------//
    // getParts //
    //----------//
    /**
     * Reports the parts of this system
     * @return the parts (non-null)
     */
    public List<PartInfo> getParts ()
    {
        return parts;
    }

    //----------//
    // getRight //
    //----------//
    /**
     * Report the abscissa of the end of the system
     *
     * @return the right abscissa, expressed in pixels
     */
    public int getRight ()
    {
        return left + width;
    }

    //----------------//
    // getScoreSystem //
    //----------------//
    /**
     * Report the related logical score system
     *
     * @return the logical score System counterpart
     */
    public ScoreSystem getScoreSystem ()
    {
        return scoreSystem;
    }

    //------------------//
    // getSlurInspector //
    //------------------//
    public SlurInspector getSlurInspector ()
    {
        return slurInspector;
    }

    //------------//
    // getStaffAt //
    //------------//
    /**
     * Given a point, retrieve the closest staff within the system
     *
     * @param point the provided point
     * @return the "containing" staff
     */
    public StaffInfo getStaffAt (PixelPoint point)
    {
        return sheet.getStaffManager()
                    .getStaffAt(point);
    }

    //-----------//
    // setStaves //
    //-----------//
    /**
     * @param staves the range of staves
     */
    public void setStaves (List<StaffInfo> staves)
    {
        this.staves = staves;
        updateCoordinates();
    }

    //-----------//
    // getStaves //
    //-----------//
    /**
     * Report the list of staves that compose this system
     *
     * @return the staves
     */
    public List<StaffInfo> getStaves ()
    {
        return staves;
    }

    //------------//
    // getTenutos //
    //------------//
    /**
     * Report the collection of tenutos found
     * @return the tenutos collection
     */
    public List<Glyph> getTenutos ()
    {
        return tenutos;
    }

    //--------//
    // getTop //
    //--------//
    /**
     * Report the ordinate of the top of this system
     *
     * @return the top ordinate, expressed in pixels
     */
    public int getTop ()
    {
        return top;
    }

    //---------------------//
    // getVerticalSections //
    //---------------------//
    /**
     * Report the (unmodifiable) collection of vertical sections in the system
     * related area
     *
     * @return the area vertical sections
     */
    public Collection<Section> getVerticalSections ()
    {
        return vSectionsView;
    }

    //----------//
    // getWidth //
    //----------//
    /**
     * Report the width of the system
     *
     * @return the width value, expressed in pixels
     */
    public int getWidth ()
    {
        return width;
    }

    //----------//
    // addGlyph //
    //----------//
    /**
     * Add a brand new glyph as an active glyph in proper system and lag.
     * If the glyph is a compound, its parts are made pointing back to it and
     * are made no longer active glyphs. To just register a glyph (without
     * impacting its sections), use {@link #registerGlyph} instead.
     *
     * <p><b>Note</b>: The caller must use the returned glyph since it may be
     * different from the provided glyph (this happens when an original glyph
     * with same signature existed before this one)
     *
     * @param glyph the brand new glyph
     * @return the original glyph as inserted in the glyph lag. Use this entity
     * instead of the provided one.
     * @see #registerGlyph
     */
    public Glyph addGlyph (Glyph glyph)
    {
        return glyphsBuilder.addGlyph(glyph);
    }

    //---------//
    // addPart //
    //---------//
    /**
     * Add a part (set of staves) in this system
     * @param partInfo the part to add
     */
    public void addPart (PartInfo partInfo)
    {
        parts.add(partInfo);
    }

    //-----------------------//
    // addToGlyphsCollection //
    //-----------------------//
    /**
     * This is a private entry meant for GlyphsBuilder only.
     * The standard entry is {@link #addGlyph}
     * @param glyph the glyph to add to the system glyph collection
     */
    public void addToGlyphsCollection (Glyph glyph)
    {
        glyphs.add(glyph);
    }

    //------------------------//
    // allocateScoreStructure //
    //------------------------//
    /**
     * Build the corresponding ScoreSystem entity with all its depending Parts
     * and Staves
     */
    public void allocateScoreStructure ()
    {
        // Allocate the score system
        scoreSystem = new ScoreSystem(
            this,
            sheet.getPage(),
            new PixelPoint(getLeft(), getTop()),
            new PixelDimension(getWidth(), getDeltaY()));

        // Allocate the parts in the system
        int partId = 0;

        for (PartInfo partInfo : getParts()) {
            SystemPart part = new SystemPart(scoreSystem);
            part.setId(--partId); // Temporary id

            // Allocate the staves in this part
            for (StaffInfo staffInfo : partInfo.getStaves()) {
                LineInfo firstLine = staffInfo.getFirstLine();
                LineInfo lastLine = staffInfo.getLastLine();
                new Staff(
                    staffInfo,
                    part,
                    new PixelPoint(left, firstLine.yAt(left)),
                    (int) Math.rint(staffInfo.getAbscissa(RIGHT) - left),
                    lastLine.yAt(left) - firstLine.yAt(left));
            }
        }
    }

    //------------//
    // buildGlyph //
    //------------//
    /**
     * Build a glyph from a collection of sections, and make the sections point
     * back to the glyph
     * @param sections the provided members of the future glyph
     * @return the newly built glyph
     */
    public Glyph buildGlyph (Collection<Section> sections)
    {
        return glyphsBuilder.buildGlyph(sections);
    }

    //---------------//
    // buildMeasures //
    //---------------//
    /**
     * Based on barlines found, build, check and cleanup score measures
     */
    public void buildMeasures ()
    {
        measuresBuilder.buildMeasures();
    }

    //------------------------//
    // buildTransientCompound //
    //------------------------//
    /**
     * Make a new glyph out of a collection of (sub) glyphs, by merging all
     * their member sections. This compound is transient, since until it is
     * properly inserted by use of {@link #addGlyph}, this building has no
     * impact on either the containing lag, the containing system, nor the
     * contained sections themselves.
     *
     * @param parts the collection of (sub) glyphs
     * @return the brand new (compound) glyph
     */
    public Glyph buildTransientCompound (Collection<Glyph> parts)
    {
        return glyphsBuilder.buildTransientCompound(parts);
    }

    //---------------------//
    // buildTransientGlyph //
    //---------------------//
    /**
     * Make a new glyph out of a collection of sections.
     * This glyph is transient, since until it is properly inserted by use of
     * {@link #addGlyph}, this building has no impact on either the containing
     * lag, the containing system, nor the contained sections themselves.
     *
     * @param sections the collection of sections
     * @return the brand new transient glyph
     */
    public Glyph buildTransientGlyph (Collection<Section> sections)
    {
        return glyphsBuilder.buildTransientGlyph(sections);
    }

    //-----------------//
    // checkBoundaries //
    //-----------------//
    /**
     * Check this system for glyphs that cross the system boundaries
     */
    public void checkBoundaries ()
    {
        glyphsBuilder.retrieveGlyphs(false);
    }

    //-------------//
    // clearGlyphs //
    //-------------//
    /**
     * Empty the system glyph collection
     */
    public void clearGlyphs ()
    {
        glyphs.clear();
    }

    //-----------//
    // compareTo //
    //-----------//
    /**
     * Needed to implement natural SystemInfo sorting, based on system id
     * @param o the other system to compare to
     * @return the comparison result
     */
    public int compareTo (SystemInfo o)
    {
        return Integer.signum(id - o.id);
    }

    //----------------------//
    // computeGlyphFeatures //
    //----------------------//
    /**
     * Compute all the features that will be used to recognize the glyph at hand
     * (it's a mix of moments plus a few other characteristics)
     *
     * @param glyph the glyph at hand
     */
    public void computeGlyphFeatures (Glyph glyph)
    {
        glyphsBuilder.computeGlyphFeatures(glyph);
    }

    //----------------------//
    // createStemCheckSuite //
    //----------------------//
    /**
     * Build a check suite for stem retrievals
     * @param isShort are we looking for short (vs standard) stems?
     * @return the newly built check suite
     * @throws omr.step.StepException
     */
    public CheckSuite<Glyph> createStemCheckSuite (boolean isShort)
        throws StepException
    {
        return verticalsBuilder.createStemCheckSuite(isShort);
    }

    //------------//
    // dumpGlyphs //
    //------------//
    /**
     * Dump all glyphs handled by this system
     */
    public void dumpGlyphs ()
    {
        dumpGlyphs(null);
    }

    //------------//
    // dumpGlyphs //
    //------------//
    /**
     * Dump the glyphs handled by this system and that are contained by the
     * provided rectangle
     *
     * @param rect the region of interest
     */
    public void dumpGlyphs (PixelRectangle rect)
    {
        for (Glyph glyph : getGlyphs()) {
            if ((rect == null) || (rect.contains(glyph.getContourBox()))) {
                System.out.println(
                    (glyph.isActive() ? "active " : "       ") +
                    (glyph.isKnown() ? "known " : "      ") +
                    (glyph.isWellKnown() ? "wellKnown " : "          ") +
                    glyph.toString());
            }
        }
    }

    //--------------//
    // dumpSections //
    //--------------//
    /**
     * Dump all (vertical) sections handled by this system
     */
    public void dumpSections ()
    {
        dumpSections(null);
    }

    //--------------//
    // dumpSections //
    //--------------//
    /**
     * Dump the (vertical) sections handled by this system and that are
     * contained by the provided rectangle
     *
     * @param rect the region of interest
     */
    public void dumpSections (PixelRectangle rect)
    {
        for (Section section : getVerticalSections()) {
            if ((rect == null) || (rect.contains(section.getContourBox()))) {
                System.out.println(
                    (section.isKnown() ? "known " : "      ") +
                    section.toString());
            }
        }
    }

    //------------------//
    // extractNewGlyphs //
    //------------------//
    /**
     * In the specified system, build new glyphs from unknown sections (sections
     * not linked to a known glyph)
     */
    public void extractNewGlyphs ()
    {
        removeInactiveGlyphs();
        retrieveGlyphs();
    }

    //--------------//
    // fixLargeSlur //
    //--------------//
    /**
     * For large glyphs, we suspect a slur with a stuck object. So the strategy
     * is to rebuild the true Slur portions from the underlying sections. These
     * "good" sections are put into the "kept" collection. Sections left over
     * are put into the "left" collection in order to be used to rebuild the
     * stuck object(s).
     *
     * <p>The method by itself does not build the new slur glyph, this task must
     * be done by the caller.
     *
     * @param slur the spurious slur slur
     * @return the extracted slur glyph, if any
     */
    public Glyph fixLargeSlur (Glyph slur)
    {
        return slurInspector.fixLargeSlur(slur);
    }

    //-----------------//
    // fixSpuriousSlur //
    //-----------------//
    /**
     * Try to correct the slur glyphs (which have a too high circle distance) by
     * either adding a neigboring glyph (for small slurs) or removing stuck
     * glyph sections (for large slurs)
     *
     * @param glyph the spurious glyph at hand
     * @return true if the slur glyph has actually been fixed
     */
    public Glyph fixSpuriousSlur (Glyph glyph)
    {
        return slurInspector.fixSpuriousSlur(glyph);
    }

    //---------------//
    // inspectGlyphs //
    //---------------//
    /**
     * Process the given system, by retrieving unassigned glyphs, evaluating
     * and assigning them if OK, or trying compounds otherwise.
     *
     * @param maxDoubt the maximum acceptable doubt for this processing
     */
    public void inspectGlyphs (double maxDoubt)
    {
        glyphInspector.inspectGlyphs(maxDoubt);
    }

    //-----------------------//
    // lookupContainedGlyphs //
    //-----------------------//
    /**
     * Look up in system glyphs for the glyphs contained by a
     * provided rectangle
     *
     * @param rect the coordinates rectangle, in pixels
     * @return the glyphs found, which may be an empty list
     */
    public List<Glyph> lookupContainedGlyphs (PixelRectangle rect)
    {
        List<Glyph> found = new ArrayList<Glyph>();

        for (Glyph glyph : getGlyphs()) {
            if (rect.contains(glyph.getContourBox())) {
                found.add(glyph);
            }
        }

        return found;
    }

    //-------------------------//
    // lookupIntersectedGlyphs //
    //-------------------------//
    /**
     * Look up in system glyphs for <b>all</b> glyphs, apart from the excluded
     * glyph, intersected by a provided rectangle
     *
     * @param rect the coordinates rectangle, in pixels
     * @param excluded the glyph to be excluded
     * @return the glyphs found, which may be an empty list
     */
    public List<Glyph> lookupIntersectedGlyphs (PixelRectangle rect,
                                                Glyph          excluded)
    {
        List<Glyph> found = new ArrayList<Glyph>();

        for (Glyph glyph : getGlyphs()) {
            if (glyph != excluded) {
                if (glyph.intersects(rect)) {
                    found.add(glyph);
                }
            }
        }

        return found;
    }

    //-------------------------//
    // lookupIntersectedGlyphs //
    //-------------------------//
    /**
     * Look up in system glyphs for <b>all</b> glyphs intersected by a
     * provided rectangle
     *
     * @param rect the coordinates rectangle, in pixels
     * @return the glyphs found, which may be an empty list
     */
    public List<Glyph> lookupIntersectedGlyphs (PixelRectangle rect)
    {
        return lookupIntersectedGlyphs(rect, null);
    }

    //---------------//
    // registerGlyph //
    //---------------//
    /**
     * Just register this glyph (as inactive) in order to persist glyph info
     * such as TextInfo. Use {@link #addGlyph} to fully add the glpyh as active.
     * @param glyph the glyph to just register
     * @return the proper (original) glyph
     * @see #addGlyph
     */
    public Glyph registerGlyph (Glyph glyph)
    {
        return glyphsBuilder.registerGlyph(glyph);
    }

    //----------------------------//
    // removeFromGlyphsCollection //
    //----------------------------//
    /**
     * Meant for access by GlypsBuilder only
     * Standard entry is {@link #removeGlyph}
     * @param glyph the glyph to remove
     * @return true if the glyph was registered
     */
    public boolean removeFromGlyphsCollection (Glyph glyph)
    {
        return glyphs.remove(glyph);
    }

    //-------------//
    // removeGlyph //
    //-------------//
    /**
     * Remove a glyph from the containing system glyph list, and make it
     * inactive by cutting the link from its member sections
     *
     * @param glyph the glyph to remove
     */
    public void removeGlyph (Glyph glyph)
    {
        glyphsBuilder.removeGlyph(glyph);
    }

    //----------------------//
    // removeInactiveGlyphs //
    //----------------------//
    /**
     * On a specified system, look for all inactive glyphs and remove them from
     * its glyphs collection (but leave them in the containing lag).
     * Purpose is to prepare room for a new glyph extraction
     */
    public void removeInactiveGlyphs ()
    {
        // To avoid concurrent modifs exception
        Collection<Glyph> toRemove = new ArrayList<Glyph>();

        for (Glyph glyph : getGlyphs()) {
            if (!glyph.isActive()) {
                toRemove.add(glyph);
            }
        }

        if (logger.isFineEnabled()) {
            logger.fine(
                "removeInactiveGlyphs: " + toRemove.size() + " " +
                Glyphs.toString(toRemove));
        }

        for (Glyph glyph : toRemove) {
            // Remove glyph from system & cut sections links to it
            removeGlyph(glyph);
        }
    }

    //----------------//
    // resetSentences //
    //----------------//
    public void resetSentences ()
    {
        sentences.clear();
        sentenceCount = 0;
    }

    //----------------//
    // retrieveGlyphs //
    //----------------//
    /**
     * In a given system area, browse through all sections not assigned to known
     * glyphs, and build new glyphs out of connected sections
     */
    public void retrieveGlyphs ()
    {
        glyphsBuilder.retrieveGlyphs(true);
    }

    //---------------------//
    // retrieveHorizontals //
    //---------------------//
    /**
     * Retrieve ledgers (and tenuto, and horizontal endings)
     * @throws omr.step.StepException
     */
    public void retrieveHorizontals ()
        throws StepException
    {
        try {
            horizontalsBuilder.buildInfo();
        } catch (Exception ex) {
            logger.warning("Error in retrieveHorizontals", ex);
        }
    }

    //-------------------//
    // retrieveVerticals //
    //-------------------//
    /**
     * Retrieve stems (and vertical endings)
     * @return the number of glyphs built
     * @throws omr.step.StepException
     */
    public int retrieveVerticals ()
        throws StepException
    {
        return verticalsBuilder.retrieveVerticals();
    }

    //-------------//
    // runPatterns //
    //-------------//
    /**
     * Run the series of glyphs patterns
     * @return true if some progress has been made
     */
    public boolean runPatterns ()
    {
        return new PatternsChecker(this).runPatterns();
    }

    //---------------------//
    // segmentGlyphOnStems //
    //---------------------//
    /**
     * Process a glyph to retrieve its internal potential stems and leaves
     * @param glyph the glyph to segment along stems
     * @param isShort should we look for short (rather than standard) stems?
     */
    public void segmentGlyphOnStems (Glyph   glyph,
                                     boolean isShort)
    {
        verticalsBuilder.segmentGlyphOnStems(glyph, isShort);
    }

    //--------------//
    // selectGlyphs //
    //--------------//
    /**
     * Select glyphs out of a provided collection of glyphs,for which the
     * provided predicate holds true
     * @param glyphs the provided collection of glyphs candidates, or the full
     * system collection if null
     * @param predicate the condition to be fulfilled to get selected
     * @return the sorted set of selected glyphs
     */
    public SortedSet selectGlyphs (Collection<Glyph> glyphs,
                                   Predicate<Glyph>  predicate)
    {
        SortedSet<Glyph> selected = new TreeSet<Glyph>();

        if (glyphs == null) {
            glyphs = getGlyphs();
        }

        for (Glyph glyph : glyphs) {
            if (predicate.check((glyph))) {
                selected.add(glyph);
            }
        }

        return selected;
    }

    //----------//
    // toString //
    //----------//
    /**
     * Report a readable description
     *
     * @return a description based on staff indices
     */
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{SystemInfo#")
          .append(id);
        sb.append(" T")
          .append(getFirstStaff().getId());

        if (staves.size() > 1) {
            sb.append("..T")
              .append(getLastStaff().getId());
        }

        if (leftBar != null) {
            sb.append(" leftBar:")
              .append(leftBar);
        }

        if (rightBar != null) {
            sb.append(" rightBar:")
              .append(rightBar);
        }

        if (leftLimit != null) {
            sb.append(" leftLimit:")
              .append(leftLimit);
        }

        if (rightLimit != null) {
            sb.append(" rightLimit:")
              .append(rightLimit);
        }

        sb.append("}");

        return sb.toString();
    }

    //----------//
    // toString //
    //----------//
    /**
     * Convenient method, to build a string with just the ids of the system
     * collection
     *
     * @param systems the collection of glysystemsphs
     * @return the string built
     */
    public static String toString (Collection<SystemInfo> systems)
    {
        if (systems == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" systems[");

        for (SystemInfo system : systems) {
            sb.append("#")
              .append(system.getId());
        }

        sb.append("]");

        return sb.toString();
    }

    //--------------//
    // getSentences //
    //--------------//
    /**
     * Report the various sentences retrieved in this system.
     * @return the (perhaps empty) collection of sentences found
     */
    public Set<Sentence> getSentences ()
    {
        return sentences;
    }

    //----------//
    // getSheet //
    //----------//
    /**
     * Report the sheet this system belongs to
     * @return the containing sheet
     */
    public Sheet getSheet ()
    {
        return sheet;
    }

    //----------------//
    // translateFinal //
    //----------------//
    /**
     * Launch from this system the final processing of impacted systems to
     * translate them to score entities
     */
    public void translateFinal ()
    {
        translator.translateFinal();
    }

    //-----------------//
    // translateSystem //
    //-----------------//
    /**
     * Translate the physical Sheet system data into Score system entities
     */
    public void translateSystem ()
    {
        translator.translateSystem();
    }

    //-------------------//
    // updateCoordinates //
    //-------------------//
    public void updateCoordinates ()
    {
        StaffInfo firstStaff = getFirstStaff();
        LineInfo  firstLine = firstStaff.getFirstLine();
        Point2D   topLeft = firstLine.getEndPoint(LEFT);
        Point2D   topRight = firstLine.getEndPoint(RIGHT);
        StaffInfo lastStaff = getLastStaff();
        LineInfo  lastLine = lastStaff.getLastLine();
        Point2D   botLeft = lastLine.getEndPoint(LEFT);

        left = (int) Math.rint(topLeft.getX());
        top = (int) Math.rint(topLeft.getY());
        width = (int) Math.rint(topRight.getX() - topLeft.getX());
        deltaY = (int) Math.rint(
            lastStaff.getFirstLine().getEndPoint(LEFT).getY() - topLeft.getY());
        bottom = (int) Math.rint(botLeft.getY());
    }

    //-----------------//
    // boundaryUpdated //
    //-----------------//
    void boundaryUpdated ()
    {
        ///logger.warning("Update for " + this);
    }
}
