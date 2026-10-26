package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.CompletedTaskOutputQuery;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqCompletedTaskOutputQuery implements CompletedTaskOutputQuery {
    private final DSLContext db;
    public JooqCompletedTaskOutputQuery(DSLContext db){this.db=db;}
    public Optional<Output> find(String task) {
        var rows=db.fetch("""
            select e.task_id,e.attempt_id,e.generation,e.artifact_id
              from wf_completion_event e join wf_execution_attempt a
                on a.attempt_id=e.attempt_id and a.task_id=e.task_id and a.generation=e.generation
              join wf_task_ownership o on o.task_id=e.task_id and o.current_generation=e.generation
             where e.task_id=? and a.state='SUCCEEDED' for share of a,o
            """,task);
        if(rows.size()>1)throw new IllegalStateException("ambiguous current task output");
        return rows.stream().findFirst().map(r->new Output(r.get("task_id",String.class),r.get("attempt_id",String.class),r.get("generation",Long.class),r.get("artifact_id",String.class)));
    }
}
