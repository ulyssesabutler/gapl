# FPGA Test Framework

A simple framework for testing FPGA packet processing via serial communication.

## Setup

1. Create and activate Python virtual environment:
```bash
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
# On Unix/macOS:
source venv/bin/activate
# On Windows:
# venv\Scripts\activate
```

2. Install dependencies from requirements.txt:
```bash
pip install -r requirements.txt
```

3. Connect your FPGA board to the computer via USB.

## Running Tests

### Basic Usage
```bash
./gradlew :scripts:runFpgaTests
```

### Options
- `--port`: Serial port (default: /dev/ttyUSB0)
- `--baudrate`: Baud rate (default: 115200)
- `--test-file`: Path to test suite JSON file
- `--test-name`: Run specific test by name

Example:
```bash
./gradlew :scripts:runFpgaTests --port=/dev/ttyUSB1 --test-name=test_packet_forwarding
```

## Test Suite Format

Tests are defined in a JSON file with the following structure:

```json
[
    {
        "name": "test_name",
        "description": "Test description",
        "input": "01020304",  // Inline hex string
        "expected_output": "05060708"  // Expected hex response
    },
    {
        "name": "test_with_file",
        "description": "Test using file input",
        "input": {
            "file": "test_data/input.hex"
        },
        "expected_output": {
            "file": "test_data/expected.hex"
        }
    }
]
```

### Input/Output Formats
- Inline hex strings: Direct hex values
- File-based: Path to .hex file containing hex string

## Test Data Files

Place test data files in the `test_data` directory:
- Use .hex extension for hex format files
- One hex string per line
- No spaces or other formatting

Example test_data/input.hex:
```
0102030405060708090A
```

## Troubleshooting

1. Virtual Environment Issues:
   - Ensure virtual environment is activated
   - If packages are missing, run `pip install -r requirements.txt` again
   - Check Python version compatibility

2. Connection Issues:
   - Check if the correct port is selected
   - Verify FPGA board is properly connected
   - Ensure no other program is using the serial port

3. Test Failures:
   - Verify test data format
   - Check FPGA configuration
   - Ensure baud rate matches FPGA settings 