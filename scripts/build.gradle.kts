plugins {
    id("base")
}

tasks.register<Exec>("setupPythonEnv") {
    group = "setup"
    description = "Set up Python virtual environment"
    
    commandLine(
        "python3",
        "-m",
        "venv",
        "venv"
    )
}

tasks.register<Exec>("installDependencies") {
    group = "setup"
    description = "Install Python dependencies"
    
    commandLine(
        "./venv/bin/pip",
        "install",
        "-r",
        "requirements.txt"
    )
    
    dependsOn("setupPythonEnv")
}

tasks.register<Exec>("runFpgaTests") {
    group = "verification"
    description = "Run FPGA tests using Python script"
    
    val testFile = project.findProperty("testFile") ?: "test_suite.json"
    val port = project.findProperty("port") ?: "/dev/ttyUSB0"
    val baudrate = project.findProperty("baudrate") ?: "9600"
    val testName = project.findProperty("testName")
    
    commandLine(
        "./venv/bin/python",
        "fpga_test.py",
        "--test-file", testFile,
        "--port", port,
        "--baudrate", baudrate
    )
    
    if (testName != null) {
        args("--test-name", testName)
    }
    
    dependsOn("installDependencies")
} 