// 웹에서 파일 하나를 고른다.
//
// 숨긴 <input type="file">을 그때그때 만들어 클릭한다. 화면에 붙여 두지 않는 이유는 버튼
// 모양을 우리 UI 키트(PrimaryActionButton)로 맞추기 위해서다. 브라우저는 사용자 제스처
// 안에서만 파일 창을 열어 주므로 onPress에서 곧바로 불러야 한다.
//
// 새 의존성을 쓰지 않는다. 네이티브 파일 선택에는 expo-document-picker가 필요한데, 지금
// OTA 정책이 runtimeVersion=appVersion이라 네이티브 모듈을 더하면 기존 APK가 업데이트를
// 받은 뒤 죽는다. 워크북은 어차피 PC에 있다.

export type PickedFile = { blob: Blob; name: string };

export const canPickFile = true;

export function pickFile(accept: string): Promise<PickedFile | null> {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.style.display = 'none';

    let settled = false;
    const finish = (file: File | null) => {
      if (settled) return;
      settled = true;
      input.remove();
      resolve(file ? { blob: file, name: file.name } : null);
    };

    input.onchange = () => finish(input.files?.[0] ?? null);
    // 취소 이벤트를 주지 않는 브라우저가 있다. 창이 닫히고 포커스가 돌아올 때 한 번 더 본다.
    input.oncancel = () => finish(null);
    window.addEventListener('focus', () => setTimeout(() => finish(null), 300), { once: true });

    document.body.appendChild(input);
    input.click();
  });
}
