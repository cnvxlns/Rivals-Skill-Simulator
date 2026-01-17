// 하나의 스킬 슬롯 정보를 카드 형태로 표시하는 프레젠테이션 컴포넌트
import { Grade, SkillSlot, Tier } from '../types';

type Props = {
  skillSlot: SkillSlot;
  slotNumber: number;
  className?: string;
};

const tierBorderClasses: Record<Tier, string> = {
  [Tier.BLACK]: 'border-black shadow-[0_0_12px_rgba(76,29,149,0.45)]',
  [Tier.MOMENT]: 'border-emerald-500',
  [Tier.HOF]: 'border-red-600',
  [Tier.IRON]: 'border-slate-300',
  [Tier.BRONZE]: 'border-amber-300',
  [Tier.SILVER]: 'border-gray-300',
  [Tier.GOLD]: 'border-yellow-400',
};

const tierTextClasses: Record<Tier, string> = {
  [Tier.BLACK]: 'text-white bg-slate-900 shadow-[0_0_8px_rgba(76,29,149,0.5)]',
  [Tier.MOMENT]: 'text-emerald-700',
  [Tier.HOF]: 'text-red-700',
  [Tier.IRON]: 'text-slate-700',
  [Tier.BRONZE]: 'text-amber-800',
  [Tier.SILVER]: 'text-gray-700',
  [Tier.GOLD]: 'text-yellow-700',
};

const gradeClasses: Record<Grade, string> = {
  [Grade.S]: 'bg-yellow-100 text-yellow-800',
  [Grade.A]: 'bg-blue-100 text-blue-800',
  [Grade.B]: 'bg-emerald-100 text-emerald-800',
  [Grade.C]: 'bg-slate-100 text-slate-700',
  [Grade.D]: 'bg-slate-100 text-slate-700',
};

const SkillCard = ({ skillSlot, slotNumber, className }: Props) => {
  const { skill, grade } = skillSlot;

  if (!skill) {
    return (
      <div
        className={`rounded-xl border border-slate-200 bg-white/95 p-4 shadow-sm ${className ?? ''}`}
        title="Skill not available"
      >
        <div className="flex items-center justify-between gap-2">
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">Slot {slotNumber}</p>
            <h3 className="text-lg font-semibold text-slate-900">No skill</h3>
          </div>
          <span className="rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-wide bg-slate-100 text-slate-700">
            Grade {grade}
          </span>
        </div>
      </div>
    );
  }

  const tierBorder = tierBorderClasses[skill.tier] ?? 'border-slate-200';
  const tierText = tierTextClasses[skill.tier] ?? 'text-slate-700';
  const hideTierText = [Tier.BRONZE, Tier.SILVER, Tier.GOLD, Tier.IRON].includes(skill.tier);
  const tierLabel = hideTierText ? '' : skill.tier;
  const gradeStyle = gradeClasses[grade] ?? 'bg-slate-100 text-slate-700';
  const isBlackTier = skill.tier === Tier.BLACK;
  const containerTone = isBlackTier ? 'bg-slate-950 text-slate-50 shadow-[0_0_20px_rgba(76,29,149,0.5)]' : 'bg-white/95 text-slate-900';
  const slotLabelTone = isBlackTier ? 'text-indigo-100/80' : 'text-slate-500';
  const hasEffectDetails = (skill.effects?.length ?? 0) > 0;

  return (
    <div
      className={`rounded-xl border p-4 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md ${containerTone} ${tierBorder} ${className ?? ''}`}
    >
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className={`text-xs uppercase tracking-wide ${slotLabelTone}`}>Slot {slotNumber}</p>
          <h3 className="text-lg font-semibold">{skill.name}</h3>
        </div>
        <div className="flex flex-col items-end gap-1">
          {!hideTierText && (
            <span
              aria-label={skill.tier}
              className={`rounded-full border px-3 py-1 text-[11px] font-semibold uppercase tracking-wide ${tierBorder} ${tierText}`}
            >
              {tierLabel}
            </span>
          )}
          <span className={`rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-wide ${gradeStyle}`}>
            Grade {grade}
          </span>
        </div>
      </div>

      {hasEffectDetails && (
        <div className="mt-3 rounded-lg border border-indigo-100/80 bg-indigo-50/80 p-3 text-sm text-slate-800 shadow-inner">
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700">Skill Effects</p>
            <span className="text-[11px] font-semibold uppercase tracking-wide text-indigo-700">Details</span>
          </div>
          <div className="mt-2 space-y-1">
            {skill.effects?.map((effect, idx) => (
              <div key={`${skill.name}-effect-${idx}`} className="flex items-start gap-2 text-xs text-indigo-800">
                <span className="rounded-full bg-indigo-100 px-2 py-1 font-semibold text-indigo-800">{effect.condition || 'ALWAYS'}</span>
                <div>
                  <p className="font-semibold">{effect.logic || 'Logic N/A'}</p>
                  {effect.description && <p className="text-[11px] text-slate-700">{effect.description}</p>}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default SkillCard;
