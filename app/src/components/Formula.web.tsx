import React, { useMemo } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import { useAppTheme } from '../theme/useTheme';

/**
 * 웹 전용 수식 렌더러. KaTeX로 조판한다.
 *
 * 네이티브에는 DOM이 없어 같은 파일명의 .native.tsx가 유니코드 텍스트로 대체한다.
 * Metro가 플랫폼 확장자를 보고 알아서 고른다.
 */
export default function Formula({ tex, fallback }: { tex: string; fallback: string }) {
  const { colors } = useAppTheme();
  const html = useMemo(() => {
    try {
      return katex.renderToString(tex, { throwOnError: false, displayMode: true });
    } catch {
      return null;
    }
  }, [tex]);

  if (!html) {
    return <div style={{ color: colors.onSurface, fontFamily: 'monospace', fontSize: 13 }}>{fallback}</div>;
  }

  return (
    <div
      style={{ color: colors.onSurface, padding: '2px 0' }}
      // KaTeX가 생성한 마크업이며 사용자 입력이 아니다.
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
