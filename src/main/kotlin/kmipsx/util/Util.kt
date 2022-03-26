package kmipsx.util

internal fun alignValue(value: Int, pad: Int = 16): Int {
  if (value % pad == 0) return value
  return (value / pad + 1) * pad
}
