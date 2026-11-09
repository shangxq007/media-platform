package com.example.platform.config.firstrender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.platform.execution.binding.*;
import com.example.platform.render.api.GraphRenderLifecycle;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import org.junit.jupiter.api.Test;
class FirstRenderSaveAndStartServiceTest {
    @Test void savesInputsBeforeMarkingDispatchReady() {
        var store=mock(BoundGraphInputStore.class);var lifecycle=mock(GraphRenderLifecycle.class);
        var plan=new FirstRenderPlanningAndBindingResult("tenant","job",new RenderRequestId("request"),mock(BoundGraphInputs.class));
        var reference=new BoundGraphReference("tenant","job","ref","a".repeat(64),"b".repeat(64));
        when(store.save(plan.boundGraphInputs(),"tenant","job")).thenReturn(reference);
        assertThat(new FirstRenderSaveAndStartService(store,lifecycle).save(plan)).isSameAs(reference);
        var order=inOrder(store,lifecycle);
        order.verify(lifecycle).preparationStage("tenant","job",GraphRenderLifecycle.PreparationStage.BOUND_GRAPH_SAVE);
        order.verify(store).save(plan.boundGraphInputs(),"tenant","job");
        order.verify(lifecycle).preparationStage("tenant","job",GraphRenderLifecycle.PreparationStage.DISPATCH);
    }
    @Test void saveFailureCannotBecomeReadyIntent() {
        var store=mock(BoundGraphInputStore.class);var lifecycle=mock(GraphRenderLifecycle.class);
        when(store.save(any(),any(),any())).thenThrow(new IllegalStateException("save failed"));
        var plan=new FirstRenderPlanningAndBindingResult("tenant","job",new RenderRequestId("request"),mock(BoundGraphInputs.class));
        assertThatThrownBy(()->new FirstRenderSaveAndStartService(store,lifecycle).save(plan)).hasMessage("save failed");
        verify(lifecycle,never()).preparationStage("tenant","job",GraphRenderLifecycle.PreparationStage.DISPATCH);
    }
}
