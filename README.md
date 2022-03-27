kmipsx
------

kmipsx is an extension of the [kmips](https://github.com/kotcrab/kmips) assembler providing more complete tools suite
for patching executables. The primary focus are PSP games.

Features:

- ELF patching tools
- Tools for compiling and including C/C++ code (PSP only)
- Helpers for function stack management
- Various kmips assembler extensions

### Example

See [`example.main.kts`](https://github.com/kotcrab/kmipsx/blob/master/example.main.kts) for a complete usage example. It shows how to:

1) Patch offsets at the input EBOOT
2) Assemble auxiliary patches that wouldn't fit into the original EBOOT
3) Expand the `.bss` section and load the auxiliary patches into it from an ISO file
4) Compile and load C++ code (the C++ code is bundled in the script to make the example self-contained)
5) Call into the compiled C++ code in the middle of the existing game code
6) Call original game code from the compiled C++ code
7) Render on screen some text and the internal id of the audio file that has started playing in game. This can be used as a base for a subtitle system.

The example can be applied to the Japanese version of Toaru Kagaku no Railgun. To apply the example:

1) Setup `pspsdk` (`psp-gcc`, `psp-ld`, `psp-strip` must be available)
2) Extract ISO to some folder
3) Decrypt the EBOOT (e.g. you can use PPSSPP dump feature)
4) Either download [Kotlin compiler](https://github.com/JetBrains/kotlin/releases/latest) or setup normal  project

If you have Kotlin compiler then run: (replace paths to match your setup)
```
kotlinc -script example.main.kts pspsdk-dir/bin ./iso-extract-dir ./decrypted-eboot-file
```

Expected output:
```
Patching...
Done!
```

In game:

![Image](docs/example.jpg)

(the in-game font does not handle ASCII too well)

### Adding to a project

#### From Gradle

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

#### From Kotlin script

Add the following lines at the top:

```
@file:Repository("https://jitpack.io")
@file:DependsOn("com.github.kotcrab:kmipsx:$anyCommitHash")
```
