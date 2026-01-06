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

export type Skill = {
  id: number;
  name: string;
  tier: Tier;
  grade: Grade;
  description: string;
  weight: number;
};

export type SkillSlot = {
  skill: Skill;
  grade: Grade;
};

export type RollRequest = {
  cardType: CardType;
  ticketType: TicketType;
  useLevelProtection: boolean;
  lockedSlots: number[];
  currentSkillIds: (number | null)[];
  currentGrades: Grade[];
};

export type RollResponse = {
  slots: SkillSlot[];
};
