import serial
import threading
import time

# Adjust this depending on your serial device's settings
SERIAL_PORT = '/dev/ttyUSB1'
BAUD_RATE = 9600
BYTES_TO_SEND = b'\x48\x65\x6C\x6C\x6F\x00'  # Example: "Hello" in ASCII

def send_data(ser):
    """Send bytes to the serial port."""
    time.sleep(0.1)  # slight delay to ensure receiver is ready
    print(f"Sending: {BYTES_TO_SEND}")
    ser.write(BYTES_TO_SEND)

def receive_data(ser):
    """Continuously read from the serial port."""
    response = ser.read(len(BYTES_TO_SEND))
    print("Received (hex):", ' '.join(f'{b:02X}' for b in response))

def main():
    with serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1) as ser:
        sender = threading.Thread(target=send_data, args=(ser,))
        receiver = threading.Thread(target=receive_data, args=(ser,))

        receiver.start()
        sender.start()

        sender.join()
        receiver.join()

if __name__ == "__main__":
    main()

