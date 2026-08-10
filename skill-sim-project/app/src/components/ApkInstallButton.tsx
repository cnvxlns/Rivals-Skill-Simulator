import React from 'react';
import { Linking, Platform, Text, View } from 'react-native';
import { PrimaryActionButton } from './ui';
import { useAppTheme } from '../theme/useTheme';
import { useTranslation } from '../lib/i18n';

// EAS internal distribution 설치 URL. 빌드마다 UUID가 바뀌므로 Vercel 환경변수로 주입한다.
const APK_URL = process.env.EXPO_PUBLIC_APK_URL?.trim();

/** 안드로이드 브라우저에서 웹으로 접속했을 때만 노출한다. */
function shouldShow(): boolean {
  if (!APK_URL) return false;
  if (Platform.OS !== 'web') return false;
  if (typeof navigator === 'undefined') return false;
  return /android/i.test(navigator.userAgent);
}

export default function ApkInstallButton() {
  const { colors, typography, spacing } = useAppTheme();
  const { t } = useTranslation();

  if (!shouldShow()) {
    return null;
  }

  return (
    <View style={{ gap: spacing.xs }}>
      <PrimaryActionButton text={t('apk_install')} onPress={() => Linking.openURL(APK_URL!)} />
      <Text style={[typography.bodySmall, { color: colors.secondaryText, textAlign: 'center' }]}>
        {t('apk_install_hint')}
      </Text>
    </View>
  );
}
