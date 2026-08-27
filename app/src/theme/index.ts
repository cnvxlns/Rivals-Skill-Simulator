// Android(Jetpack Compose) 룩을 그대로 이식한 RN 테마 토큰.
// 원본: android/.../ui/theme/Color.kt, Type.kt, Shape.kt

export type ThemeColors = {
  background: string;
  surface: string;
  surfaceVariant: string;
  primary: string;
  onPrimary: string;
  onBackground: string;
  onSurface: string;
  secondaryText: string;
  muted: string;
  outline: string;
  error: string;
  opponentStat: string;
  // 등급 칩: S=yellow, A=blue, B=green, C/D=outline
  gradeS: string;
  gradeA: string;
  gradeB: string;
};

export const darkColors: ThemeColors = {
  background: '#0B1020',
  surface: '#161C2E',
  surfaceVariant: '#1E2740',
  primary: '#C6F135',
  onPrimary: '#0B1020',
  onBackground: '#F5F7FA',
  onSurface: '#F5F7FA',
  secondaryText: '#9AA4B8',
  muted: '#66708A',
  outline: '#2A3350',
  error: '#FF5A5F',
  opponentStat: '#FF6B6B',
  gradeS: '#FFC93C',
  gradeA: '#4A90E2',
  gradeB: '#3DD68C',
};

export const lightColors: ThemeColors = {
  background: '#F5F7FA',
  surface: '#FFFFFF',
  surfaceVariant: '#F5F7FA',
  primary: '#5C8A00',
  onPrimary: '#FFFFFF',
  onBackground: '#0E1424',
  onSurface: '#0E1424',
  secondaryText: '#66708A',
  muted: '#66708A',
  outline: '#D5DAE5',
  error: '#FF5A5F',
  opponentStat: '#FF6B6B',
  gradeS: '#FFC93C',
  gradeA: '#4A90E2',
  gradeB: '#3DD68C',
};

// Compose Typography 스케일 이식 (fontFamily는 시스템 기본)
export const typography = {
  displayLarge: { fontSize: 56, lineHeight: 64, fontWeight: '700' as const },
  displayMedium: { fontSize: 44, lineHeight: 52, fontWeight: '700' as const },
  displaySmall: { fontSize: 36, lineHeight: 44, fontWeight: '700' as const },
  headlineLarge: { fontSize: 32, lineHeight: 40, fontWeight: '700' as const },
  headlineMedium: { fontSize: 28, lineHeight: 36, fontWeight: '700' as const },
  headlineSmall: { fontSize: 24, lineHeight: 32, fontWeight: '700' as const },
  titleLarge: { fontSize: 22, lineHeight: 28, fontWeight: '700' as const },
  titleMedium: { fontSize: 18, lineHeight: 24, fontWeight: '700' as const },
  titleSmall: { fontSize: 14, lineHeight: 20, fontWeight: '700' as const },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: '400' as const },
  bodyMedium: { fontSize: 14, lineHeight: 20, fontWeight: '400' as const },
  bodySmall: { fontSize: 12, lineHeight: 16, fontWeight: '400' as const },
  labelLarge: { fontSize: 14, lineHeight: 20, fontWeight: '700' as const },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: '700' as const },
  labelSmall: { fontSize: 11, lineHeight: 16, fontWeight: '700' as const },
};

// Shape.kt: small/medium/large 코너 반경
export const radius = { small: 14, medium: 20, large: 28, pill: 999 };

// 8dp 스페이싱 스케일
export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 20, xxl: 24, xxxl: 32 };

export function gradeColor(grade: string, colors: ThemeColors): string {
  const g = String(grade).toUpperCase();
  if (g.startsWith('S')) return colors.gradeS;
  if (g === 'A') return colors.gradeA;
  if (g === 'B') return colors.gradeB;
  return colors.outline;
}
