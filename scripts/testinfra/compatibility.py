"""Offline dependency/config audit; never resolves remotely or substitutes older targets."""
import hashlib
import json
from pathlib import Path
from state import PolicyError

TARGETS=(('org.postgresql','postgresql','42.7.14'),('org.flywaydb','flyway-core','11.20.3'),
         ('org.flywaydb','flyway-database-postgresql','11.20.3'),('org.jooq','jooq','3.21.9'),
         ('org.jooq','jooq-meta','3.21.9'),('org.jooq','jooq-codegen','3.21.9'))

def audit(output,cache):
    root=Path(__file__).resolve().parents[2];output=output.resolve()
    if output==root or root in output.parents:raise PolicyError('external compatibility output required')
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    coordinates=[]
    for group,name,version in TARGETS:
        folder=cache/group/name/version
        paths=list(folder.glob('*/'+name+'-'+version+'.jar'))
        coordinates.append(dict(coordinate=group+':'+name+':'+version,required_for='codegen' if name in ('jooq-meta','jooq-codegen') else 'runtime/test',
            artifacts=[dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths],
            status='PRESENT_UNVERIFIED' if paths else 'MISSING',download_authorized=False))
    sources={name:(root/name).read_text() for name in ('build.gradle.kts','gradle.properties','settings.gradle.kts','gradle/wrapper/gradle-wrapper.properties','shared-kernel/build.gradle.kts','media-module/build.gradle.kts')}
    checks=dict(java25='JavaLanguageVersion.of(25)' in sources['build.gradle.kts'],jdbc='useVersion("42.7.14")' in sources['build.gradle.kts'],
       flyway='useVersion("11.20.3")' in sources['build.gradle.kts'],jooq='useVersion("3.21.9")' in sources['build.gradle.kts'] and 'jooq.codegen.version=3.21.9' in sources['gradle.properties'],
       boot='spring-boot-dependencies:4.0.4' in sources['build.gradle.kts'],gradle='gradle-9.1.0-bin.zip' in sources['gradle/wrapper/gradle-wrapper.properties'],
       media_fixture='testFixtures(project(":shared-kernel"))' in sources['media-module/build.gradle.kts'],
       jooq_fixture='testFixturesImplementation("org.jooq:jooq")' in sources['shared-kernel/build.gradle.kts'],
       no_container_dependencies=all('org.testcontainers:' not in text for text in sources.values()))
    report=dict(configuration_checks=checks,coordinates=coordinates,target_compile='BLOCKED' if any(x['status']=='MISSING' and x['required_for']=='runtime/test' for x in coordinates) else 'NOT_EXECUTED',
       full_gradle_build='NOT_VERIFIED',downloaded=False,real_database_tests='NOT_EXECUTED',
       note='Presence/configuration is not compilation or runtime compatibility. Missing target artifacts cannot be replaced by the limited cached lock. Transitive gaps remain UNKNOWN until authorized target resolution.')
    (output/'compatibility.json').write_text(json.dumps(report,indent=2)+'\n')
    (output/'minimum-download-authorization.json').write_text(json.dumps(dict(direct_coordinates=[x for x in coordinates if x['status']=='MISSING'],
       also_required='matching POM/module descriptors and exact transitive graph from approved repositories; acquire checksums before use; no repository credentials in evidence',
       transitive_graph='UNKNOWN_NOT_DOWNLOADED',authorization_required=True),indent=2)+'\n')
    return report
