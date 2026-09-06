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
 *
 * 기하는 실제 구장을 따른다. 파울선은 홈에서 정확히 45도로 뻗고 베이스도 그 선 위에
 * 놓이므로, 페어 지역 꼭짓점과 내야 흙 다이아몬드의 홈 쪽 꼭짓점이 같은 점에서 만난다.
 * (예전에는 파울선 각도가 45도가 아니라 둘이 어긋나 보였다.)
 */

/** 홈플레이트. 모든 좌표의 기준점이다. */
const HOME_X = 50;
const HOME_Y = 84;

/** 베이스 간 거리. 파울선이 45도이므로 x·y 증분이 같다. */
const BASE = 19;

/** 파울폴까지의 거리. 역시 45도라 x·y 증분이 같다. */
const FOUL = 40;

export default function BaseballField() {
  const { colors } = useAppTheme();

  const first = { x: HOME_X + BASE, y: HOME_Y - BASE };
  const second = { x: HOME_X, y: HOME_Y - BASE * 2 };
  const third = { x: HOME_X - BASE, y: HOME_Y - BASE };
  const leftPole = { x: HOME_X - FOUL, y: HOME_Y - FOUL };
  const rightPole = { x: HOME_X + FOUL, y: HOME_Y - FOUL };

  return (
    <Svg width="100%" height="100%" viewBox="0 0 100 100" fill="none">
      {/* 페어 지역. 홈에서 45도로 뻗은 파울선과 외야 펜스 곡선으로 닫는다. */}
      <Path
        d={`M ${HOME_X} ${HOME_Y} L ${leftPole.x} ${leftPole.y} Q 50 -6 ${rightPole.x} ${rightPole.y} Z`}
        fill={colors.fieldGrass}
        stroke={colors.fieldLine}
        strokeWidth={0.6}
      />

      {/* 내야 흙. 네 베이스를 잇는 마름모이며 홈 쪽 꼭짓점이 위 경로의 시작점과 같다. */}
      <Path
        d={`M ${HOME_X} ${HOME_Y} L ${first.x} ${first.y} L ${second.x} ${second.y} L ${third.x} ${third.y} Z`}
        fill={colors.fieldDirt}
        stroke={colors.fieldLine}
        strokeWidth={0.5}
      />

      {/* 홈 주변 흙. 실제 구장처럼 원형으로 깐다. */}
      <Circle
        cx={HOME_X}
        cy={HOME_Y}
        r={7.5}
        fill={colors.fieldDirt}
        stroke={colors.fieldLine}
        strokeWidth={0.5}
      />

      {/* 홈플레이트. */}
      <Path
        d={`M ${HOME_X - 1.2} ${HOME_Y - 1.4}
            L ${HOME_X + 1.2} ${HOME_Y - 1.4}
            L ${HOME_X + 1.2} ${HOME_Y + 0.2}
            L ${HOME_X} ${HOME_Y + 1.4}
            L ${HOME_X - 1.2} ${HOME_Y + 0.2} Z`}
        fill={colors.fieldLine}
      />

      {/* 1·2·3루 베이스. */}
      {[first, second, third].map((base) => (
        <Rect
          key={`${base.x}-${base.y}`}
          x={base.x - 1.3}
          y={base.y - 1.3}
          width={2.6}
          height={2.6}
          fill={colors.fieldLine}
        />
      ))}
    </Svg>
  );
}
