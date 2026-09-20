// 네이티브에서는 파일을 고르지 않는다.
//
// expo-document-picker가 있어야 하는데, 지금 OTA 정책이 runtimeVersion=appVersion(1.0.0)이라
// 네이티브 모듈을 더하면 기존 APK가 OTA를 받은 뒤 죽는다. 새 APK를 배포해야 안전하다.
// 워크북은 어차피 PC에 있으므로 웹에서 올리게 안내한다.
//
// 지원하게 될 때는 여기만 채우면 된다. 부르는 쪽은 canPickFile만 본다.

export type PickedFile = { blob: Blob; name: string };

export const canPickFile = false;

export async function pickFile(_accept: string): Promise<PickedFile | null> {
  return null;
}
