'use client';

import { useRef, useState } from 'react';
import { PanResponder, Platform, Pressable, Text, View } from 'react-native';
import { useTranslation } from '../lib/i18n';
import {
  CHASE_AGGRESSIVE_MIN,
  RELIEVER_ROLES,
  WIN_DIVISION_MIN,
  moveRelieverRows,
} from '../lib/useDeckEditor';
import type { TranslationKey } from '../locales/strings';
import { useAppTheme } from '../theme/useTheme';
import { BullpenTactic, RelieverRole, type ChaseTactic, type WinTactic } from '../types';

export const RELIEVER_ROLE_KEYS = {
  [RelieverRole.WIN]: 'deck_reliever_win',
  [RelieverRole.CHASE]: 'deck_reliever_chase',
  [RelieverRole.LONG]: 'deck_reliever_long',
} as const;

type TacticOption<Tactic> = {
  value: Tactic;
  label: TranslationKey;
  effect: TranslationKey;
  condition?: { minimum: number; label: TranslationKey };
};

const DEFAULT_OPTION = {
  value: BullpenTactic.DEFAULT,
  label: 'deck_bullpen_tactic_default',
  effect: 'deck_bullpen_tactic_no_buff',
} as const;

const AGGRESSIVE_OPTION = {
  value: BullpenTactic.AGGRESSIVE,
  label: 'deck_bullpen_tactic_aggressive',
  effect: 'deck_bullpen_tactic_plus_two',
} as const;

const WIN_OPTIONS: readonly TacticOption<WinTactic>[] = [
  DEFAULT_OPTION,
  AGGRESSIVE_OPTION,
  {
    value: BullpenTactic.DIVISION,
    label: 'deck_bullpen_tactic_division',
    effect: 'deck_bullpen_tactic_plus_four',
    condition: { minimum: WIN_DIVISION_MIN, label: 'deck_bullpen_tactic_win_minimum' },
  },
];

const CHASE_OPTIONS: readonly TacticOption<ChaseTactic>[] = [
  DEFAULT_OPTION,
  {
    ...AGGRESSIVE_OPTION,
    condition: { minimum: CHASE_AGGRESSIVE_MIN, label: 'deck_bullpen_tactic_chase_minimum' },
  },
];

type BullpenRow = {
  slot: string;
  role: RelieverRole;
  name: string;
  subtitle?: string;
  accent?: string;
};

type MoveTarget = {
  role: RelieverRole;
  index: number;
};

type MeasuredRow = MoveTarget & {
  center: number;
};

type Drag = {
  slot: string;
  fromIndex: number;
  origin: number;
  source: MoveTarget;
  destination: MoveTarget;
  centers: MeasuredRow[];
};

const webHandleStyle =
  Platform.OS === 'web'
    ? ({ touchAction: 'none', userSelect: 'none', cursor: 'grab' } as object)
    : null;

