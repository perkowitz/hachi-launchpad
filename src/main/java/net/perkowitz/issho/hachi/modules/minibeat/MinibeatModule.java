package net.perkowitz.issho.hachi.modules.minibeat;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import net.perkowitz.issho.devices.*;
import net.perkowitz.issho.hachi.Chord;
import net.perkowitz.issho.hachi.Clockable;
import net.perkowitz.issho.hachi.Saveable;
import net.perkowitz.issho.hachi.Sessionizeable;
import net.perkowitz.issho.hachi.modules.*;
import net.perkowitz.issho.hachi.modules.rhythm.models.Pattern;
import net.perkowitz.issho.hachi.modules.rhythm.models.Step;
import net.perkowitz.issho.hachi.modules.rhythm.models.Track;
import net.perkowitz.issho.hachi.modules.step.StepUtil;
import org.codehaus.jackson.map.ObjectMapper;

import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import java.io.File;
import java.util.List;

import static net.perkowitz.issho.hachi.modules.minibeat.MinibeatUtil.*;


/**
 * Created by optic on 10/24/16.
 */
public class MinibeatModule extends MidiModule implements Module, Clockable, GridListener, Sessionizeable, Saveable, Muteable {

    ObjectMapper objectMapper = new ObjectMapper();

    private MinibeatMemory memory = new MinibeatMemory();
    private MinibeatDisplay minibeatDisplay;
    private SettingsSubmodule settingsModule;
    private boolean settingsView = false;

    private String filePrefix = "minibeat";

    private int nextStepIndex = 0;
    private Integer nextSessionIndex = null;
    private Integer nextChainStart = null;
    private Integer nextChainEnd = null;


    /***** Constructor ****************************************/

    public MinibeatModule(Transmitter inputTransmitter, Receiver outputReceiver, String filePrefix) {
        super(inputTransmitter, outputReceiver);
        this.minibeatDisplay = new MinibeatDisplay(this.display);
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

            // check for new session
            if (nextSessionIndex != null && nextSessionIndex != memory.getCurrentSessionIndex()) {
                memory.selectSession(nextSessionIndex);
                nextSessionIndex = null;
            }

            if (nextChainStart != null && nextChainEnd != null) {
                // set chain, and advance pattern to start of chain
                memory.selectChain(nextChainStart, nextChainEnd);
                nextChainStart = null;
                nextChainEnd = null;
            } else {
                // otherwise advance pattern
                memory.advancePattern();
            }

            minibeatDisplay.drawPatterns(memory);
            minibeatDisplay.drawSteps(memory);
        }

        // send the midi notes
        for (MinibeatTrack track : memory.getPlayingPattern().getTracks()) {
            MinibeatStep step = track.getStep(nextStepIndex);
            if (step.isEnabled()) {
                track.setPlaying(true);
                if (track.isEnabled()) {
                    sendMidiNote(memory.getMidiChannel(), track.getNoteNumber(), step.getVelocity());
                }
            }
        }

        // THEN update track displays
        for (MinibeatTrack track : memory.getPlayingPattern().getTracks()) {
            minibeatDisplay.drawTrack(memory, track.getIndex());
            track.setPlaying(false);
        }

        nextStepIndex = (nextStepIndex + 1) % MinibeatUtil.STEP_COUNT;

    }


    /***** Module implementation **********************************
     *
     * all the basic methods a Module must implement 
     */

    /**
     * redraw all the device controls according to current state
     */
    public void redraw() {
        minibeatDisplay.initialize();
        if (settingsView) {
            settingsModule.redraw();
            minibeatDisplay.drawLeftControls();
        } else {
            minibeatDisplay.redraw(memory);
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
        this.minibeatDisplay.setDisplay(display);
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
        minibeatDisplay.setMuted(isMuted);
    }

    /**
     * report the current muted state
     * 
     * @return boolean
     */
    public boolean isMuted() {
        return isMuted;
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
//        memory.selectSession(index);
        redraw();
    }

    /**
     * select a new pattern or chain of patterns.
     * module may do as it likes, but most modules load new patterns on the next measure start
     * 
     * @param firstIndex
     * @param lastIndex
     */
    public void selectPatterns(int firstIndex, int lastIndex) {
//        memory.selectChain(firstIndex, lastIndex);
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

        if (control.equals(StepUtil.settingsControl)) {
            settingsView = !settingsView;
            minibeatDisplay.setSettingsView(settingsView);
            this.redraw();

        } else if (control.equals(muteControl)) {
            isMuted = !isMuted();
            minibeatDisplay.setMuted(isMuted);
            minibeatDisplay.drawLeftControls();

        } else if (control.equals(saveControl)) {
            this.save(settingsModule.getCurrentFileIndex());
            minibeatDisplay.drawControl(control, true);

        } else if (settingsView) {
            // now check if we're in settings view and then process the input accordingly
           onControlPressedSettings(control, velocity);

        } else if (patternPlayControls.contains(control)) {
            int index = patternPlayControls.getIndex(control);
            nextChainStart = nextChainEnd = index;
            memory.selectPattern(index); // when a new pattern played, select it for editing too

        } else if (patternSelectControls.contains(control)) {
            int index = patternSelectControls.getIndex(control);
            memory.selectPattern(index);
            minibeatDisplay.drawPatterns(memory);
            minibeatDisplay.drawSteps(memory);

        } else if (trackMuteControls.contains(control)) {
            int index = trackMuteControls.getIndex(control);
            memory.getSelectedPattern().getTrack(index).toggleEnabled();
            minibeatDisplay.drawTracks(memory);

        } else if (trackSelectControls.contains(control)) {
            int index = trackSelectControls.getIndex(control);
            memory.selectTrack(index);
            minibeatDisplay.drawTracks(memory);
            minibeatDisplay.drawSteps(memory);

        } else if (stepControls.contains(control)) {
            int index = stepControls.getIndex(control);
            memory.getSelectedTrack().getStep(index).toggleEnabled();
            minibeatDisplay.drawSteps(memory);

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
            minibeatDisplay.drawControl(control, false);
        }

    }

    /***** Clockable implementation ***************************************
     * 
     * a Clockable can receive clock ticks and start/stop messages from Hachi's master clock. 
     */

    public void start(boolean restart) {

    }

    public void stop() {

    }

    public void tick(boolean andReset) {
        advance(andReset);
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
                memory = objectMapper.readValue(file, MinibeatMemory.class);
            } else {
                memory = new MinibeatMemory();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String filename(int index) {
        return filePrefix + "-" + index + ".json";
    }



}
