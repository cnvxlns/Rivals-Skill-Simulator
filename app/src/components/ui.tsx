// Android(Compose) 룩을 재현한 공용 RN UI 프리미티브.
// 원본: android/.../ui/common/SportyComponents.kt, CommonComponents.kt
import React, { useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  ViewStyle,
} from 'react-native';
import { useAppTheme } from '../theme/useTheme';

export function SectionCard({ title, children, style }: { title?: string; children: React.ReactNode; style?: ViewStyle }) {
  const { colors, radius, spacing, typography } = useAppTheme();
  return (
    <View
      style={[
        {
          backgroundColor: colors.surface,
          borderRadius: radius.medium,
          borderWidth: 1,
          borderColor: colors.outline,
          padding: spacing.xl,
          gap: 14,
        },
        style,
      ]}
    >
      {title ? (
        <Text style={[typography.titleMedium, { color: colors.onSurface }]}>{title}</Text>
      ) : null}
      {children}
    </View>
  );
}

export function InfoChip({ text, style }: { text: string; style?: ViewStyle }) {
  const { colors, typography } = useAppTheme();
  return (
    <View
      style={[
        {
          backgroundColor: colors.surfaceVariant,
          borderColor: colors.outline,
          borderWidth: 1,
          borderRadius: 999,
          paddingHorizontal: 10,
          paddingVertical: 5,
        },
        style,
      ]}
    >
      <Text style={[typography.labelSmall, { color: colors.secondaryText }]} numberOfLines={1}>
        {text}
      </Text>
    </View>
  );
}

export function GradeChip({ grade, style }: { grade: string; style?: ViewStyle }) {
  const { colors, typography, gradeColor } = useAppTheme();
  const g = String(grade).toUpperCase();
  const container = gradeColor(g);
  const content = g === 'A' ? '#FFFFFF' : g.startsWith('S') || g === 'B' ? colors.onPrimary : colors.onSurface;
  return (
    <View
      style={[
        { backgroundColor: container, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 5 },
        style,
      ]}
    >
      <Text style={[typography.labelSmall, { color: content }]} numberOfLines={1}>
        {g}
      </Text>
    </View>
  );
}

export function PrimaryActionButton({
  text,
  onPress,
  enabled = true,
  loading = false,
  style,
}: {
  text: string;
  onPress: () => void;
  enabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
}) {
  const { colors, typography } = useAppTheme();
  const disabled = !enabled || loading;
  const bg = loading ? colors.primary : disabled ? colors.outline : colors.primary;
  const fg = loading ? colors.onPrimary : disabled ? colors.secondaryText : colors.onPrimary;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        {
          backgroundColor: bg,
          opacity: loading ? 0.72 : pressed ? 0.85 : 1,
          borderRadius: 999,
          paddingVertical: 14,
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'row',
          gap: 8,
        },
        style,
      ]}
    >
      {loading ? <ActivityIndicator size="small" color={fg} /> : null}
      <Text style={[typography.labelLarge, { color: fg }]}>{text}</Text>
    </Pressable>
  );
}

export function ScoreHero({ label, value, style }: { label: string; value: number; style?: ViewStyle }) {
  const { colors, radius, spacing, typography } = useAppTheme();
  return (
    <View
      style={[
        {
          backgroundColor: colors.surfaceVariant,
          borderRadius: radius.medium,
          borderWidth: 1,
          borderColor: colors.outline,
          flexDirection: 'row',
          alignItems: 'center',
          padding: spacing.xl,
        },
        style,
      ]}
    >
      <View style={{ width: 4, height: 58, borderRadius: 50, backgroundColor: colors.primary }} />
      <View style={{ width: spacing.lg }} />
      <View style={{ gap: 2 }}>
        <Text style={[typography.labelMedium, { color: colors.secondaryText }]} numberOfLines={1}>
          {label}
        </Text>
        <Text style={[typography.displaySmall, { color: colors.onSurface }]} numberOfLines={1}>
          {value.toFixed(2)}
        </Text>
      </View>
    </View>
  );
}

