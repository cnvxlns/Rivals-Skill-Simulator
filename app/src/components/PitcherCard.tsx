'use client';

import type { ReactNode } from 'react';
import { Pressable, Text, View, type ViewStyle } from 'react-native';
import { useAppTheme } from '../theme/useTheme';

export const PITCHER_CARD_HEIGHT = 120;

export type PitcherCardData = {
  slot: string;
  name: string;
  subtitle?: string;
  accent?: string;
  fill?: string;
  complete: boolean;
};

export default function PitcherCard({
  slot,
  name,
  subtitle,
  accent,
  fill,
  complete,
  onPress,
  disabled,
  dragging,
  style,
  children,
}: PitcherCardData & {
  onPress: () => void;
  disabled?: boolean;
  dragging?: boolean;
  style: ViewStyle;
  children?: ReactNode;
}) {
  const { colors, typography, spacing, radius } = useAppTheme();
  return (
    <View style={[{
      height: PITCHER_CARD_HEIGHT,
      flexDirection: 'row',
      borderRadius: radius.control,
      borderWidth: 1,
      borderColor: dragging ? colors.accentAction : accent ?? colors.outline,
      backgroundColor: fill ?? colors.surfaceVariant,
    }, style]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={[slot, name, subtitle].filter(Boolean).join(', ')}
        disabled={disabled}
        onPress={onPress}
        style={({ pressed }) => ({
          flex: 1,
          minWidth: 0,
          gap: 4,
          padding: spacing.smd,
          opacity: pressed ? 0.75 : 1,
        })}
      >
        <Text style={{ ...typography.label, color: accent ?? colors.secondaryText }}>{slot}</Text>
        <Text numberOfLines={1} style={{ ...typography.card, color: complete ? colors.onSurface : colors.muted }}>
          {name}
        </Text>
        {subtitle ? (
          <Text numberOfLines={1} style={{ ...typography.label, color: colors.secondaryText }}>{subtitle}</Text>
        ) : null}
      </Pressable>
      {children}
    </View>
  );
}
