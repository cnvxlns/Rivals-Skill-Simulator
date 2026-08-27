import React from 'react';
import { ScrollViewStyleReset } from 'expo-router/html';
import type { PropsWithChildren } from 'react';

/**
 * 웹 정적 렌더링의 HTML 셸. 네이티브에는 영향이 없다.
 *
 * 앱 화면은 다크 배경을 직접 칠하지만 html/body는 브라우저 기본(흰색)이라,
 * 스크롤 바운스나 콘텐츠가 짧은 화면에서 흰 바탕이 비친다.
 */
export default function Root({ children }: PropsWithChildren) {
  return (
    <html lang="ko">
      <head>
        <meta charSet="utf-8" />
        <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
        <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no" />
        <meta name="color-scheme" content="dark" />
        <meta name="theme-color" content="#0B1020" />
        <ScrollViewStyleReset />
        <style dangerouslySetInnerHTML={{ __html: BACKGROUND_STYLE }} />
      </head>
      <body>{children}</body>
    </html>
  );
}

const BACKGROUND_STYLE = `
html, body, #root { background-color: #0B1020; color-scheme: dark; }
`;
