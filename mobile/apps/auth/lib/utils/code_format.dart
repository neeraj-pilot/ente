String formatCode(String code) => switch (code.length) {
  6 => '${code.substring(0, 3)} ${code.substring(3)}',
  7 => '${code.substring(0, 3)} ${code.substring(3, 4)} ${code.substring(4)}',
  8 => '${code.substring(0, 3)} ${code.substring(3, 5)} ${code.substring(5)}',
  9 => '${code.substring(0, 3)} ${code.substring(3, 6)} ${code.substring(6)}',
  _ => code,
};
