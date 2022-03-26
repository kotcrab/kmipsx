kmipsx
------

kmipsx is an extension of the [kmips](https://github.com/kotcrab/kmips) assembler providing more complete tools suite
for patching executables. The primary focus are PSP executables.

Features:

- ELF patching tools
- Tools for compiling and including C/C++ code (PSP only)
- Helpers for function stack management
- Various kmips assembler extensions

#### Usage from Gradle

1. Add JitPack repository

```groovy
repositories {
  // ...
  maven { url 'https://jitpack.io' }
}
```

2. Add dependency

```groovy
compile "com.github.kotcrab:kmipsx:$anyCommitHash"
```
