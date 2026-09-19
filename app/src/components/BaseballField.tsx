import React from 'react';
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import { useAppTheme } from '../theme/useTheme';

/**
 * 야구장 배경 그림.
 *
 * 100x100 좌표계로 그려 두고 부모가 크기를 정한다. 실제로 쓰는 영역은 [FIELD_VIEW_BOX]
 * 뿐이며 그 상자를 부모 칸에 늘려 채운다. 선수 칩도 [fieldRatio]로 같은 상자를 거치므로
 * 화면 크기가 바뀌어도 그림 위 제자리에 남는다.
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

/**
 * 그림이 실제로 차지하는 영역. 0~100 좌표계 안에서 잡은 값이다.
 *
 * 좌우는 파울폴(x 10·90), 위는 펜스 꼭대기(y 19), 아래는 홈 주변 흙 원의 끝(y 91.5)이며
 * 외곽선 굵기만큼 조금씩 넓혔다. 이 상자를 viewBox로 쓰고 `preserveAspectRatio`를 끄면
 * 그림이 부모 칸을 정확히 채운다 — 예전에는 100x100을 그대로 써서 위쪽에 20%에 가까운
 * 빈 띠가 남았고, 칸이 정사각형이 아니면 그림이 레터박스로 밀려 칩 좌표와도 어긋났다.
 */
export const FIELD_VIEW_BOX = { x: 9.5, y: 18.5, width: 81, height: 73.5 };

/**
 * 필드 좌표(0~100)를 부모 칸 안의 0~1 비율로 옮긴다.
 *
 * 선수 칩은 이 함수를 거쳐 놓이므로 그림과 같은 기준을 쓴다. 좌표를 두 벌 관리하지
 * 않으려고 `LINEUP_FIELD_POSITIONS`도 필드 좌표 그대로 둔다.
 */
export function fieldRatio(pos: { x: number; y: number }): { x: number; y: number } {
  return {
    x: (pos.x - FIELD_VIEW_BOX.x) / FIELD_VIEW_BOX.width,
    y: (pos.y - FIELD_VIEW_BOX.y) / FIELD_VIEW_BOX.height,
  };
}

export default function BaseballField() {
  const { colors } = useAppTheme();

  const first = { x: HOME_X + BASE, y: HOME_Y - BASE };
  const second = { x: HOME_X, y: HOME_Y - BASE * 2 };
  const third = { x: HOME_X - BASE, y: HOME_Y - BASE };
  const leftPole = { x: HOME_X - FOUL, y: HOME_Y - FOUL };
  const rightPole = { x: HOME_X + FOUL, y: HOME_Y - FOUL };

  // preserveAspectRatio를 끄는 것은 "칸을 꽉 채우라"는 뜻이다. 칸의 가로세로 비율이
  // 그림과 다르면 늘어나지만, 대신 여백이 남지 않고 칩이 그림 위 제자리에 놓인다.
  return (
    <Svg
      width="100%"
      height="100%"
      viewBox={`${FIELD_VIEW_BOX.x} ${FIELD_VIEW_BOX.y} ${FIELD_VIEW_BOX.width} ${FIELD_VIEW_BOX.height}`}
      preserveAspectRatio="none"
      fill="none"
    >
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
