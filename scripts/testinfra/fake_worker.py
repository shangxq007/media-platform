"""Internal fixed chain task; invoked beneath the CLI network-denial boundary."""
import argparse
from fake_run import run
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--evidence',type=Path,required=True);a=p.parse_args()
raise SystemExit(run(a.evidence/'04-offline/launch-probe.json',a.evidence/'04-offline/controlled-run'))
