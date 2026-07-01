/** @type {import('next').NextConfig} */
const nextConfig = {
  // 브라우저의 /api/* 요청을 서버 내부에서 백엔드로 프록시한다.
  // 이렇게 하면 프론트엔드 터널 URL 하나만 외부에 노출하면 되고, CORS 설정이 필요 없다.
  // 백엔드 주소는 BACKEND_URL 환경변수로 덮어쓸 수 있다.
  async rewrites() {
    const backend = process.env.BACKEND_URL
      || (process.env.NODE_ENV === 'production'
        ? 'https://rivals-skill-random-generator-api.onrender.com'
        : 'http://localhost:8080');
    return [
      {
        source: '/api/:path*',
        destination: `${backend}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
