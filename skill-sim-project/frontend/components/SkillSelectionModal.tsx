import { Position, SkillSlot } from '../types';
import SkillCard from './SkillCard';
import SkillSetEffect from './SkillSetEffect';
import { useEffect, useState } from 'react';

type Props = {
  isOpen: boolean;
  currentSlots: SkillSlot[];
  candidateSlots: SkillSlot[] | null;
  position?: Position | string | null;
  onKeepCurrent: () => void;
  onSelectNew: () => void;
};

const SkillSelectionModal = ({ isOpen, currentSlots, candidateSlots, position, onKeepCurrent, onSelectNew }: Props) => {
  if (!isOpen || !candidateSlots) return null;

  const [selectedSet, setSelectedSet] = useState<'current' | 'new' | null>(null);

  useEffect(() => {
    if (isOpen) {
      setSelectedSet(null);
    }
  }, [isOpen, candidateSlots]);

  const totalSlots = Math.max(currentSlots?.length ?? 0, candidateSlots.length) || 3;

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
      className="group inline-flex items-center gap-3 rounded-full border border-white/15 bg-white/5 px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-indigo-50 shadow-inner transition hover:border-indigo-300/60 hover:bg-indigo-500/10"
      aria-pressed={checked}
    >
      <span
        className={`flex h-5 w-5 items-center justify-center rounded-md border text-[12px] font-bold transition ${
          checked
            ? 'border-emerald-400 bg-emerald-500 text-slate-900 shadow-[0_0_0_3px_rgba(16,185,129,0.25)]'
            : 'border-white/50 bg-white/5 text-transparent'
        }`}
      >
        ✓
      </span>
      <span className="text-indigo-50">{label}</span>
    </button>
  );

  const handleConfirm = () => {
    if (selectedSet === 'current') {
      onKeepCurrent();
    } else if (selectedSet === 'new') {
      onSelectNew();
    }
  };

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
          <div className="rounded-2xl border border-white/10 bg-white/5 p-4 shadow-inner">
            <div className="mb-3 flex items-center justify-between gap-2">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-indigo-200">Before</p>
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-white/10 px-3 py-1 text-xs font-semibold text-indigo-100">Current</span>
                {renderSelector(selectedSet === 'current', 'Select this set', () => setSelectedSet('current'))}
              </div>
            </div>
            <div className="space-y-3">
              {Array.from({ length: totalSlots }, (_, idx) => (
                <div key={`current-${idx}`}>{renderSlot(currentSlots[idx], idx + 1)}</div>
              ))}
            </div>
            <SkillSetEffect slots={currentSlots} position={position} className="mt-4" />
          </div>

          <div className="rounded-2xl border border-indigo-300/50 bg-indigo-900/30 p-4 shadow-[0_20px_60px_rgba(59,130,246,0.25)]">
            <div className="mb-3 flex items-center justify-between gap-2">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-indigo-100">After</p>
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-indigo-500/20 px-3 py-1 text-xs font-semibold text-indigo-50 shadow-sm">New</span>
                {renderSelector(selectedSet === 'new', 'Select this set', () => setSelectedSet('new'))}
              </div>
            </div>
            <div className="space-y-3">
              {Array.from({ length: totalSlots }, (_, idx) => (
                <div key={`candidate-${idx}`}>{renderSlot(candidateSlots[idx], idx + 1, true)}</div>
              ))}
            </div>
            <SkillSetEffect slots={candidateSlots} position={position} className="mt-4" />
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/5 p-4 text-sm text-indigo-100/90 md:flex-row md:items-center md:justify-between">
          <p className="max-w-2xl leading-relaxed">
            Choosing either option will still consume the ticket you just used. Keep the current set to retain your existing skills, or change to the
            new set to adopt everything you rolled.
          </p>
          <div className="flex flex-1 items-center justify-end gap-3">
            <button
              type="button"
              onClick={onKeepCurrent}
              className="min-w-[120px] rounded-xl border border-white/20 bg-white/10 px-4 py-3 text-sm font-semibold text-indigo-50 transition hover:bg-white/20"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={!selectedSet}
              className="min-w-[150px] rounded-xl bg-gradient-to-r from-emerald-500 to-lime-500 px-5 py-3 text-sm font-semibold text-slate-900 shadow-lg transition hover:from-emerald-600 hover:to-lime-600 disabled:cursor-not-allowed disabled:from-slate-600 disabled:to-slate-600 disabled:text-slate-300"
            >
              Confirm Selection
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SkillSelectionModal;
