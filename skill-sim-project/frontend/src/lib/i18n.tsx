'use client';

import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { LanguageCode, translations, TranslationKey } from '../locales/translations';

type LanguageContextType = {
  language: LanguageCode;
  setLanguage: (lang: LanguageCode) => void;
  t: (key: TranslationKey) => string;
};

const LanguageContext = createContext<LanguageContextType | null>(null);
const STORAGE_KEY = 'skill-sim-language';

export function LanguageProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguage] = useState<LanguageCode>('KR');

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const stored = window.localStorage.getItem(STORAGE_KEY) as LanguageCode | null;
    if (stored && translations[stored]) {
      setLanguage(stored);
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(STORAGE_KEY, language);
  }, [language]);

  const value = useMemo(() => {
    const t = (key: TranslationKey) => translations[language]?.[key] ?? translations.KR[key] ?? key;
    return { language, setLanguage, t };
  }, [language]);

  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>;
}

export function useTranslation() {
  const ctx = useContext(LanguageContext);
  if (!ctx) {
    throw new Error('useTranslation must be used within LanguageProvider');
  }
  return ctx;
}
