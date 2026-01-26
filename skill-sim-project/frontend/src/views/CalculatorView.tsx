// Placeholder for the upcoming Skill Score Calculator feature
import { useTranslation } from '../lib/i18n';

const CalculatorView = () => {
  const { t } = useTranslation();

  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 p-10 text-center shadow-xl backdrop-blur">
      <p className="text-xs font-semibold uppercase tracking-[0.25em] text-indigo-200">{t('tab_calculator')}</p>
      <h2 className="mt-3 text-3xl font-bold text-white">{t('calculator_title')}</h2>
      <p className="mt-3 text-sm text-indigo-100/80">{t('calculator_desc')}</p>
      <div className="mt-6 inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-wide text-indigo-100">
        <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-300" aria-hidden />
        {t('badge_in_progress')}
      </div>
    </div>
  );
};

export default CalculatorView;
