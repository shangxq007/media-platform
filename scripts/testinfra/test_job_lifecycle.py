"""Standalone R01/R02 actual-process suite; logs outside Candidate, no real services."""
import argparse,json,time
from pathlib import Path
from job_test_scenarios import run_lifecycle,run_secret_cases
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);a=p.parse_args()
out=a.evidence/'04-offline'/('job-run-'+str(time.time_ns()));out.mkdir(mode=0o700)
life=run_lifecycle(a.evidence,out/'lifecycle');secret=run_secret_cases(a.evidence,out/'secret')
summary=dict(lifecycle_pass=len(life),secret_channel_pass=len(secret),failed=0,evidence=str(out),deployment='DEPLOYMENT_ISOLATION_NOT_VERIFIED')
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(a.evidence/'04-offline/final-job-run.txt').write_text(str(out)+'\n');print(json.dumps(summary,indent=2))
