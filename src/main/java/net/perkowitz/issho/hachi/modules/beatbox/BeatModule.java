package net.perkowitz.issho.hachi.modules.beatbox;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import lombok.Setter;
import net.perkowitz.issho.devices.GridButton;
import net.perkowitz.issho.devices.GridColor;
import net.perkowitz.issho.devices.GridControl;
import net.perkowitz.issho.devices.GridDisplay;
import net.perkowitz.issho.devices.GridListener;
import net.perkowitz.issho.devices.GridPad;
import net.perkowitz.issho.devices.launchpadpro.Color;
import net.perkowitz.issho.hachi.Clockable;
import net.perkowitz.issho.hachi.Multitrack;
import net.perkowitz.issho.hachi.Saveable;
import net.perkowitz.issho.hachi.Sessionizeable;
import net.perkowitz.issho.hachi.modules.MidiModule;
import net.perkowitz.issho.hachi.modules.Module;
import net.perkowitz.issho.hachi.modules.Muteable;
import net.perkowitz.issho.hachi.modules.SettingsSubmodule;
import net.perkowitz.issho.hachi.modules.SettingsUtil;
import org.codehaus.jackson.map.ObjectMapper;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.perkowitz.issho.hachi.modules.beatbox.BeatUtil.*;


/**
 * Created by optic on 10/24/16.
 */
public class BeatModule extends MidiModule implements Module, Clockable, GridListener, Sessionizeable, Saveable, Muteable, Multitrack {

    ObjectMapper objectMapper = new ObjectMapper();

    private BeatMemory memory = new BeatMemory();
    private BeatDisplay beatDisplay;
    private SettingsSubmodule settingsModule;
    private boolean settingsView = false;

    private String filePrefix = "beat";
    @Setter private int midiNoteOffset = 0;

    private int nextStepIndex = 0;
    private Integer nextSessionIndex = null;
    private Integer nextChainStart = null;
    private Integer nextChainEnd = null;
    private boolean playing = false;

    private int selectedStep = 0;
    private int patternsReleasedCount = 0;
    private Set<Integer> patternsPressed = Sets.newHashSet();
    private List<Integer> patternEditIndexBuffer = Lists.newArrayList();
    private boolean patternEditing = false;
    private boolean patternSelecting = false;
    private EditMode editMode = EditMode.ENABLE;


    /***** Constructor ****************************************/

    public BeatModule(Transmitter inputTransmitter, Receiver outputReceiver, Map<Integer, Color> palette, String filePrefix) {
        super(inputTransmitter, outputReceiver);
        this.beatDisplay = new BeatDisplay(this.display);
        this.beatDisplay.setPalette(palette);
        this.filePrefix = filePrefix;
        load(0);
        this.settingsModule = new SettingsSubmodule();
    }


    /***** private implementation ****************************************/

    /**
     * whatever happens every time there's a clock tick
     *
     * @param andReset
     */
    private void advance(boolean andReset) {

        if (andReset) {
            nextStepIndex = 0;
        }

        if (nextStepIndex == 0) {

            int currentPatternIndex = memory.getPlayingPatternIndex();

            // check for new session
            if (nextSessionIndex != null && nextSessionIndex != memory.getCurrentSessionIndex()) {
                memory.selectSession(nextSessionIndex);
                settingsModule.setCurrentSessionIndex(nextSessionIndex);
                nextSessionIndex = null;
            }

            if (nextChainStart != null && nextChainEnd != null) {
                // set chain, and advance pattern to start of chain
                memory.selectChain(nextChainStart, nextChainEnd);
                nextChainStart = null;
                nextChainEnd = null;
                beatDisplay.setNextChainStart(null);
                beatDisplay.setNextChainEnd(null);
            } else {
                // otherwise advance pattern
                memory.advancePattern();
            }

            if (memory.getPlayingPatternIndex() != currentPatternIndex) {
                beatDisplay.drawPatterns(memory);
                beatDisplay.drawSteps(memory);
            }
        }

        // send the midi notes
        for (BeatTrack track : memory.getPlayingPattern().getTracks()) {

            // when the selected track isn't the one currently being played (when there's a chain)
            // get the selected track so we can highlight the playing tracks as the notes hit
            BeatTrack playingTrack = memory.getSelectedPattern().getTrack(track.getIndex());


            BeatStep step = track.getStep(nextStepIndex);
            if (step.isEnabled()) {
//                track.setPlaying(true);
                playingTrack.setPlaying(true);
                if (memory.getCurrentSession().trackIsEnabled(track.getIndex())) {
                    sendMidiNote(memory.getMidiChannel(), track.getNoteNumber(), step.getVelocity());
                }
            }
        }

        // THEN update track displays
        for (BeatTrack track : memory.getPlayingPattern().getTracks()) {
            BeatTrack playingTrack = memory.getSelectedPattern().getTrack(track.getIndex());
            beatDisplay.drawTrack(memory, track.getIndex());
//            track.setPlaying(false);
            playingTrack.setPlaying(false);
        }

        nextStepIndex = (nextStepIndex + 1) % BeatUtil.STEP_COUNT;

    }


