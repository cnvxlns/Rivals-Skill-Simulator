export enum Tier {
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

export type Skill = {
  id: number;
  name: string;
  tier: Tier;
  description: string;
  weight: number;
};

export type SkillSlot = {
  skill: Skill;
  grade: Grade;
};

export type RollRequest = {
  ticketType: TicketType;
  useProtection: boolean;
  currentGrades: Grade[];
};

export type RollResponse = {
  slots: SkillSlot[];
};
