import { CardGrade, CardVariant } from '../types';

/**
 * 카드 축의 규칙. 백엔드 `CardRules`와 같은 내용을 프런트에서도 쓴다.
 *
 * 서버에 묻지 않고 "이 칸에 이 스킬을 놓을 수 있나"를 알아야 목록에서 미리 거르고, 카드를
 * 바꿨을 때 남은 스킬에 경고를 띄울 수 있다. 규칙이 두 곳에 있는 셈이지만 값이 몇 개뿐이고
 * 최종 판정은 백엔드가 하므로 어긋나도 잘못 저장되지는 않는다.
 *
 * 스킬이 어느 풀에 속하는지는 **스킬 ID 앞자리**로 안다. 덱 상태가 ID만 들고 있어도 되어
 * 스킬 목록을 따로 받아 두거나 풀을 같이 저장할 필요가 없다. ID는 이렇게 내부 판정에만
 * 쓰고 사용자에게 검색 키로 내세우지 않는다.
 */

export type SkillPool = 'NORMAL' | 'WBC' | 'BLACK' | 'MOMENT' | 'HOF';

/** 스킬 ID 앞자리 → 풀. `score_skills.csv`의 `card_type`과 일대일로 맞는다. */
const POOL_BY_PREFIX: Record<string, SkillPool> = {
  I: 'NORMAL',
  B: 'NORMAL',
  S: 'NORMAL',
  G: 'NORMAL',
  M: 'MOMENT',
  HOF: 'HOF',
  WBC: 'WBC',
  BLACK: 'BLACK',
};

/** 모르는 앞자리는 기본 풀로 본다. 모든 카드가 접근하므로 새 스킬이 생겨도 막히지 않는다. */
export function skillPoolOf(skillId: string): SkillPool {
  const prefix = skillId.split('_')[0]?.toUpperCase() ?? '';
  return POOL_BY_PREFIX[prefix] ?? 'NORMAL';
}

/** 스킬 슬롯 수. 시그니처 블랙만 네 칸이고 변형은 영향을 주지 않는다. */
export const slotCountFor = (cardGrade: string): number =>
  cardGrade === CardGrade.SIGNATURE_BLACK ? 4 : 3;

/** 이 카드가 고를 수 있는 풀. 백엔드 `CardRules.skillPools`와 같은 순서다. */
export function skillPoolsFor(cardGrade: string, cardVariant?: string): SkillPool[] {
  const pools: SkillPool[] = ['NORMAL'];
  if (cardVariant === CardVariant.WBC) pools.push('WBC');
  if (cardGrade === CardGrade.SIGNATURE_BLACK) pools.push('BLACK');
  if (cardGrade === CardGrade.MOMENT || cardGrade === CardGrade.SUPREME_MOMENT) pools.push('MOMENT');
  if (cardGrade === CardGrade.HOF) pools.push('HOF');
  return pools;
}

/**
 * 이 풀이 이 칸에 나올 수 있는가.
 *
 * 등장 확률표에서 0%인 조합을 막는다. 지금 그런 규칙은 하나다 — 모먼트 전용 스킬은
 * 모먼트·슈프림 모먼트의 **첫 칸**에서만 나온다(`RollTables.momentSlotOneChance`).
 */
export const allowsPoolInSlot = (pool: SkillPool, slotIndex: number): boolean =>
  pool !== 'MOMENT' || slotIndex === 0;

/**
 * 카드 한 장이 가질 수 있는 이 풀의 최대 장수.
 *
 * 블랙은 롤이 한 칸만 미리 잡아 두므로 두 장이 되는 경우가 없다. 모먼트는 첫 칸 전용이라
 * 위 규칙만으로 이미 한 장이다.
 */
export const maxFromPool = (pool: SkillPool): number => (pool === 'BLACK' ? 1 : Infinity);

/**
 * 이 카드의 이 칸에 놓을 수 있는 스킬인가.
 *
 * @param others 다른 칸에 이미 놓인 스킬 ID. 장수 제한이 있는 풀을 거르는 데 쓴다.
 */
export function canPlaceSkill(
  cardGrade: string,
  cardVariant: string | undefined,
  slotIndex: number,
  skillId: string,
  others: readonly string[] = [],
): boolean {
  const pool = skillPoolOf(skillId);
  if (!skillPoolsFor(cardGrade, cardVariant).includes(pool)) return false;
  if (!allowsPoolInSlot(pool, slotIndex)) return false;
  const alreadyUsed = others.filter((id) => skillPoolOf(id) === pool).length;
  return alreadyUsed < maxFromPool(pool);
}
