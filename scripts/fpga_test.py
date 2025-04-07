#!/usr/bin/env python3

import argparse
import json
import time
import serial
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union

class FPGATester:
    def __init__(self, port: str = "/dev/ttyUSB0", baudrate: int = 115200):
        self.port = port
        self.baudrate = baudrate
        self.serial = None

    def connect(self) -> bool:
        try:
            self.serial = serial.Serial(
                port=self.port,
                baudrate=self.baudrate,
                timeout=1.0
            )
            return True
        except serial.SerialException as e:
            print(f"Failed to connect to {self.port}: {e}")
            return False

    def disconnect(self):
        if self.serial and self.serial.is_open:
            self.serial.close()

    def send_packet(self, packet: bytes) -> bool:
        if not self.serial or not self.serial.is_open:
            return False
        try:
            self.serial.write(packet)
            return True
        except serial.SerialException:
            return False

    def read_response(self, timeout: float = 1.0) -> Optional[bytes]:
        if not self.serial or not self.serial.is_open:
            return None
        try:
            self.serial.timeout = timeout
            return self.serial.read_until(b'\n')
        except serial.SerialException:
            return None

def load_packet_data(data: Union[str, Dict]) -> bytes:
    """Load packet data from either a hex string or a file reference"""
    if isinstance(data, str):
        # Direct hex string
        return bytes.fromhex(data)
    elif isinstance(data, dict):
        # File reference
        file_path = data.get('file')
        if not file_path:
            raise ValueError("File reference must specify 'file' path")
        
        with open(file_path, 'rb') as f:
            # Read hex string from file
            return bytes.fromhex(f.read().decode().strip())
    else:
        raise ValueError("Input must be either a hex string or a file reference")

def run_test(test_case: Dict) -> Tuple[bool, float]:
    if not tester.connect():
        return False, 0.0

    start_time = time.time()
    success = False

    try:
        # Load input packet
        input_packet = load_packet_data(test_case['input'])
        if not tester.send_packet(input_packet):
            return False, 0.0

        # Read response
        response = tester.read_response()
        if response is None:
            return False, 0.0

        # Load expected output
        expected_output = load_packet_data(test_case['expected_output'])
        success = response.strip() == expected_output

    finally:
        tester.disconnect()
        duration = time.time() - start_time

    return success, duration

def load_test_suite(test_file: str) -> List[Dict]:
    try:
        with open(test_file, 'r') as f:
            return json.load(f)
    except (json.JSONDecodeError, FileNotFoundError) as e:
        print(f"Error loading test suite: {e}")
        return []

def main():
    parser = argparse.ArgumentParser(description='FPGA Test Runner')
    parser.add_argument('--port', default='/dev/ttyUSB0', help='Serial port to use')
    parser.add_argument('--baudrate', type=int, default=115200, help='Baud rate')
    parser.add_argument('--test-file', required=True, help='Path to test suite JSON file')
    parser.add_argument('--test-name', help='Run specific test by name')
    args = parser.parse_args()

    tester = FPGATester(port=args.port, baudrate=args.baudrate)
    test_suite = load_test_suite(args.test_file)

    if not test_suite:
        print("No tests loaded")
        sys.exit(1)

    total_tests = 0
    passed_tests = 0
    total_time = 0.0

    for test in test_suite:
        if args.test_name and test['name'] != args.test_name:
            continue

        total_tests += 1
        print(f"\nRunning test: {test['name']}")
        success, duration = run_test(test)
        
        if success:
            passed_tests += 1
            print(f"✓ PASSED - Duration: {duration:.3f}s")
        else:
            print(f"✗ FAILED - Duration: {duration:.3f}s")
        
        total_time += duration

    print(f"\nTest Summary:")
    print(f"Total Tests: {total_tests}")
    print(f"Passed: {passed_tests}")
    print(f"Failed: {total_tests - passed_tests}")
    print(f"Total Time: {total_time:.3f}s")
    print(f"Average Time per Test: {total_time/total_tests if total_tests > 0 else 0:.3f}s")

    sys.exit(0 if passed_tests == total_tests else 1)

if __name__ == "__main__":
    main() 