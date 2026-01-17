import { useEffect, useState } from 'react';
import { Position, SkillSlot } from '../types';
import SkillCard from './SkillCard';
import SkillSetEffect from './SkillSetEffect';

type Props = {
  isOpen: boolean;
  currentSlots: SkillSlot[];
  candidateSkills: SkillSlot[] | null;
  position?: Position | string | null;
  onKeepCurrent: () => void;
  onSelectNew: () => void;
};

const SkillSelectionModal = ({ isOpen, currentSlots, candidateSkills, position, onKeepCurrent, onSelectNew }: Props) => {
  if (!isOpen || !candidateSkills) return null;

  const [selectedSet, setSelectedSet] = useState<'current' | 'new' | null>(null);

  useEffect(() => {
    if (isOpen) {
      setSelectedSet(null);
    }
  }, [isOpen, candidateSkills]);

  const totalSlots = Math.max(currentSlots?.length ?? 0, candidateSkills.length) || 3;
  const isCurrentSelected = selectedSet === 'current';
  const isNewSelected = selectedSet === 'new';

  const renderSlot = (slot: SkillSlot | undefined, slotNumber: number, accent?: boolean) => {
    if (slot) {
      return (
        <SkillCard
          skillSlot={slot}
          slotNumber={slotNumber}
          className={accent ? 'border-indigo-200 shadow-md shadow-indigo-900/10' : 'border-white/30'}
        />
      );
    }

    return (
      <div className="flex h-full min-h-[88px] items-center justify-center rounded-xl border border-dashed border-white/20 bg-white/5 px-3 py-3 text-sm text-indigo-50/80">
        No skill
      </div>
    );
  };

  const renderSelector = (checked: boolean, label: string, onClick: () => void) => (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={checked}
      className={`inline-flex items-center gap-2 rounded-full border px-3 py-2 text-xs font-semibold uppercase tracking-wide transition ${
        checked
          ? 'border-emerald-400 bg-emerald-500/20 text-emerald-100 shadow-[0_0_0_3px_rgba(52,211,153,0.15)]'
          : 'border-white/20 bg-white/5 text-indigo-100 hover:border-emerald-300/60 hover:bg-emerald-500/10'
      }`}
    >
      <span
        className={`flex h-5 w-5 items-center justify-center rounded-md border text-[12px] font-bold ${
          checked ? 'border-emerald-400 bg-emerald-300 text-emerald-800' : 'border-white/40 bg-white/10 text-transparent'
        }`}
      >
        ✓
      </span>
      <span>{label}</span>
    </button>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/80 p-4 backdrop-blur">
      <div className="w-full max-w-6xl rounded-3xl border border-white/10 bg-gradient-to-br from-slate-900 via-slate-900 to-indigo-950 p-6 shadow-2xl">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.32em] text-indigo-200">Compare Rolls</p>
            <h2 className="text-2xl font-bold text-white">Choose your skill set</h2>
            <p className="text-sm text-indigo-100/80">High-tier tickets let you decide between the existing set and the freshly rolled one.</p>
          </div>
          <button
            type="button"
            onClick={onKeepCurrent}
            className="rounded-full border border-white/20 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-indigo-100 transition hover:bg-white/10"
          >
            Close
          </button>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-2">
          <div
            className={`rounded-2xl border bg-white/5 p-4 shadow-inner transition ${
              isCurrentSelected ? 'border-emerald-300/60 ring-2 ring-emerald-400/40' : 'border-white/10'
            }`}
          >
            <div className="mb-3 flex items-center justify-between gap-2">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-indigo-200">Before</p>
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-100">Current</span>
                {renderSelector(isCurrentSelected, 'Choose', () => setSelectedSet('current'))}
              </div>
            </div>
            <div className="space-y-3">
              {Array.from({ length: totalSlots }, (_, idx) => (
                <div key={`current-${idx}`}>{renderSlot(currentSlots[idx], idx + 1)}</div>
              ))}
            </div>
            <SkillSetEffect slots={currentSlots} position={position} className="mt-4" />
          </div>

          <div
            className={`rounded-2xl border bg-indigo-900/30 p-4 shadow-[0_20px_60px_rgba(59,130,246,0.25)] transition ${
              isNewSelected ? 'border-emerald-300/60 ring-2 ring-emerald-400/40' : 'border-indigo-300/50'
            }`}
          >
            <div className="mb-3 flex items-center justify-between gap-2">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-indigo-100">After</p>
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-indigo-500/20 px-3 py-1 text-xs font-semibold text-indigo-50 shadow-sm">New</span>
                {renderSelector(isNewSelected, 'Choose', () => setSelectedSet('new'))}
              </div>
            </div>
            <div className="space-y-3">
              {Array.from({ length: totalSlots }, (_, idx) => (
                <div key={`candidate-${idx}`}>{renderSlot(candidateSkills[idx], idx + 1, true)}</div>
              ))}
            </div>
            <SkillSetEffect slots={candidateSkills} position={position} className="mt-4" />
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/5 p-4 text-sm text-indigo-100/90 md:flex-row md:items-center md:justify-between">
          <p className="max-w-2xl leading-relaxed">
            Advanced and Superior tickets let you compare rolls before committing. Keeping the current set discards the newly rolled skills, while
            changing to the new set will overwrite all current slots with the right-side results.
          </p>
          <div className="flex flex-1 items-center justify-end gap-3">
            <button
              type="button"
              onClick={onKeepCurrent}
              disabled={!isCurrentSelected}
              className="min-w-[140px] rounded-xl border border-white/15 bg-white/5 px-5 py-3 text-sm font-semibold text-indigo-50 transition hover:bg-white/15 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Keep Current
            </button>
            <button
              type="button"
              onClick={onSelectNew}
              disabled={!isNewSelected}
              className="min-w-[150px] rounded-xl bg-gradient-to-r from-sky-500 to-indigo-500 px-5 py-3 text-sm font-semibold text-white shadow-lg transition hover:from-sky-600 hover:to-indigo-600 disabled:cursor-not-allowed disabled:from-slate-600 disabled:to-slate-600 disabled:text-slate-300"
            >
              Change to New
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SkillSelectionModal;
