package com.example.platform.workflow.adapter;

import com.example.platform.workflow.temporal.RenderWorkflowIdentity;
import io.temporal.client.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TemporalRenderCancellationContinuationTest {
    @Test void cancelsTheSameScopedIdentityAsBothRenderStarters() {
        var client=mock(WorkflowClient.class);var stub=mock(WorkflowStub.class);
        when(client.newUntypedWorkflowStub("render:tenant:job")).thenReturn(stub);
        new TemporalRenderCancellationContinuation(client).cancelAfterJobCancelled("tenant","job");
        verify(client).newUntypedWorkflowStub(RenderWorkflowIdentity.forJob("tenant","job"));verify(stub).cancel();verifyNoMoreInteractions(client,stub);
    }
    @Test void transportFailureIsReportedInsteadOfPretendingToCancel() {
        var client=mock(WorkflowClient.class);var stub=mock(WorkflowStub.class);when(client.newUntypedWorkflowStub("render:tenant:job")).thenReturn(stub);
        doThrow(new IllegalStateException("synthetic unavailable transport")).when(stub).cancel();
        assertThrows(IllegalStateException.class,()->new TemporalRenderCancellationContinuation(client).cancelAfterJobCancelled("tenant","job"));
    }
}
