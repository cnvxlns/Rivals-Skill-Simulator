// 하나의 스킬 슬롯 정보를 카드 형태로 표시하는 프레젠테이션 컴포넌트
import { Grade, SkillSlot, Tier } from '../types';

type Props = {
  skillSlot: SkillSlot;
  slotNumber: number;
  className?: string;
};

const tierBorderClasses: Record<Tier, string> = {
  [Tier.MOMENT]: 'border-emerald-400',
  [Tier.IRON]: 'border-slate-300',
  [Tier.BRONZE]: 'border-amber-300',
  [Tier.SILVER]: 'border-gray-300',
  [Tier.GOLD]: 'border-yellow-400',
};

const tierTextClasses: Record<Tier, string> = {
  [Tier.MOMENT]: 'text-emerald-700',
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
  const tierBorder = tierBorderClasses[skill.tier] ?? 'border-slate-200';
  const tierText = tierTextClasses[skill.tier] ?? 'text-slate-700';
  const hideTierText = [Tier.BRONZE, Tier.SILVER, Tier.GOLD, Tier.IRON].includes(skill.tier);
  const tierLabel = hideTierText ? '' : skill.tier;
  const gradeStyle = gradeClasses[grade] ?? 'bg-slate-100 text-slate-700';

  return (
    <div
      title={skill.description}
      className={`rounded-xl border bg-white/95 p-4 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md ${tierBorder} ${className ?? ''}`}
    >
      <div className="flex items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Slot {slotNumber}</p>
          <h3 className="text-lg font-semibold text-slate-900">{skill.name}</h3>
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
    </div>
  );
};

export default SkillCard;
