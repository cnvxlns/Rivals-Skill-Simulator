import { darkColors, ThemeColors, typography, radius, spacing, gradeColor } from './index';

export type AppTheme = {
  scheme: 'light' | 'dark';
  colors: ThemeColors;
  typography: typeof typography;
  radius: typeof radius;
  spacing: typeof spacing;
  gradeColor: (grade: string) => string;
};

/**
 * 앱 전체를 항상 다크로 고정한다.
 *
 * 아이콘·스플래시가 다크 기준으로 만들어져 있어 시스템 설정을 따라가면 톤이 어긋난다.
 * lightColors는 theme/index.ts에 남겨 두었으니 되돌릴 때 여기만 고치면 된다.
 */
export function useAppTheme(): AppTheme {
  const colors = darkColors;
  return {
    scheme: 'dark',
    colors,
    typography,
    radius,
    spacing,
    gradeColor: (grade: string) => gradeColor(grade, colors),
  };
}
