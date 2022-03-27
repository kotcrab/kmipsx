package kmipsx.elf

import kmips.Assembler
import kmips.Endianness
import kmips.assembleAsByteArray

fun patchSuite(
  baseAddress: Int = 0x8804000,
  useRelativeChangeAddress: Boolean = false,
  init: ElfPatchSuite.() -> Unit,
): ElfPatchSuite {
  val suite = ElfPatchSuite(baseAddress, useRelativeChangeAddress)
  suite.init()
  return suite
}

class ElfPatchSuite(
  private val baseAddress: Int,
  private val useRelativeChangeAddress: Boolean,
) {
  internal val patches = mutableListOf<ElfPatch>()

  fun patch(
    name: String,
    enabled: Boolean = true,
    autoRemoveRelocations: Boolean = true,
    init: ElfPatch.() -> Unit,
  ) {
    val patch = ElfPatch(name, baseAddress, useRelativeChangeAddress, enabled, autoRemoveRelocations)
    patch.init()
    patches.add(patch)
  }

  fun addPatches(patches: List<ElfPatch>) {
    this.patches.addAll(patches)
  }
}

class ElfPatch(
  val name: String,
  val baseAddress: Int,
  val useRelativeChangeAddress: Boolean,
  val enabled: Boolean,
  val autoRemoveRelocations: Boolean,
) {
  internal val changes: MutableList<ElfChange> = mutableListOf()
  internal val relocationsToRemove: MutableList<Int> = mutableListOf()

  fun change(
    startAddress: Int,
    baseAddress: Int = this.baseAddress,
    init: Assembler.() -> Unit,
  ) {
    val writeAddress = when {
      useRelativeChangeAddress -> startAddress
      else -> startAddress - baseAddress
    }
    val startPc = when {
      useRelativeChangeAddress -> startAddress + baseAddress
      else -> startAddress
    }
    changes.add(ElfChange(writeAddress, assembleAsByteArray(startPc, Endianness.Little, init)))
  }

  fun removeRelocation(addr: Int) {
    relocationsToRemove.add(addr)
  }
}

class ElfChange(
  val writeAddress: Int,
  val bytes: ByteArray,
)
