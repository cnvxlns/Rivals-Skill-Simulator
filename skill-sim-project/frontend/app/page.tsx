'use client';

// 스킬 변경 시뮬레이터 UI를 구성하고 상태 훅을 연결하는 메인 페이지
import SkillCard from '../components/SkillCard';
import SkillSetEffect from '../components/SkillSetEffect';
import { CardType, Position, SubPosition, TicketType } from '../types';
import { useSkillSimulator } from '../lib/useSkillSimulator';

export default function Page() {
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
    canLockSlot1,
    isSlot1Locked,
    toggleLockSlot1,
    ticketUsageCounts,
    protectionUsageCount,
    resetUsageCounts,
  } = useSkillSimulator();

  const ticketLabels: Record<TicketType, string> = {
    [TicketType.SKILL_CHANGE]: 'Skill Change',
    [TicketType.PREMIUM_SKILL_CHANGE]: 'Advanced Skill Change',
    [TicketType.SUPREME_SKILL_CHANGE]: 'Superior Skill Change',
  };
  const pitcherSubPositions: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubPositions: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subPositionOptions = position === Position.PITCHER ? pitcherSubPositions : batterSubPositions;

  return (
    <main className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950 text-slate-50">
      <div className="mx-auto max-w-6xl px-6 py-10 space-y-8">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.3em] text-indigo-200">Rivals</p>
            <h1 className="text-4xl font-bold text-white">Skill Change Simulator</h1>
            <p className="text-sm text-indigo-100">Card-specific locking rules for Signature, Prime, and Moment.</p>
          </div>
          <div className="flex flex-col items-end gap-3">
            <div className="flex flex-wrap justify-end gap-2 text-xs font-semibold text-indigo-100">
              {Object.entries(ticketUsageCounts).map(([type, count]) => (
                <span key={type} className="rounded-full bg-white/10 px-3 py-1">
                  {ticketLabels[type as TicketType]}: {count}
                </span>
              ))}
              <span className="rounded-full bg-white/10 px-3 py-1">Protection used: {protectionUsageCount}</span>
              <button
                type="button"
                onClick={resetUsageCounts}
                className="rounded-full border border-white/20 px-3 py-1 text-indigo-50 transition hover:bg-white/10"
              >
                Reset
              </button>
            </div>
            <button
              onClick={roll}
              disabled={loading}
              className="rounded-lg bg-gradient-to-r from-indigo-500 to-sky-500 px-6 py-3 text-sm font-semibold text-white shadow-lg transition hover:from-indigo-600 hover:to-sky-600 disabled:cursor-not-allowed disabled:from-indigo-300 disabled:to-sky-300"
            >
              {loading ? 'Rolling...' : 'Roll new skills'}
            </button>
          </div>
        </header>

        <section className="grid items-start gap-6 lg:grid-cols-[360px_1fr]">
          <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-white">Roll settings</h2>
              <span className="rounded-full bg-indigo-500/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-indigo-100">
                Setup
              </span>
            </div>

            <div className="space-y-4">
              <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-semibold text-white">Card Type</h3>
                  <p className="text-[11px] text-indigo-100/80">Locking rules vary</p>
                </div>
                <p className="mt-1 text-xs text-indigo-100/70">Prime allows locking slot 1; Moment only if slot 1 is Moment tier.</p>
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
                <h3 className="text-sm font-semibold text-white">Ticket Type</h3>
                <p className="mt-1 text-xs text-indigo-100/70">Choose a ticket to influence the pool and odds.</p>
                <select
                  value={ticketType}
                  onChange={(e) => setTicketType(e.target.value as TicketType)}
                  className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                >
                  <option value={TicketType.SKILL_CHANGE}>Skill Change Ticket</option>
                  <option value={TicketType.PREMIUM_SKILL_CHANGE}>Advanced Skill Change Ticket</option>
                  <option value={TicketType.SUPREME_SKILL_CHANGE}>Superior Skill Change Ticket</option>
                </select>
              </div>

              <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                <h3 className="text-sm font-semibold text-white">Position</h3>
                <p className="mt-1 text-xs text-indigo-100/70">Only roll skills valid for the selected role and sub-role.</p>
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
                    <h4 className="text-xs font-semibold text-white">Sub-Position</h4>
                    <p className="text-[11px] text-indigo-100/70">Filters SP/RP/fielding spots</p>
                  </div>
                  <select
                    value={subPosition}
                    onChange={(e) => setSubPosition(e.target.value as SubPosition)}
                    className="mt-2 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                  >
                    {subPositionOptions.map((option) => (
                      <option key={option} value={option}>
                        {option === 'ALL' ? 'All Sub-Positions' : option}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {cardType === CardType.MOMENT && (
                <div className="rounded-xl border border-white/10 bg-white/5 p-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-white">Select Theme</h3>
                    <p className="text-[11px] text-indigo-100/80">Moment exclusive skill</p>
                  </div>
                  <p className="mt-1 text-xs text-indigo-100/70">Choose the Moment-tier skill to target for slot 1 rolls.</p>
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
                    <p className="mt-2 text-xs text-amber-100/80">No Moment tier skills found for this position.</p>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="space-y-4 rounded-2xl border border-white/10 bg-white/5 p-5 shadow-xl backdrop-blur">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs uppercase tracking-wide text-indigo-200">Slot details</p>
                <h2 className="text-xl font-semibold text-white">Skill Slots</h2>
              </div>
              <p className="text-xs text-indigo-100">Slot 1 lock obeys Prime/Moment rules.</p>
            </div>
            {error && <p className="mt-3 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-200">{error}</p>}
            {!slots.length && !error && <p className="mt-3 text-sm text-indigo-100/90">Press “Roll new skills” to populate your slots.</p>}

            <div className="mt-4 space-y-3">
              {Array.from({ length: slotCount }, (_, idx) => idx).map((idx) => {
                const slot = slots[idx];
                const isFirst = idx === 0;
                const protectionHidden = cardType === CardType.MOMENT;
                return (
                  <div key={`slot-${idx}`} className="rounded-2xl border border-indigo-100/60 bg-white p-3 shadow-lg">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="text-base font-semibold text-slate-900">Slot {idx + 1}</p>
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
                          {isSlot1Locked ? 'Locked' : 'Lock slot 1'}
                        </button>
                      ) : (
                        <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600">Unlocked</span>
                      )}
                    </div>

                    <div className="mt-3 flex items-stretch gap-3">
                      <div className="flex-1">
                        {slot ? (
                          <SkillCard skillSlot={slot} slotNumber={idx + 1} className="border-indigo-100 shadow-md" />
                        ) : (
                          <div className="flex h-full min-h-[80px] items-center justify-center rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-500">
                            No skill yet. Roll to populate.
                          </div>
                        )}
                      </div>

                      <label
                        className={`flex w-40 shrink-0 items-center justify-between gap-2 rounded-xl border border-indigo-100 bg-indigo-50 px-3 py-3 text-[13px] font-semibold uppercase tracking-wide text-indigo-800 shadow-inner ${
                          protectionHidden ? 'invisible' : ''
                        }`}
                        aria-hidden={protectionHidden}
                      >
                        <span>Protect</span>
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
      </div>

      <footer className="border-t border-white/10 bg-slate-950/80 text-slate-200">
        <div className="mx-auto max-w-6xl px-6 py-6 text-xs leading-relaxed">
          This project is an unofficial fan-made application and is not affiliated with, endorsed, sponsored, or specifically approved by Com2uS Corp., MLB, or MLB Players Inc. All game data, skill names, and intellectual property are the sole property of their respective owners. This tool is intended for educational and portfolio purposes only.
        </div>
      </footer>
    </main>
  );
}
