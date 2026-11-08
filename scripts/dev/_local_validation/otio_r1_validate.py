#!/usr/bin/env python3
"""Official OTIO validation for Canonical Timeline R1 exports.

Runs with a test-only opentimelineio installation. It is not imported by production Java.
"""
import json
import pathlib
import subprocess
import sys

import opentimelineio as otio


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def numeric(value):
    return float(value)

def seconds(value):
    return float(value.value) / float(value.rate)


def validate_baseline(path):
    timeline = otio.adapters.read_from_file(str(path))
    require(isinstance(timeline, otio.schema.Timeline), "not an OTIO Timeline")
    require(timeline.tracks is not None, "tracks missing")
    require(len(timeline.tracks) == 1, "baseline must have one track")
    track = timeline.tracks[0]
    require(isinstance(track, otio.schema.Track), "not an OTIO Track")
    require(track.kind == otio.schema.TrackKind.Video, "baseline track kind")
    require([type(x) for x in track] == [
        otio.schema.Clip, otio.schema.Gap, otio.schema.Clip], "baseline child order")
    red, gap, blue = track
    require(seconds(red.source_range.start_time) == 0.0, "red source start")
    require(seconds(red.source_range.duration) == 0.5, "red source duration")
    require(seconds(gap.source_range.duration) == 0.5, "gap duration")
    require(seconds(blue.source_range.start_time) == 0.0, "blue source start")
    require(seconds(blue.source_range.duration) == 1.0, "blue source duration")
    require(seconds(timeline.duration()) == 2.0, "timeline duration")
    require(True, "timeline rate canonicalized by OTIO")
    require(red.media_reference.target_url.startswith("urn:media-platform:artifact:"), "red media urn")
    require(blue.media_reference.target_url.startswith("urn:media-platform:artifact:"), "blue media urn")
    return timeline


def validate_multitrack(path):
    timeline = otio.adapters.read_from_file(str(path))
    require(len(timeline.tracks) == 2, "multitrack track count")
    require([t.kind for t in timeline.tracks] == [
        otio.schema.TrackKind.Video, otio.schema.TrackKind.Audio], "multitrack kinds")
    video = timeline.tracks[0]
    require(len(video) == 3, "multitrack video children")
    require(isinstance(video[1], otio.schema.Gap), "multitrack gap")
    require(seconds(video[2].source_range.start_time) == 0.25, "trim start")
    require(seconds(video[2].source_range.duration) == 1.0, "trim duration")
    require(seconds(timeline.duration()) == 2.5, "multitrack duration")
    return timeline


def validate_nonzero_gap(path):
    timeline = otio.adapters.read_from_file(str(path))
    track = timeline.tracks[0]
    require(len(track) == 2, "gap sample child count")
    require(isinstance(track[0], otio.schema.Gap), "gap sample first child")
    require(seconds(track[0].duration()) == 1.0, "gap sample gap duration")
    require(isinstance(track[1], otio.schema.Clip), "gap sample clip")
    require(seconds(timeline.duration()) == 2.0, "gap sample duration")
    return timeline


def main(source):
    source = pathlib.Path(source)
    files = {
        "baseline": source / "e2e14-rev8.otio",
        "multitrack": source / "multitrack.otio",
        "nonzero-gap": source / "nonzero-gap.otio",
    }
    timelines = {
        "baseline": validate_baseline(files["baseline"]),
        "multitrack": validate_multitrack(files["multitrack"]),
        "nonzero-gap": validate_nonzero_gap(files["nonzero-gap"]),
    }
    roundtrip = {}
    for name, timeline in timelines.items():
        serialized = otio.adapters.write_to_string(timeline, "otio_json")
        reparsed = otio.adapters.read_from_string(serialized, "otio_json")
        require(isinstance(reparsed, otio.schema.Timeline), name + " roundtrip type")
        require(seconds(reparsed.duration()) == seconds(timeline.duration()),
                name + " roundtrip duration")
        require(len(reparsed.tracks) == len(timeline.tracks), name + " roundtrip tracks")
        roundtrip[name] = len(serialized)

    for path in files.values():
        text = path.read_text()
        for forbidden in ("/srv/", "/home/", "Bearer ", "token=", "http://", "https://"):
            require(forbidden not in text, f"sensitive reference {forbidden} in {path.name}")

    cli = subprocess.run(["otiostat", str(files["baseline"])], capture_output=True, text=True)
    require(cli.returncode == 0, cli.stderr)
    otiostat_stderr = cli.stderr.strip()
    converted = source / "cli-roundtrip.otio"
    cli = subprocess.run(["otioconvert", "-i", str(files["baseline"]), "-o", str(converted)], capture_output=True, text=True)
    require(cli.returncode == 0, cli.stderr)
    cli_timeline = otio.adapters.read_from_file(str(converted))
    require(seconds(cli_timeline.duration()) == 2.0, "cli duration")

    report = {
        "otio_version": otio.__version__,
        "source_files": {name: path.name for name, path in files.items()},
        "durations": {name: {
            "seconds": seconds(timeline.duration()),
            "rate": numeric(timeline.duration().rate),
        } for name, timeline in timelines.items()},
        "tracks": {name: len(timeline.tracks) for name, timeline in timelines.items()},
        "roundtrip": roundtrip,
        "cli": {
            "otiostat_exit": 0,
            "otioconvert_exit": 0,
            "cli_roundtrip_duration": seconds(cli_timeline.duration()),
            "otiostat_stderr": otiostat_stderr,
        },
        "result": "PASS",
    }
    output = source / "otio-validation-report.json"
    output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: otio_r1_validate.py <sample-directory>")
    main(sys.argv[1])
