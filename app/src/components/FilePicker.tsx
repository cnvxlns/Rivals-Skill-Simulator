// 웹에서 파일 하나를 고른다.
//
// 숨긴 <input type="file">을 그때그때 만들어 클릭한다. 화면에 붙여 두지 않는 이유는 버튼
// 모양을 우리 UI 키트(PrimaryActionButton)로 맞추기 위해서다. 브라우저는 사용자 제스처
// 안에서만 파일 창을 열어 주므로 onPress에서 곧바로 불러야 한다.

export type PickedFile = { blob: Blob; name: string };

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
