// FilePicker는 플랫폼 확장자(.web.tsx / .native.tsx)로만 존재한다.
// Metro는 알아서 고르지만 TypeScript는 못 찾으므로 여기서 형태만 선언한다.
// Formula와 같은 방식이다.

export type PickedFile = { blob: Blob; name: string };

/** 파일 고르기를 지원하는 플랫폼인가. 네이티브는 false다. */
export declare const canPickFile: boolean;

/**
 * 파일 하나를 고른다. 취소하면 null.
 *
 * @param accept 받아들일 확장자·MIME. 웹의 input accept와 같은 형식이다.
 */
export declare function pickFile(accept: string): Promise<PickedFile | null>;
