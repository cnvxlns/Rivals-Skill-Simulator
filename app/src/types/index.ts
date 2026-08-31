// 프론트엔드에서 사용하는 점수 관련 타입 정의와 열거형 모음
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

export enum Handedness {
  LEFT = 'LEFT',
  RIGHT = 'RIGHT',
  SWITCH = 'SWITCH',
}

export type ScoreRequest = {
  cardType: CardType | string;
  position: Position | SubPosition | string;
  selections: ScoreSelection[];
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  userStats?: Record<string, number>;
  throwHand?: Handedness;
  batHand?: Handedness;
};

export type ScoreTableRequest = {
  position: Position | SubPosition | string;
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  throwHand?: Handedness;
  batHand?: Handedness;
  userStats?: Record<string, number>;
};

export type ScoreTableEntry = {
  skillId: string;
  name: string;
  description?: string | null;
  /** description의 x·y·z를 appliedGrade 기준 수치로 바꾼 것. 치환 불가 시 원문과 같다. */
  resolvedDescription?: string | null;
  score: number;
  appliedGrade: string;
};

export type ScoreTierGroup = {
  tier: string;
  totalCount: number;
  entries: ScoreTableEntry[];
};

export type ScoreTableResponse = {
  tiers: ScoreTierGroup[];
};

export type ScoreStatBreakdown = {
  stat: string;
  value: number;
};

export type ScoreSkillBreakdown = {
  skillId: string;
  name: string;
  /** 설명의 x·y·z를 선택한 레벨 기준 수치로 바꾼 것. 치환 불가 시 원문과 같다. */
  resolvedDescription?: string | null;
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
