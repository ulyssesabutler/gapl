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

def receive_response(ser):
    print(f"Listening for {LISTEN_DURATION} seconds...")
    end_time = time.time() + LISTEN_DURATION
    while time.time() < end_time:
        if ser.in_waiting:
            byte = ser.read(1)
            if byte:
                print("Result: True")
            else:
                print("Result: False")
        time.sleep(0.01)  # prevent CPU hogging

def receive_data(ser):
    print(f"Listening for {LISTEN_DURATION} seconds...")
    end_time = time.time() + LISTEN_DURATION
    while time.time() < end_time:
        if ser.in_waiting:
            byte = ser.read(1)
            print(f"Received byte: {byte.hex().upper()}")
        else:
            time.sleep(0.01)  # prevent CPU hogging

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
        receiver = threading.Thread(target=receive_data, args=(ser,))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()

if __name__ == '__main__':
    main()
