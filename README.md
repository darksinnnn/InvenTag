NFC-Based Inventory Management System
This project implements an NFC-based inventory management system using ESP32, PN532 NFC module, Firebase, and a custom Android app. It allows users to scan NFC tags to manage product inventory, with features such as real-time alerts for expired products and low stock levels.

Table of Contents
Project Overview

Components

Software Requirements

Hardware Setup

Installation & Setup

Android App Features

Web Server Endpoints

Firebase Integration

License

Project Overview
This NFC-based inventory system uses an ESP32 as the central controller, with an NFC scanner (PN532) connected via I2C to scan NFC tags. The Android app communicates with the ESP32 to retrieve scanned NFC tag data, manage inventory, and trigger real-time alerts for expired or low-stock products. The system is also integrated with Firebase for authentication and database storage.

Components
Hardware
ESP32 development board

PN532 NFC module (I2C)

16x2 I2C LCD display

Buzzer

Red LED

Software
Arduino IDE (for ESP32 development)

Firebase for authentication and database

Android Studio (for Android app development)

Software Requirements
Arduino IDE with the following libraries:

WiFi.h

ESPAsyncWebServer.h

Wire.h

Adafruit_PN532.h

LiquidCrystal_PCF8574.h

AsyncTCP.h

Firebase Authentication and Firestore integration for the Android app

Kotlin and Dagger Hilt for Android app development

Hardware Setup
ESP32 Connections:
PN532 NFC Module (I2C)

SDA → GPIO 21 (SDA)

SCL → GPIO 22 (SCL)

IRQ → GPIO 2 (Interrupt pin)

RESET → GPIO 0 (Reset pin)

16x2 LCD (I2C)

SDA → GPIO 21

SCL → GPIO 22

Buzzer and Red LED

Buzzer → GPIO 5

Red LED → GPIO 4

Installation & Setup
1. Setting Up the ESP32:
Clone this repository to your local machine.

Open the ESP32 code in the Arduino IDE.

Set up your Wi-Fi credentials in the setup() function:

cpp
Copy
Edit
const char* ssid = "Your_SSID";
const char* password = "Your_Password";
Upload the code to the ESP32 board.

After uploading, the ESP32 will display the IP address on the LCD after connecting to Wi-Fi.

2. Android App Setup:
Clone the repository for the Android app or create your own.

Open the project in Android Studio.

Set up Firebase Authentication and Firestore.

Ensure that the ESP32’s IP address is hardcoded or fetched from a configuration in the app for communication.

Build and run the app on an Android device.

Android App Features
NFC Scanning: Allows users to scan NFC tags with their phone.

Inventory Management: View and update product inventory from the mobile app.

Alerts: Trigger alerts for low stock and expired products by activating the buzzer and red LED.

Firebase Integration: Secure login using Firebase Authentication and Firestore database integration for product data storage.

Dark Mode Support: Toggle between dark and light themes.

Web Server Endpoints
The ESP32 serves a web server with the following routes:

GET /nfc/read: Returns the last scanned NFC tag ID in JSON format.

Example: {"tagId": "04:1F:91:7C:19:1A:56"}

GET /buzzer: Activates the buzzer for 1 second (for expired product alerts).

GET /led: Toggles the red LED for 1 second (for low-stock alerts).

Firebase Integration
Firebase Authentication is used to manage user accounts in the Android app.

Firestore Database stores:

Product inventory data.

Product expiration details.

Scanned NFC tag history.

License
This project is open-source and released under the MIT License.

Notes:
Ensure your ESP32 is connected to the same Wi-Fi network as your Android device for proper communication.

Use the IP address of the ESP32 in your Android app to send HTTP requests to the web server running on the ESP32.

