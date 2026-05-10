# Gradle Wrapper

`gradle-wrapper.properties` and `gradle-wrapper.jar` are both checked in. The
wrapper targets Gradle 8.11 and works on any machine with a compatible JDK
(JDK 17 recommended; AGP 8.7.3 requires JDK 17).

To regenerate the wrapper (e.g. to bump the Gradle version in
`gradle-wrapper.properties`), run once with any local Gradle install:

```
gradle wrapper --gradle-version <new-version> --distribution-type bin
```

The companion launchers at the repo root are `gradlew` (POSIX) and
`gradlew.bat` (Windows). Both are the standard Gradle 8.x wrapper scripts.
