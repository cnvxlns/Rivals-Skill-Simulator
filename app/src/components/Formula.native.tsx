import React from 'react';
import { Text } from 'react-native';
import { useAppTheme } from '../theme/useTheme';

/**
 * 네이티브 수식 표시. LaTeX 대신 유니코드 조판된 문자열을 그대로 보여준다.
 *
 * RN에는 DOM이 없어 KaTeX를 쓸 수 없고, WebView 기반 래퍼는 네이티브 의존성이라
 * OTA로 문구를 못 고친다. 산정 방식 설명은 계속 다듬을 화면이라 그 제약이 크다.
 *
 * 가로 넘침 처리는 호출부가 감싼 ScrollView가 맡는다.
 */
export default function Formula({ tex, fallback }: { tex: string; fallback: string }) {
  const { colors } = useAppTheme();
  return (
    <Text style={{ color: colors.onSurface, fontSize: 13, lineHeight: 20, fontFamily: 'monospace' }}>{fallback}</Text>
  );
}
