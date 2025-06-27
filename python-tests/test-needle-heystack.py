import serial
import threading
import time
import sys

SERIAL_PORT = '/dev/ttyUSB1'
BAUD_RATE = 9600
LISTEN_DURATION = 2  # seconds

def encode_payload(needle, heystack):
    return needle.encode() + heystack.encode() + b'\x00'

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

# def main():
#     with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0) as ser:
#         sender = threading.Thread(target=send_data, args=(ser,))
#         receiver = threading.Thread(target=receive_data, args=(ser,))
#
#         receiver.start()
#         sender.start()
#
#         sender.join()
#         receiver.join()


def main():
    if len(sys.argv) != 3:
        print("Usage: python [SCRIPT] <needle> <haystack>")
        sys.exit(1)

    needle = sys.argv[1]
    haystack = sys.argv[2]
    payload = encode_payload(needle, haystack)

    with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0) as ser:
        sender = threading.Thread(target=send_data, args=(ser, payload))
        receiver = threading.Thread(target=receive_data, args=(ser,))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()


if __name__ == "__main__":
    main()

