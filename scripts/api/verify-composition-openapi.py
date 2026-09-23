#!/usr/bin/env python3
"""Fail-closed contract check: controller, runtime export, candidate and base agree."""
import json,re,sys,argparse
from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[2]
controller=(root/'composition-module/src/main/java/com/example/platform/composition/api/CompositionController.java').read_text()
methods=re.findall(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping)\("([^"]+)',controller)
expected={('/composition'+path, method.removesuffix('Mapping').lower()) for method,path in methods}
def load(p):
    with open(p) as f:return yaml.safe_load(f)
def ops(doc,prefix='/composition'):
    out=set()
    for path,item in doc.get('paths',{}).items():
      if path.startswith(prefix):
       for method in item:
        if method.lower() in {'get','post','put','patch','delete','options','head','trace'}:out.add((path,method.lower()))
    return out
cand=load(root/'contracts/http/media-api/openapi.candidate.yaml'); base=load(root/'contracts/http/media-api/openapi.base.yaml'); parser=argparse.ArgumentParser(); parser.add_argument('--runtime', default=str(root/'docs/api/openapi-preview-current.json')); args=parser.parse_args(); runtime=json.loads(Path(args.runtime).read_text())
rt={ (p.removeprefix('/api'),m.lower()) for p,item in runtime.get('paths',{}).items() if p.startswith('/api/composition') for m in item if m.lower() in {'get','post','put','patch','delete','options','head','trace'} }
results={'controller':expected,'candidate':ops(cand),'base':ops(base),'runtime':rt}
failed=False
for name,value in results.items():
 print(f'{name} operations={len(value)}')
 miss=expected-value; extra=value-expected
 if miss: print(f'  missing from {name}: {sorted(miss)}');failed=True
 if extra: print(f'  undocumented in {name}: {sorted(extra)}');failed=True
# Runtime is the generation authority for composition schemas.  Candidate/base must expose each model.
required={'CapabilityAvailability','TemplateWorkflow','WorkflowOutput','Application','ValidationIssue','ValidationResult','WorkflowEntry','ContractRef'}
for name,doc in [('candidate',cand),('base',base)]:
 schemas=set(doc.get('components',{}).get('schemas',{}))
 missing=required-schemas
 if missing: print(f'  {name} missing schemas: {sorted(missing)}'); failed=True
runtime_schemas=set(runtime.get('components',{}).get('schemas',{}))
missing=required-runtime_schemas
if missing: print('runtime missing schemas:',sorted(missing));failed=True
if failed: raise SystemExit(1)
print('composition OpenAPI controller/runtime/candidate/base agreement: PASS')
