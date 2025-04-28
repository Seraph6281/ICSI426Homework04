## **A Guide to Run Programs**
ICSI 426 Homework 4 Yingzhao (Seraph) Ma

This guide provides instructions to run the programs that are required for Homework 4.

This repository is also available on [GitHub](https://github.com/Seraph6281/ICSI426Homework04).

---

### **File Directory**

#### Program Files

|            Problem Number             |        Corresponding File         |
|:-------------------------------------:|:---------------------------------:|
|   Problem 1: SSS Scheme for Images    |     _ShamirImageSharing.java_     |
| Problem 2: SSS with Image Downscaling | _ShamirHomomorphicDownscale.java_ |

**Note:** For validation purpose, problem 1 and 2 are implemented separately and into 2 completely independent programs.

#### Test Files

|    File Number     |                           Description                            |
|:------------------:|:----------------------------------------------------------------:|
|    _snail.bmp_     |                  $256\times256$ BMP test image                   |
|    _land2.bmp_     |                      another BMP test image                      |

---

### **Environment for Running**

If you'd like to use an IDE (e.g., IntelliJ IDEA, Eclipse, or VS Code), open/import the project directory into the IDE. 

The IDE will handle compilation and execution for you.

_In case you are using a brand-new setup:_

1. **Install Java**: Download and install the [Java Development Kit (JDK)](https://www.oracle.com/java/technologies/javase-downloads.html). The latest stable version is highly recommended.
   - Make sure to add the JDK's `bin` folder to your system's PATH so that the `java` and `javac` commands are available in the terminal or command prompt.
   - To verify the installation, execute the following in your terminal/command prompt:
     ```bash
     java -version
     javac -version
     ```
     Both commands should display the installed version of Java.

2. **Organize Program Files**: Ensure all `.java` files are located in the same directory. For example, if the directory structure is:
   ```
   project-folder/
   ├── ShamirImageSharing.java
   ├── ShamirHomomorphicDownscale.java
   ├── snail.bmp
   ├── land2.bmp
   ```
   Make sure to work within this directory when compiling and running.

---

### **Steps to Compile and Run**

#### **Using Terminal/Command Prompt**

1. **Navigate to the Directory**: Open a terminal or command prompt and navigate to the directory containing the `.java` files:
   ```bash
   cd path/to/project-folder
   ```
   
2. **Compile the Programs**: Use the `javac` command to compile the `.java` source files you intend to run. For example:
   ```bash
   javac ShamirHomomorphicDownscale.java
   ```
   This will generate `.class` files in the same directory.

3. **Run the Program**: Use the `java` command to run the compiled program. For example:
   ```bash
   java ShamirHomomorphicDownscale
   ```

#### **Using an IDE**

1. Open the project in your preferred IDE.
2. Ensure the `.java` files are recognized in the IDE's project pane.
3. Compile and run the desired file (e.g., `ShamirHomomorphicDownscale.java`) by selecting it and pressing the **Run** button in the IDE.

---