    /***** Module implementation **********************************
     *
     * all the basic methods a Module must implement
     */

    /**
     * redraw all the device controls according to current state
     */
    public void redraw() {
        beatDisplay.initialize();
        if (settingsView) {
            settingsModule.redraw();
            beatDisplay.drawLeftControls();
        } else {
            beatDisplay.redraw(memory);
        }
    }

    /**
     * when using a settings module and a dedicated display class, need to make sure
     * the display object is passed on to them, since Hachi may set the module's display
     *
     * @param display: a GridDisplay where this module should display its state
     */
    public void setDisplay(GridDisplay display) {
        this.display = display;
        this.beatDisplay.setDisplay(display);
        this.settingsModule.setDisplay(display);
    }

    /**
     * anything the module should do when Hachi is turned off (e.g. turn off any lingering midi notes)
     */
    public void shutdown() {

    }


    /***** Muteable implementation **********************************
     *
     * a Muteable is anything that may be muted (e.g. by a ShihaiModule
     */

    /**
     * toggle the muted state
     *
     * @param muted
     */
    public void mute(boolean muted) {
        this.isMuted = muted;
        beatDisplay.setMuted(isMuted);
    }

    /**
     * report the current muted state
     *
     * @return boolean
     */
    public boolean isMuted() {
        return isMuted;
    }

    /***** Multitrack implementation ************************************/

    public int trackCount() {
        return BeatUtil.TRACK_COUNT;
    }

    public boolean getTrackEnabled(int index) {
        return memory.getCurrentSession().getTracksEnabled().get(index);
    }

    public void setTrackEnabled(int index, boolean enabled) {
        memory.getCurrentSession().setTrackEnabled(index, enabled);
        beatDisplay.drawTracks(memory);
    }

    public void toggleTrackEnabled(int index) {
        memory.getCurrentSession().toggleTrackEnabled(index);
        beatDisplay.drawTracks(memory);
    }

    public GridColor getEnabledColor() {
        return beatDisplay.getPalette().get(BeatUtil.COLOR_TRACK_SELECTION);
    }




    /***** Sessionizeable implementation ************************************
    *
    * a Sessionizeable implements sessions and/or patterns and can be told
    * which sessions or patterns to load (e.g. by a ShihaiModule)
    * sessions/patterns may be defined differently for every module; they are just referred to by index
    */

    /**
     * select a new Session.
     * module may do as it likes, but most modules load new sessions on the next measure start
     *
     * @param index
     */
    public void selectSession(int index) {
        if (playing) {
            nextSessionIndex = index;
        } else {
            memory.selectSession(index);
            settingsModule.setCurrentSessionIndex(index);
            redraw();
        }
    }

    /**
     * select a new pattern or chain of patterns.
     * module may do as it likes, but most modules load new patterns on the next measure start
     *
     * @param firstIndex
     * @param lastIndex
     */
    public void selectPatterns(int firstIndex, int lastIndex) {
        memory.selectChain(firstIndex, lastIndex);
    }


    /***** GridListener interface ***************************************
     *
     * a GridListener receives events from user input: press and release of pads and buttons
     */

    public void onPadPressed(GridPad pad, int velocity) {
        onControlPressed(new GridControl(pad, null), velocity);
    }

    public void onPadReleased(GridPad pad) {
        onControlReleased(new GridControl(pad, null));
    }

    public void onButtonPressed(GridButton button, int velocity) {
        onControlPressed(new GridControl(button, null), velocity);
    }

