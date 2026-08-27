import {
  apkButton,
  banner,
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
  apkButton: typeof apkButton;
  inactiveRow: typeof inactiveRow;
  tierColor: typeof tierColor;
  gradeStyle: typeof gradeStyle;
};

/**
 * 앱 전체를 항상 다크로 고정한다. 라이트 모드는 만들지 않는다.
 * 아이콘·스플래시가 다크 기준으로 만들어져 있어 시스템 설정을 따라가면 톤이 어긋난다.
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
    apkButton,
    inactiveRow,
    tierColor,
    gradeStyle,
  };
}
