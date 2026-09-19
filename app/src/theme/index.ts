import type { TextStyle } from 'react-native';

// 디자인 토큰. 원본 디자인 핸드오프는 레포에 없다(docs/는 추적하지 않는다).
// 확정한 결정과 그 이유는 f28b2a4 커밋 메시지에 남아 있다.
//
// 인코딩 축을 넷으로 나눠 한 행에 동시에 나와도 충돌하지 않게 한다.
//   티어 = 색상 / 등급 = 채움과 밝기 / 강조 = 역할(값 vs 인터랙션) / 의미 = 숫자 부호
// 그림자는 쓰지 않는다. 카드 분리는 외곽선 + 배경 명도차로만 한다(플랫폼별 동작이 달라서).

export type ThemeColors = {
  background: string;
  surface: string;
  surfaceVariant: string;
  outline: string;
  outlineFaint: string;
  divider: string;
  onSurface: string;
  secondaryText: string;
  muted: string;
  mutedFaint: string;
  accentValue: string;
  accentAction: string;
  onAccentValue: string;
  onAccentAction: string;
  statMine: string;
  statOpponent: string;
  error: string;
  disabled: string;

  // 야구장 그림 전용. 값이나 상태를 뜻하지 않으므로 강조색과 섞지 않는다.
  fieldGrass: string;
  fieldDirt: string;
  fieldLine: string;
};

export const colors: ThemeColors = {
  background: '#101216',
  surface: '#16181E',
  surfaceVariant: '#1D2027',
  outline: '#2A2C33',
  outlineFaint: '#22242B',
  divider: '#1D2027',

  onSurface: '#E8EAED', //  15.6:1
  secondaryText: '#9AA4B8', //   7.6:1
  muted: '#7E8798', //   5.2:1  (구 #66708A는 4.5:1 미달)
  mutedFaint: '#6E7787', //   4.1:1  비필수 텍스트 전용

  // 강조 2색. 기존엔 라임 하나가 강조·점수·활성탭·링크를 전부 맡아 위계가 없었다.
  accentValue: '#3DF5A8', //  13.3:1  값 전용. 숫자가 아닌 것에 쓰지 않는다
  accentAction: '#4CC9F0', //   9.8:1  인터랙션 전용. 값에 쓰지 않는다
  onAccentValue: '#06120C',
  onAccentAction: '#06121A',

  // 의미색. 상대 스탯 감소는 사용자에게 이득이므로 경고색(빨강)에서 빼냈다.
  statMine: '#3DF5A8',
  statOpponent: '#FFB43D',
  error: '#FF4D5E', // 배너와 입력 검증에만. 숫자에는 쓰지 않는다
  disabled: '#5A606C', // 미발동 0점 행. 사유 텍스트가 항상 함께 붙으므로 대비 예외

  // 야구장 그림. 배경이므로 어둡게 깔고 그 위의 칩이 읽히게 한다.
  fieldGrass: '#13211A',
  fieldDirt: '#241C15',
  fieldLine: '#3A4048',
};

// 게임의 색상 배정 관습만 계승하고 값은 전부 새로 산출했다(원본 색 복제 아님).
// 전부 배경 #101216 대비 4.5:1 이상.
export type TierKey = 'iron' | 'bronze' | 'silver' | 'gold' | 'moment' | 'wbc' | 'black' | 'hof';

export const tierColors: Record<TierKey, { hex: string; soft: string }> = {
  iron: { hex: '#7A8290', soft: 'rgba(122,130,144,0.16)' },
  bronze: { hex: '#B0713C', soft: 'rgba(176,113,60,0.16)' },
  silver: { hex: '#B8C2D0', soft: 'rgba(184,194,208,0.14)' },
  // 앰버(상대 스탯 감소)와 부딪히지 않도록 밝은 앰버가 아닌 황동으로 낮췄다.
  gold: { hex: '#C89B4A', soft: 'rgba(200,155,74,0.14)' },
  moment: { hex: '#3EA55C', soft: 'rgba(62,165,92,0.15)' },
  wbc: { hex: '#4A7FE0', soft: 'rgba(74,127,224,0.16)' },
  black: { hex: '#9B6BF0', soft: 'rgba(155,107,240,0.16)' },
  hof: { hex: '#FF6A2B', soft: 'rgba(255,106,43,0.14)' },
};

