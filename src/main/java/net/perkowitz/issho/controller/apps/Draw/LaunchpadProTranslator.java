/**
 * Translator to implement the Draw app on a LaunchpadPro.
 */
package net.perkowitz.issho.controller.apps.Draw;

import net.perkowitz.issho.controller.Controller;
import net.perkowitz.issho.controller.ControllerListener;
import net.perkowitz.issho.controller.elements.Button;
import net.perkowitz.issho.controller.elements.Knob;
import net.perkowitz.issho.controller.elements.Light;
import net.perkowitz.issho.controller.elements.Pad;
import net.perkowitz.issho.controller.novation.LaunchpadPro;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import java.awt.*;

public class LaunchpadProTranslator implements Controller, Receiver {

    private LaunchpadPro launchpad;

    private Button[] palette;

    public LaunchpadProTranslator(LaunchpadPro launchpad) {
        this.launchpad = launchpad;
    }


    /***** Controller implementation *****/

    public void initialize() {
        launchpad.initialize();
    }

    public void setPad(Pad pad, Color color) {
        launchpad.setPad(pad, color);
    }

    public void setButton(Button button, Color color) {
        launchpad.setButton(button, color);
    }

    public void setKnob(Knob knob, Color color) {}

    public void setLight(Light light, Color color) {}

    public void setListener(ControllerListener listener) {}


    /***** Receiver implementation *****/

    public void send(MidiMessage message, long timeStamp) {
        launchpad.send(message, timeStamp);
    }

    public void close() {
        launchpad.close();
    }

}
