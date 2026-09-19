// 표시용 문자열 헬퍼

/** 줄임말은 대문자 그대로 둔다. Hof로 적으면 다른 이름처럼 읽힌다. */
const ACRONYMS = new Set(['HOF', 'WBC', 'FA']);

export function cardTypeLabel(value: string): string {
  return value
    .split('_')
    .map((token) =>
      ACRONYMS.has(token.toUpperCase())
        ? token.toUpperCase()
        : token.charAt(0).toUpperCase() + token.slice(1).toLowerCase(),
    )
    .join(' ');
}