    public void onButtonReleased(GridButton button) {
        onControlReleased(new GridControl(button, null));
    }

    /**
     * it is generally easier to use the GridControl abstraction, which generalizes both pads and buttons.
     * many modules pass the listener functions directly here, and then define their controls in their Util class
     *
     * @param control
     * @param velocity
     */
    private void onControlPressed(GridControl control, int velocity) {

        // these controls apply in main view or settings view
        if (control.equals(settingsControl)) {
            settingsView = !settingsView;
            beatDisplay.setSettingsView(settingsView);
            this.redraw();

        } else if (control.equals(muteControl)) {
            isMuted = !isMuted();
            beatDisplay.setMuted(isMuted);
            beatDisplay.drawLeftControls();

        } else if (control.equals(saveControl)) {
            this.save(settingsModule.getCurrentFileIndex());
            beatDisplay.drawControl(control, true);

        } else if (settingsView) {
            // now check if we're in settings view and then process the input accordingly
           onControlPressedSettings(control, velocity);

        // these controls are main view only
        } else if (control.equals(copyControl)) {
            patternEditIndexBuffer.clear();
            patternEditing = true;
            beatDisplay.drawControl(control, true);

        } else if (control.equals(patternSelectControl)) {
            patternSelecting = true;
            beatDisplay.drawControl(control, true);

        } else if (patternPlayControls.contains(control)) {
            int index = patternPlayControls.getIndex(control);
            if (patternEditing) {
                patternEditIndexBuffer.add(index);
            } else if (patternSelecting) {
                memory.selectPattern(index);
                beatDisplay.drawPatterns(memory);
                beatDisplay.drawSteps(memory);
            } else {
                patternsPressed.add(index);
            }

        } else if (trackMuteControls.contains(control)) {
            int index = trackMuteControls.getIndex(control);
            memory.getCurrentSession().toggleTrackEnabled(index);
            beatDisplay.drawTracks(memory);

        } else if (trackSelectControls.contains(control)) {
            int index = trackSelectControls.getIndex(control);
            memory.selectTrack(index);
            beatDisplay.drawTracks(memory);
            beatDisplay.drawSteps(memory);

        } else if (stepControls.contains(control)) {
            int index = stepControls.getIndex(control);
            BeatStep step = memory.getSelectedTrack().getStep(index);
            switch (editMode) {
                case ENABLE:
                    step.toggleEnabled();
                    selectedStep = index;
                    beatDisplay.drawSteps(memory);
                    beatDisplay.drawValue(step.getVelocity(), 127);
                    break;
                case VELOCITY:
                    selectedStep = index;
                    beatDisplay.drawSteps(memory);
                    beatDisplay.drawValue(step.getVelocity(), 127);
                    break;
                case JUMP:
                    nextStepIndex = index;
                    break;
                case PLAY:
                    BeatTrack track = memory.getSelectedPattern().getTrack(index);
                    sendMidiNote(memory.getMidiChannel(), track.getNoteNumber(), velocity);
                    break;
            }

        } else if (editModeControls.contains(control)) {
            int index = editModeControls.getIndex(control);
            editMode = EditMode.values()[index];
            beatDisplay.setEditMode(editMode);
            beatDisplay.drawEditMode();

        } else if (valueControls.contains(control)) {
            BeatStep step = memory.getSelectedTrack().getStep(selectedStep);
            Integer index = valueControls.getIndex(control);
            step.setVelocity((8 - index) * 16 - 1);
            beatDisplay.drawValue(step.getVelocity(), 127);

        }


    }

    /**
     * when settings view is active, the user input should be passed to the SettingsSubmodule
     * but then the module must check with the SettingsSubmodule to see what was changed and
     * follow up accordingly. settings being similar for many modules, this still saves
     * some implementation effort
     *
     * @param control
     * @param velocity
     */
    private void onControlPressedSettings(GridControl control, int velocity) {

        SettingsUtil.SettingsChanged settingsChanged = settingsModule.controlPressed(control, velocity);
        switch (settingsChanged) {
            case SELECT_SESSION:
                selectSession(settingsModule.getNextSessionIndex());
                break;
            case LOAD_FILE:
                load(settingsModule.getCurrentFileIndex());
                break;
            case SAVE_FILE:
                save(settingsModule.getCurrentFileIndex());
                break;
            case SET_MIDI_CHANNEL:
                memory.setMidiChannel(settingsModule.getMidiChannel());
                break;
        }
    }

