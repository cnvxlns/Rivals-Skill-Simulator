import React from 'react';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { useAppTheme } from '../theme/useTheme';

/**
 * 야구장 배경 그림.
 *
 * 100x100 좌표계로 그려 두고 부모가 크기를 정한다. 그 위에 얹는 선수 칩도 같은
 * 비율 좌표(`LINEUP_FIELD_POSITIONS`)를 쓰므로 화면 크기가 바뀌어도 위치가 유지된다.
 *
 * 홈플레이트를 아래쪽 가운데 두고 외야를 위로 펼친, 중계 화면과 같은 시점이다.
 */
export default function BaseballField() {
  const { colors } = useAppTheme();

  return (
    <Svg width="100%" height="100%" viewBox="0 0 100 100" fill="none">
      {/* 페어 지역. 홈에서 좌우 파울폴까지 선을 긋고 외야 펜스를 곡선으로 잇는다. */}
      <Path
        d="M 50 90 L 7 40 Q 50 -6 93 40 Z"
        fill={colors.fieldGrass}
        stroke={colors.fieldLine}
        strokeWidth={0.6}
      />

      {/* 내야 흙. 홈 · 1루 · 2루 · 3루를 잇는 마름모. */}
      <Path
        d="M 50 88 L 71.5 66 L 50 44 L 28.5 66 Z"
        fill={colors.fieldDirt}
        stroke={colors.fieldLine}
        strokeWidth={0.5}
      />

      {/* 베이스 넷. 작게 찍어 위치만 알린다. */}
      {[
        [50, 88],
        [71.5, 66],
        [50, 44],
        [28.5, 66],
      ].map(([x, y]) => (
        <Rect
          key={`${x}-${y}`}
          x={x - 1.3}
          y={y - 1.3}
          width={2.6}
          height={2.6}
          fill={colors.fieldLine}
        />
      ))}

      {/* 투수 마운드. 라인업에는 투수가 없어 자리 표시만 한다. */}
      <Circle cx={50} cy={66} r={3.2} fill={colors.fieldDirt} stroke={colors.fieldLine} strokeWidth={0.5} />
    </Svg>
  );
}
