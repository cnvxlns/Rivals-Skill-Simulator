import { Grade, Position, SkillSlot } from '../types';

type Props = {
  slots: SkillSlot[];
  position?: Position | string | null;
  className?: string;
};

const gradePriority: Record<Grade, number> = {
  [Grade.S4]: 9,
  [Grade.S3]: 8,
  [Grade.S2]: 7,
  [Grade.S1]: 6,
  [Grade.S]: 5,
  [Grade.A]: 4,
  [Grade.B]: 3,
  [Grade.C]: 2,
  [Grade.D]: 1,
};

const gradeBadgeStyles: Record<Grade, string> = {
  [Grade.S4]: 'bg-fuchsia-500 text-white',
  [Grade.S3]: 'bg-purple-500 text-white',
  [Grade.S2]: 'bg-amber-500 text-slate-900',
  [Grade.S1]: 'bg-orange-500 text-slate-900',
  [Grade.S]: 'bg-yellow-500 text-slate-900',
  [Grade.A]: 'bg-blue-500 text-white',
  [Grade.B]: 'bg-green-500 text-white',
  [Grade.C]: 'bg-gray-500 text-white',
  [Grade.D]: 'bg-gray-600 text-white',
};

const bannerStyles: Record<Grade, string> = {
  [Grade.S4]: 'bg-fuchsia-950/30 border-fuchsia-500 text-fuchsia-300',
  [Grade.S3]: 'bg-purple-950/30 border-purple-500 text-purple-300',
  [Grade.S2]: 'bg-amber-950/30 border-amber-500 text-amber-300',
  [Grade.S1]: 'bg-orange-950/30 border-orange-500 text-orange-300',
  [Grade.S]: 'bg-yellow-950/30 border-yellow-500 text-yellow-400',
  [Grade.A]: 'bg-blue-950/30 border-blue-500 text-blue-400',
  [Grade.B]: 'bg-green-950/30 border-green-500 text-green-400',
  [Grade.C]: 'bg-gray-800/50 border-gray-500 text-gray-300',
  [Grade.D]: 'bg-gray-800/50 border-gray-600 text-gray-300',
};

function pickMinimumGrade(slots: SkillSlot[]): Grade {
  let min: Grade = Grade.S;
  if (!slots?.length) return Grade.D;
  slots.forEach((slot) => {
    const g = slot?.grade ?? Grade.D;
    if (gradePriority[g] < gradePriority[min]) {
      min = g;
    }
  });
  return min;
}

function effectTextByGrade(grade: Grade, position?: Position | string | null): string {
  const pos = String(position ?? '').toUpperCase();
  switch (grade) {
    case Grade.S4:
    case Grade.S3:
    case Grade.S2:
    case Grade.S1:
    case Grade.S:
      return 'All Stats +3';
    case Grade.A:
      return 'All Stats +1';
    case Grade.B:
      return pos === Position.BATTER ? 'POW +1, CON +1, PAT +1' : 'MOV +1, STU +1, CTRL +1';
    case Grade.C:
      return pos === Position.BATTER ? 'POW +1, PAT +1' : 'STU +1, CTRL +1';
    default:
      return 'No set effect';
  }
}

const SkillSetEffect = ({ slots, position, className }: Props) => {
  // 슬롯이 아직 없으면(첫 롤 전) 배너를 숨겨 레이아웃 흔들림을 방지한다.
  if (!slots?.length) {
    return null;
  }

  // 슬롯이 있으면 최저 등급이 D여도 배너 자리를 유지해 "스킬 변경" 버튼이 움직이지 않도록 한다.
  const minGrade = pickMinimumGrade(slots);
  const effectText = effectTextByGrade(minGrade, position);

  const bannerStyle = bannerStyles[minGrade] ?? bannerStyles[Grade.D];
  const badgeStyle = gradeBadgeStyles[minGrade] ?? gradeBadgeStyles[Grade.D];

  return (
    <div className={`rounded-2xl border p-4 shadow-lg ${bannerStyle} ${className ?? ''}`}>
      <p className="text-xs font-semibold uppercase tracking-[0.18em] opacity-80">Skill Level Effect</p>
      <div className="mt-2 flex items-center gap-4">
        <div className={`flex h-12 w-12 items-center justify-center rounded-lg text-xl font-black ${badgeStyle}`}>{minGrade}</div>
        <div className="text-sm font-semibold leading-relaxed">{effectText}</div>
      </div>
    </div>
  );
};

export default SkillSetEffect;
