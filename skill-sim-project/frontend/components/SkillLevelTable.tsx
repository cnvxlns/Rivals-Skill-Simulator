import { useEffect, useMemo, useState } from 'react';
import { Grade } from '../types';

type Props = {
  levelEffects?: Record<string, string> | null;
  currentLevel?: Grade | string | null;
  className?: string;
};

const LEVEL_ORDER = ['D', 'C', 'B', 'A', 'S', 'S1', 'S2', 'S3', 'S4'];

const badgeBase =
  'flex-1 rounded-lg border px-3 py-2 text-center text-xs font-semibold uppercase tracking-wide transition hover:-translate-y-[1px] hover:shadow';

const SkillLevelTable = ({ levelEffects, currentLevel, className }: Props) => {
  const levelEntries = useMemo(() => {
    const effective = levelEffects ?? {};
    const filtered = LEVEL_ORDER.filter((lvl) => effective[lvl]);
    return filtered.map((lvl) => ({ level: lvl, text: effective[lvl] }));
  }, [levelEffects]);

  const initialLevel = useMemo(() => {
    const normalizedCurrent = currentLevel ? String(currentLevel).toUpperCase() : null;
    if (normalizedCurrent && levelEntries.some((item) => item.level === normalizedCurrent)) {
      return normalizedCurrent;
    }
    return levelEntries[0]?.level ?? null;
  }, [currentLevel, levelEntries]);

  const [selectedLevel, setSelectedLevel] = useState<string | null>(initialLevel);

  useEffect(() => {
    setSelectedLevel(initialLevel);
  }, [initialLevel]);

  if (!levelEntries.length) {
    return null;
  }

  const selectedText = levelEntries.find((item) => item.level === selectedLevel)?.text ?? levelEntries[0].text;

  return (
    <div className={`rounded-xl border border-indigo-100 bg-white/90 p-3 shadow-inner ${className ?? ''}`}>
      <div className="flex flex-wrap gap-2">
        {levelEntries.map(({ level }) => {
          const isActive = selectedLevel === level;
          const isCurrent = currentLevel && String(currentLevel).toUpperCase() === level;
          return (
            <button
              key={level}
              type="button"
              onClick={() => setSelectedLevel(level)}
              className={`${badgeBase} ${
                isActive
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-800'
                  : 'border-slate-200 bg-slate-50 text-slate-700'
              } ${isCurrent ? 'ring-2 ring-indigo-300 ring-offset-2 ring-offset-white' : ''}`}
            >
              {level}
            </button>
          );
        })}
      </div>

      <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50/70 p-3 text-sm text-slate-900">
        <p className="text-[11px] uppercase tracking-wide text-slate-500">Effect</p>
        <p className="mt-1 whitespace-pre-line leading-relaxed">{selectedText}</p>
      </div>
    </div>
  );
};

export default SkillLevelTable;
