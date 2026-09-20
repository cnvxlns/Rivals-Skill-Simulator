'use client';

import { useState } from 'react';
import { Text, View } from 'react-native';
import { canPickFile, pickFile } from '../components/FilePicker';
import { InfoBanner, PrimaryActionButton, SectionCard } from '../components/ui';
import { apiErrorMessage, importDeckWorkbook } from '../lib/api';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { DeckImportResponse, DeckImportWarning, DeckSaveRequest, PositionTraining } from '../types';

/** 워크북의 MIME과 확장자 둘 다 받는다. 브라우저·OS마다 무엇을 보내는지가 다르다. */
const ACCEPT = '.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

/**
 * 덱 관리 워크북을 올려 편집기를 채운다.
 *
 * 저장하지 않는다. 워크북에는 18명뿐이라 남은 여덟 자리(중계 셋과 후보 다섯)는 사용자가
 * 채워야 하고, 그러고 나서 저장 버튼을 누르면 그때 검증이 걸린다.
 *
 * 포지션 훈련은 따로 물어본다. 덱 하나가 아니라 **구단 전체**에 걸리는 설정이라 다른 덱의
 * 점수까지 같이 움직인다. 자동으로 적용하면 무엇이 바뀌었는지 아무도 모른다.
 */
export default function DeckImportCard({
  onApply,
  onApplyTraining,
}: {
  onApply: (draft: DeckSaveRequest, warnings: DeckImportWarning[]) => void;
  onApplyTraining: (training: PositionTraining) => void;
}) {
  const { t } = useTranslation();
  const { colors, spacing, typography } = useAppTheme();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<DeckImportResponse | null>(null);

  const upload = async () => {
    setError(null);
    const picked = await pickFile(ACCEPT);
    if (!picked) return;
    setBusy(true);
    try {
      const response = await importDeckWorkbook(picked.blob, picked.name);
      setResult(response);
      onApply(response.deck, response.warnings);
    } catch (err) {
      setError(apiErrorMessage(err) ?? t('deck_import_failed'));
      setResult(null);
    } finally {
      setBusy(false);
    }
  };

  const training = result?.positionTraining;
  // 포훈 안내는 따로 띄우므로 목록에서는 뺀다.
  const warnings = (result?.warnings ?? []).filter((w) => w.code !== 'POSITION_TRAINING_FOUND');

  return (
    <SectionCard title={t('deck_import_title')}>
      <View style={{ gap: spacing.md }}>
        <Text style={{ ...typography.body, color: colors.secondaryText }}>
          {t('deck_import_hint')}
        </Text>

        {canPickFile ? (
          <PrimaryActionButton
            text={t('deck_import_button')}
            onPress={upload}
            loading={busy}
          />
        ) : (
          <InfoBanner text={t('deck_import_web_only')} />
        )}

        {error ? <InfoBanner text={error} tone="error" /> : null}

        {result ? (
          <Text style={{ ...typography.label, color: colors.secondaryText }}>
            {t('deck_import_done').replace('{n}', String(result.deck.players.length))}
          </Text>
        ) : null}

        {training && Object.keys(training.slots).length ? (
          <View style={{ gap: spacing.sm }}>
            <InfoBanner text={t('deck_import_training_found')} />
            <PrimaryActionButton
              text={t('deck_import_training_apply')}
              onPress={() => onApplyTraining(training)}
            />
          </View>
        ) : null}

        {warnings.length ? (
          <View style={{ gap: spacing.xs }}>
            <Text style={{ ...typography.label, color: colors.onSurface }}>
              {t('deck_import_warnings').replace('{n}', String(warnings.length))}
            </Text>
            {warnings.map((warning, index) => (
              <Text
                key={`${warning.code}-${warning.cell ?? index}`}
                style={{ ...typography.label, color: colors.muted }}
              >
                {warning.cell ? `${warning.cell} · ` : ''}
                {warning.message}
              </Text>
            ))}
          </View>
        ) : null}
      </View>
    </SectionCard>
  );
}
