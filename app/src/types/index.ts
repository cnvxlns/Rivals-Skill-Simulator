// 프론트엔드에서 사용하는 점수 관련 타입 정의와 열거형 모음

/**
 * 카드 등급. 낮은 것부터 높은 순이며 이 순서가 곧 서열이다.
 *
 * season = live < impact < prime < moment < signature < signature black < hof.
 * 서열이 낮을수록 상대등급우세 조건이 자주 발동해 관련 스킬의 점수가 올라간다.
 * SUPREME_MOMENT는 실제 위치가 확인되지 않아 모먼트와 시그니처 사이에 둔다.
 */
export enum CardGrade {
  SEASON = 'SEASON',
  LIVE = 'LIVE',
  IMPACT = 'IMPACT',
  PRIME = 'PRIME',
  MOMENT = 'MOMENT',
  SUPREME_MOMENT = 'SUPREME_MOMENT',
  SIGNATURE = 'SIGNATURE',
  SIGNATURE_BLACK = 'SIGNATURE_BLACK',
  HOF = 'HOF',
}

/** 낮은 등급부터. 드롭다운 순서로 쓴다. */
export const CARD_GRADES_LOW_TO_HIGH: CardGrade[] = [
  CardGrade.SEASON,
  CardGrade.LIVE,
  CardGrade.IMPACT,
  CardGrade.PRIME,
  CardGrade.MOMENT,
  CardGrade.SUPREME_MOMENT,
  CardGrade.SIGNATURE,
  CardGrade.SIGNATURE_BLACK,
  CardGrade.HOF,
];

/**
 * 카드 변형. 서열을 바꾸지 않으므로 등급과 별개 축이다.
 *
 * WBC만 스킬 풀을 넓히고, FA는 지금은 이름뿐이다.
 */
export enum CardVariant {
  NONE = 'NONE',
  FA = 'FA',
  WBC = 'WBC',
}

/** FA·WBC를 가질 수 있는 등급. 나머지는 기본형만 존재한다. */
export const VARIANT_CAPABLE_GRADES: CardGrade[] = [
  CardGrade.PRIME,
  CardGrade.SIGNATURE,
  CardGrade.SIGNATURE_BLACK,
];

export const variantsFor = (grade: CardGrade): CardVariant[] =>
  VARIANT_CAPABLE_GRADES.includes(grade)
    ? [CardVariant.NONE, CardVariant.FA, CardVariant.WBC]
    : [CardVariant.NONE];

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
  cardGrade: CardGrade | string;
  cardVariant?: CardVariant | string;
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

/** 주전 타자 9자리. */
export const LINEUP_SLOTS = ['C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'] as const;

/**
 * 야구장 위의 수비 위치. BaseballField와 같은 100x100 필드 좌표이며 x는 오른쪽,
 * y는 아래쪽이 양수다. 홈플레이트가 (50, 84)이고 펜스 꼭대기가 y=19다.
 *
 * 화면에 놓을 때는 `fieldRatio`로 그림이 차지하는 상자 기준 비율로 바꾼다. 예전에는
 * 여기에 0~1 비율을 직접 적어 뒀는데, 그림의 여백을 걷어내면서 두 좌표계가 갈라졌다.
 *
 * 가로로 이웃한 자리는 칩 폭만큼 벌려 둔다(유격수·2루수 0.32 등). 그러지 않으면 칸이
 * 좁아졌을 때 두 칩이 겹쳐 서로를 가린다.
 */
