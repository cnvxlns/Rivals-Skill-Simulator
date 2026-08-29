import React from 'react';
import { Linking, Platform, Pressable, Text, View } from 'react-native';
import { useAppTheme } from '../theme/useTheme';
import { useTranslation } from '../lib/i18n';
import { useResponsive } from '../lib/useResponsive';
import { DownloadIcon } from './icons';

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
  const { apkButton, colors, radius, spacing } = useAppTheme();
  const { t } = useTranslation();
  const { isWide } = useResponsive();

  if (!shouldShow()) {
    return null;
  }

  return (
    <View style={{ gap: spacing.xs }}>
      <Pressable
        onPress={() => Linking.openURL(APK_URL!)}
        style={({ pressed }) => ({
          width: '100%',
          height: isWide ? 64 : 66,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 13,
          backgroundColor: apkButton.background,
          borderWidth: 1,
          borderColor: apkButton.border,
          borderRadius: radius.input,
          opacity: pressed ? 0.72 : 1,
        })}
      >
        <DownloadIcon size={20} color={apkButton.text} />
        <Text style={{ color: apkButton.text, fontSize: 18, fontWeight: '700' }}>{t('apk_install')}</Text>
      </Pressable>
      <Text
        style={{
          color: colors.mutedFaint,
          fontSize: 15,
          lineHeight: 17.6,
          textAlign: isWide ? 'left' : 'center',
        }}
      >
        {t('apk_install_hint')}
      </Text>
    </View>
  );
}
