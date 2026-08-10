import { useColorScheme } from 'react-native';
import { darkColors, lightColors, ThemeColors, typography, radius, spacing, gradeColor } from './index';

export type AppTheme = {
  scheme: 'light' | 'dark';
  colors: ThemeColors;
  typography: typeof typography;
  radius: typeof radius;
  spacing: typeof spacing;
  gradeColor: (grade: string) => string;
};

export function useAppTheme(): AppTheme {
  const raw = useColorScheme();
  const scheme: 'light' | 'dark' = raw === 'light' ? 'light' : 'dark';
  const colors = scheme === 'dark' ? darkColors : lightColors;
  return {
    scheme,
    colors,
    typography,
    radius,
    spacing,
    gradeColor: (grade: string) => gradeColor(grade, colors),
  };
}
