'use client';

import { useRef, useState } from 'react';
import { PanResponder, Pressable, Text, View } from 'react-native';
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
  {
    value: BullpenTactic.DIVISION,
    label: 'deck_bullpen_tactic_division',
    effect: 'deck_bullpen_tactic_plus_four',
    condition: { minimum: WIN_DIVISION_MIN, label: 'deck_bullpen_tactic_win_minimum' },
  },
  AGGRESSIVE_OPTION,
  DEFAULT_OPTION,
];

const CHASE_OPTIONS: readonly TacticOption<ChaseTactic>[] = [
  {
    ...AGGRESSIVE_OPTION,
    condition: { minimum: CHASE_AGGRESSIVE_MIN, label: 'deck_bullpen_tactic_chase_minimum' },
  },
  DEFAULT_OPTION,
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

type CardRow = Omit<BullpenRow, 'role'>;

const CLOSERS = 'CLOSERS';
const LANES = [...RELIEVER_ROLES, CLOSERS] as const;
export const PITCHER_CARD_HEIGHT = 120;
const MOVE_CONTROL_SIZE = 32;

export function pitcherCardGrid(width: number, gap: number) {
  const columns = width >= 740 ? 4 : width >= 370 ? 2 : 1;
  return { columns, cardWidth: (width - (columns - 1) * gap) / columns };
}

type Lane = {
  role: (typeof LANES)[number];
  left: number;
  top: number;
  cardTop: number;
  bottom: number;
  count: number;
};

type Drag = {
  slot: string;
  fromIndex: number;
  x: number;
  y: number;
  source: MoveTarget;
  destination: MoveTarget;
  lanes: readonly Lane[];
  laneWidth: number;
  pitch: number;
};

const webHandleStyle = { touchAction: 'none', userSelect: 'none', cursor: 'grab' } as object;

export default function BullpenRoleLane({
  rows,
  closers,
  winTactic,
  chaseTactic,
  onWinTacticChange,
  onChaseTacticChange,
  onMove,
  onPress,
}: {
  rows: readonly BullpenRow[];
  closers: readonly CardRow[];
  winTactic: WinTactic;
  chaseTactic: ChaseTactic;
  onWinTacticChange: (tactic: WinTactic) => void;
  onChaseTacticChange: (tactic: ChaseTactic) => void;
  onMove: (fromIndex: number, targetRole: RelieverRole, targetIndexInRole: number) => void;
  onPress: (slot: string) => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();
  const [width, setWidth] = useState(0);
  const [headerHeights, setHeaderHeights] = useState<Partial<Record<Lane['role'], number>>>({});
  const drag = useRef<Drag | null>(null);
  const [preview, setPreview] = useState<Drag | null>(null);
  const view = preview
    ? moveRelieverRows(rows, preview.fromIndex, preview.destination.role, preview.destination.index)
    : rows;
  const gap = spacing.sm;
  const { columns: columnCount, cardWidth: laneWidth } = pitcherCardGrid(width, gap);
  const pitch = PITCHER_CARD_HEIGHT + gap;
  const cardsByRole = {
    [RelieverRole.WIN]: view.filter((row) => row.role === RelieverRole.WIN),
    [RelieverRole.CHASE]: view.filter((row) => row.role === RelieverRole.CHASE),
    [RelieverRole.LONG]: view.filter((row) => row.role === RelieverRole.LONG),
    [CLOSERS]: closers,
  };
  const lanes: Lane[] = [];
  const headersMeasured = LANES.every((role) => headerHeights[role] != null);
  let height = 0;
  for (let start = 0; start < LANES.length; start += columnCount) {
    const band = LANES.slice(start, start + columnCount);
    const headerHeight = Math.max(...band.map((role) => headerHeights[role] ?? 0));
    const cardTop = height + headerHeight + gap;
    const maxCards = Math.max(...band.map((role) => cardsByRole[role].length));
    const bottom = cardTop + maxCards * pitch;
    band.forEach((role, column) => lanes.push({
      role,
      left: column * (laneWidth + gap),
      top: height,
      cardTop,
      bottom,
      count: cardsByRole[role].length,
    }));
    height = bottom + gap;
  }

  function startDrag(row: BullpenRow, lane: Lane, index: number) {
    const fromIndex = rows.indexOf(row);
    if (fromIndex === -1 || drag.current) return;
    const source = { role: row.role, index };
    drag.current = {
      slot: row.slot,
      fromIndex,
      x: lane.left + laneWidth - 1 - MOVE_CONTROL_SIZE / 2,
      y: lane.cardTop + index * pitch + PITCHER_CARD_HEIGHT / 2 - MOVE_CONTROL_SIZE,
      source,
      destination: source,
      lanes,
      laneWidth,
      pitch,
    };
    setPreview(drag.current);
  }

  function moveDrag(dx: number, dy: number) {
    const current = drag.current;
    if (!current) return;
    const x = current.x + dx;
    const y = current.y + dy;
    const lane = current.lanes.find((candidate) =>
      x >= candidate.left - gap / 2 && x < candidate.left + current.laneWidth + gap / 2 &&
      y >= candidate.top - gap / 2 && y < candidate.bottom + gap / 2,
    );
    let destination = current.source;
    if (lane && lane.role !== CLOSERS && lane.count > 0) {
      const row = (y - lane.cardTop - PITCHER_CARD_HEIGHT / 2) / current.pitch;
      const sourceCount = current.lanes.find(({ role }) => role === current.source.role)?.count ?? 0;
      const inserts = lane.role !== current.source.role && sourceCount > 1;
      const index = inserts ? Math.ceil(row) : Math.round(row);
      destination = {
        role: lane.role,
        index: Math.max(0, Math.min(index, inserts ? lane.count : lane.count - 1)),
      };
    }
    if (destination.role === current.destination.role && destination.index === current.destination.index) return;
    drag.current = { ...current, destination };
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
    <View style={{ gap }}>
      <Text style={{ ...typography.label, color: colors.secondaryText }}>{t('deck_bullpen_move_hint')}</Text>
      <View
        onLayout={({ nativeEvent: { layout } }) => setWidth(layout.width)}
        style={{ height: width > 0 ? height - gap * 2 : undefined }}
      >
        {width > 0 ? lanes.flatMap((lane) => {
          const role = lane.role;
          const roleName = role === CLOSERS ? t('deck_label_closers') : t(RELIEVER_ROLE_KEYS[role]);
          const laneStyle = { position: 'absolute' as const, left: lane.left, width: laneWidth };
          return [
            <View
              key={role}
              onLayout={({ nativeEvent: { layout } }) => {
                const measured = Math.ceil(layout.height);
                setHeaderHeights((prev) => prev[role] === measured ? prev : { ...prev, [role]: measured });
              }}
              style={{ ...laneStyle, top: lane.top, gap: spacing.xs }}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.xs }}>
                <Text accessibilityRole="header" style={{ ...typography.card, color: colors.onSurface }}>
                  {roleName}
                </Text>
                <Text style={{
                  ...typography.label,
                  color: colors.secondaryText,
                  backgroundColor: colors.surfaceVariant,
                  borderRadius: radius.control,
                  paddingHorizontal: spacing.sm,
                  paddingVertical: spacing.xs,
                }}>
                  {lane.count}{t('deck_bullpen_count_suffix')}
                </Text>
              </View>
              {role === RelieverRole.WIN ? (
                <TacticGroup
                  roleName={roleName}
                  count={lane.count}
                  options={WIN_OPTIONS}
                  value={winTactic}
                  onChange={onWinTacticChange}
                  dragging={preview !== null}
                />
              ) : null}
              {role === RelieverRole.CHASE ? (
                <TacticGroup
                  roleName={roleName}
                  count={lane.count}
                  options={CHASE_OPTIONS}
                  value={chaseTactic}
                  onChange={onChaseTacticChange}
                  dragging={preview !== null}
                />
              ) : null}
            </View>,

            // Flat, slot-keyed cards keep the same responder while crossing roles in the preview.
            ...(headersMeasured ? cardsByRole[role] : []).map((card, rowIndex) => {
              const reliever = role === CLOSERS ? undefined : view.find(({ slot }) => slot === card.slot);
              const index = reliever ? view.indexOf(reliever) : -1;
              const slot = preview && reliever ? `RP${index + 1}` : card.slot;
              const dragging = preview?.slot === card.slot;
              return (
                <View key={card.slot} style={{
                  ...laneStyle,
                  top: lane.cardTop + rowIndex * pitch,
                  height: PITCHER_CARD_HEIGHT,
                  flexDirection: 'row',
                  alignItems: 'stretch',
                  borderRadius: radius.control,
                  borderWidth: 1,
                  borderColor: dragging ? colors.accentAction : card.accent ?? colors.outline,
                  backgroundColor: colors.surfaceVariant,
                }}>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={[slot, card.name, card.subtitle].filter(Boolean).join(', ')}
                    disabled={preview !== null}
                    onPress={() => onPress(card.slot)}
                    style={({ pressed }) => ({
                      flex: 1,
                      minWidth: 0,
                      gap: spacing.xs,
                      padding: spacing.sm,
                      opacity: pressed ? 0.75 : 1,
                    })}
                  >
                    <Text style={{ ...typography.label, color: card.accent ?? colors.secondaryText }}>{slot}</Text>
                    <Text numberOfLines={1} style={{ ...typography.card, color: colors.onSurface }}>{card.name}</Text>
                    {card.subtitle ? (
                      <Text numberOfLines={1} style={{ ...typography.label, color: colors.secondaryText }}>
                        {card.subtitle}
                      </Text>
                    ) : null}
                  </Pressable>
                  {reliever ? (
                    <View style={{ justifyContent: 'center' }}>
                      <DragHandle
                        label={slot + ' ' + t('deck_bullpen_move_label')}
                        onStart={() => startDrag(reliever, lane, rowIndex)}
                        onMove={moveDrag}
                        onFinish={finishDrag}
                      />
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
                  ) : null}
                </View>
              );
            }),
          ];
        }) : null}
      </View>
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
  onMove: (dx: number, dy: number) => void;
  onFinish: (commit: boolean) => void;
}) {
  const { colors, typography } = useAppTheme();
  const latest = useRef({ onStart, onMove, onFinish });
  latest.current = { onStart, onMove, onFinish };
  const [responder] = useState(() => PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onPanResponderTerminationRequest: () => false,
    onPanResponderGrant: () => latest.current.onStart(),
    onPanResponderMove: (_, gesture) => latest.current.onMove(gesture.dx, gesture.dy),
    onPanResponderRelease: () => latest.current.onFinish(true),
    onPanResponderTerminate: () => latest.current.onFinish(false),
  }));

  return (
    <View
      {...responder.panHandlers}
      accessibilityLabel={label}
      style={[
        { width: MOVE_CONTROL_SIZE, height: MOVE_CONTROL_SIZE, justifyContent: 'center', alignItems: 'center' },
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
        width: MOVE_CONTROL_SIZE,
        height: MOVE_CONTROL_SIZE,
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
  const { colors, typography, spacing } = useAppTheme();
  const selected = options.find((option) => option.value === value);
  const unavailable = options.find((option) => option.condition && count < option.condition.minimum);

  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={roleName + ' ' + t('deck_bullpen_tactics')}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: spacing.sm }}>
        {options.map((option) => {
          const disabled = dragging || option === unavailable;
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
                minHeight: 36,
                flexDirection: 'row',
                alignItems: 'center',
                gap: spacing.xs,
                opacity: pressed ? 0.75 : 1,
              })}
            >
              <Text style={{ ...typography.label, color: checked ? colors.accentAction : colors.secondaryText }}>
                {checked ? '◉' : '○'}
              </Text>
              <Text style={{ ...typography.label, color: disabled ? colors.muted : colors.onSurface }}>
                {title}
              </Text>
            </Pressable>
          );
        })}
      </View>
      {selected ? (
        <Text style={{ ...typography.caption, color: colors.secondaryText }}>{t(selected.effect)}</Text>
      ) : null}
      {unavailable?.condition ? (
        <Text style={{ ...typography.caption, color: colors.error }}>
          {t(unavailable.condition.label)}
        </Text>
      ) : null}
    </View>
  );
}