export const LINEUP_FIELD_POSITIONS: Record<string, { x: number; y: number }> = {
  // 외야. 펜스(가운데 y=19) 앞에 선다. 중견수를 충분히 올려 좌·우익수와 줄이 갈리게 한다.
  LF: { x: 27, y: 38 },
  CF: { x: 50, y: 24 },
  RF: { x: 73, y: 38 },
  // 중간 내야. 2루 베이스(50, 46)를 사이에 두고 흙 밖 잔디 위에 갈라선다.
  SS: { x: 37, y: 50 },
  '2B': { x: 63, y: 50 },
  // 코너 내야. 3루(31, 65)와 1루(69, 65) 곁, 파울선 안쪽이다.
  '3B': { x: 31, y: 63 },
  '1B': { x: 69, y: 63 },
  // 포수는 홈플레이트(50, 84) 뒤.
  C: { x: 50, y: 87 },
  // 지명타자는 수비 위치가 없다. 포수 오른쪽에 세운다.
  DH: { x: 78, y: 87 },
};

export const DH_SLOT = 'DH';

/** 후보 5자리. */
export const BENCH_SLOTS = ['BENCH1', 'BENCH2', 'BENCH3', 'BENCH4', 'BENCH5'] as const;

/** 투수 정원. 선발·중계·마무리의 합이 이 값이어야 저장할 수 있다. */
export const PITCHER_COUNT = 12;

export const ROSTER_SIZE = 26;

export type DeckSkillSelection = {
  skillId: string;
  level: number;
};

export type DeckPlayer = {
  slot: string;
  /** 선수 이름. 표시용이며 점수에는 영향이 없다. */
  playerName?: string | null;
  /** 후보만 직접 정한다. 주전과 투수는 백엔드가 자리에서 유도한다. */
  position?: string;
  cardGrade: CardGrade | string;
  cardVariant?: CardVariant | string;
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
  /** 셋 중 둘만 보내면 나머지는 서버가 계산한다. */
  starterCount?: number;
  relieverCount?: number;
  closerCount?: number;
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
  playerName?: string | null;
  cardType: string;
  score: number;
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  relieverRole?: RelieverRole | null;
  perSkill: ScoreSkillBreakdown[];
  perStat: ScoreStatBreakdown[];
  warnings: string[];
};

/** 카드 계열을 모아서 받는 능력치 보정. 채점에 이미 반영돼 있다. */
export type CollectionBuffFamily = 'HOF' | 'SIGNATURE' | 'MOMENT' | 'LIVE' | 'SEASON';

export type CollectionBuffFamilyCount = {
  family: CollectionBuffFamily;
  /** 가중치 합. 슈프림 모먼트를 두 장으로 세므로 실제 장수보다 클 수 있다. */
  count: number;
  batterBonus: number;
  pitcherBonus: number;
};

export type CollectionBuffInfo = {
  families: CollectionBuffFamilyCount[];
  batterBonus: number;
  pitcherBonus: number;
};

export type DeckScoreResponse = {
  total: number;
  parts: DeckPartScore[];
  players: DeckPlayerScore[];
  warnings: string[];
  collectionBuff: CollectionBuffInfo;
};

/** 스킬 변경권 세 종류. 백엔드 TicketType과 같은 이름을 쓴다. */
export type TicketKind = 'SKILL_CHANGE' | 'PREMIUM_SKILL_CHANGE' | 'SUPREME_SKILL_CHANGE';

export type TicketOutcome = {
  ticket: TicketKind;
  /** 결과를 무를 수 있는가. 일반 변경권만 false다. */
  revocable: boolean;
  /** 한 장으로 지금보다 나아질 확률. */
  improveChance: number;
  /** 나아질 때까지 기대되는 장수. 나아질 수 없으면 null이다. */
  expectedTickets: number | null;
  averageGain: number | null;
  averageTotal: number;
  /** 장수별 누적 성공 확률. */
  chanceWithin: Record<string, number>;
};

export type TicketExpectationResponse = {
  currentTotal: number;
  /** 이 카드가 첫 슬롯을 잠글 수 있는가. */
  slotOneLockable: boolean;
  tickets: TicketOutcome[];
};

export type TicketExpectationRequest = {
  cardGrade: string;
  cardVariant?: string;
  position: string;
  selections: ScoreSelection[];
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  userStats?: Record<string, number>;
  throwHand?: Handedness;
  batHand?: Handedness;
  lockSlotOne?: boolean;
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
