import React from 'react';
import { Modal, Text, View } from 'react-native';
import { useTranslation } from '../lib/i18n';
import { WarmupStatus } from '../lib/useBackendWarmup';
import { useAppTheme } from '../theme/useTheme';
import { PrimaryActionButton } from './ui';
import { AppMark } from './icons';

export default function WakeUpOverlay({
  status,
  elapsedSeconds,
  onRetry,
}: {
  status: WarmupStatus;
  elapsedSeconds: number;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, radius, spacing, tabularNums } = useAppTheme();

  const visible = status === 'waking' || status === 'error';
  if (!visible) return null;
  const isError = status === 'error';

  return (
    <Modal transparent visible animationType="fade">
      <View
        style={{
          flex: 1,
          backgroundColor: 'rgba(7,8,11,0.88)',
          alignItems: 'center',
          justifyContent: 'center',
          padding: spacing.xxl,
        }}
      >
        <View
          style={{
            width: '100%',
            maxWidth: 530,
            backgroundColor: colors.surface,
            borderColor: colors.outline,
            borderWidth: 1,
            borderRadius: radius.shell,
            padding: 46,
            alignItems: 'center',
            gap: spacing.md,
          }}
        >
          {!isError ? (
            <>
              <AppMark size={44} />
              <Text style={[typography.card, { color: colors.onSurface, textAlign: 'center' }]}>
                {t('warmup_waking')}
              </Text>
              <Text style={{ color: colors.secondaryText, fontSize: 16, lineHeight: 19, textAlign: 'center' }}>
                {t('warmup_sub_waking')}
              </Text>
              <View
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderRadius: radius.pill,
                  paddingHorizontal: 22,
                  paddingVertical: 10,
                }}
              >
                <Text
                  style={[
                    { color: colors.accentValue, fontSize: 16, lineHeight: 19, fontWeight: '800' },
                    tabularNums,
                  ]}
                >
                  {t('warmup_elapsed')}: {elapsedSeconds}s
                </Text>
              </View>
            </>
          ) : (
            <>
              <Text style={[typography.card, { color: colors.error, textAlign: 'center' }]}>
                {t('warmup_error')}
              </Text>
              <PrimaryActionButton
                text={t('warmup_retry')}
                onPress={onRetry}
                style={{ marginTop: spacing.md, width: '100%' }}
              />
            </>
          )}
        </View>
      </View>
    </Modal>
  );
}
