package com.example.platform.studio.screenplay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.studio.identity.ScreenplayElementId;
import com.example.platform.studio.identity.ScreenplayId;
import com.example.platform.studio.identity.ScreenplayVersionId;
import com.example.platform.studio.scope.ProjectId;
import com.example.platform.studio.scope.StudioScope;
import com.example.platform.studio.scope.TenantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenplayVersionTest {

    private static final StudioScope SCOPE = new StudioScope(new TenantId("tenant-a"), new ProjectId("project-a"));

    @Test
    void preservesStableTypedElementIdentityAndAuthoredOrder() {
        var heading = new ScreenplayElement.SceneHeading(
                new ScreenplayElementId("element-heading"),
                ScreenplayElement.InteriorExterior.INTERIOR,
                "Caf\u00e9",
                ScreenplayElement.TimeOfDay.NIGHT);
        var action = new ScreenplayElement.Action(new ScreenplayElementId("element-action"), "Rain strikes the glass.");

        var version = ScreenplayVersion.create(
                new ScreenplayId("screenplay-a"),
                new ScreenplayVersionId("screenplay-version-1"),
                SCOPE,
                null,
                List.of(heading, action));

        assertThat(version.elements()).containsExactly(heading, action);
        assertThat(version.semanticDigest()).isNotNull();
        assertThat(version.canonicalBytes()).isEqualTo(version.canonicalBytes());
    }

    @Test
    void rejectsDuplicateStableElementIdentityInsteadOfRepairingAuthoredInput() {
        var id = new ScreenplayElementId("same-element");
        assertThatThrownBy(() -> ScreenplayVersion.create(
                new ScreenplayId("screenplay-a"),
                new ScreenplayVersionId("screenplay-version-1"),
                SCOPE,
                null,
                List.of(
                        new ScreenplayElement.Action(id, "First"),
                        new ScreenplayElement.Note(id, "Second"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsBlankTypedIdentitiesAndAbsentScope() {
        assertThatThrownBy(() -> new ScreenplayId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudioScope(null, new ProjectId("project-a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsExactlyTheSevenClosedAuthoredElementVariants() {
        var version = ScreenplayVersion.create(new ScreenplayId("screenplay-a"),
                new ScreenplayVersionId("screenplay-version-1"), SCOPE, null, List.of(
                        new ScreenplayElement.SceneHeading(new ScreenplayElementId("h"),
                                ScreenplayElement.InteriorExterior.EXTERIOR, "Street", ScreenplayElement.TimeOfDay.DUSK),
                        new ScreenplayElement.Action(new ScreenplayElementId("a"), "Moves."),
                        new ScreenplayElement.CharacterCue(new ScreenplayElementId("c"), "Alex"),
                        new ScreenplayElement.Dialogue(new ScreenplayElementId("d"), "Wait."),
                        new ScreenplayElement.Parenthetical(new ScreenplayElementId("p"), "quietly"),
                        new ScreenplayElement.TransitionIntent(new ScreenplayElementId("t"), "Cut to"),
                        new ScreenplayElement.Note(new ScreenplayElementId("n"), "Non-production note")));
        assertThat(version.elements()).hasSize(7);
        assertThat(ScreenplayElement.class.getPermittedSubclasses()).hasSize(7);
    }
}