/** 티어색은 카드 상단 레일과 티어 칩 두 곳에서만 쓴다. 행 안으로 번지게 하지 않는다. */
export function tierColor(tier: string): { hex: string; soft: string } {
  return tierColors[String(tier).toLowerCase() as TierKey] ?? tierColors.iron;
}

// 카드 등급색. 스킬 티어색과 축이 다르므로 값을 따로 둔다.
//
// 티어는 스킬 한 줄에, 등급은 선수 한 명에 붙는다. 두 축이 한 화면에 같이 나오지 않도록
// 등급색은 로스터 칩에서만 쓰고 점수표에는 쓰지 않는다. 게임의 배정 관습(모먼트=초록,
// 블랙=보라, HOF=주황)만 따르고 값은 배경 #101216 대비 4.5:1 이상으로 새로 잡았다.
export type CardGradeKey =
  | 'SEASON'
  | 'LIVE'
  | 'IMPACT'
  | 'PRIME'
  | 'MOMENT'
  | 'SUPREME_MOMENT'
  | 'SIGNATURE'
  | 'SIGNATURE_BLACK'
  | 'HOF';

export const cardGradeColors: Record<CardGradeKey, { hex: string; soft: string }> = {
  SEASON: { hex: '#8B94A3', soft: 'rgba(139,148,163,0.14)' },
  LIVE: { hex: '#5BC8C0', soft: 'rgba(91,200,192,0.14)' },
  IMPACT: { hex: '#E0685C', soft: 'rgba(224,104,92,0.14)' },
  PRIME: { hex: '#C89B4A', soft: 'rgba(200,155,74,0.14)' },
  MOMENT: { hex: '#3EA55C', soft: 'rgba(62,165,92,0.15)' },
  // 모먼트와 같은 계열이되 한 단계 밝게. 색상까지 갈라 두면 둘이 한 계열임이 지워진다.
  SUPREME_MOMENT: { hex: '#5FD98A', soft: 'rgba(95,217,138,0.15)' },
  SIGNATURE: { hex: '#D9DEE8', soft: 'rgba(217,222,232,0.12)' },
  SIGNATURE_BLACK: { hex: '#9B6BF0', soft: 'rgba(155,107,240,0.16)' },
  HOF: { hex: '#FF6A2B', soft: 'rgba(255,106,43,0.14)' },
};

/** 모르는 등급이면 가장 낮은 등급 색으로 떨어뜨린다. 색이 비면 칩 테두리가 사라져서다. */
export function cardGradeColor(grade: string): { hex: string; soft: string } {
  return cardGradeColors[String(grade).toUpperCase() as CardGradeKey] ?? cardGradeColors.SEASON;
}

// 티어가 색상환을 거의 다 쓰므로 등급에는 새 색상을 주지 않는다.
// D~A는 외곽선 + 뉴트럴 4단, S~S4는 채움 + 민트 5단. 형태가 먼저 두 그룹을 가른다.
export type GradeStyle = { fill: string; text: string; border: string };

