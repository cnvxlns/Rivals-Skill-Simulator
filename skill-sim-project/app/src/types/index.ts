// 프론트엔드에서 사용하는 스킬 관련 타입 정의와 열거형 모음
export enum Tier {
  IRON = 'IRON',
  BRONZE = 'BRONZE',
  SILVER = 'SILVER',
  GOLD = 'GOLD',
  BLACK = 'BLACK',
  MOMENT = 'MOMENT',
  HOF = 'HOF',
  WBC = 'WBC',
}

export enum Grade {
  D = 'D',
  C = 'C',
  B = 'B',
  A = 'A',
  S = 'S',
  S1 = 'S1',
  S2 = 'S2',
  S3 = 'S3',
  S4 = 'S4',
}

export enum TicketType {
  SKILL_CHANGE = 'SKILL_CHANGE',
  PREMIUM_SKILL_CHANGE = 'PREMIUM_SKILL_CHANGE',
  SUPREME_SKILL_CHANGE = 'SUPREME_SKILL_CHANGE',
}

export enum CardType {
  SIGNATURE = 'SIGNATURE',
  SIGNATURE_BLACK = 'SIGNATURE_BLACK',
  WBC = 'WBC',
  WBC_SIGNATURE_BLACK = 'WBC_SIGNATURE_BLACK',
  HOF = 'HOF',
  MOMENT = 'MOMENT',
  SUPREME_MOMENT = 'SUPREME_MOMENT',
}

export enum Position {
  PITCHER = 'PITCHER',
  BATTER = 'BATTER',
}

export type SkillEffect = {
  condition: string;
  logic: string;
  description?: string | null;
};

export type SubPosition =
  | 'ALL'
  | 'SP'
  | 'RP'
  | 'CP'
  | 'C'
  | '1B'
  | '2B'
  | '3B'
  | 'SS'
  | 'LF'
  | 'CF'
  | 'RF'
  | 'DH';

export type Skill = {
  id: number;
  skillId?: string;
  name: string;
  tier: Tier;
  description?: string | null;
  position?: Position | string;
  subPositions?: string | null;
  effects?: SkillEffect[];
  levelEffects?: Record<string, string>;
};

export type SkillSlot = {
  skill: Skill | null;
  grade: Grade;
  score?: number;
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
  subPosition?: SubPosition | string | null;
};

export type RollResponse = {
  slots: SkillSlot[];
  totalScore: number;
};

export type ScoreSkillOption = {
  skillId: string;
  cardType: string;
  position: string;
  name: string;
  description?: string | null;
  maxLevel: number;
  levelLabels: string[];
};

export type ScoreSelection = {
  skillId: string;
  level: number;
};

export type ScoreRequest = {
  cardType: CardType | string;
  position: Position | SubPosition | string;
  selections: ScoreSelection[];
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  userStats?: Record<string, number>;
};

export type ScoreStatBreakdown = {
  stat: string;
  value: number;
};

export type ScoreSkillBreakdown = {
  skillId: string;
  name: string;
  score: number;
  perStat: ScoreStatBreakdown[];
  breakdown?: ScoreEffectBreakdown[];
  warnings?: string[];
};

export type ScoreEffectBreakdown = {
  stat: string;
  condition: string;
  weight: number;
  value: number;
  conditionProbability: number;
  subtotal: number;
  baseStat?: string | null;
  baseValue?: number | null;
  rawValue: number;
};

export type ScoreResponse = {
  total: number;
  perSkill: ScoreSkillBreakdown[];
  perStat: ScoreStatBreakdown[];
  warnings?: string[];
};

export type FormulaItem = {
  displayText: string;
  descriptionKey: string;
};

export type FormulaInfo = {
  perSkillFormula: FormulaItem;
  totalFormula: FormulaItem;
  percentEffectRule: FormulaItem;
  roundingRule: FormulaItem;
  conditionCombinationRule: FormulaItem;
};

export type ConditionProbabilityEntry = {
  token: string;
  value: number;
  descriptionKey: string;
};

export type RoleProbabilityEntry = {
  role: string;
  inningWeights: number[];
  gutsProbability: number;
  patienceBelowVelocityProbability: number;
  nineBatterDuration: number;
  maestroCumulative: number;
};

export type ReachProbabilityEntry = {
  orderGroup: string;
  descriptionKey: string;
  reachProbabilities: number[];
};

export type GateEntry = {
  token: string;
  descriptionKey: string;
};

export type ConditionProbabilitiesInfo = {
  staticProbabilities: ConditionProbabilityEntry[];
  roleProbabilities: RoleProbabilityEntry[];
  battingOrderProbabilities: ConditionProbabilityEntry[];
  reachProbabilities: ReachProbabilityEntry[];
  gates: GateEntry[];
};

export type MethodologyResponse = {
  formula: FormulaInfo;
  statWeights: Record<string, number>;
  conditionProbabilities: ConditionProbabilitiesInfo;
};
