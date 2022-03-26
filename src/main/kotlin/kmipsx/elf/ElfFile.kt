package kmipsx.elf

import kio.KioInputStream
import kio.LERandomAccessFile
import kio.util.seek
import kio.util.toUnsignedInt
import kio.util.toWHex
import kmipsx.util.alignValue
import java.io.File

fun extendElfBss(
  file: File,
  extendByBytes: Int,
  programHeaderIndex: Int? = null,
) {
  val elf = ElfFile(file)
  val programHeader = when {
    programHeaderIndex != null -> elf.programHeaders[programHeaderIndex]
    else -> {
      elf.programHeaders
        .filter { it.type == 1 && it.pAddr == 0 }
        .maxByOrNull { it.vAddr }
        ?: error("Could not auto detect ELF program header to apply BSS extension, please set program header index manually")
    }
  }
  with(LERandomAccessFile(file)) {
    seek(programHeader.headerOffset + 0x14)
    val newBssSize = alignValue(readInt() + extendByBytes, 16)
    seek(programHeader.headerOffset + 0x14)
    writeInt(newBssSize)
    close()
  }
}

class ElfFile(val bytes: ByteArray) {
  constructor(file: File) : this(file.readBytes())

  val header: ElfHeader
  val programHeaders: List<ElfProgramHeader>
  val sectionHeaders: List<ElfSectionHeader>

  init {
    val programHeaders = mutableListOf<ElfProgramHeader>()
    val sectionHeaders = mutableListOf<ElfSectionHeader>()

    with(KioInputStream(bytes)) {
      if (readByte().toUnsignedInt() != 0x7F || readString(3) != "ELF") {
        error("Not an ELF file")
      }
      header = ElfHeader(
        entryPoint = readInt(at = 0x18),
        programHeaderOffset = readInt(at = 0x1C),
        programHeaderSize = readShort(at = 0x2A).toInt(),
        programHeaderCount = readShort(at = 0x2C).toInt(),
        sectionHeaderOffset = readInt(at = 0x20),
        sectionHeaderSize = readShort(at = 0x2E).toInt(),
        sectionHeaderCount = readShort(at = 0x30).toInt()
      )

      setPos(header.programHeaderOffset)
      repeat(header.programHeaderCount) {
        val headerOffset = pos()
        with(KioInputStream(readBytes(header.programHeaderSize))) {
          programHeaders.add(
            ElfProgramHeader(
              headerOffset = headerOffset,
              type = readInt(at = 0x0),
              offset = readInt(at = 0x04),
              vAddr = readInt(at = 0x08),
              pAddr = readInt(at = 0x0C),
              fileSize = readInt(at = 0x10),
              memSize = readInt(at = 0x14)
            )
          )
        }
      }

      setPos(header.sectionHeaderOffset)
      repeat(header.sectionHeaderCount) {
        val headerOffset = pos()
        with(KioInputStream(readBytes(header.sectionHeaderSize))) {
          sectionHeaders.add(
            ElfSectionHeader(
              headerOffset = headerOffset,
              type = readInt(at = 0x04),
              vAddr = readInt(at = 0x0C),
              offset = readInt(at = 0x10),
              size = readInt(at = 0x14)
            )
          )
        }
      }
    }

    this.programHeaders = programHeaders
    this.sectionHeaders = sectionHeaders
  }
}

class ElfHeader(
  val entryPoint: Int,
  val programHeaderOffset: Int,
  val programHeaderSize: Int,
  val programHeaderCount: Int,
  val sectionHeaderOffset: Int,
  val sectionHeaderSize: Int,
  val sectionHeaderCount: Int,
) {
  override fun toString(): String {
    return "ElfHeader(entryPoint=${entryPoint.toWHex()}, " +
      "programHeaderOffset=${programHeaderOffset.toWHex()}, " +
      "programHeaderSize=${programHeaderSize.toWHex()}, " +
      "programHeaderCount=${programHeaderCount.toWHex()}, " +
      "sectionHeaderOffset=${sectionHeaderOffset.toWHex()}, " +
      "sectionHeaderSize=${sectionHeaderSize.toWHex()}, " +
      "sectionHeaderCount=${sectionHeaderCount.toWHex()})"
  }
}

class ElfProgramHeader(
  val headerOffset: Int,
  val type: Int,
  val offset: Int,
  val vAddr: Int,
  val pAddr: Int,
  val fileSize: Int,
  val memSize: Int,
) {
  override fun toString(): String {
    return "ElfProgramHeader(headerOffset=${headerOffset.toWHex()}, type=${type.toWHex()}, offset=${offset.toWHex()}, vAddr=${vAddr.toWHex()}, " +
      "pAddr=${pAddr.toWHex()}, fileSize=${fileSize.toWHex()}, memSize=${memSize.toWHex()})"
  }
}

class ElfSectionHeader(
  val headerOffset: Int,
  val type: Int,
  val vAddr: Int,
  val offset: Int,
  val size: Int,
) {
  override fun toString(): String {
    return "ElfSectionHeader(headerOffset=${headerOffset.toWHex()}, type=${type.toWHex()}, vAddr=${vAddr.toWHex()}, offset=${offset.toWHex()}, " +
      "size=${size.toWHex()})"
  }
}
