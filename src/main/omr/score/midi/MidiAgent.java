//----------------------------------------------------------------------------//
//                                                                            //
//                             M i d i A g e n t                              //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Herve Bitteur 2000-2009. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.score.midi;

import omr.log.Logger;

import omr.score.MeasureRange;
import omr.score.Score;
import omr.score.ScoreExporter;
import omr.score.ui.ScoreActions;

import omr.util.OmrExecutors;

import com.xenoage.player.Player;
import com.xenoage.player.musicxml.MusicXMLDocument;

import org.w3c.dom.Document;

import java.io.*;
import java.util.concurrent.*;

import javax.swing.JOptionPane;
import javax.xml.parsers.*;

/**
 * Class <code>MidiAgent</code> is in charge of representing a score when
 * dealing with the Midi System. There are two main usages: playing a score
 * and writing the Midi file for a score.
 *
 * There is only one instance of this class for the whole application, to allow
 * the preloading of the default Midi sequencer. Also, it would not really make
 * sense to have two score playbacks at the same time, since there is no means
 * to accurately synchronize the moment when a playback really starts.
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class MidiAgent
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(MidiAgent.class);

    /** Type used when writing Midi files */
    public static final int MIDI_FILE_TYPE = 1;

    /** A future which reflects whether Midi Agent has been initialized **/
    private static final Future<Void> loading = OmrExecutors.getCachedLowExecutor()
                                                            .submit(
        new Callable<Void>() {
                public Void call ()
                    throws Exception
                {
                    try {
                        Object obj = Holder.INSTANCE;
                    } catch (Exception ex) {
                        logger.warning("Could not preload the Midi Agent", ex);
                        throw ex;
                    }

                    return null;
                }
            });


    //~ Enumerations -----------------------------------------------------------

    /**
     * The various possibilities for the status of this entity regarding Midi
     * playback
     */
    public enum Status {
        //~ Enumeration constant initializers ----------------------------------


        /** Playback is not started or paused, sequence position is irrelevant */
        STOPPED,
        /** Playback has started */
        PLAYING, 
        /** Playback is paused, current sequence position is kept */
        PAUSED;
    }

    //~ Instance fields --------------------------------------------------------

    /** The underlying XenoPlay player */
    private final Player player;

    /** (Current) related score. Beware of memory leak! */
    private Score score;

    /** Current status of this player */
    private Status status = Status.STOPPED;

    /** The MusicXML document */
    private Document document;

    /** Specific measure range if any */
    private MeasureRange measureRange;

    /** In charge of receiving Midi events to update score display */
    private MidiReceiver receiver;

    //~ Constructors -----------------------------------------------------------

    //-----------//
    // MidiAgent //
    //-----------//
    /**
     * Create a Midi Agent
     *
     * @param score the related score
     */
    private MidiAgent ()
    {
        player = new OmrPlayer();
        receiver = new MidiReceiver(this);
        player.connectReceiver(receiver);
    }

    //~ Methods ----------------------------------------------------------------

    //-------------//
    // getInstance //
    //-------------//
    /**
     * Report the single instance of this singleton class (after creating it if
     * necessary)
     * @return the single instance of MidiAgent (or null if failed)
     */
    public static MidiAgent getInstance ()
    {
        try {
            loading.get();
        } catch (Throwable ex) {
            logger.severe("Cannot load Midi", ex);

            return null;
        }

        return Holder.INSTANCE;
    }

    //---------//
    // preload //
    //---------//
    public static void preload ()
    {
    }

    //----------//
    // setScore //
    //----------//
    /**
     * Assign a score to the Midi Agent
     *
     * @param score the new current score (perhaps null)
     */
    public void setScore (Score score)
    {
        if (this.score != score) {
            reset();
            this.score = score;

            switch (status) {
            case PLAYING :
                stop();

                break;

            case PAUSED :
                status = Status.STOPPED;
                MidiActions.getInstance()
                           .updateActions();
                logger.info("Stopped.");
            }
        }
    }

    //----------//
    // getScore //
    //----------//
    /**
     * Report the score (if any) currently handled by the Midi Agent
     *
     * @return the score currently handled, if any
     */
    public Score getScore ()
    {
        return score;
    }

    //-----------//
    // getStatus //
    //-----------//
    /**
     * Report the current status of the agent
     *
     * @return the current status (STOPPED, PLAYING, PAUSED)
     */
    public Status getStatus ()
    {
        return status;
    }

    //-------//
    // pause //
    //-------//
    /**
     * Pause the playback, keeping the current position in the Midi sequence
     */
    public void pause ()
    {
        if (status == Status.PLAYING) {
            status = Status.PAUSED;
            player.pause();
            MidiActions.getInstance()
                       .updateActions();
        }
    }

    //------//
    // play //
    //------//
    /**
     * Start the playback from start (or continue if just paused)
     *
     * @param measureRange if a non null measure range is provided as a
     * parameter it is taken into account, otherwise the measure range defined
     * at score level, if any, is being considered
     */
    public void play (MeasureRange measureRange)
    {
        if (ScoreActions.checkParameters(score)) {
            logger.info(
                "Playing " + score.getRadix() +
                ((measureRange != null) ? (" " + measureRange) : "") +
                " tempo:" + score.getTempo() + " volume:" + score.getVolume() +
                " ...");

            // Make sure the document (and the Midi sequence) is available
            retrieveDocument(measureRange);

            // We could adjust the tempo here
            ///sequencer.setTempoFactor(2.0f);

            // Infos
            if (logger.isFineEnabled()) {
                try {
                    long ms = player.getSequencer()
                                    .getSequence()
                                    .getMicrosecondLength() / 1000;
                    long ticks = player.getSequencer()
                                       .getSequence()
                                       .getTickLength();
                    logger.fine("Midi sequence length is " + ms + " ms");
                    logger.fine("Midi tick length is " + ticks);

                    int lastTime = score.getLastSoundTime(
                        (measureRange != null) ? measureRange.getLastId() : null);
                    lastTime /= score.getDurationDivisor();
                    logger.fine(
                        "Score tick " +
                        ((measureRange != null) ? "selection" : "length") +
                        " is " + lastTime);
                } catch (Exception e) {
                }
            }

            // Hand it over to the player and the receiver
            receiver.setScore(score, measureRange);
            player.play();
            status = Status.PLAYING;
            MidiActions.getInstance()
                       .updateActions();
        }
    }

    //-------//
    // reset //
    //-------//
    /**
     * Invalidate the MusicXML document, so that it gets recreated the next time
     * it is needed
     */
    public void reset ()
    {
        document = null;
        measureRange = null;

        if (receiver != null) {
            receiver.reset();
        }
    }

    //------//
    // stop //
    //------//
    /**
     * Stop the playback, discarding current position in the sequence
     */
    public void stop ()
    {
        if ((status == Status.PLAYING) || (status == Status.PAUSED)) {
            status = Status.STOPPED;
            player.stop();
            receiver.reset();
            MidiActions.getInstance()
                       .updateActions();
        }
    }

    //-------//
    // write //
    //-------//
    /**
     * Write the Midi sequence to an output stream
     * @param os the stream to be written
     */
    public void write (OutputStream os)
    {
        // Make sure the document (and the Midi sequence) is available
        retrieveDocument(null);

        player.saveSequence(os);
    }

    //-------//
    // write //
    //-------//
    /**
     * Write the Midi sequence to an output file
     *
     * @param file the output file
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     */
    public void write (File file)
        throws FileNotFoundException, IOException
    {
        OutputStream os = new FileOutputStream(file);
        write(os);
        os.close();
    }

    //------------------//
    // getLengthInTicks //
    //------------------//
    /**
     * Report the length of the current sequence
     *
     * @return the sequence length in Midi ticks
     */
    long getLengthInTicks ()
    {
        try {
            return player.getSequencer()
                         .getSequence()
                         .getTickLength();
        } catch (Exception ex) {
            return 0;
        }
    }

    //--------------------//
    // getPositionInTicks //
    //--------------------//
    /**
     * Report the current position within the current sequence
     *
     * @return the current position in Midi ticks
     */
    long getPositionInTicks ()
    {
        try {
            return player.getSequencer()
                         .getTickPosition();
        } catch (Exception ex) {
            return 0;
        }
    }

    //--------//
    // ending //
    //--------//
    /**
     * Notification of the end of playback
     */
    void ending ()
    {
        logger.info("Ended.");
        status = Status.STOPPED;
        MidiActions.getInstance()
                   .updateActions();
    }

    //------------------//
    // retrieveDocument //
    //------------------//
    /**
     * Make sure the MusicXML document (and its Midi counterpart) is available
     *
     * @param measureRange a potential range of measure. If null, the range
     * defined at score level, if any, will be taken into account. If no range
     * is specified (either as a method parameter or as a score parameter) then
     * the whole score is played.
     */
    private void retrieveDocument (MeasureRange measureRange)
    {
        if ((document == null) || (this.measureRange != measureRange)) {
            try {
                this.measureRange = measureRange;

                // Populate the document
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder        builder = factory.newDocumentBuilder();
                document = builder.newDocument();

                ScoreExporter exporter = new ScoreExporter(score);

                if (measureRange == null) {
                    measureRange = score.getMeasureRange();
                }

                exporter.setMeasureRange(measureRange);
                exporter.export(document);

                // Hand it over directly to MusicXML reader
                player.loadDocument(new MusicXMLDocument(document));
            } catch (Exception ex) {
                logger.warning("Midi Agent error", ex);
                document = null; // Safer
            }
        }
    }

    //~ Inner Classes ----------------------------------------------------------

    //--------//
    // Holder //
    //--------//
    private static class Holder
    {
        //~ Static fields/initializers -----------------------------------------

        public static final MidiAgent INSTANCE = new MidiAgent();
    }

    //-----------//
    // OmrPlayer //
    //-----------//
    /**
     * Subclass of Player to redirect logging messages
     */
    private static class OmrPlayer
        extends Player
    {
        //~ Constructors -------------------------------------------------------

        public OmrPlayer ()
        {
            super(null, false);
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public void MsgBox (String Msg,
                            int    MsgType)
        {
            switch (MsgType) {
            case JOptionPane.WARNING_MESSAGE :
            case JOptionPane.ERROR_MESSAGE :
                logger.warning(Msg);

                break;

            default :
                logger.info(Msg);
            }
        }
    }
}
