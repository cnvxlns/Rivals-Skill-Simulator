'use client';

// Encapsulates the existing Skill Change Simulator UI and state management
import SkillCard from '../components/SkillCard';
import SkillSetEffect from '../components/SkillSetEffect';
import SkillSelectionModal from '../components/SkillSelectionModal';
import { CardType, Position, SubPosition, TicketType } from '../types';
import { useSkillSimulator } from '../lib/useSkillSimulator';
import { useTranslation } from '../lib/i18n';

const SimulatorView = () => {
  const {
    cardType,
    setCardType,
    ticketType,
    setTicketType,
    slotCount,
    position,
    setPosition,
    subPosition,
    setSubPosition,
    availableThemes,
    selectedTheme,
    setSelectedTheme,
    useLevelProtectionSlots,
    toggleLevelProtection,
    slots,
    loading,
    error,
    roll,
    candidateSkills,
    isSelectionModalOpen,
    keepCurrentSkills,
    applyCandidateSkills,
    canLockSlot1,
    isSlot1Locked,
    toggleLockSlot1,
    ticketUsageCounts,
    protectionUsageCount,
    resetUsageCounts,
  } = useSkillSimulator();
  const { t } = useTranslation();

  const ticketLabels: Record<TicketType, string> = {
    [TicketType.SKILL_CHANGE]: t('ticket_skill_change'),
    [TicketType.PREMIUM_SKILL_CHANGE]: t('ticket_premium'),
    [TicketType.SUPREME_SKILL_CHANGE]: t('ticket_supreme'),
  };
  const pitcherSubPositions: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubPositions: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subPositionOptions = position === Position.PITCHER ? pitcherSubPositions : batterSubPositions;

  return (
    <>
      <div className="space-y-8 pb-24">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-indigo-200">Rivals</p>
            <h1 className="text-4xl font-bold text-white">{t('hdr_title')}</h1>
            <p className="text-sm text-indigo-100">{t('hdr_subtitle')}</p>
          </div>
          <div className="flex flex-col items-end gap-3">
            <div className="flex flex-wrap justify-end gap-2 text-xs font-semibold text-indigo-100">
              {Object.entries(ticketUsageCounts).map(([type, count]) => (
                <span key={type} className="rounded-full bg-white/10 px-3 py-1">
                  {ticketLabels[type as TicketType]}: {count}
                </span>
              ))}
              <span className="rounded-full bg-white/10 px-3 py-1">{t('protection_used')}: {protectionUsageCount}</span>
              <button
                type="button"
                onClick={resetUsageCounts}
                className="rounded-full border border-white/20 px-3 py-1 text-indigo-50 transition hover:bg-white/10"
              >
                {t('btn_reset')}
              </button>
            </div>
          </div>
        </header>

        <section className="grid items-start gap-6 lg:grid-cols-[360px_1fr]">
          <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-white">{t('section_roll_settings')}</h2>
              <span className="rounded-full bg-indigo-500/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-indigo-100">
                {t('badge_setup')}
              </span>
            </div>

          <div className="space-y-4">
            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-white">{t('label_card_type')}</h3>
              </div>
              <p className="mt-1 text-xs text-indigo-100/70">{t('body_card_type')}</p>
              <select
                value={cardType}
                onChange={(e) => setCardType(e.target.value as CardType)}
                className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              >
                <option value={CardType.SIGNATURE}>Signature</option>
                <option value={CardType.SIGNATURE_BLACK}>Signature Black</option>
                <option value={CardType.PRIME}>Prime</option>
                <option value={CardType.HOF}>HOF</option>
                <option value={CardType.MOMENT}>Moment</option>
              </select>
            </div>

            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                <h3 className="text-sm font-semibold text-white">{t('label_ticket_type')}</h3>
                <p className="mt-1 text-xs text-indigo-100/70">{t('hint_ticket_type')}</p>
                <select
                  value={ticketType}
                  onChange={(e) => setTicketType(e.target.value as TicketType)}
                  className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                >
                  <option value={TicketType.SKILL_CHANGE}>{t('ticket_skill_change')}</option>
                  <option value={TicketType.PREMIUM_SKILL_CHANGE}>{t('ticket_premium')}</option>
                  <option value={TicketType.SUPREME_SKILL_CHANGE}>{t('ticket_supreme')}</option>
                </select>
              </div>

            <div className="rounded-xl border border-white/10 bg-white/5 p-4">
              <h3 className="text-sm font-semibold text-white">{t('label_position')}</h3>
              <p className="mt-1 text-xs text-indigo-100/70">{t('hint_position')}</p>
              <select
                value={position ?? ''}
                onChange={(e) => setPosition(e.target.value as Position)}
                className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              >
                <option value={Position.PITCHER}>Pitcher</option>
                <option value={Position.BATTER}>Batter</option>
              </select>
              <div className="mt-4">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-semibold text-white">{t('label_sub_position')}</h4>
                  <p className="text-[11px] text-indigo-100/70">{t('hint_sub_position')}</p>
                </div>
                <select
                  value={subPosition}
                  onChange={(e) => setSubPosition(e.target.value as SubPosition)}
                  className="mt-2 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                >
                  {subPositionOptions.map((option) => (
                    <option key={option} value={option}>
                    {option === 'ALL' ? t('option_all_sub_positions') : option}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {cardType === CardType.MOMENT && (
              <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-white">{t('label_theme')}</h3>
                  <p className="text-[11px] text-indigo-100/80">{t('hint_theme')}</p>
                </div>
                <p className="mt-1 text-xs text-indigo-100/70">{t('hint_theme')}</p>
                <select
                  value={selectedTheme ?? ''}
                  onChange={(e) => setSelectedTheme(e.target.value)}
                  disabled={!availableThemes.length}
                  className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400 disabled:cursor-not-allowed"
                >
                  {availableThemes.map((theme) => (
                    <option key={theme} value={theme}>
                      {theme}
                    </option>
                  ))}
                </select>
                  {!availableThemes.length && (
                    <p className="mt-2 text-xs text-amber-100/80">{t('no_theme')}</p>
                  )}
              </div>
            )}
          </div>
        </div>

        <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-indigo-200">{t('slot_details')}</p>
              <h2 className="text-xl font-semibold text-white">{t('slot_details')}</h2>
            </div>
            <p className="text-xs text-indigo-100">{t('slot_lock_rule_hint')}</p>
          </div>
          {error && <p className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-200">{error}</p>}
          {!slots.length && !error && <p className="mt-3 text-sm text-indigo-100/90">{t('empty_slots_hint')}</p>}

          <div className="mt-4 space-y-3">
            {Array.from({ length: slotCount }, (_, idx) => idx).map((idx) => {
              const slot = slots[idx];
              const isFirst = idx === 0;
              const protectionHidden = cardType === CardType.MOMENT;
              return (
                <div key={`slot-${idx}`} className="rounded-2xl border border-indigo-100/60 bg-white p-3 shadow-lg">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-base font-semibold text-slate-900">{t('slot_label')} {idx + 1}</p>
                    </div>

                    {isFirst ? (
                      <button
                        type="button"
                        onClick={toggleLockSlot1}
                        disabled={!canLockSlot1}
                        className={`flex items-center gap-2 rounded-full px-3 py-1 text-xs font-semibold ${
                          isSlot1Locked ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'
                        } ${!canLockSlot1 ? 'cursor-not-allowed opacity-60' : 'hover:shadow-sm'}`}
                      >
                        <span>{isSlot1Locked ? '🔒' : '🔓'}</span>
                        {isSlot1Locked ? t('locked') : t('lock_slot1')}
                      </button>
                    ) : (
                      <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">{t('unlocked')}</span>
                    )}
                  </div>

                  <div className="mt-3 flex items-stretch gap-3">
                    <div className="flex-1">
                      {slot ? (
                        <SkillCard skillSlot={slot} slotNumber={idx + 1} className="border-indigo-100 shadow-md" />
                      ) : (
                        <div className="flex h-full min-h-[80px] items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-500">
                          {t('no_skill_cta')}
                        </div>
                      )}
                    </div>

                    <label
                      className={`flex w-40 shrink-0 items-center justify-between gap-2 rounded-xl border border-indigo-100 bg-indigo-50 px-3 py-3 text-[13px] font-semibold uppercase tracking-wide text-indigo-800 shadow-inner ${
                        protectionHidden ? 'invisible' : ''
                      }`}
                      aria-hidden={protectionHidden}
                    >
                      <span>{t('protect_label')}</span>
                      <input
                        type="checkbox"
                        checked={useLevelProtectionSlots[idx] ?? false}
                        onChange={() => toggleLevelProtection(idx)}
                        disabled={protectionHidden}
                        className="h-4 w-4 text-indigo-600"
                      />
                    </label>
                  </div>
                </div>
              );
            })}
          </div>

          <SkillSetEffect slots={slots} position={position} className="mt-6" />
        </div>
        </section>

        <div className="fixed bottom-0 left-0 right-0 z-30 border-t border-slate-700 bg-slate-900/90 p-4 backdrop-blur">
          <div className="mx-auto w-11/12 max-w-md">
            <button
              onClick={roll}
              disabled={loading}
              className="w-full rounded-xl bg-gradient-to-r from-indigo-500 to-sky-500 px-6 py-3 text-lg font-bold text-white shadow-lg transition hover:from-indigo-600 hover:to-sky-600 disabled:cursor-not-allowed disabled:from-indigo-300 disabled:to-sky-300"
            >
              {loading ? `${t('btn_roll')}...` : t('btn_roll')}
            </button>
          </div>
        </div>
      </div>

      <SkillSelectionModal
        isOpen={isSelectionModalOpen}
        currentSlots={slots}
        candidateSkills={candidateSkills}
        position={position}
        onKeepCurrent={keepCurrentSkills}
        onSelectNew={applyCandidateSkills}
      />
    </>
  );
};

export default SimulatorView;
