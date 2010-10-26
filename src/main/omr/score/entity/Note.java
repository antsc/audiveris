//----------------------------------------------------------------------------//
//                                                                            //
//                                  N o t e                                   //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2010. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.score.entity;

import omr.constant.ConstantSet;

import omr.glyph.Glyphs;
import omr.glyph.Shape;
import static omr.glyph.Shape.*;
import omr.glyph.ShapeRange;
import omr.glyph.facets.Glyph;

import omr.log.Logger;

import omr.math.GCD;
import omr.math.Rational;

import omr.score.common.PixelPoint;
import omr.score.common.PixelRectangle;
import omr.score.visitor.ScoreVisitor;

import omr.sheet.Scale;

import omr.util.TreeNode;

import java.util.*;

/**
 * Class <code>Note</code> represents the characteristics of a note. Besides a
 * regular note (standard note, or rest), it can also be a cue note or a grace
 * note (these last two variants are not handled yet, TODO).
 *
 * @author Hervé Bitteur
 */
public class Note
    extends MeasureNode
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(Note.class);

    /**
     * A quarter duration value chosen to fit all cases for internal
     * computations. This will be simplified when the score is exported to XML,
     * using the greatest common divisor found in the score.
     */
    public static final int QUARTER_DURATION = 96;

    //~ Enumerations -----------------------------------------------------------

    /** Names of the various note steps */
    public static enum Step {
        //~ Enumeration constant initializers ----------------------------------


        /** La */ A,
        /** Si */ B, 
        /** Do */ C, 
        /** Re */ D, 
        /** Mi */ E, 
        /** Fa */ F, 
        /** Sol */ G;
    }

    //~ Instance fields --------------------------------------------------------

    /** The note shape */
    private final Shape shape;

    /** Pitch position */
    private final double pitchPosition;

    /**
     * Cardinality of the note pack (stuck glyphs) this note is part of.
     * Card = 1 for an isolated note
     */
    private final int packCard;

    /* Index within the note pack. Index = 0 for an isolated note */
    private final int packIndex;

    /** Indicate a rest */
    private final boolean isRest;

    /** First augmentation dot, if any */
    private Glyph firstDot;

    /** Second augmentation dot, if any */
    private Glyph secondDot;

    /** Accidental glyph, if any */
    private Glyph accidental;

    /** Pitch alteration (not for rests) */
    private Integer alter;

    /** Note step */
    private Step step;

    /** Octave */
    private Integer octave;

    /** Tie / slurs */
    private List<Slur> slurs = new ArrayList<Slur>();

    /** Lyrics syllables (in different lines) */
    private SortedSet<LyricsItem> syllables;

    //~ Constructors -----------------------------------------------------------

    //------//
    // Note //
    //------//
    /** Create a new instance of an isolated Note
     *
     * @param chord the containing chord
     * @param glyph the underlying glyph
     */
    public Note (Chord chord,
                 Glyph glyph)
    {
        this(
            chord,
            glyph,
            getItemCenter(glyph, 0, chord.getScale().interline()),
            1,
            0);
        glyph.setTranslation(this);
    }

    //------//
    // Note //
    //------//
    /**
     * Create a note as a clone of another Note, into another chord
     *
     * @param chord the chord to host the newly created note
     * @param other the note to clone
     */
    public Note (Chord chord,
                 Note  other)
    {
        super(chord);

        for (Glyph glyph : other.getGlyphs()) {
            addGlyph(glyph);
        }

        packCard = other.packCard;
        packIndex = other.packIndex;
        isRest = other.isRest;
        setCenter(other.getCenter());
        setStaff(other.getStaff());
        pitchPosition = other.pitchPosition;
        shape = other.getShape();
        setBox(other.getBox());

        for (Glyph glyph : getGlyphs()) {
            glyph.addTranslation(this);
        }

        // We specifically don't carry over:
        // slurs
    }

    //------//
    // Note //
    //------//
    /** Create a new instance of Note with no underlying glyph
     *
     * @param staff the containing staff
     * @param chord the containing chord
     * @param shape the provided shape
     * @param pitchPosition the pitchPosition
     * @param center the center (PixelPoint) of the note
     */
    private Note (Staff      staff,
                  Chord      chord,
                  Shape      shape,
                  double     pitchPosition,
                  PixelPoint center)
    {
        super(chord);

        this.packCard = 1;
        this.packIndex = 0;

        // Rest?
        isRest = ShapeRange.Rests.contains(shape);

        // Staff
        setStaff(staff);

        // Pitch Position
        this.pitchPosition = pitchPosition;

        // Location center
        setCenter(
            new PixelPoint(
                center.x,
                (int) Math.rint(
                    (staff.getTopLeft().y - staff.getSystem().getTopLeft().y) +
                    ((chord.getScale()
                           .interline() * (4d + pitchPosition)) / 2))));

        // Note box
        setBox(null);

        // Shape of this note
        this.shape = baseShapeOf(shape);
    }

    //------//
    // Note //
    //------//
    /** Create a new instance of Note, as a chunk of a larger note pack.
     *
     * @param chord the containing chord
     * @param glyph the underlying glyph
     * @param center the center of the note instance
     * @param packCard the number of notes in the pack
     * @param packIndex the zero-based index of this note in the pack
     */
    private Note (Chord      chord,
                  Glyph      glyph,
                  PixelPoint center,
                  int        packCard,
                  int        packIndex)
    {
        super(chord);

        addGlyph(glyph);
        this.packCard = packCard;
        this.packIndex = packIndex;

        ScoreSystem system = getSystem();
        int         interline = system.getScale()
                                      .interline();

        // Rest?
        isRest = ShapeRange.Rests.contains(glyph.getShape());

        // Location center
        setCenter(center);

        // Note box
        setBox(getItemBox(glyph, packIndex, interline));

        // Shape of this note
        shape = baseShapeOf(glyph.getShape());

        // Staff
        Staff  noteStaff = getSystem()
                               .getStaffAt(getCenter());

        // Pitch Position wrt staff
        double pp = noteStaff.getInfo()
                             .precisePitchPositionOf(
            getCenter(),
            system.getInfo());

        // Beware, when note is far from staff, use staff of related stem
        if (Math.abs(pp) >= 8) {
            Glyph stem = chord.getStem();

            if (stem != null) {
                PixelPoint stemCenter = stem.getAreaCenter();
                Staff      stemStaff = getSystem()
                                           .getStaffAt(stemCenter);

                if (logger.isFineEnabled() && (stemStaff != noteStaff)) {
                    logger.fine("Changed staff for glyph#" + glyph.getId());
                }

                noteStaff = stemStaff;
                pp = noteStaff.getInfo()
                              .precisePitchPositionOf(
                    getCenter(),
                    system.getInfo());
            }
        }

        // OK, register proper staff and pitchPosition
        setStaff(noteStaff);
        pitchPosition = pp;
    }

    //~ Methods ----------------------------------------------------------------

    //----------------//
    // getActualShape //
    //----------------//
    public static Shape getActualShape (Shape base,
                                        int   card)
    {
        switch (card) {
        case 3 :

            switch (base) {
            case VOID_NOTEHEAD :
                return VOID_NOTEHEAD_3;

            case NOTEHEAD_BLACK :
                return NOTEHEAD_BLACK_3;

            case WHOLE_NOTE :
                return WHOLE_NOTE_3;

            default :
                return null;
            }

        case 2 :

            switch (base) {
            case VOID_NOTEHEAD :
                return VOID_NOTEHEAD_2;

            case NOTEHEAD_BLACK :
                return NOTEHEAD_BLACK_2;

            case WHOLE_NOTE :
                return WHOLE_NOTE_2;

            default :
                return null;
            }

        case 1 :
            return base;

        default :
            return null;
        }
    }

    //---------//
    // setDots //
    //---------//
    /**
     * Define the number of augmentation dots that impact this chord
     *
     * @param first the glyph of first dot
     * @param second the glyph of second dot (if any)
     */
    public void setDots (Glyph first,
                         Glyph second)
    {
        firstDot = first;
        secondDot = second;
    }

    //-------------//
    // getFirstDot //
    //-------------//
    /**
     * Report the first augmentation dot, if any
     * @return first dot or null
     */
    public Glyph getFirstDot ()
    {
        return firstDot;
    }

    //--------------------//
    // getPackCardinality //
    //--------------------//
    public int getPackCardinality ()
    {
        return packCard;
    }

    //--------------//
    // getSecondDot //
    //--------------//
    /**
     * Report the second augmentation dot, if any
     * @return second dot or null
     */
    public Glyph getSecondDot ()
    {
        return secondDot;
    }

    //-----------------//
    // createWholeRest //
    //-----------------//
    public static Note createWholeRest (Staff      staff,
                                        Chord      chord,
                                        PixelPoint center)
    {
        return new Note(staff, chord, Shape.WHOLE_REST, -1.5, center);
    }

    //------------------//
    // getPitchPosition //
    //------------------//
    /**
     * Report the pith position of the note within the containing staff
     *
     * @return staff-based pitch position
     */
    public double getPitchPosition ()
    {
        return pitchPosition;
    }

    //--------//
    // isRest //
    //--------//
    /**
     * Check whether this note is a rest (vs a 'real' note)
     *
     * @return true if a rest, false otherwise
     */
    public boolean isRest ()
    {
        return isRest;
    }

    //----------//
    // getShape //
    //----------//
    /**
     * Report the shape of the note
     *
     * @return the note shape
     */
    public Shape getShape ()
    {
        return shape;
    }

    //----------//
    // getSlurs //
    //----------//
    /**
     * Report the collection of slurs that start or stop at this note
     *
     * @return a perhaps empty collection of slurs
     */
    public List<Slur> getSlurs ()
    {
        return slurs;
    }

    //---------//
    // getStep //
    //---------//
    /**
     * Report the note step (within the octave)
     *
     * @return the note step
     */
    public Note.Step getStep ()
    {
        if (step == null) {
            step = Clef.noteStepOf(
                getMeasure().getClefBefore(getCenter()),
                (int) Math.rint(getPitchPosition()));
        }

        return step;
    }

    //--------------//
    // getSyllables //
    //--------------//
    public SortedSet<LyricsItem> getSyllables ()
    {
        return syllables;
    }

    //-----------------//
    // getTypeDuration //
    //-----------------//
    /**
     * Report the duration indicated by the shape of the note head (regardless
     * of any beam, flag, dot or tuplet)
     *
     * @param shape the shape of the note head
     * @return the corresponding duration
     */
    public static int getTypeDuration (Shape shape)
    {
        switch (baseShapeOf(shape)) {
        case LONG_REST : // 4 measures
            return 16 * QUARTER_DURATION;

        case BREVE_REST : // 2 measures
        case BREVE :
            return 8 * QUARTER_DURATION;

        case WHOLE_REST : // 1 measure
        case WHOLE_NOTE :
            return 4 * QUARTER_DURATION;

        case HALF_REST :
        case VOID_NOTEHEAD :
            return 2 * QUARTER_DURATION;

        case QUARTER_REST :
        case OLD_QUARTER_REST :
        case NOTEHEAD_BLACK :
            return QUARTER_DURATION;

        case EIGHTH_REST :
            return QUARTER_DURATION / 2;

        case SIXTEENTH_REST :
            return QUARTER_DURATION / 4;

        case THIRTY_SECOND_REST :
            return QUARTER_DURATION / 8;

        case SIXTY_FOURTH_REST :
            return QUARTER_DURATION / 16;

        case ONE_HUNDRED_TWENTY_EIGHTH_REST :
            return QUARTER_DURATION / 32;

        default :
            // Error
            logger.severe("Illegal note type " + shape);

            return 0;
        }
    }

    //--------//
    // accept //
    //--------//
    @Override
    public boolean accept (ScoreVisitor visitor)
    {
        return visitor.visit(this);
    }

    //---------//
    // addSlur //
    //---------//
    /**
     * Add a slur in the collection of slurs connected to this note
     *
     * @param slur the slur to connect
     */
    public void addSlur (Slur slur)
    {
        slurs.add(slur);
    }

    //-------------//
    // addSyllable //
    //-------------//
    public void addSyllable (LyricsItem item)
    {
        if (syllables == null) {
            syllables = new TreeSet<LyricsItem>(LyricsItem.numberComparator);
        }

        syllables.add(item);
    }

    //------------//
    // createPack //
    //------------//
    /**
     * Create a bunch of Note instances for one note pack
     *
     * @param chord the containing chord
     * @param glyph the underlying glyph of the note pack
     */
    public static void createPack (Chord chord,
                                   Glyph glyph)
    {
        final int      card = packCardOf(glyph.getShape());

        // Test on ordinates between stem (if any) and note
        // Be strict when glyph has 2 stems and more relaxed with just one stem
        final Glyph    stem = chord.getStem();
        PixelRectangle stemBox = null;
        Scale          scale = chord.getScale();

        if (stem != null) {
            stemBox = stem.getContourBox();
            stemBox.grow(
                scale.toPixels(constants.maxStemDx),
                scale.toPixels(
                    (glyph.getStemNumber() >= 2) ? constants.maxMultiStemDy
                                        : constants.maxSingleStemDy));
        }

        for (int i = 0; i < card; i++) {
            PixelRectangle itemBox = getItemBox(glyph, i, scale.interline());

            if (stem != null) {
                if (!itemBox.intersects(stemBox)) {
                    continue;
                }
            }

            PixelPoint center = new PixelPoint(
                itemBox.x + (itemBox.width / 2),
                itemBox.y + (itemBox.height / 2));
            glyph.addTranslation(new Note(chord, glyph, center, card, i));
        }
    }

    //---------------//
    // getAccidental //
    //---------------//
    /**
     * Report the accidental, if any, related to this note
     *
     * @return the accidental, or null
     */
    public Glyph getAccidental ()
    {
        return accidental;
    }

    //----------//
    // getAlter //
    //----------//
    /**
     * Report the actual alteration of this note, taking into account the
     * accidental of this note if any, the accidental of previous note with same
     * step within the same measure, and finally the current key signature.
     *
     * @return the actual alteration
     */
    public int getAlter ()
    {
        if (alter == null) {
            if (accidental != null) {
                // TODO: handle double flat & double sharp !!!
                switch (accidental.getShape()) {
                case SHARP :
                    return alter = 1;

                case FLAT :
                    return alter = -1;

                default :
                }
            }

            // Look for a previous accidental with the same note step in the measure
            Collection<Slot> collection = getMeasure()
                                              .getSlots();
            Slot[]           slots = collection.toArray(
                new Slot[collection.size()]);

            boolean          started = false;

            for (int is = slots.length - 1; is >= 0; is--) {
                Slot slot = slots[is];

                if (slot.isAlignedWith(getCenter())) {
                    started = true;
                }

                if (started) {
                    // Inspect all notes of all chords
                    for (Chord chord : slot.getChords()) {
                        for (TreeNode node : chord.getNotes()) {
                            Note note = (Note) node;

                            if (note == this) {
                                continue;
                            }

                            if ((note.getStep() == getStep()) &&
                                (note.getAccidental() != null)) {
                                switch (note.getAccidental()
                                            .getShape()) {
                                case SHARP :
                                    return alter = 1;

                                case FLAT :
                                    return alter = -1;

                                default :
                                }
                            }
                        }
                    }
                }
            }

            // Finally, use the current key signature
            KeySignature ks = getMeasure()
                                  .getKeyBefore(getCenter());

            if (ks != null) {
                return alter = ks.getAlterFor(getStep());
            }

            // By default ...
            alter = 0;
        }

        return alter;
    }

    //-----------------//
    // getCenterBottom //
    //-----------------//
    /**
     * Report the system point at the center bottom of the note
     *
     * @return center point at bottom of note
     */
    public PixelPoint getCenterBottom ()
    {
        return new PixelPoint(
            getCenter().x,
            getCenter().y + (getBox().height / 2));
    }

    //---------------//
    // getCenterLeft //
    //---------------//
    /**
     * Report the system point at the center left of the note
     *
     * @return left point at mid height
     */
    public PixelPoint getCenterLeft ()
    {
        return new PixelPoint(
            getCenter().x - (getBox().width / 2),
            getCenter().y);
    }

    //----------------//
    // getCenterRight //
    //----------------//
    /**
     * Report the system point at the center right of the note
     *
     * @return right point at mid height
     */
    public PixelPoint getCenterRight ()
    {
        return new PixelPoint(
            getCenter().x + (getBox().width / 2),
            getCenter().y);
    }

    //--------------//
    // getCenterTop //
    //--------------//
    /**
     * Report the system point at the center top of the note
     *
     * @return center point at top of note
     */
    public PixelPoint getCenterTop ()
    {
        return new PixelPoint(
            getCenter().x,
            getCenter().y - (getBox().height / 2));
    }

    //----------//
    // getChord //
    //----------//
    /**
     * Report the chord this note is part of
     *
     * @return the containing chord (cannot be null)
     */
    public Chord getChord ()
    {
        return (Chord) getParent();
    }

    //-----------------//
    // getNoteDuration //
    //-----------------//
    /**
     * Report the duration of this note, based purely on its shape and the
     * number of beams or flags. This does not take into account the potential
     * augmentation dots, nor tuplets. The purpose of this method is to find out
     * the name of the note ("eighth" versus "quarter" for example)
     *
     * @return the intrinsic note duration
     */
    public int getNoteDuration ()
    {
        int dur = getTypeDuration(shape);

        // Apply fraction if any (not for rests) due to beams or flags
        if (!isRest()) {
            int fbn = getChord()
                          .getFlagsNumber() + getChord()
                                                  .getBeams()
                                                  .size();

            for (int i = 0; i < fbn; i++) {
                dur /= 2;
            }
        }

        return dur;
    }

    //-----------//
    // getOctave //
    //-----------//
    /**
     * Report the octave for this note, using the current clef, and the pitch
     * position of the note
     *
     * @return the related octave
     */
    public int getOctave ()
    {
        if (octave == null) {
            octave = Clef.octaveOf(
                getMeasure().getClefBefore(getCenter()),
                (int) Math.rint(getPitchPosition()));
        }

        return octave;
    }

    //--------//
    // moveTo //
    //--------//
    /**
     * Move this note from its current chord to the provided chord
     *
     * @param chord the new hosting chord
     */
    public void moveTo (Chord chord)
    {
        getParent()
            .getChildren()
            .remove(this);
        chord.addChild(this);

        for (Glyph glyph : getGlyphs()) {
            glyph.setTranslation(this);
        }
    }

    //--------------------//
    // populateAccidental //
    //--------------------//
    /**
     * Process the potential impact of an accidental glyph within the containing
     * measure
     *
     * @param glyph the underlying glyph of the accidental
     * @param measure the containing measure
     * @param accidCenter the center of the glyph
     */
    public static void populateAccidental (Glyph      glyph,
                                           Measure    measure,
                                           PixelPoint accidCenter)
    {
        final Scale     scale = measure.getScale();
        final int       minDx = scale.toPixels(constants.minAccidDx);
        final int       maxDx = scale.toPixels(constants.maxAccidDx);
        final int       maxDy = scale.toPixels(constants.maxAccidDy);
        final Set<Note> candidates = new HashSet<Note>();

        // An accidental impacts the note right after (even if duplicated)
        ChordLoop: 
        for (TreeNode node : measure.getChords()) {
            final Chord chord = (Chord) node;

            for (TreeNode n : chord.getNotes()) {
                final Note note = (Note) n;

                if (!note.isRest()) {
                    final PixelPoint noteRef = note.getCenterLeft();
                    final PixelPoint toNote = new PixelPoint(
                        noteRef.x - accidCenter.x,
                        noteRef.y - accidCenter.y);

                    if (logger.isFineEnabled()) {
                        logger.fine(measure.getContextString() + " " + toNote);
                    }

                    if (toNote.x > (2 * maxDx)) {
                        break ChordLoop; // Other chords/notes will be too far
                    }

                    if ((toNote.x >= minDx) &&
                        (toNote.x <= maxDx) &&
                        (Math.abs(toNote.y) <= maxDy)) {
                        candidates.add(note);
                    }
                }
            }
        }

        if (logger.isFineEnabled()) {
            logger.info(candidates.size() + " Candidates=" + candidates);
        }

        // Select the best note candidate, the one whose ordinate is closest
        // TODO: Bug here? what if the note is duplicated ("shared" by 2 chords)?
        if (!candidates.isEmpty()) {
            int  bestDy = Integer.MAX_VALUE;
            Note bestNote = null;
            glyph.clearTranslations();

            for (Note note : candidates) {
                int dy = Math.abs(note.getCenter().y - accidCenter.y);

                if (dy < bestDy) {
                    bestDy = dy;
                    bestNote = note;
                }
            }

            bestNote.accidental = glyph;
            glyph.addTranslation(bestNote);

            if (logger.isFineEnabled()) {
                logger.fine(
                    bestNote.getContextString() + " accidental " +
                    glyph.getShape() + " at " + bestNote.getCenter());
            }
        }
    }

    //----------------//
    // quarterValueOf //
    //----------------//
    /**
     * Report a easy-to-read string, where a duration is expressed in quarters
     *
     * @param val a duration value
     * @return a string such as "3Q/4" or "Q"
     */
    public static String quarterValueOf (int val)
    {
        final StringBuilder sb = new StringBuilder();

        if (val < 0) {
            sb.append("-");
            val = -val;
        }

        final int gcd = GCD.gcd(val, QUARTER_DURATION);
        final int num = val / gcd;
        final int den = QUARTER_DURATION / gcd;

        if (num == 0) {
            return "0";
        } else if (num != 1) {
            sb.append(num);
        }

        sb.append("Q");

        if (den != 1) {
            sb.append("/")
              .append(den);
        }

        return sb.toString();
    }

    //-----------------//
    // rationalValueOf //
    //-----------------//
    /**
     * Report the rational equivalent to provided duration
     *
     * @param val a duration value
     * @return a rational such as 1/4, 3/4, ...
     */
    public static Rational rationalValueOf (int val)
    {
        final int gcd = GCD.gcd(val, QUARTER_DURATION);
        final int num = val / gcd;
        final int den = (4 * QUARTER_DURATION) / gcd;

        return new Rational(num, den);
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{Note");
        sb.append(" ")
          .append(shape);

        sb.append(" Ch#")
          .append(getChord().getId());

        if (packCard != 1) {
            sb.append(" [")
              .append(packIndex + 1) // For easier reading by end user

              .append("/")
              .append(packCard)
              .append("]");
        }

        if (accidental != null) {
            sb.append(" ")
              .append(accidental);
        }

        if (isRest) {
            sb.append(" ")
              .append("rest");
        }

        if (alter != null) {
            sb.append(" alter=")
              .append(alter);
        }

        sb.append(" pp=")
          .append((float) pitchPosition);

        sb.append(" ")
          .append(Glyphs.toString(getGlyphs()));

        sb.append("}");

        return sb.toString();
    }

    //------------//
    // getItemBox //
    //------------//
    /**
     * Compute the bounding box of item with rank 'index' in the provided note
     * pack glyph
     */
    private static PixelRectangle getItemBox (Glyph glyph,
                                              int   index,
                                              int   interline)
    {
        final Shape          shape = glyph.getShape();
        final int            card = packCardOf(shape);
        final PixelRectangle box = glyph.getContourBox();

        // For true notes use centroid y, for rests use area center y
        // For head/flag combination use head side
        if (ShapeRange.Rests.contains(shape)) {
            return glyph.getContourBox();
        } else if (ShapeRange.HeadAndFlagsDown.contains(shape)) {
            // Head is at bottom side of glyph
            return new PixelRectangle(
                box.x,
                (box.y + box.height) - ((card - index) * interline),
                box.width,
                interline);
        } else if (ShapeRange.HeadAndFlagsUp.contains(shape)) {
            // Head is at top side of glyph
            return new PixelRectangle(
                box.x,
                box.y + (index * interline),
                box.width,
                interline);
        } else {
            final PixelPoint centroid = glyph.getCentroid();
            final int        top = centroid.y - ((card * interline) / 2);

            return new PixelRectangle(
                box.x,
                top + (index * interline),
                box.width,
                interline);
        }
    }

    //---------------//
    // getItemCenter //
    //---------------//
    /**
     * Compute the center of item with rank 'index' in the provided note
     * pack glyph
     */
    private static PixelPoint getItemCenter (Glyph glyph,
                                             int   index,
                                             int   interline)
    {
        PixelRectangle box = getItemBox(glyph, index, interline);

        return new PixelPoint(
            box.x + (box.width / 2),
            box.y + (box.height / 2));
    }

    //-------------//
    // baseShapeOf //
    //-------------//
    private static Shape baseShapeOf (Shape shape)
    {
        switch (shape) {
        case NOTEHEAD_BLACK :
        case NOTEHEAD_BLACK_2 :
        case NOTEHEAD_BLACK_3 :
        case HEAD_AND_FLAG_1 :
        case HEAD_AND_FLAG_1_UP :
        case HEAD_AND_FLAG_2 :
        case HEAD_AND_FLAG_2_UP :
        case HEAD_AND_FLAG_3 :
        case HEAD_AND_FLAG_3_UP :
        case HEAD_AND_FLAG_4 :
        case HEAD_AND_FLAG_4_UP :
        case HEAD_AND_FLAG_5 :
        case HEAD_AND_FLAG_5_UP :
            return Shape.NOTEHEAD_BLACK;

        case VOID_NOTEHEAD :
        case VOID_NOTEHEAD_2 :
        case VOID_NOTEHEAD_3 :
            return Shape.VOID_NOTEHEAD;

        case WHOLE_NOTE :
        case WHOLE_NOTE_2 :
        case WHOLE_NOTE_3 :
            return Shape.WHOLE_NOTE;

        default :
            return shape;
        }
    }

    //------------//
    // packCardOf //
    //------------//
    private static int packCardOf (Shape shape)
    {
        switch (shape) {
        case VOID_NOTEHEAD_3 :
        case NOTEHEAD_BLACK_3 :
        case WHOLE_NOTE_3 :
            return 3;

        case VOID_NOTEHEAD_2 :
        case NOTEHEAD_BLACK_2 :
        case WHOLE_NOTE_2 :
            return 2;

        default :
            return 1;
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        /**
         * Minimum dx between accidental and note
         */
        Scale.Fraction minAccidDx = new Scale.Fraction(
            0.3d,
            "Minimum dx between accidental and note");

        /**
         * Maximum dx between accidental and note
         */
        Scale.Fraction maxAccidDx = new Scale.Fraction(
            4d,
            "Maximum dx between accidental and note");

        /**
         * Maximum absolute dy between note and accidental
         */
        Scale.Fraction maxAccidDy = new Scale.Fraction(
            0.5d,
            "Maximum absolute dy between note and accidental");

        /**
         * Maximum absolute dx between note and stem
         */
        Scale.Fraction maxStemDx = new Scale.Fraction(
            0.25d,
            "Maximum absolute dx between note and stem");

        /**
         * Maximum absolute dy between note and single stem end
         */
        Scale.Fraction maxSingleStemDy = new Scale.Fraction(
            3d,
            "Maximum absolute dy between note and single stem end");

        /**
         * Maximum absolute dy between note and multi stem end
         */
        Scale.Fraction maxMultiStemDy = new Scale.Fraction(
            0.25d,
            "Maximum absolute dy between note and multi stem end");

        /**
         * Maximum absolute dy between note center and centroid
         */
        Scale.Fraction maxCenterDy = new Scale.Fraction(
            0.1d,
            "Maximum absolute dy between note center and centroid");
    }
}
