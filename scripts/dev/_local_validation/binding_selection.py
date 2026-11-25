"""Read the persisted, UTF-8 byte-framed binding selection for formal runtime evidence."""
import base64


def selection(text):
    data = text.encode("utf-8")

    def frame():
        nonlocal data
        length, delimiter, rest = data.partition(b":")
        assert delimiter and length.isdigit()
        size = int(length)
        assert len(rest) >= size
        result, data = rest[:size], rest[size:]
        return result.decode("utf-8")

    assert frame() == "binding-selection-v1" and frame() == "policy"
    policy = frame()
    tag = "bmf-preferred-homogeneous-v1"
    prefix = str(len(tag)) + ":" + tag
    restrictions = {prefix + "0": None, prefix + "13:bmf": "bmf", prefix + "16:ffmpeg": "ffmpeg"}
    restriction = restrictions[policy]
    count = int(frame())
    assert count > 0
    candidates = [base64.b64decode(frame(), validate=True).decode("utf-8") for _ in range(count)]
    assert not data
    return restriction, "\n".join(candidates)