    /**
     * any momentary controls may need to be lit on press and unlit on release
     *
     * @param control
     */
    private void onControlReleased(GridControl control) {
        if (settingsView) return;

        if (control.equals(saveControl)) {
            beatDisplay.drawControl(control, false);

        } else if (control.equals(copyControl)) {
            if (patternEditIndexBuffer.size() >= 2) {
                Integer fromIndex = patternEditIndexBuffer.get(0);
                Integer toIndex = patternEditIndexBuffer.get(1);
                if (fromIndex != null && toIndex != null) {
                    BeatSession currentSession = memory.getCurrentSession();
                    BeatPattern fromPattern = currentSession.getPattern(fromIndex);
                    BeatPattern clone = BeatPattern.copy(fromPattern, toIndex);
                    currentSession.getPatterns().set(toIndex, clone);
//                    memory.getCurrentSession().getPatterns().set(toIndex, BeatPattern.copy(memory.getCurrentSession().getPattern(fromIndex), toIndex));
                }
            }
            patternEditIndexBuffer.clear();
            patternEditing = false;
            beatDisplay.drawControl(control, false);

        } else if (control.equals(patternSelectControl)) {
            patternSelecting = false;
            beatDisplay.drawControl(control, false);

        } else if (patternPlayControls.contains(control)) {
            // releasing a pattern pad
            // don't activate until the last pattern pad is released (so additional releases don't look like a new press/release)
            if (!patternEditing && !patternSelecting) {
                patternsReleasedCount++;
                if (patternsReleasedCount >= patternsPressed.size()) {
                    GridControl selectedControl = patternPlayControls.get(control);
                    Integer index = selectedControl.getIndex();
                    patternsPressed.add(index); // just to make sure
                    int min = index;
                    int max = index;
                    if (patternsPressed.size() > 1) {
                        for (Integer pattern : patternsPressed) {
                            if (pattern < min) {
                                min = pattern;
                            }
                            if (pattern > max) {
                                max = pattern;
                            }
                        }
                    }
                    nextChainStart = min;
                    nextChainEnd = max;
                    beatDisplay.setNextChainStart(nextChainStart);
                    beatDisplay.setNextChainEnd(nextChainEnd);

                    memory.selectPattern(min);
                    patternsPressed.clear();
                    patternsReleasedCount = 0;
                    beatDisplay.drawPatterns(memory);
                }
            }
        }

    }

    /***** Clockable implementation ***************************************
     *
     * a Clockable can receive clock ticks and start/stop messages from Hachi's master clock.
     */

    public void start(boolean restart) {
        playing = true;
    }

    public void stop() {
        playing = false;
    }

    public void tick(boolean andReset) {
        if (playing) {
            advance(andReset);
        }
    }



    /************************************************************************
     * midi output implementation
     *
     */
    protected void sendMidiNote(int channel, int noteNumber, int velocity) {
        int offsetNoteNumber = midiNoteOffset + noteNumber;
        super.sendMidiNote(channel, offsetNoteNumber, velocity);
    }

    /***** Saveable implementation ***************************************
     *
     * a Saveable can write its memory to a file and load memory from a file.
     * Saveables can also be asked (e.g. by a ShihaiModule) to save/load by index.
     * many modules store a filePrefix and then just select files by appending an index number to the prefix
     */

    public void setFilePrefix(String filePrefix) {
        this.filePrefix = filePrefix;
    }

    public String getFilePrefix() {
        return filePrefix;
    }

    public void save(int index) {
        try {
            String filename = filename(index);
            File file = new File(filename);
            if (file.exists()) {
                // make a backup, but will overwrite any previous backups
                Files.copy(file, new File(filename + ".backup"));
            }
            objectMapper.writeValue(file, memory);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void load(int index) {
        try {
            String filename = filename(index);
            File file = new File(filename);
            if (file.exists()) {
                memory = objectMapper.readValue(file, BeatMemory.class);
            } else {
                memory = new BeatMemory();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String filename(int index) {
        return filePrefix + "-" + index + ".json";
    }



}