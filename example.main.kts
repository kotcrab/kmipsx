@file:Repository("https://jitpack.io")
@file:DependsOn("com.github.kotcrab:kmipsx:1fb772da94")

import kio.util.child
import kmips.Reg.*
import kmips.assembleAsByteArray
import kmipsx.elf.CompileResult
import kmipsx.elf.extendElfBss
import kmipsx.elf.patchElf
import kmipsx.elf.patchSuite
import kmipsx.elf.pspCodeCompiler
import kmipsx.util.align16
import kmipsx.util.callerSavedRegisters
import kmipsx.util.compiledElf
import kmipsx.util.preserve
import kmipsx.util.region
import kmipsx.util.zeroTerminatedString
import java.io.File
import kotlin.system.exitProcess

if (args.size != 3) {
  println("Arguments: [pspSdkDir] [extractedIsoDir] [decryptedEboot]")
  exitProcess(1)
}
patchEboot(File(args[0]), File(args[1]), File(args[2]))

fun patchEboot(
  pspSdkDir: File,
  extractedIsoDir: File,
  decryptedEboot: File,
) {
  // A place to load our auxiliary patches, after original .bss ends
  val auxStartAddress = 0x08A12380

  // Prepare temporary directory for the compiled code
  val tmpDir = extractedIsoDir.child("tmp").apply { mkdir() }
  val patchCppFile = tmpDir.child("patch.cpp").apply { writeText(Code.patchCpp) }

  println("Compiling native code...")
  val compileResult = pspCodeCompiler(pspSdkDir)
    .compile(
      patchBaseAddress = auxStartAddress,
      srcFiles = listOf(patchCppFile),
      outDir = tmpDir.child("out")
    )

  // Now that we have compiled the native code we can assemble patches
  println("Assembling patches...")
  val assembler = EbootPatchesAssembler(compileResult, auxStartAddress)
  val auxPatchBytes = assembler.assembleAuxiliaryPatch()
  val ebootPatches = assembler.assembleEbootPatches(auxPatchBytes.size)

  // We are pretty much ready to patch the original EBOOT now
  println("Patching EBOOT...")
  val ebootOut = extractedIsoDir.child("PSP_GAME/SYSDIR/EBOOT.BIN")
  decryptedEboot.copyTo(ebootOut, overwrite = true)

  // First let's write our auxiliary patch data
  extractedIsoDir.child("patch").writeBytes(auxPatchBytes)
  // Now we just need to extend ELF .bss section to make space for auxiliary patch
  extendElfBss(ebootOut, auxPatchBytes.size)

  // Then finally apply EBOOT patches!
  patchElf(decryptedEboot, ebootOut, ebootPatches, baseProgramHeaderIndex = 0, relocationSectionHeaderIndex = 4)

  println("Cleaning up...")
  tmpDir.deleteRecursively()

  println("Done!")
}

