// 표시용 문자열 헬퍼
export function cardTypeLabel(value: string): string {
  return value
    .split('_')
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(' ');
}
