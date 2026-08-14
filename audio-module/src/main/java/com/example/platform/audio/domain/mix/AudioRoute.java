package com.example.platform.audio.domain.mix;

import java.util.List;
import java.util.Objects;

/**
 * AUDIO_V2 (A7/A4/A5/A6/A9): canonical audio route — the typed contribution of
 * one clip's audio into the mix: input reference, gain, mute, stereo balance
 * and an ordered bounded DSP chain. Deterministic order: the route list of
 * {@link AudioMix} preserves caller order (semantic), and this record's own
 * fields are fixed. No provider labels, no FFmpeg syntax.
 */
public record AudioRoute(
        AudioMixInput input,
        AudioGain gain,
        AudioMute mute,
        StereoBalance balance,
        List<AudioDspNode> dspChain) {

    public AudioRoute {
        Objects.requireNonNull(input, "input");
        if (gain == null) gain = AudioGain.defaultGain();
        if (mute == null) mute = AudioMute.defaultMute();
        if (balance == null) balance = StereoBalance.neutral();
        if (dspChain == null) dspChain = List.of();
        dspChain = List.copyOf(dspChain);
    }

    public static AudioRoute of(AudioMixInput input) {
        return new AudioRoute(input, AudioGain.defaultGain(), AudioMute.defaultMute(),
                StereoBalance.neutral(), List.of());
    }

    public static AudioRoute of(AudioMixInput input, AudioGain gain) {
        return new AudioRoute(input, gain, AudioMute.defaultMute(), StereoBalance.neutral(), List.of());
    }

    public AudioRoute withGain(AudioGain newGain) {
        return new AudioRoute(input, newGain, mute, balance, dspChain);
    }

    public AudioRoute withMute(AudioMute newMute) {
        return new AudioRoute(input, gain, newMute, balance, dspChain);
    }

    public AudioRoute withBalance(StereoBalance newBalance) {
        return new AudioRoute(input, gain, mute, newBalance, dspChain);
    }

    public AudioRoute withDspChain(List<AudioDspNode> newChain) {
        return new AudioRoute(input, gain, mute, balance, newChain);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("route(").append(input)
                .append(" gain=").append(gain)
                .append(" mute=").append(mute)
                .append(" balance=").append(balance);
        for (AudioDspNode n : dspChain) {
            sb.append(" dsp=").append(n);
        }
        return sb.append(')').toString();
    }
}
