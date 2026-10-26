package com.example.platform.shared.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Version 2 wire format, consumed only from the controlled launch pipes. */
final class RunConnectionSpec {
    private static final Set<String> FIELDS = Set.of("version", "run_id", "attempt_id", "project_id",
            "parent_branch_id", "branch_id", "endpoint_id", "endpoint", "port", "database", "role",
            "expected_major", "launch_id", "ca_file");
    final String runId, attemptId, projectId, parentBranchId, branchId, endpointId, endpoint, database, role, caFile, launchId;
    final int port = 5432, expectedMajor = 18;
    private RunConnectionSpec(Map<String,String> v) {
        runId=v.get("run_id"); attemptId=v.get("attempt_id"); projectId=v.get("project_id");
        parentBranchId=v.get("parent_branch_id"); branchId=v.get("branch_id"); endpointId=v.get("endpoint_id");
        endpoint=v.get("endpoint"); database=v.get("database"); role=v.get("role"); caFile=v.get("ca_file"); launchId=v.get("launch_id");
    }
    static RunConnectionSpec parse(String text, ControlledLaunch.JobContext job) {
        if (text==null || !text.endsWith("\nEND\n")) throw new IllegalArgumentException("SPEC_TRUNCATED");
        Map<String,String> v=new LinkedHashMap<>();
        for (String line:text.substring(0,text.length()-5).split("\n",-1)) {
            int eq=line.indexOf('=');
            if (eq<=0) throw new IllegalArgumentException("SPEC_LINE");
            String key=line.substring(0,eq), value=line.substring(eq+1);
            if (!FIELDS.contains(key)) throw new IllegalArgumentException("SPEC_UNKNOWN_FIELD");
            if (v.put(key,value)!=null) throw new IllegalArgumentException("SPEC_DUPLICATE_FIELD");
            if (value.isBlank() || value.chars().anyMatch(c -> c<32 || c==127)) throw new IllegalArgumentException("SPEC_VALUE");
        }
        if (!v.keySet().equals(FIELDS)) throw new IllegalArgumentException("SPEC_MISSING_FIELD");
        boolean local="3".equals(v.get("version"));
        if (!local && !"2".equals(v.get("version"))) throw new IllegalArgumentException("SPEC_VERSION");
        if (!job.run().equals(v.get("run_id")) || !job.attempt().equals(v.get("attempt_id")))
            throw new IllegalArgumentException("JOB_RUN_ATTEMPT_MISMATCH");
        if (!job.launch().equals(v.get("launch_id"))) throw new IllegalArgumentException("JOB_LAUNCH_MISMATCH");
        if (!"5432".equals(v.get("port")) || !"18".equals(v.get("expected_major"))) throw new IllegalArgumentException("SPEC_PORT_MAJOR");
        for (String k:new String[]{"project_id","parent_branch_id","branch_id","endpoint_id"})
            if (!v.get(k).matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("SPEC_RESOURCE_ID");
        for (String k:new String[]{"database","role"})
            if (!v.get(k).matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("SPEC_DB_ROLE");
        String host=v.get("endpoint");
        if (local) {
            // Version3 is an explicitly owned, network-isolated local PG18 resource.
            // It never accepts an arbitrary localhost URL/port or environment fallback.
            if (!v.get("project_id").matches("local_[a-f0-9]{64}")
                    || !v.get("branch_id").equals("ct_"+v.get("project_id").substring(6))
                    || !v.get("parent_branch_id").equals("local_fresh") || !host.equals("127.0.0.1")
                    || !v.get("database").equals("mp_f8_"+job.run()+"_"+job.attempt())
                    || !v.get("role").equals("mp_test_"+job.run()+"_"+job.attempt()))
                throw new IllegalArgumentException("SPEC_LOCAL_RESOURCE");
            try {
                String namespace=Files.readSymbolicLink(Path.of("/proc/self/ns/net")).toString();
                if (!v.get("endpoint_id").equals(namespace.replace("net:[","net_").replace("]","")))
                    throw new IllegalArgumentException("SPEC_LOCAL_NAMESPACE");
            } catch (java.io.IOException e) { throw new IllegalArgumentException("SPEC_LOCAL_NAMESPACE"); }
        } else if (!host.matches("[a-z0-9][a-z0-9.-]{1,252}") || !host.contains(".") || host.startsWith("127.") || host.equals("localhost"))
            throw new IllegalArgumentException("SPEC_ENDPOINT");
        Path ca=Path.of(v.get("ca_file"));
        if (!ca.isAbsolute() || Files.isSymbolicLink(ca) || !Files.isRegularFile(ca) || !Files.isReadable(ca))
            throw new IllegalArgumentException("SPEC_CA");
        return new RunConnectionSpec(v);
    }
}
