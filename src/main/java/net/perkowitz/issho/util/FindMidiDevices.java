package net.perkowitz.issho.util;

import net.perkowitz.issho.util.MidiUtil;
import org.apache.commons.lang3.StringUtils;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;


public class FindMidiDevices {

    public static void main(String args[]) throws Exception {

        MidiUtil.printMidiDevices();

    }

}