// 알약형 세그먼트 탭 (SegmentedTabs)
export function SegmentedTabs<T extends string>({
  tabs,
  selected,
  onSelect,
}: {
  tabs: { key: T; label: string }[];
  selected: T;
  onSelect: (key: T) => void;
}) {
  const { colors, typography } = useAppTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: colors.surfaceVariant,
        borderColor: colors.outline,
        borderWidth: 1,
        borderRadius: 999,
        padding: 4,
        gap: 4,
      }}
    >
      {tabs.map((tab) => {
        const isActive = tab.key === selected;
        return (
          <Pressable
            key={tab.key}
            onPress={() => onSelect(tab.key)}
            style={{
              flex: 1,
              minHeight: 40,
              borderRadius: 999,
              alignItems: 'center',
              justifyContent: 'center',
              paddingHorizontal: 12,
              paddingVertical: 10,
              backgroundColor: isActive ? colors.primary : 'transparent',
            }}
          >
            <Text
              style={[typography.labelLarge, { color: isActive ? colors.onPrimary : colors.secondaryText }]}
              numberOfLines={1}
            >
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

// 라벨 붙은 드롭다운 (LabeledDropdown) - 모달 기반 크로스플랫폼 셀렉트
export function LabeledDropdown<T>({
  label,
  selected,
  options,
  optionLabel,
  onSelect,
  disabled = false,
}: {
  label: string;
  selected: T;
  options: T[];
  optionLabel: (opt: T) => string;
  onSelect: (opt: T) => void;
  disabled?: boolean;
}) {
  const { colors, radius, typography } = useAppTheme();
  const [open, setOpen] = useState(false);
  return (
    <View style={{ gap: 4 }}>
      <Text style={[typography.labelMedium, { color: colors.secondaryText }]}>{label}</Text>
      <Pressable
        onPress={() => !disabled && setOpen(true)}
        style={{
          backgroundColor: colors.surfaceVariant,
          borderColor: colors.outline,
          borderWidth: 1,
          borderRadius: radius.small,
          paddingHorizontal: 14,
          paddingVertical: 12,
          flexDirection: 'row',
          justifyContent: 'space-between',
          alignItems: 'center',
          opacity: disabled ? 0.5 : 1,
        }}
      >
        <Text style={[typography.bodyMedium, { color: colors.onSurface }]} numberOfLines={1}>
          {optionLabel(selected)}
        </Text>
        <Text style={{ color: colors.secondaryText }}>▾</Text>
      </Pressable>

      <Modal transparent visible={open} animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.modalBackdrop} onPress={() => setOpen(false)}>
          <View style={[styles.modalSheet, { backgroundColor: colors.surface, borderColor: colors.outline }]}>
            <Text style={[typography.titleSmall, { color: colors.secondaryText, padding: 16 }]}>{label}</Text>
            <ScrollView>
              {options.map((opt, idx) => {
                const isSel = optionLabel(opt) === optionLabel(selected);
                return (
                  <Pressable
                    key={`${optionLabel(opt)}-${idx}`}
                    onPress={() => {
                      onSelect(opt);
                      setOpen(false);
                    }}
                    style={{ paddingHorizontal: 16, paddingVertical: 14, backgroundColor: isSel ? colors.surfaceVariant : 'transparent' }}
                  >
                    <Text style={[typography.bodyLarge, { color: isSel ? colors.primary : colors.onSurface }]}>
                      {optionLabel(opt)}
                    </Text>
                  </Pressable>
                );
              })}
            </ScrollView>
          </View>
        </Pressable>
      </Modal>
    </View>
  );
}

export function Toggle({ value, onToggle }: { value: boolean; onToggle: () => void }) {
  const { colors } = useAppTheme();
  return (
    <Pressable
      onPress={onToggle}
      style={{
        width: 44,
        height: 26,
        borderRadius: 999,
        padding: 3,
        backgroundColor: value ? colors.primary : colors.outline,
        justifyContent: 'center',
      }}
    >
      <View
        style={{
          width: 20,
          height: 20,
          borderRadius: 999,
          backgroundColor: value ? colors.onPrimary : colors.secondaryText,
          alignSelf: value ? 'flex-end' : 'flex-start',
        }}
      />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    padding: 24,
  },
  modalSheet: {
    borderRadius: 20,
    borderWidth: 1,
    maxHeight: '70%',
    overflow: 'hidden',
  },
});
