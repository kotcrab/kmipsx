package kmipsx.util

import kio.KioInputStream
import kmips.Assembler
import kmips.Reg
import kmips.Reg.*
import kmipsx.FunctionContext
import kmipsx.elf.CompileResult
import kmipsx.elf.writeElfSectionsInto
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

val callerSavedRegisters = listOf(
  ra, v0, v1,
  a0, a1, a2, a3,
  t0, t1, t2, t3, t4, t5, t6, t7, t8, t9
)

fun Assembler.compiledElf(compileResult: CompileResult) {
  val elfBytes = writeElfSectionsInto(mutableListOf(), compileResult) {
    virtualPc + it
  }
  bytes(elfBytes.toByteArray())
}

fun Assembler.preserve(regs: List<Reg>): FunctionContext {
  val ctx = FunctionContext(this, regs)
  ctx.preserve()
  return ctx
}

fun Assembler.region(block: Assembler.() -> Unit): Int {
  val address = virtualPc
  block()
  return address
}

fun Assembler.zeroTerminatedString(string: String, charset: Charset = Charsets.US_ASCII): Int {
  val address = virtualPc
  var zeroWritten = false
  val bytes = string.toByteArray(charset)
  for (i in 0..bytes.size step 4) {
    val defaultChar: (Int) -> Byte = {
      zeroWritten = true
      0
    }
    val char0 = bytes.getOrElse(i + 3, defaultChar).toInt() shl 24
    val char1 = bytes.getOrElse(i + 2, defaultChar).toInt() shl 16
    val char2 = bytes.getOrElse(i + 1, defaultChar).toInt() shl 8
    val char3 = bytes.getOrElse(i, defaultChar).toInt()
    data(char0 or char1 or char2 or char3)
  }
  if (!zeroWritten) {
    data(0)
  }
  return address
}

fun Assembler.float(value: Float): Int {
  val buf = ByteBuffer.allocate(4).putFloat(value)
  buf.rewind()
  val address = virtualPc
  data(buf.order(ByteOrder.BIG_ENDIAN).int)
  return address
}

fun Assembler.bytes(bytes: ByteArray): Int {
  if (bytes.size % 4 != 0) {
    error("Buffer size is not aligned to word size")
  }
  val address = virtualPc
  with(KioInputStream(bytes)) {
    while (!eof()) {
      data(readInt())
    }
  }
  return address
}

fun Assembler.word(intValue: Long): Int {
  val address = virtualPc
  data(intValue.toInt())
  return address
}

fun Assembler.word(value: Int): Int {
  val address = virtualPc
  data(value)
  return address
}

fun Assembler.align16() {
  if (virtualPc % 4 != 0) {
    error("Can't align (likely due to a bug in the assembler)")
  }
  while (virtualPc % 16 != 0) {
    data(0)
  }
}
