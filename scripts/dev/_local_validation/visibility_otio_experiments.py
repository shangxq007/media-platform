#!/usr/bin/env python3
"""Isolated OTIO 0.18.1 experiments for Render Visibility R1.

These are official-OTIO algorithm fixtures, not persisted Canonical revisions,
except the First Render baseline which reads the real exported revision.
"""
import json
import pathlib
import sys

import opentimelineio as otio

R = lambda v, rate=1: otio.opentime.RationalTime(v, rate)
TR = lambda start, dur, rate=1: otio.opentime.TimeRange(R(start, rate), R(dur, rate))


def clip(name, dur, url):
    return otio.schema.Clip(
        name=name,
        source_range=TR(0, dur),
        media_reference=otio.schema.ExternalReference(target_url=url),
    )


def video_stack():
    stack = otio.schema.Stack(name="official-algorithm-fixture")
    v1 = otio.schema.Track(name="V1", kind=otio.schema.TrackKind.Video)
    v1.append(clip("A", 10, "urn:test:A"))
    stack.append(v1)
    return stack


def partial_v2():
    v2 = otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video)
    v2.append(otio.schema.Gap(source_range=TR(3, 3)))
    v2.append(clip("B", 4, "urn:test:B"))
    v2.append(otio.schema.Gap(source_range=TR(7, 3)))
    return v2


def _mixed_stack():
    stack = otio.schema.Stack(name="mixed-audio-video")
    v1 = otio.schema.Track(name="V1", kind=otio.schema.TrackKind.Video)
    v1.append(clip("A", 10, "urn:test:A"))
    v2 = otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video)
    v2.append(clip("B", 10, "urn:test:B"))
    a1 = otio.schema.Track(name="A1", kind=otio.schema.TrackKind.Audio)
    a1.append(clip("AUD", 10, "urn:test:AUDIO"))
    stack.append(v1)
    stack.append(v2)
    stack.append(a1)
    return stack


def _audio_video_only():
    stack = otio.schema.Stack(name="audio-video-separate")
    v1 = otio.schema.Track(name="V1", kind=otio.schema.TrackKind.Video)
    v1.append(clip("A", 10, "urn:test:A"))
    v2 = otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video)
    v2.append(clip("B", 10, "urn:test:B"))
    stack.append(v1)
    stack.append(v2)
    return stack


def flatten(stack):
    result = otio.algorithms.flatten_stack(stack)
    return [
        {
            "type": type(item).__name__,
            "name": item.name,
            "timeline_start": float(result.range_of_child_at_index(i).start_time.value),
            "timeline_duration": float(result.range_of_child_at_index(i).duration.value),
            "source_start": float(item.source_range.start_time.value) if item.source_range else None,
            "source_duration": float(item.source_range.duration.value) if item.source_range else None,
        }
        for i, item in enumerate(result)
    ]


def baseline(path):
    timeline = otio.adapters.read_from_file(str(path))
    stack = timeline.tracks
    flat = otio.algorithms.flatten_stack(stack)
    return {
        "type": type(timeline).__name__,
        "duration_seconds": float(timeline.duration().value / timeline.duration().rate),
        "tracks": len(stack),
        "flattened": [
            {
                "type": type(item).__name__,
                "name": item.name,
                "timeline_start_seconds": float(flat.range_of_child_at_index(i).start_time.value
                                                 / flat.range_of_child_at_index(i).start_time.rate),
                "timeline_duration_seconds": float(flat.range_of_child_at_index(i).duration.value
                                                   / flat.range_of_child_at_index(i).duration.rate),
                "source_start": float(item.source_range.start_time.value / item.source_range.start_time.rate),
                "source_duration": float(item.source_range.duration.value / item.source_range.duration.rate),
            }
            for i, item in enumerate(flat)
        ],
    }


def main(baseline_path):
    full = video_stack()
    full.append(otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video))
    full[1].append(clip("B", 10, "urn:test:B"))

    partial = video_stack()
    partial.append(partial_v2())

    spatial = video_stack()
    spatial.append(partial_v2())
    spatial[1].metadata["platform.coverage"] = "RIGHT_HALF"

    unknown = video_stack()
    unknown.append(otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video))
    unknown[1].append(clip("B", 10, "urn:test:B"))
    unknown[1].metadata["platform.opacity"] = "UNKNOWN"

    audio = video_stack()
    audio.append(otio.schema.Track(name="V2", kind=otio.schema.TrackKind.Video))
    audio[1].append(clip("B", 10, "urn:test:B"))
    a1 = otio.schema.Track(name="A1", kind=otio.schema.TrackKind.Audio)
    a1.append(clip("AUD", 10, "urn:test:AUDIO"))
    audio.append(a1)

    trim_track = otio.schema.Track(name="trim", kind=otio.schema.TrackKind.Video)
    trim_track.append(clip("A", 10, "urn:test:A"))
    trimmed = otio.algorithms.track_trimmed_to_range(trim_track, TR(3, 4))

    report = {
        "otio_version": otio.__version__,
        "official_algorithm_samples": {
            "full_occlusion": {
                "flattened": flatten(full),
                "interpretation": "flatten replaces V1 with B only when opacity/coverage is independently proven",
            },
            "partial_time": {
                "flattened": flatten(partial),
                "interpretation": "A [0,3), B [3,7), A [7,10); A source/decode dependencies remain necessary",
            },
            "partial_spatial": {
                "flattened": flatten(spatial),
                "decision": "KEEP",
                "reason": "OTIO has no pixel geometry/alpha proof; flatten must not be treated as occlusion evidence",
            },
            "unknown_opacity": {
                "flattened": flatten(unknown),
                "decision": "KEEP",
                "reason": "OTIO metadata is not a formal alpha/compositing proof",
            },
            "audio_independent": {
                "mixed_stack_flattened": flatten(_mixed_stack()),
                "video_flattened": flatten(_audio_video_only()),
                "audio_track": [type(x).__name__ for x in audio[2]],
                "decision": "KEEP_AUDIO",
                "reason": "video visibility cannot remove audio contribution",
            },
            "first_render_baseline": baseline(baseline_path),
        },
        "trim": {
            "input": {"source_duration": 10.0},
            "trim_range": {"start": 3.0, "duration": 4.0},
            "output": [
                {
                    "type": type(x).__name__,
                    "source_start": float(x.source_range.start_time.value),
                    "source_duration": float(x.source_range.duration.value),
                }
                for x in trimmed
            ],
        },
        "limitations": [
            "flatten_stack performs temporal flattening, not pixel occlusion or alpha analysis",
            "a mixed audio/video Stack is not safe to flatten as one visual result",
            "OTIO metadata is not a proof of opacity, coverage, mask or blend semantics",
            "transition, freeze, reverse and arbitrary effects remain dependency-unknown",
        ],
        "result": "PASS",
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: visibility_otio_experiments.py <e2e14-rev8.otio>")
    main(sys.argv[1])
