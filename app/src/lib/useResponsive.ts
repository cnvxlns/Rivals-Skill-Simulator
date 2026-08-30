import { useEffect, useState } from 'react';
import { Dimensions, Platform } from 'react-native';

/** 웹에서만 의미가 있는 폭 기준. 네이티브는 항상 1단으로 본다. */
export const BREAKPOINTS = {
  /** 이 폭부터 폼을 2열로 깐다. */
  wide: 720,
  /** 이 폭부터 표를 좌우 2단으로 나눈다. */
  split: 1000,
} as const;

/** 넓은 화면에서 본문 좌우에 두는 고정 여백. 폭 상한 대신 이것만 두고 화면을 채운다. */
export const GUTTER = 48;

/**
 * 최소 열 폭을 기준으로 지금 화면에 들어갈 열 수를 구한다.
 *
 * 브레이크포인트마다 열 수를 박아두면 2560, 3440 같은 폭이 나올 때마다 분기를 늘려야
 * 한다. 열 폭의 하한만 정해 두면 화면이 넓어지는 만큼 열이 따라 늘어난다.
 *
 * 첫 렌더의 width는 0이다(아래 useResponsive 주석 참고). 그때는 min을 돌려준다.
 */
export function columnsFor(
  width: number,
  minColumnWidth: number,
  { min = 1, max = 6 }: { min?: number; max?: number } = {},
): number {
  if (width <= 0) return min;
  const fits = Math.floor((width - GUTTER * 2) / minColumnWidth);
  return Math.max(min, Math.min(max, fits));
}

export type Responsive = {
  width: number;
  /** 폼 컨트롤을 2열로 배치할 수 있는가. */
  isWide: boolean;
  /** 표를 좌우로 이등분할 수 있는가. */
  isSplit: boolean;
};

/**
 * 화면 폭 기반 레이아웃 판정.
 *
 * `useWindowDimensions`를 그대로 쓰면 안 된다. web.output이 "static"이라 HTML을 미리
 * 생성하는데 그 시점엔 window가 없어 폭이 0으로 잡히고, 좁은 화면 기준 마크업이 구워진다.
 * React 하이드레이션은 속성 불일치를 덮어쓰지 않으므로 그 좁은 레이아웃이 그대로 남는다.
 *
 * 그래서 첫 렌더는 서버와 동일하게 0으로 맞추고, 마운트 후 상태를 갱신해 리렌더를 일으킨다.
 * 상태 변경은 하이드레이션과 달리 DOM에 확실히 반영된다.
 */
export function useResponsive(): Responsive {
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const apply = () => setWidth(Dimensions.get('window').width);
    apply();
    const sub = Dimensions.addEventListener('change', apply);
    return () => sub.remove();
  }, []);

  const web = Platform.OS === 'web';
  return {
    width,
    isWide: web && width >= BREAKPOINTS.wide,
    isSplit: web && width >= BREAKPOINTS.split,
  };
}
