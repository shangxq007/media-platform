"""Observe the production graph's single audio input before the AAC boundary.

Run the generated graph program with stream objects that record FFmpeg calls.
Replay that exact audio filter chain against constant stereo PCM in libavfilter.
The stub is only the graph recorder, not a gain, delay, or sample evaluator.
"""
import json
import pathlib
import subprocess
import sys
import types

arguments, output = map(pathlib.Path, sys.argv[1:])
args = arguments.read_bytes().decode().split('\0')
assert args[:2] == ['-s', '-c']
program, graph_args = args[2], args[3:]
assert graph_args[0] == '1' and graph_args[-1].startswith('A,')
final_audio = None


class Stream:
    def __init__(self, filters=()):
        self.filters = filters

    def encode(self, mixed, options):
        global final_audio
        assert options['audio_params'] == {'codec': 'aac', 'sample_rate': 48000, 'channels': 2}
        final_audio = mixed.filters
        return types.SimpleNamespace(run=lambda: pathlib.Path(options['output_path']).write_bytes(b'graph-recorded'))


def record(stream, name, *params, **options):
    return Stream((*stream.filters, (name, params, options)))


def wrapper(name):
    return lambda stream, *params, **options: record(stream, name, *params, **options)


bmf = types.ModuleType('bmf')
builder = types.ModuleType('bmf.builder')
filters = types.ModuleType('bmf.builder.ff_filter')
filters.ff_filter = record
filters.__getattr__ = wrapper
bmf.graph = lambda: types.SimpleNamespace(decode=lambda _: {'audio': Stream()})
sys.modules.update({'bmf': bmf, 'bmf.builder': builder, 'bmf.builder.ff_filter': filters})
graph_args[2] = str(output / 'graph-marker.bin')
sys.argv = ['bmf-production-graph', *graph_args]
exec(compile(program, 'production-multitrack-graph', 'exec'), {'__name__':'__main__'})
assert final_audio is not None
assert [n for n, _, _ in final_audio].count('aeval') == 1
assert [n for n, _, _ in final_audio].count('adelay') == 1
assert final_audio[-1] == ('aformat', (), {'sample_fmts': 's16'})
(output / 'filters.json').write_text(json.dumps({'placement':graph_args[-1],'beforeAac':final_audio},indent=2)+'\n')


def field(value):
    return "'" + str(value) + "'"


def filter_arg(item):
    name, parameters, options = item
    parts = [field(p) for p in parameters]
    parts.extend(f'{k}={field(v)}' for k,v in options.items())
    return name+('=' + ':'.join(parts) if parts else '')


chain = ','.join(map(filter_arg, final_audio))
subprocess.run(['/usr/bin/ffmpeg','-hide_banner','-loglevel','error','-nostdin','-y','-f','lavfi',
    '-i','aevalsrc=0.125|0.0625:s=48000:d=2','-af',chain,'-f','s16le',
    '-c:a','pcm_s16le',str(output/'before-aac.s16le')], check=True)
