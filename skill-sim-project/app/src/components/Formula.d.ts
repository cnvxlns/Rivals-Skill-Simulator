// Formula는 플랫폼 확장자(.web.tsx / .native.tsx)로만 존재한다.
// Metro는 알아서 고르지만 TypeScript는 못 찾으므로 여기서 형태만 선언한다.
declare const Formula: (props: { tex: string; fallback: string }) => JSX.Element;
export default Formula;
