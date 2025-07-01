import serial
import threading
import time
import sys
import argparse
import queue
import math


class CountMinSketch:
    def __init__(self, hash_fns):
        self.hash_fns = hash_fns
        self.sketch = [[0 for _ in range(16)] for _ in range(len(hash_fns))] # Hash function should return 4-bit index

    def update(self, value):
        for i, h in enumerate(self.hash_fns):
            index = h(value) & 0xF
            self.sketch[i][index] += 1

    def estimate(self, value):
        return min(
            self.sketch[i][h(value) & 0xF]
            for i, h in enumerate(self.hash_fns)
        )

    def dump(self):
        return self.sketch

    def __str__(self):
        lines = ["Sketch state:"]
        for row in self.sketch:
            lines.append(" ".join(f"{v:02x}" for v in row))
        return "\n".join(lines)

def h1(x):
    high, low = (x >> 4) & 0xF, x & 0xF
    return (high ^ low) & 0xF

def h2(x):
    high, low = (x >> 4) & 0xF, x & 0xF
    return (high + low) & 0xF

def h3(x):
    high, low = (x >> 4) & 0xF, x & 0xF
    return (high * low) & 0xF

SERIAL_PORT = '/dev/ttyUSB1'
BAUD_RATE = 9600
LISTEN_DURATION = 2  # seconds

def send_data(ser, payload):
    time.sleep(0.1)  # ensure device is ready
    for b in payload:
        # print(f"Sending byte: {b:02X}")
        ser.write(bytes([b]))
        ser.flush()
        time.sleep(0.01)  # slight delay between bytes (tweak as needed)

def receive_data(ser, duration=LISTEN_DURATION) -> bytes:
    print(f"Listening for {duration} seconds...")
    end_time = time.time() + duration
    received = bytearray()

    while time.time() < end_time:
        if ser.in_waiting:
            byte = ser.read(1)
            received += byte
        else:
            time.sleep(0.01)  # Sleep briefly to avoid CPU hogging

    return bytes(received)

def output_raw(data):
    for byte in data:
        print(f"{byte:02X}")

def output_tabled(data, item_width_bits: int = 8, array_width: int = 1):
    if item_width_bits % 8 != 0:
        raise ValueError("Item width must be a multiple of 8.")
    item_width_bytes = item_width_bits // 8
    if item_width_bytes == 0:
        raise ValueError("Item width must be at least 8 bits (1 byte).")

    grouped = [data[i:i+item_width_bytes] for i in range(0, len(data), item_width_bytes)]
    for i in range(0, len(grouped), array_width):
        row = grouped[i:i+array_width]
        print("  ".join("".join(f"{b:02X}" for b in item) for item in row))

def receive_formatted_data(mode: str, item_width: int, array_width: int, ser, result_queue):
    data = receive_data(ser)

    if mode == "raw":
        output_raw(data)
    elif mode == "tabled":
        output_tabled(data, item_width, array_width)
    else:
        raise ValueError(f"Unknown output mode: {mode}")

    result_queue.put(data)

def parse_string_input(args):
    return b''.join(arg.encode() for arg in args)

def parse_hex_input(args):
    try:
        return bytes(int(arg, 16) for arg in args)
    except ValueError:
        raise ValueError("All arguments must be valid hexadecimal bytes (e.g., '0f', '1A', 'FF').")

def parse_decimal_input(args):
    try:
        return bytes(int(arg) for arg in args if 0 <= int(arg) <= 255)
    except ValueError:
        raise ValueError("All arguments must be integers in the range 0-255.")

def unpack_sketch(byte_data: bytes, item_width: int, array_width: int, array_height: int):
    total_items = array_width * array_height
    total_bits_needed = total_items * item_width

    # Convert bytes to bit string
    bit_str = ''.join(f'{byte:08b}' for byte in byte_data)

    # Truncate to exactly the number of bits we need
    bit_str = bit_str[:total_bits_needed]

    result = []
    idx = 0
    for _ in range(array_width):
        row = []
        for _ in range(array_height):
            item_bits = bit_str[idx:idx+item_width]
            value = int(item_bits, 2) if item_bits else 0
            row.append(value)
            idx += item_width
        result.append(row)

    return result

def validate_cms(input, output, item_width, array_width, array_height):
    # Generate the expected sketch
    cms = CountMinSketch([h1, h2, h3])

    for b in input[:-1]:
        cms.update(b)

    # Expected Sketch
    expected_sketch = cms.dump()

    # Now, generate the actual sketch
    actual_sketch = unpack_sketch(output, item_width, array_width, array_height)

    match = expected_sketch == actual_sketch
    print("Sketch match:", match)

def main():
    parser = argparse.ArgumentParser(description="Send byte data via strings, hex, or decimal.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--string', nargs='+', help='List of strings to concatenate and send as bytes.')
    group.add_argument('--hex', nargs='+', help='List of hex byte values (e.g. 0f 1a ff).')
    group.add_argument('--dec', nargs='+', help='List of decimal byte values (0–255).')

    parser.add_argument('--output', choices=['raw', 'tabled'], default='raw', help='Output mode (default: raw).')
    parser.add_argument('--item-width', type=int, help='Item width in bits (must be multiple of 8) for tabled output.')
    parser.add_argument('--array-width', type=int, help='Number of items per row for tabled output.')
    parser.add_argument('--array-height', type=int, help='Number of rows for tabled output.')
    parser.add_argument('--validation', choices=['none', 'cms'], default='none', help='Validation mode (default: none).')

    args = parser.parse_args()

    if args.string:
        payload = parse_string_input(args.string)
    elif args.hex:
        payload = parse_hex_input(args.hex)
    elif args.dec:
        payload = parse_decimal_input(args.dec)
    else:
        parser.error("No valid input provided.")

    payload += b'\x00'

    result_queue = queue.Queue()

    with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0) as ser:

        sender = threading.Thread(target=send_data, args=(ser, payload))
        receiver = threading.Thread(target=receive_formatted_data, args=(args.output, args.item_width or 8, args.array_width or 1, ser, result_queue))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()

    received_data = result_queue.get()

    if args.validation == "cms":
        validate_cms(payload, received_data, args.item_width, args.array_width, args.array_height)

if __name__ == '__main__':
    main()
