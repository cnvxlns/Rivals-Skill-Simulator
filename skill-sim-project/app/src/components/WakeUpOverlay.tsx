import React from 'react';
import { ActivityIndicator, Modal, Text, View } from 'react-native';
import { useTranslation } from '../lib/i18n';
import { WarmupStatus } from '../lib/useBackendWarmup';
import { useAppTheme } from '../theme/useTheme';
import { PrimaryActionButton } from './ui';

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
  const { colors, typography, radius, spacing } = useAppTheme();

  const visible = status === 'waking' || status === 'error';
  if (!visible) return null;
  const isError = status === 'error';

  return (
    <Modal transparent visible animationType="fade">
      <View
        style={{
          flex: 1,
          backgroundColor: 'rgba(11,16,32,0.95)',
          alignItems: 'center',
          justifyContent: 'center',
          padding: spacing.xxl,
        }}
      >
        <View
          style={{
            width: '100%',
            maxWidth: 420,
            backgroundColor: colors.surface,
            borderColor: colors.outline,
            borderWidth: 1,
            borderRadius: radius.large,
            padding: spacing.xxxl,
            alignItems: 'center',
            gap: spacing.md,
          }}
        >
          {!isError ? (
            <>
              <ActivityIndicator size="large" color={colors.primary} />
              <Text style={[typography.titleMedium, { color: colors.onSurface, textAlign: 'center' }]}>
                {t('warmup_waking')}
              </Text>
              <Text style={[typography.bodySmall, { color: colors.secondaryText, textAlign: 'center' }]}>
                {t('warmup_sub_waking')}
              </Text>
              <View
                style={{
                  backgroundColor: colors.surfaceVariant,
                  borderRadius: 999,
                  paddingHorizontal: 14,
                  paddingVertical: 6,
                }}
              >
                <Text style={[typography.labelMedium, { color: colors.primary }]}>
                  {t('warmup_elapsed')}: {elapsedSeconds}s
                </Text>
              </View>
            </>
          ) : (
            <>
              <Text style={[typography.titleMedium, { color: colors.error, textAlign: 'center' }]}>
                {t('warmup_error')}
              </Text>
              <PrimaryActionButton text={t('warmup_retry')} onPress={onRetry} style={{ marginTop: spacing.md }} />
            </>
          )}
        </View>
      </View>
    </Modal>
  );
}