export const gradeStyles: Record<string, GradeStyle> = {
  D: { fill: 'transparent', text: '#7E8798', border: '#2A2C33' },
  C: { fill: 'transparent', text: '#939BAA', border: '#32353D' },
  B: { fill: 'transparent', text: '#AEB5C2', border: '#3B3F48' },
  A: { fill: 'transparent', text: '#CFD4DD', border: '#474C56' },
  S: { fill: '#2A2C33', text: '#E8EAED', border: '#2A2C33' },
  S1: { fill: '#163A2C', text: '#5FDCA4', border: '#1E4E3A' },
  S2: { fill: '#1B5540', text: '#7CEFBC', border: '#246B52' },
  S3: { fill: '#2A8F65', text: '#06120C', border: '#2A8F65' },
  S4: { fill: '#3DF5A8', text: '#06120C', border: '#3DF5A8' },
};

/** 미발동 행에서는 등급 칩도 같이 죽인다. 색 하나에만 의존하지 않기 위해서다. */
export const inactiveGradeStyle: GradeStyle = {
  fill: 'transparent',
  text: '#5A606C',
  border: '#22242B',
};

export function gradeStyle(grade: string, inactive = false): GradeStyle {
  if (inactive) return inactiveGradeStyle;
  return gradeStyles[String(grade).toUpperCase()] ?? gradeStyles.D;
}

/** 미발동 행은 아래 4색을 동시에 바꾼다. */
export const inactiveRow = {
  rank: '#4A505C',
  name: '#5A606C',
  score: '#4A505C',
};

export const banner = {
  warn: { background: '#1B1E17', border: '#3A3F26', text: '#E8DFC4', badge: '#FFB43D', onBadge: '#14100A' },
  error: { background: '#1F1416', border: '#4A2129', text: '#FFD5DA', badge: '#FF4D5E', onBadge: '#180608' },
};

export const apkButton = { background: '#14252C', border: '#2A5A6B', text: '#4CC9F0' };

/**
 * 웹은 CDN 동적 서브셋, 앱은 expo-font 번들을 기대한다.
 * 폰트가 없으면 undefined로 떨어져 시스템 폰트를 쓴다(레이아웃은 그대로 유지).
 */
export const fontFamily = {
  regular: 'Pretendard',
  mono: 'monospace',
};

// M3 15단을 7단으로 줄였다. 표 안 숫자는 전부 tabular-nums 고정폭이어야 한다.
export const typography = {
  hero: { fontSize: 48, lineHeight: 56, fontWeight: '800' as const, letterSpacing: -0.4 },
  title: { fontSize: 32, lineHeight: 42, fontWeight: '700' as const, letterSpacing: -0.28 },
  section: { fontSize: 24, lineHeight: 34, fontWeight: '700' as const, letterSpacing: -0.2 },
  card: { fontSize: 20, lineHeight: 30, fontWeight: '700' as const },
  body: { fontSize: 18, lineHeight: 32, fontWeight: '600' as const },
  label: { fontSize: 16, lineHeight: 26, fontWeight: '600' as const },
  caption: { fontSize: 15, lineHeight: 26, fontWeight: '500' as const },
};

/** 표에 세로로 쌓이는 숫자에 반드시 얹는다. RN 웹에서도 동작한다. */
export const tabularNums: TextStyle = { fontVariant: ['tabular-nums'] };

// 밀도를 낮추려고 한 단계씩 올린 값이다(대략 +25%). 원 핸드오프는 8dp 배수를 쓰되
// "조정해도 좋다"고 열어 뒀다. 여백은 98곳이 이 토큰을 쓰므로 여기가 가장 큰 지렛대다.
export const spacing = {
  xxs: 6,
  xs: 10,
  sm: 12,
  smd: 14,
  md: 18,
  mdl: 22,
  lg: 24,
  lgx: 26,
  xl: 30,
  xxl: 36,
  xxlx: 38,
  xxxl: 46,
  huge: 60,
};

export const radius = {
  chipSm: 7,
  chip: 8,
  control: 12,
  input: 14,
  card: 18,
  cardLg: 20,
  shell: 28,
  pill: 999,
};

/** 전부 44dp 이상. 터치 타겟 최소치. */
export const controlHeight = { dropdown: 46, input: 52, button: 48, tabMobile: 44 };
