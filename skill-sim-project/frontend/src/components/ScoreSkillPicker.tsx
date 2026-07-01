'use client';

import { Search, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { ScoreSkillOption } from '../types';

type ScoreSkillPickerProps = {
  slotIndex: number;
  label: string;
  skills: ScoreSkillOption[];
  selectedSkillId: string;
  selectedSkillIds: string[];
  level: number;
  disabled?: boolean;
  searchLabel: string;
  selectLabel: string;
  levelLabel: string;
  clearLabel: string;
  noSkillsLabel: string;
  onSkillChange: (slotIndex: number, skillId: string) => void;
  onLevelChange: (slotIndex: number, level: number) => void;
  onClear: (slotIndex: number) => void;
};

const ScoreSkillPicker = ({
  slotIndex,
  label,
  skills,
  selectedSkillId,
  selectedSkillIds,
  level,
  disabled = false,
  searchLabel,
  selectLabel,
  levelLabel,
  clearLabel,
  noSkillsLabel,
  onSkillChange,
  onLevelChange,
  onClear,
}: ScoreSkillPickerProps) => {
  const [query, setQuery] = useState('');
  const selectedSkill = skills.find((skill) => skill.skillId === selectedSkillId) ?? null;
  const unavailableIds = new Set(selectedSkillIds.filter((id) => id && id !== selectedSkillId));

  const filteredSkills = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return skills.filter((skill) => {
      if (unavailableIds.has(skill.skillId)) return false;
      if (!normalizedQuery) return true;
      return skill.name.toLowerCase().includes(normalizedQuery);
    });
  }, [query, skills, unavailableIds]);

  const maxLevel = selectedSkill?.maxLevel ?? 0;

  return (
    <div className="rounded-2xl border border-white/10 bg-slate-950/30 p-4 shadow-lg">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-indigo-200">{label}</p>
          <p className="mt-1 min-h-5 text-sm font-semibold text-white" title={selectedSkill?.description ?? selectedSkill?.name ?? ''}>
            {selectedSkill?.name ?? selectLabel}
          </p>
        </div>
        <button
          type="button"
          onClick={() => onClear(slotIndex)}
          disabled={disabled || !selectedSkillId}
          aria-label={clearLabel}
          title={clearLabel}
          className="grid h-9 w-9 shrink-0 place-items-center rounded-full border border-white/15 text-indigo-100 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <X size={16} />
        </button>
      </div>

      <label className="mt-4 block">
        <span className="sr-only">{searchLabel}</span>
        <span className="flex items-center gap-2 rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-slate-900 focus-within:border-indigo-500 focus-within:ring-2 focus-within:ring-indigo-400">
          <Search size={16} className="shrink-0 text-slate-500" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={searchLabel}
            disabled={disabled}
            className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-slate-500 disabled:cursor-not-allowed"
          />
        </span>
      </label>

      <select
        value={selectedSkillId}
        onChange={(event) => onSkillChange(slotIndex, event.target.value)}
        disabled={disabled || !filteredSkills.length}
        className="mt-3 w-full rounded-lg border border-indigo-200/60 bg-white/90 px-3 py-2 text-sm text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400 disabled:cursor-not-allowed disabled:opacity-70"
      >
        <option value="">{filteredSkills.length ? selectLabel : noSkillsLabel}</option>
        {filteredSkills.map((skill) => (
          <option key={skill.skillId} value={skill.skillId} title={skill.description ?? skill.name}>
            {skill.name}
          </option>
        ))}
      </select>

      <label className="mt-3 flex items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/5 px-3 py-2">
        <span className="text-sm font-semibold text-indigo-100">{levelLabel}</span>
        <select
          value={selectedSkill ? Math.min(level, maxLevel) : ''}
          onChange={(event) => onLevelChange(slotIndex, Number(event.target.value))}
          disabled={disabled || !selectedSkillId}
          className="w-24 rounded-md border border-indigo-200/60 bg-white/90 px-2 py-1 text-sm font-semibold text-slate-900 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-400 disabled:cursor-not-allowed disabled:opacity-70"
        >
          {!selectedSkill && <option value="">{levelLabel}</option>}
          {Array.from({ length: maxLevel }, (_, idx) => idx + 1).map((value) => (
            <option key={value} value={value}>
              {selectedSkill?.levelLabels?.[value - 1] ?? String(value)}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
};

export default ScoreSkillPicker;
