import { strings, TranslationKey } from '../locales/strings';

// MVP는 한국어 단일 언어다. 다국어를 다시 붙일 때를 대비해 t() 호출 규약만 유지한다.
const t = (key: TranslationKey): string => strings[key] ?? key;

export function useTranslation() {
  return { t };
}
