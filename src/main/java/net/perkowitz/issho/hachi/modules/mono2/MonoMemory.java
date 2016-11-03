package net.perkowitz.issho.hachi.modules.mono2;

import lombok.Getter;
import lombok.Setter;

import static net.perkowitz.issho.hachi.modules.mono2.MonoUtil.StepEditState.NOTE;
import static net.perkowitz.issho.hachi.modules.mono2.MonoUtil.StepEditState.VELOCITY;

/**
 * Created by optic on 10/24/16.
 */
public class MonoMemory {

    private static int SESSION_COUNT = 16;

    @Getter private MonoSession[] sessions = new MonoSession[SESSION_COUNT];
    @Getter private int currentSessionIndex = 0;
    @Getter private int currentPatternIndex = 0;
    @Getter private int currentStepIndex = 0;
    @Getter @Setter private int keyboardOctave;

    @Getter @Setter MonoUtil.StepEditState stepEditState = NOTE;
    @Getter @Setter MonoUtil.ValueState valueState = MonoUtil.ValueState.VELOCITY;


    public MonoMemory() {
        for (int i = 0; i < SESSION_COUNT; i++) {
            sessions[i] = new MonoSession(i);
        }
    }


    /***** getters for step/pattern/session by index or current **************************/

    public MonoSession currentSession() {
        return sessions[currentSessionIndex];
    }

    public MonoPattern currentPattern() {
        return currentSession().getPattern(currentPatternIndex);
    }

    public MonoStep currentStep() {
        return currentPattern().getStep(currentStepIndex);
    }

    public MonoSession getSession(int index) {
        return sessions[index];
    }

    public MonoPattern getPattern(int index) { return currentSession().getPattern(index); }

    public MonoStep getStep(int index) { return currentPattern().getStep(index); }


    /***** select *******************************************************/

    public void selectStep(int index) {
        currentStep().setSelected(false);
        currentStepIndex = index;
        currentStep().setSelected(true);
    }

}