class EbootPatchesAssembler(
  private val compileResult: CompileResult,
  private val auxStartAddress: Int,
) {
  private val functions = Functions()

  fun assembleAuxiliaryPatch() = assembleAsByteArray(auxStartAddress) {
    compiledElf(compileResult) // include our compiled C++ code

    align16()

    functions.subsRendererDispatch = region {
      val ctx = preserve(callerSavedRegisters)
      jal(compileResult.functions.getValue("subsRenderer")) // call into C++
      nop()
      ctx.restore()
      jal(0x8804000 + 0x00001F70) // call original replaced func
      nop()
      j(0x8804000 + 0x00001390) // return to the old code
      nop()
    }

    functions.subsSoundPlaybackInterceptorDispatch = region {
      move(a0, s0) // original code
      val ctx = preserve(callerSavedRegisters)
      // a0 here is conveniently internal id to be played
      jal(compileResult.functions.getValue("subsSoundPlaybackInterceptor"))
      nop()
      ctx.restoreAndExit()
    }

    align16()
  }

  // if you prefer you can commit baseAddress and use virtual addresses everywhere
  fun assembleEbootPatches(auxiliarySize: Int) = patchSuite(baseAddress = 0x8804000) {
    patch("Auxiliary: Load file") {
      change(0x0016415C) {
        zeroTerminatedString("disc0:/patch")
      }
      change(0x0097358) {
        j(0x8804000 + 0x0016416C)
        // keep original branch delay slot
      }
      change(0x0016416C) {
        lui(v1, 0x892) // original replaced instr
        val ctx = preserve(callerSavedRegisters + s0)

        la(a0, 0x8804000 + 0x0016415C)
        li(a1, 1)
        jal(functions.sceIoOpen)
        li(a2, 31)

        move(s0, v0)
        move(a0, v0)

        la(a1, auxStartAddress)
        la(a2, auxiliarySize)
        jal(functions.sceIoRead)
        nop()

        jal(functions.sceIoClose)
        move(a0, s0)

        ctx.restore()
        j(0x0889b360)
        nop()
      }

    }
    patch("Subtitles: Custom text render") {
      change(0x00001388) {
        j(functions.subsRendererDispatch)
        nop()
      }
    }
    patch("Subtitles: Sound playback interceptor") {
      change(0x000114E0) {
        jal(functions.subsSoundPlaybackInterceptorDispatch)
        // keep orig branch delay slot
      }
    }
  }
}

class Functions {
  var subsRendererDispatch = 0
  var subsSoundPlaybackInterceptorDispatch = 0

  // From the original EBOOT
  val sceIoOpen = 0x088ec994
  val sceIoRead = 0x088ec964
  val sceIoClose = 0x088ec96c
}

// Bundled C++ code to make the example self-contained
// For normal projects you can just load from files
object Code {
  val patchCpp = """
#include <cstdint>

// Core types
typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef int8_t i8;
typedef int16_t i16;
typedef int32_t i32;

// Typedef and functions to call the original game code

const u32 TEXT_INVISIBLE = 1;
const u32 TEXT_VISIBLE = 0xFFFFFFFF;

typedef u32 (*GameDrawTextDef)(const char* text, u32 x, u32 y, u32 scale, u32 priority);
typedef void (*GameSetTextVisibleDef)(u32 visibility);
const GameDrawTextDef GameDrawText = (GameDrawTextDef)(0x088089E0);
const GameSetTextVisibleDef GameSetTextVisible = (GameSetTextVisibleDef)(0x088061DC);

// Helper for drawing centered text, notice we're using the game itself to measure the text for us

void DrawCenteredText(const char* text, u32 y) {
  GameSetTextVisible(TEXT_INVISIBLE);
  u32 width = GameDrawText(text, 0, 0, 100, 0x45D);
  GameSetTextVisible(TEXT_VISIBLE);
  GameDrawText(text, (480 - width) / 2, y, 100, 0x45D);
}

// Random itoa function
void itoa(i32 i, char b[]) {
    char const digit[] = "0123456789";
    char* p = b;
    if (i < 0) {
        *p++ = '-';
        i *= -1;
    }
    int shifter = i;
    do { // Move to where representation ends
        ++p;
        shifter = shifter / 10;
    } while (shifter);
    *p = '\0';
    do { // Move back, inserting digits as u go
        *--p = digit[i % 10];
        i = i / 10;
    } while (i);
}

// Our variables and functions

static u32 internalAudioId = 0;
static i32 remainingSubtitleFrames = 0;

// Main entry points from ASM dispatchers

extern "C" {

void subsSoundPlaybackInterceptor(u32 internalId) {
  internalAudioId = internalId;
  remainingSubtitleFrames = 300;
}

void subsRenderer() {
  DrawCenteredText("Hello from C++!", 10);
  if (remainingSubtitleFrames-- > 0) {
    char buf[32] = "Last audio file id: ";
    itoa(internalAudioId, buf + 20);
    DrawCenteredText(buf, 35);
  }
}
}

""".trimIndent()
}
