import serial
import threading
import time
import sys
import argparse


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

def output_raw(ser=None):
    data = receive_data(ser)
    for byte in data:
        print(f"{byte:02X}")

def output_tabled(item_width_bits: int = 8, array_width: int = 1, ser=None):
    data = receive_data(ser)
    if item_width_bits % 8 != 0:
        raise ValueError("Item width must be a multiple of 8.")
    item_width_bytes = item_width_bits // 8
    if item_width_bytes == 0:
        raise ValueError("Item width must be at least 8 bits (1 byte).")

    grouped = [data[i:i+item_width_bytes] for i in range(0, len(data), item_width_bytes)]
    for i in range(0, len(grouped), array_width):
        row = grouped[i:i+array_width]
        print("  ".join("".join(f"{b:02X}" for b in item) for item in row))

def receive_formatted_data(mode: str = 'raw', item_width: int = 8, array_width: int = 1, ser=None):
    if mode == "raw":
        output_raw(ser)
    elif mode == "tabled":
        output_tabled(item_width, array_width, ser)
    else:
        raise ValueError(f"Unknown output mode: {mode}")

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

def main():
    parser = argparse.ArgumentParser(description="Send byte data via strings, hex, or decimal.")
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--string', nargs='+', help='List of strings to concatenate and send as bytes.')
    group.add_argument('--hex', nargs='+', help='List of hex byte values (e.g. 0f 1a ff).')
    group.add_argument('--dec', nargs='+', help='List of decimal byte values (0–255).')

    parser.add_argument('--output', choices=['raw', 'tabled'], default='raw', help='Output mode (default: raw).')
    parser.add_argument('--item-width', type=int, help='Item width in bits (must be multiple of 4) for tabled output.')
    parser.add_argument('--array-width', type=int, help='Number of items per row for tabled output.')

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

    with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0) as ser:
        sender = threading.Thread(target=send_data, args=(ser, payload))
        receiver = threading.Thread(target=receive_formatted_data, args=(args.output, args.item_width or 8, args.array_width or 1, ser))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()

if __name__ == '__main__':
    main()
