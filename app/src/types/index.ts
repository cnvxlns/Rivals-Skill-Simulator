// 프론트엔드에서 사용하는 점수 관련 타입 정의와 열거형 모음
export enum CardType {
  SIGNATURE = 'SIGNATURE',
  SIGNATURE_BLACK = 'SIGNATURE_BLACK',
  WBC = 'WBC',
  WBC_SIGNATURE_BLACK = 'WBC_SIGNATURE_BLACK',
  HOF = 'HOF',
  MOMENT = 'MOMENT',
  SUPREME_MOMENT = 'SUPREME_MOMENT',
  // 라이브/시즌은 전용 스킬이 없고 아이언·브론즈·실버·골드만 가진다.
  // 백엔드가 시그니처와 같은 스킬 풀·슬롯 3개·D~S4 사다리로 다룬다.
  LIVE = 'LIVE',
  SEASON = 'SEASON',
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

/* ── 인증 ────────────────────────────────────────────── */

export type AuthUser = {
  id: number;
  email: string;
};

export type AuthResponse = {
  token: string;
  expiresAt: string;
  user: AuthUser;
};

/* ── 덱 ──────────────────────────────────────────────── */

/** 중계 하위 역할. 저장·표시만 하고 점수에는 반영되지 않는다. */
export enum RelieverRole {
  WIN = 'WIN',
  CHASE = 'CHASE',
  LONG = 'LONG',
}

/** 주전 타자 9자리. 순서가 곧 화면 표시 순서다. */
export const LINEUP_SLOTS = ['C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'] as const;

/** 후보 5자리. */
export const BENCH_SLOTS = ['BENCH1', 'BENCH2', 'BENCH3', 'BENCH4', 'BENCH5'] as const;

/** 투수 정원. 선발과 마무리를 고르면 중계 정원이 여기서 파생된다. */
export const PITCHER_COUNT = 12;

export const ROSTER_SIZE = 26;

export type DeckSkillSelection = {
  skillId: string;
  level: number;
};

export type DeckPlayer = {
  slot: string;
  /** 후보만 직접 정한다. 주전과 투수는 백엔드가 자리에서 유도한다. */
  position?: string;
  cardType: string;
  skills: DeckSkillSelection[];
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  relieverRole?: RelieverRole | null;
  stats?: Record<string, number>;
  throwHand?: Handedness | null;
  batHand?: Handedness | null;
};

export type DeckSaveRequest = {
  name?: string;
  starterCount: number;
  closerCount: number;
  players: DeckPlayer[];
};

export type DeckRoster = {
  starterCount: number;
  closerCount: number;
  players: DeckPlayer[];
};

export type DeckPartScore = {
  /** LINEUP | BENCH | ROTATION | BULLPEN */
  part: string;
  total: number;
  playerCount: number;
};

export type DeckPlayerScore = {
  slot: string;
  position: string;
  cardType: string;
  score: number;
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  relieverRole?: RelieverRole | null;
  perSkill: ScoreSkillBreakdown[];
  perStat: ScoreStatBreakdown[];
  warnings: string[];
};

export type DeckScoreResponse = {
  total: number;
  parts: DeckPartScore[];
  players: DeckPlayerScore[];
  warnings: string[];
};

export type DeckSummary = {
  id: number;
  name: string;
  /** 저장 시점 값이라 낡을 수 있다. 정확한 값은 상세 조회를 쓴다. */
  totalScore?: number | null;
  updatedAt: string;
};

export type DeckDetail = {
  id: number;
  name: string;
  roster: DeckRoster;
  score: DeckScoreResponse;
  createdAt: string;
  updatedAt: string;
};
