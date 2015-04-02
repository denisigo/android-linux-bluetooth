import thread
import serial

# Our virtual serial port device name
PORT = '/dev/rfcomm0'
# Baud rate for communication
BAUDRATE = 921600
# Timeout for operations
TIMEOUT = 1

# Create and open our serial port
port = serial.Serial(port=PORT, baudrate=BAUDRATE, timeout=TIMEOUT)
port.open()

# This is the reading thread, similar to one in our Android app
def readThread(port):
  while True:
    # Read some bytes available from port
    bytes = port.read(1024)
    # Decode them as a string
    message = bytes.decode("utf-8")
    if message:
      print message

thread.start_new_thread( readThread, ( port, ) )

# Take raw input and write it to the port until magic symbol entered
while True:
  inp = raw_input('>')
  if len(inp):
    if inp == 'q':
      break
    else:
      port.write(inp)

port.close()