export default function BullpenRoleLane({
  rows,
  winTactic,
  chaseTactic,
  onWinTacticChange,
  onChaseTacticChange,
  onMove,
  onPress,
}: {
  rows: readonly BullpenRow[];
  winTactic: WinTactic;
  chaseTactic: ChaseTactic;
  onWinTacticChange: (tactic: WinTactic) => void;
  onChaseTacticChange: (tactic: ChaseTactic) => void;
  onMove: (fromIndex: number, targetRole: RelieverRole, targetIndexInRole: number) => void;
  onPress: (slot: string) => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();
  const centers = useRef(new Map<string, number>());
  const drag = useRef<Drag | null>(null);
  const [preview, setPreview] = useState<Drag | null>(null);
  const view = preview
    ? moveRelieverRows(rows, preview.fromIndex, preview.destination.role, preview.destination.index)
    : rows;

  function startDrag(slot: string) {
    const measured: MeasuredRow[] = [];
    const counts = new Map<RelieverRole, number>();
    let start: { fromIndex: number; row: MeasuredRow } | undefined;

    rows.forEach((row, fromIndex) => {
      const index = counts.get(row.role) ?? 0;
      counts.set(row.role, index + 1);
      const center = centers.current.get(row.slot);
      if (center === undefined) return;
      const entry = { role: row.role, index, center };
      measured.push(entry);
      if (row.slot === slot) start = { fromIndex, row: entry };
    });

    if (!start) return;
    const source = { role: start.row.role, index: start.row.index };
    drag.current = {
      slot,
      fromIndex: start.fromIndex,
      origin: start.row.center,
      source,
      destination: source,
      centers: measured,
    };
    setPreview(drag.current);
  }

  function moveDrag(dy: number) {
    const current = drag.current;
    if (!current) return;
    const position = current.origin + dy;
    const nearest = current.centers.reduce((closest, row) =>
      Math.abs(row.center - position) < Math.abs(closest.center - position) ? row : closest,
    );
    let index = nearest.index;
    const sourceCount = current.centers.filter((row) => row.role === current.source.role).length;
    if (nearest.role !== current.source.role && sourceCount > 1 && position > nearest.center) {
      index += 1;
    }
    if (nearest.role === current.destination.role && index === current.destination.index) return;
    drag.current = { ...current, destination: { role: nearest.role, index } };
    setPreview(drag.current);
  }

  function finishDrag(commit: boolean) {
    const current = drag.current;
    drag.current = null;
    setPreview(null);
    if (!commit || !current) return;
    const { fromIndex, source, destination } = current;
    if (source.role !== destination.role || source.index !== destination.index) {
      onMove(fromIndex, destination.role, destination.index);
    }
  }

  function step(fromIndex: number, delta: -1 | 1) {
    const next = rows[fromIndex + delta];
    if (!next) return;
    const groupIndex = rows.slice(0, fromIndex + delta).filter((row) => row.role === next.role).length;
    const crossesUp = delta === -1 && rows[fromIndex].role !== next.role;
    onMove(fromIndex, next.role, groupIndex + (crossesUp ? 1 : 0));
  }

  return (
    <View style={{ gap: spacing.sm }}>
      <Text style={{ ...typography.label, color: colors.secondaryText }}>{t('deck_bullpen_move_hint')}</Text>

      {RELIEVER_ROLES.flatMap((role) => {
        const assigned = view.filter((row) => row.role === role);
        const roleName = t(RELIEVER_ROLE_KEYS[role]);
        return [
          <View key={role} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
            <Text accessibilityRole="header" style={{ ...typography.card, color: colors.onSurface }}>
              {roleName}
            </Text>
            <Text
              style={{
                ...typography.label,
                color: colors.secondaryText,
                backgroundColor: colors.surfaceVariant,
                borderRadius: radius.control,
                paddingHorizontal: spacing.sm,
                paddingVertical: spacing.xs,
              }}
            >
              {assigned.length}{t('deck_bullpen_count_suffix')}
            </Text>
          </View>,

          // Group headers and rows share a parent so a handle keeps its responder when crossing roles.
          ...assigned.map((row) => {
            const index = view.indexOf(row);
            const slot = preview ? `RP${index + 1}` : row.slot;
            const dragging = preview?.slot === row.slot;
            return (
              <View
                key={row.slot}
                onLayout={({ nativeEvent: { layout } }) => {
                  centers.current.set(row.slot, layout.y + layout.height / 2);
                }}
                style={{
                  minHeight: 48,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: spacing.xs,
                  paddingHorizontal: spacing.xs,
                  borderRadius: radius.control,
                  borderWidth: 1,
                  borderColor: dragging ? colors.accentAction : row.accent ?? colors.outline,
                  backgroundColor: colors.surfaceVariant,
                }}
              >
                <DragHandle
                  label={slot + ' ' + t('deck_bullpen_move_label')}
                  onStart={() => startDrag(row.slot)}
                  onMove={moveDrag}
                  onFinish={finishDrag}
                />
                <Pressable
                  accessibilityRole="button"
                  disabled={preview !== null}
                  onPress={() => onPress(row.slot)}
                  style={({ pressed }) => ({
                    minHeight: 44,
                    flex: 1,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: spacing.sm,
                    opacity: pressed ? 0.75 : 1,
                  })}
                >
                  <Text style={{ ...typography.label, color: row.accent ?? colors.secondaryText }}>
                    {slot}
                  </Text>
                  <View style={{ flex: 1 }}>
                    <Text style={{ ...typography.card, color: colors.onSurface }}>{row.name}</Text>
                    {row.subtitle ? (
                      <Text style={{ ...typography.label, color: colors.secondaryText }}>{row.subtitle}</Text>
                    ) : null}
                  </View>
                </Pressable>
                <StepButton
                  symbol="▲"
                  label={slot + ' ' + t('deck_batting_order_up')}
                  disabled={preview !== null || index === 0}
                  onPress={() => step(index, -1)}
                />
                <StepButton
                  symbol="▼"
                  label={slot + ' ' + t('deck_batting_order_down')}
                  disabled={preview !== null || index === view.length - 1}
                  onPress={() => step(index, 1)}
                />
              </View>
            );
          }),

          ...(role === RelieverRole.WIN
            ? [(
                <TacticGroup
                  key="win-tactics"
                  roleName={roleName}
                  count={assigned.length}
                  options={WIN_OPTIONS}
                  value={winTactic}
                  onChange={onWinTacticChange}
                  dragging={preview !== null}
                />
              )]
            : []),
          ...(role === RelieverRole.CHASE
            ? [(
                <TacticGroup
                  key="chase-tactics"
                  roleName={roleName}
                  count={assigned.length}
                  options={CHASE_OPTIONS}
                  value={chaseTactic}
                  onChange={onChaseTacticChange}
                  dragging={preview !== null}
                />
              )]
            : []),
        ];
      })}
    </View>
  );
}

function DragHandle({
  label,
  onStart,
  onMove,
  onFinish,
}: {
  label: string;
  onStart: () => void;
  onMove: (dy: number) => void;
  onFinish: (commit: boolean) => void;
}) {
  const { colors, typography } = useAppTheme();
  const latest = useRef({ onStart, onMove, onFinish });
  latest.current = { onStart, onMove, onFinish };
  const [responder] = useState(() => PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onPanResponderTerminationRequest: () => false,
    onPanResponderGrant: () => latest.current.onStart(),
    onPanResponderMove: (_, gesture) => latest.current.onMove(gesture.dy),
    onPanResponderRelease: () => latest.current.onFinish(true),
    onPanResponderTerminate: () => latest.current.onFinish(false),
  }));

  return (
    <View
      {...responder.panHandlers}
      accessibilityLabel={label}
      style={[
        { minWidth: 32, minHeight: 44, justifyContent: 'center', alignItems: 'center' },
        webHandleStyle,
      ]}
    >
      <Text style={{ ...typography.card, color: colors.secondaryText }}>⠿</Text>
    </View>
  );
}

