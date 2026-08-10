import React, { useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { LanguageCode } from '../locales/translations';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';

const options: { value: LanguageCode; label: string }[] = [
  { value: 'KR', label: '한국어' },
  { value: 'EN', label: 'English' },
  { value: 'JP', label: '日本語' },
  { value: 'ES', label: 'Español' },
  { value: 'CN', label: '繁體中文' },
];

export default function LanguageSelector() {
  const { language, setLanguage } = useTranslation();
  const { colors, radius, typography } = useAppTheme();
  const [open, setOpen] = useState(false);
  const current = options.find((o) => o.value === language)?.label ?? language;

  return (
    <>
      <Pressable
        onPress={() => setOpen(true)}
        style={{
          backgroundColor: colors.surfaceVariant,
          borderColor: colors.outline,
          borderWidth: 1,
          borderRadius: radius.small,
          paddingHorizontal: 12,
          paddingVertical: 8,
          flexDirection: 'row',
          alignItems: 'center',
          gap: 6,
        }}
      >
        <Text style={[typography.labelMedium, { color: colors.onSurface }]}>{current}</Text>
        <Text style={{ color: colors.secondaryText }}>▾</Text>
      </Pressable>

      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 24 }}
          onPress={() => setOpen(false)}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderColor: colors.outline,
              borderWidth: 1,
              borderRadius: 20,
              overflow: 'hidden',
            }}
          >
            {options.map((opt) => {
              const isSel = opt.value === language;
              return (
                <Pressable
                  key={opt.value}
                  onPress={() => {
                    setLanguage(opt.value);
                    setOpen(false);
                  }}
                  style={{
                    paddingHorizontal: 16,
                    paddingVertical: 14,
                    backgroundColor: isSel ? colors.surfaceVariant : 'transparent',
                  }}
                >
                  <Text style={[typography.bodyLarge, { color: isSel ? colors.primary : colors.onSurface }]}>
                    {opt.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </Pressable>
      </Modal>
    </>
  );
}
