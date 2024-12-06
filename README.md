# Gate Array Programming Language [WIP]

## Building

This project is managed using the Gradle build system.
To build this project so you can use the command line application, you can use the included gradle wrapper script.

```text
./gradlew install
```

This task will create an installable distribution of this application in the `build/install` directory.
Since this is a JVM application, that installation will consist of a set of JAR files, as well as an execution script.

## Running

Once you've built the application, you can invoke the execution script.

```text
 ./build/install/gapl/bin/gapl [ARGUMENTS]
```

For example, if you have a `main.g` file to "compile", you can run
```text
 ./build/install/gapl/bin/gapl main.g
```
