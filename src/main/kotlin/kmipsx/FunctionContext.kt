package kmipsx

import kmips.Assembler
import kmips.Reg
import kmips.Reg.*

class FunctionContext(private val assembler: Assembler, regs: List<Reg>) {
  private val regs = regs.toSet()

  fun byteSize() = regs.size * 4

  fun preserve() = with(assembler) {
    if (regs.isEmpty()) {
      return
    }
    addi(sp, sp, -regs.size * 4)
    regs.forEachIndexed { idx, reg ->
      sw(reg, idx * 4, sp)
    }
  }

  fun restore(skipRegs: List<Reg> = emptyList(), adjustSp: Boolean = true) = with(assembler) {
    restoreRegs(skipRegs)
    if (regs.isNotEmpty() && adjustSp) {
      addi(sp, sp, regs.size * 4)
    }
  }

  fun exit() = with(assembler) {
    jr(ra)
    nop()
  }

  fun restoreAndExit(skipRegs: List<Reg> = emptyList()) = with(assembler) {
    restoreRegs(skipRegs)
    jr(ra)
    if (regs.isNotEmpty()) {
      addi(sp, sp, regs.size * 4)
    } else {
      nop()
    }
  }

  private fun restoreRegs(skipRegs: List<Reg>) = with(assembler) {
    regs.forEachIndexed { idx, reg ->
      if (reg !in skipRegs) {
        lw(reg, idx * 4, sp)
      }
    }
  }
}
