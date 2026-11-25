import {
  banner,
  cardGradeColor,
  colors,
  controlHeight,
  fontFamily,
  gradeStyle,
  inactiveRow,
  radius,
  spacing,
  tabularNums,
  tierColor,
  typography,
} from './index';

export type AppTheme = {
  colors: typeof colors;
  typography: typeof typography;
  radius: typeof radius;
  spacing: typeof spacing;
  controlHeight: typeof controlHeight;
  fontFamily: typeof fontFamily;
  tabularNums: typeof tabularNums;
  banner: typeof banner;
  inactiveRow: typeof inactiveRow;
  tierColor: typeof tierColor;
  cardGradeColor: typeof cardGradeColor;
  gradeStyle: typeof gradeStyle;
};

/**
 * 앱 전체를 항상 다크로 고정한다. 라이트 모드는 만들지 않는다.
 */
export function useAppTheme(): AppTheme {
  return {
    colors,
    typography,
    radius,
    spacing,
    controlHeight,
    fontFamily,
    tabularNums,
    banner,
    inactiveRow,
    tierColor,
    cardGradeColor,
    gradeStyle,
  };
}
