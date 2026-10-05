"""Real D3D11 shared-texture round-trip and native lifecycle checks."""
import ctypes as C
import pathlib
import struct
import subprocess
import time

root = pathlib.Path(__file__).resolve().parents[3]
dll = C.CDLL(str(root / 'build/native-check/Release/spw-spout.dll'))
dll.spw_spout_create.argtypes = [C.c_char_p, C.c_int]
dll.spw_spout_create.restype = C.c_void_p
dll.spw_spout_send.argtypes = [C.c_void_p, C.c_void_p, C.c_int, C.c_int]
dll.spw_spout_destroy.argtypes = [C.c_void_p]
dll.spw_spout_error.restype = C.c_char_p
probe = root / 'build/native-check/Release/spout-probe.exe'
out = root / 'build/verification'
out.mkdir(parents=True, exist_ok=True)
assert not dll.spw_spout_create(b'SPW Invalid Adapter Check', 31)
assert dll.spw_spout_error()
for cycle, (w, h) in enumerate([(320, 128), (640, 256), (320, 128)]):
    sender = dll.spw_spout_create(b'SPW Native Check', 0)
    assert sender, dll.spw_spout_error()
    # Red at top, blue at bottom; RGB <= alpha. Transparent right half.
    data = bytearray(w * h * 4)
    for y in range(h):
        for x in range(w // 2):
            p = (y * w + x) * 4
            data[p:p+4] = bytes([0, 0, 128, 128] if y < h // 2 else [192, 0, 0, 192])
    pixels = (C.c_ubyte * len(data)).from_buffer(data)
    assert dll.spw_spout_send(sender, pixels, w, h) == 1
    path = out / f'native-{cycle}.bgra'
    receiver = subprocess.Popen([str(probe), 'SPW Native Check', str(path)], stdout=subprocess.PIPE)
    deadline = time.monotonic() + 20
    while receiver.poll() is None and time.monotonic() < deadline:
        assert dll.spw_spout_send(sender, pixels, w, h) == 1
        time.sleep(.016)
    assert receiver.wait(timeout=2) == 0
    raw = path.read_bytes()
    assert struct.unpack('<II', raw[:8]) == (w, h)
    assert raw[8:] == data, 'Shared texture changed BGRA, alpha or orientation'
    dll.spw_spout_destroy(sender)
    senders = subprocess.check_output([str(probe)]).decode(errors='replace')
    assert 'SPW Native Check' not in senders, 'Sender leaked after destroy'
    print(f'PASS roundtrip/lifecycle {w}x{h}', flush=True)
print('PASS invalid adapter; exact BGRA/alpha/orientation; repeat create/destroy and resize')
