'use client';

import { useState } from 'react';
import { useMethodology } from '../lib/useMethodology';
import { useTranslation } from '../lib/i18n';
import { Loader2, RefreshCw, BarChart2, CheckCircle2, Shield, Info } from 'lucide-react';
import { TranslationKey } from '../locales/translations';

export default function MethodologyView() {
  const { data, loading, error, refresh } = useMethodology();
  const { t } = useTranslation();
  const [activeGroup, setActiveGroup] = useState<'static' | 'role' | 'batting' | 'reach' | 'gates'>('static');

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 space-y-4">
        <Loader2 className="h-10 w-10 animate-spin text-emerald-400" />
        <p className="text-sm text-indigo-200">{t('score_loading_skills')}</p>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-6 text-center">
        <h3 className="text-lg font-semibold text-red-200">Error Loading Methodology</h3>
        <p className="mt-2 text-sm text-red-100">{error || 'No data returned'}</p>
        <button
          type="button"
          onClick={refresh}
          className="mt-4 inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-500 transition"
        >
          <RefreshCw size={16} />
          Retry
        </button>
      </div>
    );
  }

  const { formula, statWeights, conditionProbabilities } = data;

  const translateToken = (token: string, descriptionKey: string): string => {
    if (descriptionKey) {
      const val = t(descriptionKey as TranslationKey);
      if (val && val !== descriptionKey) {
        return val;
      }
    }
    return token;
  };

  const statLabelKey = (stat: string): TranslationKey => {
    return `stat_${stat}` as TranslationKey;
  };

  return (
    <div className="space-y-8 pb-8">
      {/* Header */}
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-emerald-200">
            {t('tab_methodology')}
          </p>
          <h1 className="text-4xl font-bold text-white">{t('methodology_title')}</h1>
          <p className="mt-2 max-w-2xl text-sm text-indigo-100">{t('methodology_desc')}</p>
        </div>
      </header>

      {/* Formulas Section */}
      <section className="space-y-4">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <Info className="text-emerald-400" size={20} />
          {t('methodology_section_formula')}
        </h2>
        <div className="grid gap-4 md:grid-cols-2">
          {/* Per-Skill Formula */}
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
            <h3 className="text-sm font-bold text-emerald-300">
              {t(formula.perSkillFormula.descriptionKey as TranslationKey)}
            </h3>
            <div className="mt-3 rounded-lg bg-slate-950/40 p-3 font-mono text-xs text-indigo-200 overflow-x-auto">
              {formula.perSkillFormula.displayText}
            </div>
          </div>

          {/* Total Formula */}
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
            <h3 className="text-sm font-bold text-emerald-300">
              {t(formula.totalFormula.descriptionKey as TranslationKey)}
            </h3>
            <div className="mt-3 rounded-lg bg-slate-950/40 p-3 font-mono text-xs text-indigo-200 overflow-x-auto">
              {formula.totalFormula.displayText}
            </div>
          </div>

          {/* Percent Effect Rule */}
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur md:col-span-2">
            <h3 className="text-sm font-bold text-emerald-300">
              {t(formula.percentEffectRule.descriptionKey as TranslationKey)}
            </h3>
            <p className="mt-2 text-xs text-slate-300 leading-relaxed">
              {t('formula_percent_effect')}
            </p>
            <div className="mt-3 rounded-lg bg-slate-950/40 p-3 font-mono text-xs text-indigo-200 overflow-x-auto">
              {formula.percentEffectRule.displayText}
            </div>
          </div>

          {/* Rounding Rule */}
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
            <h3 className="text-sm font-bold text-emerald-300">
              {t(formula.roundingRule.descriptionKey as TranslationKey)}
            </h3>
            <div className="mt-3 rounded-lg bg-slate-950/40 p-3 font-mono text-xs text-indigo-200 overflow-x-auto">
              {formula.roundingRule.displayText}
            </div>
          </div>

          {/* Condition Combination Rule */}
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
            <h3 className="text-sm font-bold text-emerald-300">
              {t(formula.conditionCombinationRule.descriptionKey as TranslationKey)}
            </h3>
            <div className="mt-3 rounded-lg bg-slate-950/40 p-3 font-mono text-xs text-indigo-200 overflow-x-auto">
              {formula.conditionCombinationRule.displayText}
            </div>
          </div>
        </div>
      </section>

      {/* Stat Weights Section */}
      <section className="space-y-4">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <BarChart2 className="text-emerald-400" size={20} />
          {t('methodology_section_stat_weights')}
        </h2>
        <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
          <div className="grid gap-3 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
            {Object.entries(statWeights).map(([stat, weight]) => {
              const localizedLabel = t(statLabelKey(stat));
              const displayLabel = localizedLabel !== statLabelKey(stat) ? localizedLabel : stat;
              return (
                <div
                  key={stat}
                  className="group rounded-xl border border-white/5 bg-gradient-to-br from-indigo-950/20 to-white/5 p-3 text-center transition duration-200 hover:scale-[1.03] hover:bg-white/10 hover:border-emerald-500/20"
                >
                  <p className="text-xs font-medium text-indigo-200 group-hover:text-emerald-200 transition-colors">
                    {displayLabel}
                  </p>
                  <p className="mt-1 text-lg font-bold text-white">{weight.toFixed(2)}</p>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* Conditional Probabilities Section */}
      <section className="space-y-4">
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <Shield className="text-emerald-400" size={20} />
          {t('methodology_section_conditions')}
        </h2>

        {/* Group Tabs */}
        <div className="flex flex-wrap gap-2 border-b border-white/10 pb-2">
          {(['static', 'role', 'batting', 'reach', 'gates'] as const).map((group) => {
            const labelKey = `methodology_group_${group}` as TranslationKey;
            const label = t(labelKey);
            const isActive = activeGroup === group;
            return (
              <button
                key={group}
                type="button"
                onClick={() => setActiveGroup(group)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
                  isActive
                    ? 'bg-emerald-500/20 border border-emerald-500/30 text-emerald-200 shadow-md'
                    : 'text-indigo-200 hover:text-white hover:bg-white/5 border border-transparent'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* Group Content */}
        <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-lg backdrop-blur">
          {/* Static Group */}
          {activeGroup === 'static' && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-indigo-100">
                <thead>
                  <tr className="border-b border-white/10 text-indigo-300 font-semibold uppercase">
                    <th className="py-2 px-3">{t('methodology_col_token')}</th>
                    <th className="py-2 px-3 text-center">{t('methodology_col_value')}</th>
                    <th className="py-2 px-3">{t('methodology_col_description')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {conditionProbabilities.staticProbabilities.map((entry) => (
                    <tr key={entry.token} className="hover:bg-white/5 transition-colors">
                      <td className="py-2 px-3 font-mono text-emerald-300 font-semibold">{entry.token}</td>
                      <td className="py-2 px-3 text-center font-bold">{entry.value.toFixed(3)}</td>
                      <td className="py-2 px-3 text-slate-300">
                        {translateToken(entry.token, entry.descriptionKey)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Batting Order Group */}
          {activeGroup === 'batting' && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-indigo-100">
                <thead>
                  <tr className="border-b border-white/10 text-indigo-300 font-semibold uppercase">
                    <th className="py-2 px-3">{t('methodology_col_token')}</th>
                    <th className="py-2 px-3 text-center">{t('methodology_col_value')}</th>
                    <th className="py-2 px-3">{t('methodology_col_description')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {conditionProbabilities.battingOrderProbabilities.map((entry) => (
                    <tr key={entry.token} className="hover:bg-white/5 transition-colors">
                      <td className="py-2 px-3 font-mono text-emerald-300 font-semibold">{entry.token}</td>
                      <td className="py-2 px-3 text-center font-bold">{entry.value.toFixed(3)}</td>
                      <td className="py-2 px-3 text-slate-300">
                        {translateToken(entry.token, entry.descriptionKey)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Role Group */}
          {activeGroup === 'role' && (
            <div className="space-y-6">
              {conditionProbabilities.roleProbabilities.map((entry) => (
                <div key={entry.role} className="rounded-xl border border-white/5 bg-slate-950/20 p-4 space-y-3">
                  <h3 className="text-sm font-bold text-white flex items-center gap-2">
                    <CheckCircle2 size={16} className="text-emerald-400" />
                    Role: <span className="text-emerald-300">{entry.role}</span>
                  </h3>
                  <div className="grid gap-4 sm:grid-cols-3 text-xs">
                    <div className="bg-white/5 rounded-lg p-2.5">
                      <p className="text-indigo-300 font-semibold">{t('condition_guts_probability')}</p>
                      <p className="mt-1 text-base font-bold text-white">{entry.gutsProbability.toFixed(2)}</p>
                    </div>
                    <div className="bg-white/5 rounded-lg p-2.5">
                      <p className="text-indigo-300 font-semibold">{t('condition_nine_batter_duration')}</p>
                      <p className="mt-1 text-base font-bold text-white">{entry.nineBatterDuration.toFixed(2)}</p>
                    </div>
                    <div className="bg-white/5 rounded-lg p-2.5">
                      <p className="text-indigo-300 font-semibold">{t('condition_maestro_cumulative')}</p>
                      <p className="mt-1 text-base font-bold text-white">{entry.maestroCumulative.toFixed(3)}</p>
                    </div>
                  </div>
                  <div className="bg-white/5 rounded-lg p-3">
                    <p className="text-xs font-semibold text-indigo-300 mb-2">
                      {t(`condition_${entry.role.toLowerCase()}_inning_weights` as TranslationKey) || 'Inning Weights (1~9)'}
                    </p>
                    <div className="flex flex-wrap gap-1 font-mono text-xs">
                      {entry.inningWeights.map((w, idx) => (
                        <div key={idx} className="flex-1 min-w-[50px] bg-slate-950/40 rounded px-2 py-1 text-center">
                          <span className="block text-[10px] text-slate-500">{idx + 1}H</span>
                          <span className="font-bold text-indigo-200">{w.toFixed(3)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Reaches Group */}
          {activeGroup === 'reach' && (
            <div className="space-y-4">
              {conditionProbabilities.reachProbabilities.map((entry) => (
                <div key={entry.orderGroup} className="rounded-xl border border-white/5 bg-slate-950/20 p-4">
                  <h3 className="text-sm font-bold text-emerald-300">
                    {t(entry.descriptionKey as TranslationKey)}
                  </h3>
                  <div className="mt-3 flex flex-wrap gap-1.5 font-mono text-xs">
                    {entry.reachProbabilities.map((p, idx) => (
                      <div key={idx} className="flex-1 min-w-[55px] bg-slate-950/40 rounded p-2 text-center">
                        <span className="block text-[10px] text-slate-500">PA {idx + 1}</span>
                        <span className="font-bold text-indigo-200">{p.toFixed(3)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Gates Group */}
          {activeGroup === 'gates' && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-indigo-100">
                <thead>
                  <tr className="border-b border-white/10 text-indigo-300 font-semibold uppercase">
                    <th className="py-2 px-3">{t('methodology_col_token')}</th>
                    <th className="py-2 px-3 text-center">{t('methodology_col_value')}</th>
                    <th className="py-2 px-3">{t('methodology_col_description')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {conditionProbabilities.gates.map((entry) => (
                    <tr key={entry.token} className="hover:bg-white/5 transition-colors">
                      <td className="py-2 px-3 font-mono text-emerald-300 font-semibold">{entry.token}</td>
                      <td className="py-2 px-3 text-center font-bold text-slate-400">1.0 / 0.0</td>
                      <td className="py-2 px-3 text-slate-300">
                        {translateToken(entry.token, entry.descriptionKey)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
