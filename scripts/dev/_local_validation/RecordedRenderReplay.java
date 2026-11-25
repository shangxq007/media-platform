import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowImpl;
import io.temporal.testing.WorkflowReplayer;
import java.io.File;

/** Validation entry point for an actual Temporal history exported by the owned formal runtime. */
class RecordedRenderReplay {
    public static void main(String[] args) throws Exception {
        WorkflowReplayer.replayWorkflowExecution(new File(args[0]), MediaTaskWorkflowImpl.class);
        System.out.println("Actual render history replayed successfully");
    }
}
