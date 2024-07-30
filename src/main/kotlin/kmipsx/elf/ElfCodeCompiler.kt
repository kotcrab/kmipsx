package kmipsx.elf

import kio.util.child
import kio.util.execute
import kio.util.padArray
import kio.util.stdoutStreamHandler
import kio.util.toHex
import kmips.Assembler
import kmipsx.util.bytes
import java.io.File

fun writeElfSectionsInto(
  patchBytes: MutableList<Byte>,
  compileResult: CompileResult,
  virtualAddr: (patchBytesSize: Int) -> Int,
): List<Byte> {
  val compiledBytes = compileResult.patchFile.readBytes()
  val compiledElf = ElfFile(compileResult.patchFile)
  compiledElf.sectionHeaders.forEach {
    if (it.vAddr == 0) {
      return@forEach
    }
    if (virtualAddr(patchBytes.size) > it.vAddr) {
      error("Can't write ELF section past starting point")
    }
    while (virtualAddr(patchBytes.size) != it.vAddr) {
      patchBytes.add(0)
    }
    when (it.type) {
      1 -> { // text, data, rodata, etc. (write data from ELF)
        val data = padArray(compiledBytes.sliceArray(it.offset until it.offset + it.size), 4)
        patchBytes.addAll(data.toList())
      }
      8 -> { // BSS (allocate empty array)
        patchBytes.addAll(padArray(ByteArray(it.size), 4).toList())
      }
      else -> error("Don't know how to write ELF section with type ${it.type}")
    }
  }
  return patchBytes
}

fun pspCodeCompiler(
  pspSdkDir: File,
  maxCompiledSize: Int = 256 * 1024,
): ElfCodeCompiler {
  return ElfCodeCompiler(
    toolchainDir = pspSdkDir,
    toolchainPrefix = "psp-",
    gccFlags = standardPspGccFlags,
    ldFlags = emptyList(),
    linkerTemplate = PspLinkerTemplate,
    maxCompiledSize = maxCompiledSize
  )
}

class ElfCodeCompiler internal constructor(
  private val toolchainDir: File,
  private val toolchainPrefix: String,
  private val gccFlags: List<String>,
  private val ldFlags: List<String>,
  private val linkerTemplate: LinkerTemplate,
  private val maxCompiledSize: Int,
) {
  fun compile(
    patchBaseAddress: Int,
    srcFiles: List<File>,
    outDir: File,
    patchFileName: String = "patch.elf",
    memoryMapFileName: String = "memory.map",
    symbolsFileName: String = "symbols.map",
    additionalGccFlags: List<String> = emptyList(),
  ): CompileResult {
    outDir.mkdir()
    outDir.child(memoryMapFileName).writeText(linkerTemplate.get(patchBaseAddress, maxCompiledSize))
    val gcc = toolchainDir.child("${toolchainPrefix}gcc")
    val ld = toolchainDir.child("${toolchainPrefix}ld")
    val strip = toolchainDir.child("${toolchainPrefix}strip")

    val objFiles = srcFiles.map { it.nameWithoutExtension + ".o" }
    if (objFiles.size != objFiles.toSet().size) {
      error("Source file would result in object file conflict when compiling. Use unique file names for source files to fix this.")
    }
    val compileUnits = srcFiles.mapIndexed { idx, src -> src to objFiles[idx] }
    compileUnits.forEach { (src, obj) ->
      execute(
        gcc, workingDirectory = outDir, streamHandler = stdoutStreamHandler(),
        args = gccFlags + additionalGccFlags + listOf("-c", src, "-o", obj)
      )
    }
    val patchOut = outDir.child(patchFileName)
    execute(
      ld, workingDirectory = outDir, streamHandler = stdoutStreamHandler(),
      args = ldFlags + listOf("-T", memoryMapFileName, "-Map", symbolsFileName) + objFiles + listOf("-o", patchOut)
    )
    execute(
      strip, workingDirectory = outDir, streamHandler = stdoutStreamHandler(),
      args = listOf("--strip-all", "-R", ".note", "-R", ".comment", patchOut)
    )
    val (functions, vars) = parseSymbols(outDir.child(symbolsFileName), objFiles)
    return CompileResult(patchOut, functions, vars)
  }

  private fun parseSymbols(symFile: File, objFiles: List<String>): Pair<CompiledSymbols, CompiledSymbols> {
    val functions = mutableMapOf<String, Int>()
    val vars = mutableMapOf<String, Int>()
    val symLines = symFile.readLines()
    parseSymbolsSect(
      symLines,
      startPrefix = ".text",
      endPrefix = ".rodata"
    ).forEach {
      functions[it.first] = it.second
    }
    parseSymbolsSect(
      symLines,
      startPrefix = ".rodata",
      endPrefix = "LOAD ${objFiles.first()}"
    ).forEach {
      vars[it.first] = it.second
    }
    return Pair(functions, vars)
  }

  private fun parseSymbolsSect(lines: List<String>, startPrefix: String, endPrefix: String): List<Pair<String, Int>> {
    return lines.subList(
      fromIndex = lines.indexOfFirst { it.startsWith(startPrefix) },
      toIndex = lines.indexOfFirst { it.startsWith(endPrefix) }
    )
      .map { line -> line.dropWhile { it == ' ' } }
      .filter { it.startsWith("0x0000") }
      .map { it.split("                ") }
      .map { it[1] to Integer.parseUnsignedInt(it[0].substring(2), 16) }
  }
}

val standardPspGccFlags = listOf(
  "-Wall",
  "-Wextra",
  "-std=gnu++0x",
  "-s",
  "-G0",
  "-fomit-frame-pointer",
  "-fno-exceptions",
  "-fno-asynchronous-unwind-tables",
  "-fno-unwind-tables",
  "-ffreestanding",
  "-nostdlib",
  "-nostartfiles",
)

internal object PspLinkerTemplate : LinkerTemplate {
  override fun get(patchBase: Int, maxCompiledSize: Int): String {
    return """
      MEMORY
      {
          ram : ORIGIN = ${(patchBase).toHex()}, LENGTH = ${maxCompiledSize.toHex()}
      }
      SECTIONS
      {
          .text : { *(.text*) } > ram
          .rodata : { *(.rodata*) } > ram
          .data : { *(.data*) } > ram
          .bss : { *(.bss*) } > ram
          .ctors : { *(.ctors*) } > ram
      }
      """.trimIndent()
  }
}

internal interface LinkerTemplate {
  fun get(patchBase: Int, maxCompiledSize: Int): String
}

class CompileResult(
  val patchFile: File,
  val functions: CompiledSymbols,
  val variables: CompiledSymbols,
)

typealias CompiledSymbols = Map<String, Int>
