/** @type {import('next').NextConfig} */
const nextConfig = {
  // 브라우저의 /api/* 요청을 서버 내부에서 백엔드로 프록시한다.
  // 이렇게 하면 프론트엔드 터널 URL 하나만 외부에 노출하면 되고, CORS 설정이 필요 없다.
  // 백엔드 주소는 BACKEND_URL 환경변수로 덮어쓸 수 있다.
  async rewrites() {
    const rawBackend = process.env.BACKEND_URL
      || (process.env.NODE_ENV === 'production'
        ? 'https://rivals-skill-random-generator-api.onrender.com'
        : 'http://localhost:8080');
    // BACKEND_URL 끝에 슬래시가 붙어 있으면 destination이 `//api/...`가 되어
    // 백엔드가 404를 낸다. 끝의 슬래시를 제거해 견고하게 만든다.
    const backend = rawBackend.replace(/\/+$/, '');
    return [
      {
        source: '/api/:path*',
        destination: `${backend}/api/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
