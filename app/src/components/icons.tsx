// 아이콘 4종. 세트를 도입하지 않고 react-native-svg로 직접 그린다.
// 원본 스펙: docs/design_handoff_mlb_rival_skill_tool/README.md 의 "아이콘 세트"
import React from 'react';
import Svg, { Circle, Path } from 'react-native-svg';

type IconProps = { size?: number; color?: string };

export function CaretDown({ size = 12, color = '#7E8798' }: IconProps) {
  return (
    <Svg width={size} height={(size / 12) * 8} viewBox="0 0 12 8" fill="none">
      <Path d="M1 1.5 L6 6.5 L11 1.5" stroke={color} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </Svg>
  );
}

export function SearchIcon({ size = 17, color = '#7E8798' }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20" fill="none">
      <Circle cx={8.5} cy={8.5} r={6} stroke={color} strokeWidth={1.9} />
      <Path d="M13 13 L18 18" stroke={color} strokeWidth={1.9} strokeLinecap="round" />
    </Svg>
  );
}

export function DownloadIcon({ size = 20, color = '#4CC9F0' }: IconProps) {
  return (
    <Svg width={size} height={size} viewBox="0 0 20 20" fill="none">
      <Path d="M10 2 L10 13" stroke={color} strokeWidth={1.9} strokeLinecap="round" />
      <Path d="M5.5 9 L10 13.5 L14.5 9" stroke={color} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round" />
      <Path d="M3 17 L17 17" stroke={color} strokeWidth={1.9} strokeLinecap="round" />
    </Svg>
  );
}

/**
 * 앱 마크. 트랙 링 + 75% 게이지 아크 + 야구공.
 * 40px 이하에서는 실밥을 생략하고 stroke를 굵혀야 뭉개지지 않는다.
 */
export function AppMark({ size = 26 }: { size?: number }) {
  const small = size <= 40;
  const stroke = small ? 8 : 7;
  return (
    <Svg width={size} height={size} viewBox="0 0 100 100" fill="none">
      <Circle cx={50} cy={50} r={38} stroke="#2A2C33" strokeWidth={stroke} fill="none" />
      <Path
        d="M 50 12 A 38 38 0 1 1 15 65"
        stroke="#3DF5A8"
        strokeWidth={stroke}
        strokeLinecap="round"
        fill="none"
      />
      <Circle cx={50} cy={50} r={20} fill="#E8EAED" />
      {!small ? (
        <>
          <Path d="M 38 36 Q 48 50 38 64" stroke="#101216" strokeWidth={3.5} strokeLinecap="round" fill="none" />
          <Path d="M 62 36 Q 52 50 62 64" stroke="#101216" strokeWidth={3.5} strokeLinecap="round" fill="none" />
        </>
      ) : null}
    </Svg>
  );
}
