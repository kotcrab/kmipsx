package kmipsx.elf

import kmips.Assembler
import kmips.Endianness
import kmips.assembleAsByteArray

fun patchSuite(
  baseAddress: Int = 0,
  init: ElfPatchSuite.() -> Unit,
): ElfPatchSuite {
  val suite = ElfPatchSuite(baseAddress)
  suite.init()
  return suite
}

class ElfPatchSuite(
  private val baseAddress: Int,
) {
  internal val patches = mutableListOf<ElfPatch>()

  fun patch(
    name: String,
    enabled: Boolean = true,
    autoRemoveRelocations: Boolean = true,
    init: ElfPatch.() -> Unit,
  ) {
    val patch = ElfPatch(name, baseAddress, enabled, autoRemoveRelocations)
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
    changes.add(ElfChange(startAddress, assembleAsByteArray(startAddress + baseAddress, Endianness.Little, init)))
  }

  fun removeRelocation(addr: Int) {
    relocationsToRemove.add(addr)
  }
}

class ElfChange(
  val startAddress: Int,
  val bytes: ByteArray,
)