function StepButton({
  symbol,
  label,
  disabled,
  onPress,
}: {
  symbol: string;
  label: string;
  disabled: boolean;
  onPress: () => void;
}) {
  const { colors, typography } = useAppTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => ({
        minWidth: 32,
        minHeight: 44,
        justifyContent: 'center',
        alignItems: 'center',
        opacity: disabled ? 0.35 : pressed ? 0.75 : 1,
      })}
    >
      <Text style={{ ...typography.label, color: colors.secondaryText }}>{symbol}</Text>
    </Pressable>
  );
}

function TacticGroup<Tactic extends WinTactic>({
  roleName,
  count,
  options,
  value,
  onChange,
  dragging,
}: {
  roleName: string;
  count: number;
  options: readonly TacticOption<Tactic>[];
  value: Tactic;
  onChange: (value: Tactic) => void;
  dragging: boolean;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();
  const label = t('deck_bullpen_tactics');

  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={roleName + ' ' + label} style={{ gap: spacing.xs }}>
      <Text style={{ ...typography.label, color: colors.secondaryText }}>{label}</Text>
      {options.map((option) => {
        const belowMinimum = option.condition !== undefined && count < option.condition.minimum;
        const disabled = dragging || belowMinimum;
        const checked = option.value === value;
        const condition = option.condition ? t(option.condition.label) : '';
        const title = t(option.label);
        const effect = t(option.effect);
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityLabel={[title, effect, condition].filter(Boolean).join(', ')}
            accessibilityState={{ checked, disabled }}
            disabled={disabled}
            onPress={() => onChange(option.value)}
            style={({ pressed }) => ({
              minHeight: 44,
              flexDirection: 'row',
              alignItems: 'flex-start',
              gap: spacing.sm,
              paddingHorizontal: spacing.smd,
              paddingVertical: spacing.xs,
              borderRadius: radius.control,
              borderWidth: 1,
              borderColor: checked ? colors.accentAction : colors.outline,
              backgroundColor: checked ? colors.surfaceVariant : 'transparent',
              opacity: pressed ? 0.75 : 1,
            })}
          >
            <Text style={{ ...typography.body, color: checked ? colors.accentAction : colors.secondaryText }}>
              {checked ? '◉' : '○'}
            </Text>
            <View style={{ flex: 1 }}>
              <Text style={{ ...typography.card, color: disabled ? colors.muted : colors.onSurface }}>
                {title}
              </Text>
              <Text style={{ ...typography.caption, color: colors.secondaryText }}>{effect}</Text>
              {condition ? (
                <Text style={{ ...typography.caption, color: belowMinimum ? colors.error : colors.secondaryText }}>
                  {condition}
                </Text>
              ) : null}
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}
