package com.example.platform.render.app;
import com.example.platform.render.api.RenderReadQuery;
import java.util.List;
import org.springframework.stereotype.Service;
@Service
public class RenderReadProjection implements RenderReadQuery {
 private final RenderJobService jobs;
 public RenderReadProjection(RenderJobService jobs){this.jobs=jobs;}
 private Job view(com.example.platform.render.app.dto.RenderJobResponse r){return new Job(r.id(),r.projectId(),r.timelineSnapshotId(),r.profile(),r.status());}
 public List<Job> jobs(){return jobs.list().stream().map(this::view).toList();}
 public List<Job> jobsForProject(String tenant,String project){return jobs.listByProject(tenant,project).stream().map(this::view).toList();}
 public Job job(String id){return view(jobs.getById(id));}
}
