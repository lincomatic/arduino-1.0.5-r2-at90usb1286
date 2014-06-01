/* UART Example, any character received on either the real
   serial port, or USB serial (or emulated serial to the
   Arduino Serial Monitor when using non-serial USB types)
   is printed as a message to both ports.

   This example code is in the public domain.
*/

// This line defines a "Uart" object to access the serial port
HardwareSerial Uart = HardwareSerial();

void setup() {
	Serial.begin(9600);
        Uart.begin(38400);
}

void loop() {
        int incomingByte;
        
	if (Serial.available() > 0) {
		incomingByte = Serial.read();
		Serial.print("USB received: ");
		Serial.println(incomingByte, DEC);
                Uart.print("USB received:");
                Uart.println(incomingByte, DEC);
	}
	if (Uart.available() > 0) {
		incomingByte = Uart.read();
		Serial.print("UART received: ");
		Serial.println(incomingByte, DEC);
                Uart.print("UART received:");
                Uart.println(incomingByte, DEC);
	}
}

