# Clinic Management System
A robust Java-based console application designed to manage clinic operations including staff hiring, patient registration, medical records, and payroll processing.

## Features
- **Object-Oriented Design:** Uses Inheritance, Abstraction, and Polymorphism.
- **Data Persistence:** Saves and loads data using Java Serialization.
- **Robust Security:** Input validation to prevent runtime crashes (e.g., negative numbers, empty inputs).
- **Business Logic:** Prevents common business errors like discharging a patient with unpaid bills or firing a doctor who has an active queue.
- **Cross-Platform:** Includes shell scripts for easy execution on Windows, Linux, and macOS.

## How to Run
1. Ensure you have JDK installed.
2. Clone the repository.
3. Use the provided execution scripts:
   - **Windows:** Run `run_windows.bat`
   - **Linux/macOS:** Run `sh run_linux_mac.sh`

## Built With
- Java (Standard Edition)
- Serialization for database management
