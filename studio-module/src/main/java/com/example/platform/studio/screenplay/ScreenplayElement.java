package com.example.platform.studio.screenplay;

import com.example.platform.studio.identity.ScreenplayElementId;
import com.example.platform.studio.serialization.CanonicalJson;
import java.util.Map;

public sealed interface ScreenplayElement permits ScreenplayElement.SceneHeading, ScreenplayElement.Action,
        ScreenplayElement.CharacterCue, ScreenplayElement.Dialogue, ScreenplayElement.Parenthetical,
        ScreenplayElement.TransitionIntent, ScreenplayElement.Note {
    ScreenplayElementId id();
    String canonicalJson();

    enum InteriorExterior { INTERIOR, EXTERIOR, INTERIOR_EXTERIOR }
    enum TimeOfDay { DAWN, DAY, DUSK, NIGHT, CONTINUOUS, UNSPECIFIED }

    record SceneHeading(ScreenplayElementId id, InteriorExterior setting, String location, TimeOfDay timeOfDay)
            implements ScreenplayElement {
        public SceneHeading {
            if (id == null || setting == null || timeOfDay == null) throw new IllegalArgumentException("scene heading fields are required");
            location = CanonicalJson.requiredText(location, "location");
        }
        public String canonicalJson() { return CanonicalJson.object(Map.of(
                "id", CanonicalJson.quote(id.value()), "location", CanonicalJson.quote(location),
                "setting", CanonicalJson.quote(setting.name()), "timeOfDay", CanonicalJson.quote(timeOfDay.name()),
                "type", CanonicalJson.quote("SCENE_HEADING"))); }
    }

    record Action(ScreenplayElementId id, String text) implements ScreenplayElement {
        public Action { if (id == null) throw new IllegalArgumentException("element id is required"); text = CanonicalJson.requiredText(text, "action text"); }
        public String canonicalJson() { return textElement("ACTION", id, text); }
    }
    record CharacterCue(ScreenplayElementId id, String character) implements ScreenplayElement {
        public CharacterCue { if (id == null) throw new IllegalArgumentException("element id is required"); character = CanonicalJson.requiredText(character, "character cue"); }
        public String canonicalJson() { return textElement("CHARACTER_CUE", id, character); }
    }
    record Dialogue(ScreenplayElementId id, String text) implements ScreenplayElement {
        public Dialogue { if (id == null) throw new IllegalArgumentException("element id is required"); text = CanonicalJson.requiredText(text, "dialogue text"); }
        public String canonicalJson() { return textElement("DIALOGUE", id, text); }
    }
    record Parenthetical(ScreenplayElementId id, String text) implements ScreenplayElement {
        public Parenthetical { if (id == null) throw new IllegalArgumentException("element id is required"); text = CanonicalJson.requiredText(text, "parenthetical text"); }
        public String canonicalJson() { return textElement("PARENTHETICAL", id, text); }
    }
    record TransitionIntent(ScreenplayElementId id, String text) implements ScreenplayElement {
        public TransitionIntent { if (id == null) throw new IllegalArgumentException("element id is required"); text = CanonicalJson.requiredText(text, "transition intent"); }
        public String canonicalJson() { return textElement("TRANSITION_INTENT", id, text); }
    }
    record Note(ScreenplayElementId id, String text) implements ScreenplayElement {
        public Note { if (id == null) throw new IllegalArgumentException("element id is required"); text = CanonicalJson.requiredText(text, "note text"); }
        public String canonicalJson() { return textElement("NOTE", id, text); }
    }

    private static String textElement(String type, ScreenplayElementId id, String text) {
        return CanonicalJson.object(Map.of("id", CanonicalJson.quote(id.value()), "text", CanonicalJson.quote(text), "type", CanonicalJson.quote(type)));
    }
}
