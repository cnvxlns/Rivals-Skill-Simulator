'use client';

import { LanguageCode } from '../locales/translations';
import { useTranslation } from '../lib/i18n';

const options: { value: LanguageCode; label: string }[] = [
  { value: 'KR', label: '한국어' },
  { value: 'EN', label: 'English' },
  { value: 'JP', label: '日本語' },
  { value: 'ES', label: 'Español' },
  { value: 'CN', label: '繁體中文' },
];

const LanguageSelector = () => {
  const { language, setLanguage } = useTranslation();

  return (
    <div className="relative">
      <select
        value={language}
        onChange={(e) => setLanguage(e.target.value as LanguageCode)}
        className="rounded-lg border border-white/15 bg-white/10 px-3 py-2 text-xs font-semibold text-white shadow-sm backdrop-blur transition hover:border-white/30 focus:border-white/50 focus:outline-none"
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value} className="bg-slate-900 text-white">
            {opt.label}
          </option>
        ))}
      </select>
    </div>
  );
};

export default LanguageSelector;
