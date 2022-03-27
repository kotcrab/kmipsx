package kmipsx.elf

import kio.KioInputStream
import kio.util.arrayCopy
import kio.util.toWHex
import java.io.File

fun patchElf(
  inFile: File,
  outFile: File,
  patchSuite: ElfPatchSuite,
  baseProgramHeaderIndex: Int,
  relocationSectionHeaderIndex: Int? = null,
) {
  ElfPatcher(inFile, outFile, patchSuite.patches, baseProgramHeaderIndex, relocationSectionHeaderIndex)
}

private class ElfPatcher(
  inFile: File,
  outFile: File,
  patches: List<ElfPatch>,
  baseProgramHeaderIndex: Int,
  relocationSectionHeaderIndex: Int?,
) {
  private val elf = ElfFile(inFile)

  private val baseProgramHeader = elf.programHeaders[baseProgramHeaderIndex]
  private val nextProgramHeader = elf.programHeaders.getOrNull(baseProgramHeaderIndex + 1)
  private val relocationSectionHeader = relocationSectionHeaderIndex?.let { elf.sectionHeaders[it] }

  private val outBytes = inFile.readBytes()
  private val relocations = readRelocations(outBytes)

  // maps address to relocation type positions (for easy zeroing)
  private fun readRelocations(bytes: ByteArray): Map<Int, List<Int>> {
    if (relocationSectionHeader == null) {
      return emptyMap()
    }

    val relocations = mutableMapOf<Int, MutableList<Int>>()
    with(KioInputStream(bytes)) {
      setPos(relocationSectionHeader.offset)
      while (pos() < relocationSectionHeader.offset + relocationSectionHeader.size) {
        val addr = readInt()
        val typePos = pos()
        relocations.getOrPut(addr) { mutableListOf() }
          .add(typePos)
      }
    }
    return relocations
  }

  init {
    patches.forEach { patch ->
      if (!patch.enabled) {
        return@forEach
      }
      patch.changes.forEach { change ->
        applyPatchChange(patch, change)
      }
      removePatchRelocations(patch)
    }
    outFile.writeBytes(outBytes)
  }

  private fun applyPatchChange(patch: ElfPatch, change: ElfChange) {
    if (nextProgramHeader != null && change.writeAddress > nextProgramHeader.offset) {
      error("Trying to patch unsafe address: ${change.writeAddress.toWHex()} (it overflows to the next ELF program header)")
    }
    arrayCopy(src = change.bytes, dest = outBytes, destPos = change.writeAddress + baseProgramHeader.offset)
    autoRemovePatchChangeRelocations(patch, change)
  }

  private fun autoRemovePatchChangeRelocations(patch: ElfPatch, change: ElfChange) {
    if (!patch.autoRemoveRelocations) {
      return
    }
    for (i in change.writeAddress until change.writeAddress + change.bytes.size step 4) {
      val typePositions = relocations[i] ?: continue
      clearRelocations(outBytes, typePositions)
    }
  }

  private fun removePatchRelocations(patch: ElfPatch) {
    if (patch.relocationsToRemove.isEmpty()) {
      return
    }
    if (relocationSectionHeader == null) {
      error("A list of relocations to remove was provided but no relocation section header index was set")
    }
    patch.relocationsToRemove.forEach { addressToRemove ->
      val typePositions = relocations[addressToRemove]
        ?: error("Can't remove relocation at: ${addressToRemove.toWHex()}, entry for this relocation was not found.")
      clearRelocations(outBytes, typePositions)
    }
  }

  private fun clearRelocations(bytes: ByteArray, typePositions: List<Int>) {
    typePositions.forEach { typePosition ->
      arrayCopy(src = ByteArray(4), dest = bytes, destPos = typePosition)
    }
  }
}
