// 전체 페이지의 공통 메타데이터와 HTML 뼈대를 정의하는 레이아웃 컴포넌트
import './globals.css';

export const metadata = {
  title: 'Rivals Skill Change Simulator',
  description: 'Skill change simulator built with Next.js and Tailwind CSS',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}
