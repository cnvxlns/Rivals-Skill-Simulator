// 하나의 스킬 슬롯 정보를 카드 형태로 표시하는 프레젠테이션 컴포넌트
import { SkillSlot } from '../types';

type Props = {
    skillSlot: SkillSlot;
    slotNumber: number;
};

const badgeColors: Record<string, string> = {
    IRON: 'bg-slate-200 text-slate-800',
    BRONZE: 'bg-amber-200 text-amber-900',
    SILVER: 'bg-gray-200 text-gray-800',
    GOLD: 'bg-yellow-200 text-yellow-900',
};

const SkillCard = ({ skillSlot, slotNumber }: Props) => {
    const { skill, grade } = skillSlot;
    return (
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md">
            <div className="flex items-center justify-between gap-2">
                <div>
                    <p className="text-xs uppercase tracking-wide text-slate-500">Slot {slotNumber}</p>
                    <h3 className="text-lg font-semibold text-slate-900">{skill.name}</h3>
                </div>
                <div className="flex flex-col items-end gap-2">
                    <span className={`rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide ${badgeColors[skill.tier]}`}>
                        {skill.tier}
                    </span>
                    <span className="rounded-full bg-indigo-100 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-indigo-700">
                        Grade {grade}
                    </span>
                </div>
            </div>
            <p className="mt-2 text-sm text-slate-600">{skill.description}</p>
        </div>
    );
};

export default SkillCard;
