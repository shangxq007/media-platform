package com.example.platform.render.domain.renderplan;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.StereoBalance;
import java.util.Objects;

/**
 * ROADMAP20 correction F1: typed logical materialization requirement for an
 * AUDIO_PROCESS node.
 *
 * <p>Retains the supported authored audio processing WHAT as typed values:
 * gain, mute, balance. Derived projection of the authoritative
 * {@code AudioRoute} semantics — never a second gain model. A future #22
 * Physical Planner can recover supported audio WHAT from the Logical
 * RenderPlan alone, without re-reading {@code AudioRoute}.
 */
public record AudioProcessMaterializationRequirement(
        AudioGain gain,
        AudioMute mute,
        StereoBalance balance) implements RenderMaterializationRequirement {

    public AudioProcessMaterializationRequirement {
        gain = gain != null ? gain : AudioGain.defaultGain();
        mute = mute != null ? mute : AudioMute.defaultMute();
        balance = balance != null ? balance : StereoBalance.neutral();
    }

    public static AudioProcessMaterializationRequirement of(
            AudioGain gain, AudioMute mute, StereoBalance balance) {
        return new AudioProcessMaterializationRequirement(gain, mute, balance);
    }

    @Override
    public String variantKey() {
        return "AUDIO_PROCESS";
    }
}
