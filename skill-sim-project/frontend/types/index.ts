// 프론트엔드에서 사용하는 스킬 관련 타입 정의와 열거형 모음
export enum Tier {
  MOMENT = 'MOMENT',
  IRON = 'IRON',
  BRONZE = 'BRONZE',
  SILVER = 'SILVER',
  GOLD = 'GOLD',
}

export enum Grade {
  D = 'D',
  C = 'C',
  B = 'B',
  A = 'A',
  S = 'S',
}

export enum TicketType {
  SKILL_CHANGE = 'SKILL_CHANGE',
  PREMIUM_SKILL_CHANGE = 'PREMIUM_SKILL_CHANGE',
  SUPREME_SKILL_CHANGE = 'SUPREME_SKILL_CHANGE',
}

export enum CardType {
  SIGNATURE = 'SIGNATURE',
  PRIME = 'PRIME',
  MOMENT = 'MOMENT',
}

export enum Position {
  PITCHER = 'PITCHER',
  BATTER = 'BATTER',
}

export type Skill = {
  id: number;
  name: string;
  tier: Tier;
  grade: Grade;
  description: string;
  weight: number;
  position?: Position | string;
};

export type SkillSlot = {
  skill: Skill;
  grade: Grade;
};

export type RollRequest = {
  cardType: CardType;
  ticketType: TicketType;
  useLevelProtectionSlots: boolean[];
  lockedSlots: number[];
  currentSkillIds: (number | null)[];
  currentGrades: Grade[];
  selectedTheme?: string | null;
  position?: Position | string | null;
};

export type RollResponse = {
  slots: SkillSlot[];
};
