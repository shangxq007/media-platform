package com.example.platform.operation.operation;

import com.example.platform.operation.operation.OperationDefinitionVersion;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM5/OM6/OM7/OM8/OIR4): static code-owned typed
 * OperationDefinition. Each definition owns its target contract (kind +
 * cardinality), parameter contract, OperationDefinitionVersion and lifecycle.
 * No dynamic registry; no Map schema; no String action dispatch.
 */
public record OperationDefinition(
        OperationDefinitionId definitionId,
        OperationDefinitionVersion version,
        Lifecycle lifecycle,
        TargetKind targetKind,
        int minCardinality,
        int maxCardinality,
        Class<? extends OperationParameters> parameterType) {

    public enum Lifecycle {
        ACTIVE,
        RETIRED
    }

    public enum TargetKind {
        TIMELINE,
        CLIP_SCOPE,
        GROUP,
        SYNC,
        AUDIO,
        TEXT
    }

    public OperationDefinition {
        if (definitionId == null || version == null || lifecycle == null || targetKind == null
                || parameterType == null) {
            throw new IllegalArgumentException("definition fields required");
        }
        if (minCardinality < 1 || maxCardinality < minCardinality) {
            throw new IllegalArgumentException("invalid cardinality contract");
        }
    }

    /** Static code-owned V1 vocabulary (exactly 15, frozen). */
    public static final class V1 {
        /** H7: first vertically complete real-media authoring operation. */
        public static final OperationDefinition ADD_MEDIA_CLIP = new OperationDefinition(
                OperationDefinitionId.of("timeline.media-clip.add"),
                OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.TIMELINE,
                1, 1, OperationParameters.AddMediaClipParameters.class);
        public static final OperationDefinition MOVE = def("timeline.move", 1, Integer.MAX_VALUE, OperationParameters.MoveParameters.class);
        public static final OperationDefinition DELETE = def("timeline.delete", 1, Integer.MAX_VALUE, OperationParameters.NoParameters.class);
        public static final OperationDefinition TRIM = def("timeline.trim", 1, 1, OperationParameters.TrimParameters.class);
        public static final OperationDefinition SET_TEMPORAL_RATE = def("timeline.temporal.set-rate", 1, Integer.MAX_VALUE, OperationParameters.SetTemporalRateParameters.class);
        public static final OperationDefinition SET_TEMPORAL_DIRECTION = def("timeline.temporal.set-direction", 1, Integer.MAX_VALUE, OperationParameters.SetTemporalDirectionParameters.class);
        public static final OperationDefinition FREEZE = def("timeline.temporal.freeze", 1, 1, OperationParameters.FreezeParameters.class);
        public static final OperationDefinition SET_AUDIO_GAIN = new OperationDefinition(OperationDefinitionId.of("audio.gain.set"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.AUDIO, 1, 1, OperationParameters.AudioGainParameters.class);
        public static final OperationDefinition SET_AUDIO_MUTE = new OperationDefinition(OperationDefinitionId.of("audio.mute.set"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.AUDIO, 1, 1, OperationParameters.AudioMuteParameters.class);
        public static final OperationDefinition SET_STEREO_BALANCE = new OperationDefinition(OperationDefinitionId.of("audio.balance.set"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.AUDIO, 1, 1, OperationParameters.StereoBalanceParameters.class);
        public static final OperationDefinition CREATE_GROUP = def("timeline.group.create", 2, Integer.MAX_VALUE, OperationParameters.CreateGroupParameters.class);
        public static final OperationDefinition UPDATE_GROUP_MEMBERSHIP = new OperationDefinition(OperationDefinitionId.of("timeline.group.update-members"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.GROUP, 1, 1, OperationParameters.UpdateGroupMembershipParameters.class);
        public static final OperationDefinition REMOVE_GROUP = new OperationDefinition(OperationDefinitionId.of("timeline.group.remove"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.GROUP, 1, 1, OperationParameters.NoParameters.class);
        public static final OperationDefinition CREATE_SYNC = new OperationDefinition(OperationDefinitionId.of("timeline.sync.create"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.SYNC, 1, 1, OperationParameters.CreateSyncParameters.class);
        public static final OperationDefinition UPDATE_SYNC_ANCHOR = new OperationDefinition(OperationDefinitionId.of("timeline.sync.update-anchor"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.SYNC, 1, 1, OperationParameters.UpdateSyncAnchorParameters.class);
        public static final OperationDefinition REMOVE_SYNC = new OperationDefinition(OperationDefinitionId.of("timeline.sync.remove"), OperationDefinitionVersion.of(1, 0), Lifecycle.ACTIVE, TargetKind.SYNC, 1, 1, OperationParameters.NoParameters.class);

        private static OperationDefinition def(String id, int min, int max, Class<? extends OperationParameters> param) {
            return new OperationDefinition(OperationDefinitionId.of(id), OperationDefinitionVersion.of(1, 0),
                    Lifecycle.ACTIVE, TargetKind.CLIP_SCOPE, min, max, param);
        }

        // ROADMAP_19 (C37/FTG19): nine typed Text operations (frozen set, no 10th).
        public static final OperationDefinition ADD_TEXT_ELEMENT = textDef("timeline.text.add", OperationParameters.AddTextElementParameters.class);
        public static final OperationDefinition REMOVE_TEXT_ELEMENT = textDef("timeline.text.remove", OperationParameters.RemoveTextElementParameters.class);
        public static final OperationDefinition REPLACE_TEXT_CONTENT = textDef("timeline.text.replace-content", OperationParameters.ReplaceTextContentParameters.class);
        public static final OperationDefinition SET_TEXT_STYLE_RANGE = textDef("timeline.text.set-style-range", OperationParameters.SetTextStyleRangeParameters.class);
        public static final OperationDefinition SET_PARAGRAPH_STYLE = textDef("timeline.text.set-paragraph-style", OperationParameters.SetParagraphStyleParameters.class);
        public static final OperationDefinition SET_FONT_SELECTION = textDef("timeline.text.set-font-selection", OperationParameters.SetFontSelectionParameters.class);
        public static final OperationDefinition SET_FONT_FALLBACK_POLICY = textDef("timeline.text.set-fallback-policy", OperationParameters.SetFontFallbackPolicyParameters.class);
        public static final OperationDefinition SET_VARIABLE_FONT_AXIS = textDef("timeline.text.set-variable-axis", OperationParameters.SetVariableFontAxisParameters.class);
        public static final OperationDefinition SET_TEXT_LAYOUT = textDef("timeline.text.set-layout", OperationParameters.SetTextLayoutParameters.class);

        private static OperationDefinition textDef(String id, Class<? extends OperationParameters> param) {
            return new OperationDefinition(OperationDefinitionId.of(id), OperationDefinitionVersion.of(1, 0),
                    Lifecycle.ACTIVE, TargetKind.TEXT, 1, 1, param);
        }

        public static final java.util.List<OperationDefinition> ALL = java.util.List.of(
                ADD_MEDIA_CLIP,
                MOVE, DELETE, TRIM, SET_TEMPORAL_RATE, SET_TEMPORAL_DIRECTION, FREEZE,
                SET_AUDIO_GAIN, SET_AUDIO_MUTE, SET_STEREO_BALANCE,
                CREATE_GROUP, UPDATE_GROUP_MEMBERSHIP, REMOVE_GROUP,
                CREATE_SYNC, UPDATE_SYNC_ANCHOR, REMOVE_SYNC,
                ADD_TEXT_ELEMENT, REMOVE_TEXT_ELEMENT, REPLACE_TEXT_CONTENT,
                SET_TEXT_STYLE_RANGE, SET_PARAGRAPH_STYLE, SET_FONT_SELECTION,
                SET_FONT_FALLBACK_POLICY, SET_VARIABLE_FONT_AXIS, SET_TEXT_LAYOUT);

        private V1() {
        }
    }
}
