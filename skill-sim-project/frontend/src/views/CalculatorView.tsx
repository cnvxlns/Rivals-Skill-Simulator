'use client';

import { Calculator, Cog, Loader2, RotateCcw } from 'lucide-react';
import { useState } from 'react';
import ScoreSkillPicker from '../components/ScoreSkillPicker';
import { useTranslation } from '../lib/i18n';
import { useScoreCalculator } from '../lib/useScoreCalculator';
import { TranslationKey } from '../locales/translations';
import { CardGrade, CardType, Position, SubPosition } from '../types';

interface CalculatorViewProps {
  onViewMethodology?: () => void;
}

const CalculatorView = ({ onViewMethodology }: CalculatorViewProps) => {
  const { t } = useTranslation();
  const [showStats, setShowStats] = useState(false);
  const {
    cardType,
    setCardType,
    cardGrade,
    setCardGrade,
    position,
    setPosition,
    subPosition,
    setSubPosition,
    scorePosition,
    slotCount,
    skills,
    selections,
    selectedSkillIds,
    visibleStats,
    userStats,
    battingOrder,
    pitcherSlot,
    loadingSkills,
    calculating,
    canCalculate,
    error,
    result,
    updateSkill,
    updateLevel,
    updateUserStat,
    updateBattingOrder,
    updatePitcherSlot,
    resetUserStats,
    clearSlot,
    calculate,
  } = useScoreCalculator();

  const pitcherSubPositions: SubPosition[] = ['SP', 'RP', 'CP'];
  const batterSubPositions: SubPosition[] = ['C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subPositionOptions = position === Position.PITCHER ? pitcherSubPositions : batterSubPositions;
  const cardGradeOptions: CardGrade[] = [
    CardGrade.LIVE_SEASON,
    CardGrade.IMPACT,
    CardGrade.PRIME,
    CardGrade.WBC_PRIME,
    CardGrade.MOMENT,
    CardGrade.SIGNATURE,
    CardGrade.WBC_SIGNATURE,
    CardGrade.SIGNATURE_BLACK,
    CardGrade.WBC_SIGNATURE_BLACK,
    CardGrade.HOF,
  ];

  const formatScore = (value: number) => value.toFixed(2);
  const statLabelKey = (stat: string): TranslationKey => `stat_${stat}` as TranslationKey;
  const defaultStatValue = (stat: string) => (stat.includes('덱') ? 500 : 120);

  // 선택 포지션 기준으로 "상대(반대 역할) 스탯"을 판정한다. 그 외는 내 스탯.
  // 값은 모두 양수 크기이며, 상대 스탯은 감소(디버프)이므로 −부호로 표시한다.
  const opponentStats =
    position === Position.PITCHER
      ? new Set(['파워', '정확', '선구', '인내', '주루'])
      : new Set(['구속', '변화', '구위', '제구', '지구력']);
  const perStatMine = result?.perStat.filter((stat) => !opponentStats.has(stat.stat)) ?? [];
  const perStatOpponent = result?.perStat.filter((stat) => opponentStats.has(stat.stat)) ?? [];

  return (
    <div className="space-y-8 pb-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.3em] text-emerald-200">{t('tab_calculator')}</p>
          <h1 className="text-4xl font-bold text-white">{t('calculator_title')}</h1>
          <p className="mt-2 max-w-2xl text-sm text-indigo-100">{t('calculator_desc')}</p>
        </div>
        <span className="rounded-full border border-emerald-300/30 bg-emerald-400/10 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-100">
          {t('score_badge_ready')}
        </span>
      </header>

      <section className="grid items-start gap-6 xl:grid-cols-[420px_1fr]">
        <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-lg font-semibold text-white">{t('score_settings')}</h2>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowStats((prev) => !prev)}
                aria-label={t('score_stat_settings')}
                title={t('score_stat_settings')}
                className="grid h-9 w-9 place-items-center rounded-full border border-white/15 text-indigo-100 transition hover:bg-white/10"
              >
                <Cog size={17} />
              </button>
              <span className="rounded-full bg-indigo-500/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-indigo-100">
                {slotCount} {t('score_slots')}
              </span>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
              <h3 className="text-sm font-semibold text-white">{t('label_card_type')}</h3>
              <select
                value={cardType}
                onChange={(event) => setCardType(event.target.value as CardType)}
                className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              >
                <option value={CardType.SIGNATURE}>Signature</option>
                <option value={CardType.SIGNATURE_BLACK}>Signature Black</option>
                <option value={CardType.WBC}>WBC</option>
                <option value={CardType.WBC_SIGNATURE_BLACK}>WBC Signature Black</option>
                <option value={CardType.HOF}>HOF</option>
                <option value={CardType.MOMENT}>Moment</option>
              </select>
            </div>

            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
              <h3 className="text-sm font-semibold text-white">{t('label_card_grade')}</h3>
              <select
                value={cardGrade}
                onChange={(event) => setCardGrade(event.target.value as CardGrade)}
                className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              >
                {cardGradeOptions.map((option) => (
                  <option key={option} value={option}>
                    {t(`card_grade_${option.toLowerCase()}` as TranslationKey)}
                  </option>
                ))}
              </select>
            </div>

            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
              <h3 className="text-sm font-semibold text-white">{t('label_position')}</h3>
              <select
                value={position}
                onChange={(event) => setPosition(event.target.value as Position)}
                className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              >
                <option value={Position.BATTER}>Batter</option>
                <option value={Position.PITCHER}>Pitcher</option>
              </select>

              <label className="mt-4 block">
                <span className="text-xs font-semibold text-indigo-100">{t('label_sub_position')}</span>
                <select
                  value={subPosition}
                  onChange={(event) => setSubPosition(event.target.value as SubPosition)}
                  className="mt-2 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                >
                  <option value="" disabled hidden>
                    {t('option_select_sub_position')}
                  </option>
                  {subPositionOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>

              {position === Position.BATTER && (
                <label className="mt-4 block">
                  <span className="text-xs font-semibold text-indigo-100">{t('label_batting_order')}</span>
                  <select
                    value={battingOrder ?? ''}
                    onChange={(event) => updateBattingOrder(event.target.value ? Number(event.target.value) : null)}
                    className="mt-2 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                  >
                    <option value="" disabled hidden>
                      {t('option_select_batting_order')}
                    </option>
                    {Array.from({ length: 9 }, (_, idx) => idx + 1).map((order) => (
                      <option key={order} value={order}>
                        {order}번 타순
                      </option>
                    ))}
                  </select>
                </label>
              )}

              {position === Position.PITCHER && (subPosition === 'SP' || subPosition === 'RP') && (
                <label className="mt-4 block">
                  <span className="text-xs font-semibold text-indigo-100">{t('label_pitcher_slot')}</span>
                  <select
                    value={pitcherSlot ?? ''}
                    onChange={(event) => updatePitcherSlot(event.target.value ? Number(event.target.value) : null)}
                    className="mt-2 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                  >
                    <option value="" disabled hidden>
                      {t('option_select_pitcher_slot')}
                    </option>
                    {Array.from({ length: subPosition === 'SP' ? 5 : 6 }, (_, idx) => idx + 1).map((slot) => (
                      <option key={slot} value={slot}>
                        {subPosition === 'SP' ? `${slot}선발` : `${slot}중계`}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
          </div>

          <div className="rounded-xl border border-white/10 bg-slate-950/25 p-4">
            <p className="text-xs uppercase tracking-wide text-indigo-200">{t('score_current_pool')}</p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs font-semibold text-slate-100">
              <span className="rounded-full bg-white/10 px-3 py-1">{cardType}</span>
              <span className="rounded-full bg-white/10 px-3 py-1">{t(`card_grade_${cardGrade.toLowerCase()}` as TranslationKey)}</span>
              <span className="rounded-full bg-white/10 px-3 py-1">{scorePosition}</span>
              <span className="rounded-full bg-white/10 px-3 py-1">
                {loadingSkills ? t('score_loading_skills') : `${skills.length} ${t('score_skills_loaded')}`}
              </span>
            </div>
          </div>

          {showStats && (
            <div className="rounded-xl border border-white/10 bg-slate-950/25 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-xs uppercase tracking-wide text-indigo-200">{t('score_stat_settings')}</p>
                  <h3 className="text-sm font-semibold text-white">{t('score_user_stats')}</h3>
                </div>
                <button
                  type="button"
                  onClick={resetUserStats}
                  aria-label={t('score_reset_stats')}
                  title={t('score_reset_stats')}
                  className="grid h-8 w-8 place-items-center rounded-full border border-white/15 text-indigo-100 transition hover:bg-white/10"
                >
                  <RotateCcw size={15} />
                </button>
              </div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
                {visibleStats.map((stat) => (
                  <label key={stat} className="flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/5 px-3 py-2">
                    <span className="text-sm font-semibold text-indigo-100">{t(statLabelKey(stat))}</span>
                    <input
                      type="number"
                      min={0}
                      value={userStats[stat] ?? defaultStatValue(stat)}
                      onChange={(event) => updateUserStat(stat, Number(event.target.value))}
                      className="w-24 rounded-md border border-indigo-200/60 bg-white/90 px-2 py-1 text-right text-sm font-semibold text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                    />
                  </label>
                ))}
              </div>
            </div>
          )}

          {error && <p className="rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-200">{t(error as TranslationKey)}</p>}
          {!!result?.warnings?.length && (
            <div className="rounded-lg border border-amber-300/25 bg-amber-400/10 px-3 py-2 text-sm text-amber-100">
              <p className="font-semibold">{t('score_warnings')}</p>
              <ul className="mt-1 space-y-1">
                {result.warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-indigo-200">{t('score_skill_slots')}</p>
              <h2 className="text-xl font-semibold text-white">{t('score_select_skills')}</h2>
            </div>
            <button
              type="button"
              onClick={calculate}
              disabled={!canCalculate || calculating || loadingSkills}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-500 px-4 py-2 text-sm font-bold text-slate-950 shadow-lg transition hover:bg-emerald-400 disabled:cursor-not-allowed disabled:bg-slate-500 disabled:text-slate-200"
            >
              {calculating ? <Loader2 size={16} className="animate-spin" /> : <Calculator size={16} />}
              {calculating ? t('score_calculating') : t('score_calculate')}
            </button>
          </div>

          <div className="space-y-4">
            {selections.map((selection, idx) => (
              <ScoreSkillPicker
                key={`score-slot-${idx}`}
                slotIndex={idx}
                label={`${t('slot_label')} ${idx + 1}`}
                skills={skills}
                selectedSkillId={selection.skillId}
                selectedSkillIds={selectedSkillIds}
                level={selection.level}
                disabled={loadingSkills}
                searchLabel={t('score_search_skill')}
                selectLabel={t('score_select_skill')}
                levelLabel={t('score_level')}
                clearLabel={t('score_clear_slot')}
                noSkillsLabel={t('score_no_skills')}
                onSkillChange={updateSkill}
                onLevelChange={updateLevel}
                onClear={clearSlot}
              />
            ))}
          </div>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[320px_1fr]">
        <div className="rounded-2xl border border-emerald-300/20 bg-emerald-400/10 p-5 shadow-xl">
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-100">{t('score_total')}</p>
          <p className="mt-3 text-5xl font-bold text-white">{formatScore(result?.total ?? 0)}</p>
          <p className="mt-2 text-sm text-emerald-50/80">{result ? t('score_result_ready') : t('score_result_empty')}</p>
        </div>

        <div className="grid gap-6 xl:grid-cols-2">
          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
            <h2 className="text-lg font-semibold text-white">{t('score_by_skill')}</h2>
            <div className="mt-4 space-y-3">
              {result?.perSkill.length ? (
                result.perSkill.map((skill) => (
                  <div key={skill.skillId} className="rounded-xl border border-white/10 bg-slate-950/30 p-4">
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-white" title={skill.name}>{skill.name}</p>
                      </div>
                      <p className="shrink-0 text-lg font-bold text-emerald-200">{formatScore(skill.score)}</p>
                    </div>
                    <div className="mt-3 flex flex-wrap gap-2">
                      {skill.perStat.map((stat) => (
                        <span key={`${skill.skillId}-${stat.stat}`} className="rounded-full bg-white/10 px-2 py-1 text-xs text-indigo-100">
                          {stat.stat} {formatScore(stat.value)}
                        </span>
                      ))}
                    </div>
                    {!!skill.warnings?.length && (
                      <div className="mt-3 rounded-lg border border-amber-300/20 bg-amber-400/10 px-3 py-2 text-xs text-amber-100">
                        {skill.warnings.map((warning) => (
                          <p key={warning}>{warning}</p>
                        ))}
                      </div>
                    )}
                    {!!skill.breakdown?.length && (
                      <details className="mt-3 rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-xs text-indigo-50">
                        <summary className="cursor-pointer font-semibold text-indigo-100">{t('score_formula')}</summary>
                        <div className="mt-2 space-y-1">
                          {onViewMethodology && (
                            <button
                              type="button"
                              onClick={onViewMethodology}
                              className="mb-2 block text-left font-medium text-emerald-300 hover:text-emerald-200 transition-colors underline"
                            >
                              {t('methodology_formula_detail_link')}
                            </button>
                          )}
                          {skill.breakdown.map((term, termIdx) => (
                            <div key={`${skill.skillId}-term-${termIdx}`} className="rounded-md bg-slate-950/30 px-2 py-1">
                              <span className="font-semibold">{term.stat}</span>
                              <span>
                                {' '}
                                {formatScore(term.weight)} x {formatScore(term.value)} x {formatScore(term.conditionProbability)} = {formatScore(term.subtotal)}
                              </span>
                              {term.baseStat && (
                                <span className="ml-1 text-indigo-100/70">
                                  ({term.baseStat} {formatScore(term.baseValue ?? 0)} x {formatScore(term.rawValue)})
                                </span>
                              )}
                            </div>
                          ))}
                        </div>
                      </details>
                    )}
                  </div>
                ))
              ) : (
                <p className="rounded-xl border border-dashed border-white/10 px-4 py-6 text-sm text-indigo-100/80">
                  {t('score_result_empty')}
                </p>
              )}
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
            <h2 className="text-lg font-semibold text-white">{t('score_by_stat')}</h2>
            {result?.perStat.length ? (
              <div className="mt-4 space-y-4">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-emerald-200">{t('score_stat_mine')}</p>
                  <div className="mt-2 space-y-2">
                    {perStatMine.length ? (
                      perStatMine.map((stat) => (
                        <div key={stat.stat} className="flex items-center justify-between rounded-xl border border-emerald-300/20 bg-emerald-400/5 px-4 py-3">
                          <span className="text-sm font-semibold text-indigo-100">{stat.stat}</span>
                          <span className="text-base font-bold text-emerald-200">+{formatScore(stat.value)}</span>
                        </div>
                      ))
                    ) : (
                      <p className="rounded-xl border border-dashed border-white/10 px-4 py-3 text-xs text-indigo-100/60">—</p>
                    )}
                  </div>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-rose-200">{t('score_stat_opponent')}</p>
                  <div className="mt-2 space-y-2">
                    {perStatOpponent.length ? (
                      perStatOpponent.map((stat) => (
                        <div key={stat.stat} className="flex items-center justify-between rounded-xl border border-rose-400/20 bg-rose-500/5 px-4 py-3">
                          <span className="text-sm font-semibold text-indigo-100">{stat.stat}</span>
                          <span className="text-base font-bold text-rose-300">-{formatScore(stat.value)}</span>
                        </div>
                      ))
                    ) : (
                      <p className="rounded-xl border border-dashed border-white/10 px-4 py-3 text-xs text-indigo-100/60">—</p>
                    )}
                  </div>
                </div>
              </div>
            ) : (
              <p className="mt-4 rounded-xl border border-dashed border-white/10 px-4 py-6 text-sm text-indigo-100/80">
                {t('score_result_empty')}
              </p>
            )}
          </div>
        </div>
      </section>
    </div>
  );
};

export default CalculatorView;
