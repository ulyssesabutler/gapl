import serial
import threading
import time

SERIAL_PORT = '/dev/ttyUSB1'
BAUD_RATE = 9600
BYTES_TO_SEND = b'\x48\x65\x6C\x6C\x6C\x6C\x6F\x6F\x6F\x6F\x6F\x6F\x6F\x6F\x00'  # "Hello"
LISTEN_DURATION = 10  # seconds

def send_data(ser):
    time.sleep(0.1)  # ensure device is ready
    print(f"Sending: {BYTES_TO_SEND}")
    ser.write(BYTES_TO_SEND)
    ser.flush()

def receive_data(ser):
    print(f"Listening for {LISTEN_DURATION} seconds...")
    end_time = time.time() + LISTEN_DURATION
    while time.time() < end_time:
        if ser.in_waiting:
            byte = ser.read(1)
            print(f"Received byte: {byte.hex().upper()}")
        else:
            time.sleep(0.01)  # prevent CPU hogging

def main():
    with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=0) as ser:
        sender = threading.Thread(target=send_data, args=(ser,))
        receiver = threading.Thread(target=receive_data, args=(ser,))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()

if __name__ == "__main__":
    main()

