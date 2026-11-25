import React, { useMemo } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import { useAppTheme } from '../theme/useTheme';

/** KaTeX로 수식을 조판한다. */
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
    return <div style={{ color: colors.onSurface, fontFamily: 'monospace', fontSize: 17 }}>{fallback}</div>;
  }

  return (
    <div
      style={{ color: colors.onSurface, padding: '2px 0' }}
      // KaTeX가 생성한 마크업이며 사용자 입력이 아니다.
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
