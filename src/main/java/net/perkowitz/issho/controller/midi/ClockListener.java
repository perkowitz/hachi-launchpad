package net.perkowitz.issho.controller.midi;

public interface ClockListener {

    public void onStart(boolean restart);
    public void onStop();
    public void onTick();

}
