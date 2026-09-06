'use client';

import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  InfoBanner,
  LabeledDropdown,
  LinkAction,
  PrimaryActionButton,
  ScoreHero,
  SectionCard,
  TextField,
} from '../components/ui';
import { cardTypeLabel } from '../lib/format';
import { useTranslation } from '../lib/i18n';
import { PITCHER_RANGES, PitcherField, isPlayerComplete, useDeckEditor } from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import { useResponsive } from '../lib/useResponsive';
import {
  BENCH_SLOTS,
  CardVariant,
  DeckDetail,
  DeckPlayer,
  LINEUP_FIELD_POSITIONS,
} from '../types';
import BaseballField from '../components/BaseballField';
import BattingOrderLane from '../components/BattingOrderLane';
import DeckPlayerEditor from './DeckPlayerEditor';

/** 변형이 있으면 등급 뒤에 붙여 보여 준다. 예: Signature Black · WBC */
const cardLabel = (player: DeckPlayer) => {
  const grade = cardTypeLabel(String(player.cardGrade));
  const variant = player.cardVariant;
  return !variant || variant === CardVariant.NONE ? grade : `${grade} · ${variant}`;
};

/** 다이아몬드 한 칸의 폭. 좁은 화면에서도 3열이 들어가야 해서 고정값으로 둔다. */
const FIELD_SLOT_WIDTH = 108;
/** 칩을 좌표의 정중앙에 놓기 위한 대략적인 높이. */
const FIELD_SLOT_HEIGHT = 52;

/**
 * 야구장과 타순을 한 행에 두기 시작하는 폭.
 *
 * 야구장은 620px에서 칩이 겹치지 않는다. 좌우 여백 48x2와 가운데 간격을 빼고도 한 칸이
 * 620을 넘겨야 하므로 표를 좌우로 나누는 기준(1000)보다 높게 잡는다.
 * 그 아래에서는 세로로 떨어진다.
 */
const LINEUP_SPLIT = 1360;

const PITCHER_FIELDS: { field: PitcherField; labelKey: 'deck_label_starters' | 'deck_label_relievers' | 'deck_label_closers' }[] = [
  { field: 'starters', labelKey: 'deck_label_starters' },
  { field: 'relievers', labelKey: 'deck_label_relievers' },
  { field: 'closers', labelKey: 'deck_label_closers' },
];

const PART_LABEL_KEYS = {
  LINEUP: 'deck_section_lineup',
  BENCH: 'deck_section_bench',
  ROTATION: 'deck_section_rotation',
  BULLPEN: 'deck_section_bullpen',
} as const;

const BUFF_FAMILY_LABEL_KEYS = {
  HOF: 'deck_buff_family_hof',
  SIGNATURE: 'deck_buff_family_signature',
  MOMENT: 'deck_buff_family_moment',
  LIVE: 'deck_buff_family_live',
  SEASON: 'deck_buff_family_season',
} as const;

/**
 * 26칸 로스터 편집기.
 *
 * 자리를 누르면 바텀시트로 선수 편집기가 열린다. 기존 LabeledDropdown이 이미 같은
 * 방식이라 화면 감각이 이어진다.
 */
export default function DeckEditorView({
  initial,
  onDone,
  onDelete,
}: {
  initial?: DeckDetail;
  onDone: () => void;
  onDelete?: (id: number) => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();
  const { width } = useResponsive();
  const sideBySide = width >= LINEUP_SPLIT;
  const editor = useDeckEditor(initial);
  const [editing, setEditing] = useState<string | null>(null);

  const save = async () => {
    const saved = await editor.save(initial?.id);
    if (saved) onDone();
  };

  const slotChip = (slot: string, compact = false) => {
    const player = editor.players[slot];
    const done = isPlayerComplete(player);
    return (
      <Pressable
        key={slot}
        onPress={() => setEditing(slot)}
        style={({ pressed }) => ({
          flexGrow: compact ? 0 : 1,
          flexBasis: compact ? 'auto' : 150,
          padding: compact ? spacing.xs : spacing.smd,
          borderRadius: radius.control,
          backgroundColor: colors.surfaceVariant,
          borderWidth: 1,
          borderColor: done ? colors.accentValue : colors.outline,
          opacity: pressed ? 0.75 : 1,
          gap: 4,
        })}
      >
        <Text style={{ ...typography.label, color: colors.secondaryText }}>
          {slot}
          {player?.battingOrder ? `  ${player.battingOrder}번` : ''}
        </Text>
        <Text style={{ ...typography.card, color: done ? colors.onSurface : colors.muted }} numberOfLines={1}>
          {player?.playerName?.trim() || (done ? cardLabel(player) : t('deck_slot_empty'))}
        </Text>
        {player?.playerName?.trim() && done ? (
          <Text style={{ ...typography.label, color: colors.secondaryText }} numberOfLines={1}>
            {cardLabel(player)}
          </Text>
        ) : null}
      </Pressable>
    );
  };

  const section = (titleKey: keyof typeof PART_LABEL_KEYS, slots: readonly string[]) => (
    <SectionCard title={t(PART_LABEL_KEYS[titleKey])}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm }}>
        {slots.map((slot) => slotChip(slot))}
      </View>
    </SectionCard>
  );

  /**
   * 두 명씩 세로로 묶어 열을 만든다. RP1 RP3 RP5 / RP2 RP4 RP6 처럼 읽힌다.
   *
   * 중계와 마무리를 각각 따로 묶는 이유는 인원이 홀수일 때다. 통째로 묶으면
   * 중계 5명 + 마무리 2명에서 RP5와 CP1이 한 열에 들어가 구분이 사라진다.
   */
  const pairColumns = (slots: readonly string[]) =>
    Array.from({ length: Math.ceil(slots.length / 2) }, (_, index) =>
      slots.slice(index * 2, index * 2 + 2),
    );

  /**
   * 한 무리를 열로 세운다.
   *
   * 칩은 compact로 그린다. 기본 칩은 flexBasis가 150이라 세로로 쌓으면 그게 폭이 아니라
   * 높이로 먹는다. 대신 열에 flex를 줘서 다른 섹션과 비슷한 폭이 되게 한다.
   */
  const bullpenGroup = (slots: readonly string[]) => {
    const columns = pairColumns(slots);
    return (
      <View style={{ flexDirection: 'row', gap: spacing.sm, flex: columns.length, minWidth: 0 }}>
        {columns.map((column) => (
          <View key={column[0]} style={{ gap: spacing.sm, flex: 1, minWidth: 0 }}>
            {column.map((slot) => slotChip(slot, true))}
          </View>
        ))}
      </View>
    );
  };

  const starterSlots = editor.pitcherSlots.filter((s) => s.startsWith('SP'));
  const bullpenSlots = editor.pitcherSlots.filter((s) => !s.startsWith('SP'));

  return (
    <View style={{ gap: spacing.lg }}>
      <SectionCard
        title={initial ? initial.name : t('deck_btn_new')}
        right={<LinkAction text={t('deck_btn_back')} onPress={onDone} />}
      >
        <View style={{ gap: spacing.lg }}>
          <TextField
            label={t('deck_label_name')}
            value={editor.name}
            onChangeText={editor.setName}
            placeholder={t('deck_placeholder_name')}
            editable={!editor.saving}
          />
        </View>
      </SectionCard>

      <SectionCard title={t('deck_strategy_title')}>
        <View style={{ gap: spacing.lg }}>
          <Text style={{ ...typography.body, color: colors.secondaryText }}>
            {t('deck_strategy_hint')}
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            {PITCHER_FIELDS.map(({ field, labelKey }) => (
              <View key={field} style={{ flexGrow: 1, flexBasis: 160 }}>
                <LabeledDropdown
                  label={t(labelKey)}
                  selected={editor.pitcherCounts[field]}
                  // 서로 묶지 않는다. 각 자리는 자기 범위 안에서 자유롭게 고른다.
                  options={PITCHER_RANGES[field]}
                  optionLabel={(v) => String(v)}
                  onSelect={(v) => editor.setPitcherCount(field, v)}
                />
              </View>
            ))}
          </View>

          {/* 합이 12가 아니면 저장할 수 없다. 어긋난 값을 보여 주고 막는다. */}
          {!editor.isPitcherStaffValid ? (
            <InfoBanner
              text={`${t('deck_pitcher_sum_prefix')}${editor.pitcherTotal}${t('deck_pitcher_sum_suffix')}`}
              tone="error"
            />
          ) : null}
        </View>
      </SectionCard>

      {/*
        넓은 화면에서는 야구장과 타순을 한 행에 둔다. 같은 상태를 두 방식으로 보는 것이라
        나란히 놓여야 서로 맞물리는 게 보인다. 좁으면 세로로 떨어진다.
      */}
      <View style={sideBySide ? { flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' } : { gap: spacing.md }}>
      <SectionCard title={t(PART_LABEL_KEYS.LINEUP)} style={sideBySide ? { flex: 1, minWidth: 0 } : undefined}>
        {/*
          야구장을 그리고 그 위 실제 수비 위치에 선수를 얹는다. 필드와 칩이 같은 비율
          좌표를 쓰기 때문에 화면 폭이 바뀌어도 어긋나지 않는다.
        */}
        <View
          style={{
            width: '100%',
            // 그림이 가로세로 비슷하다. 카드가 넓어도 필드만 지나치게 커지지 않게 상한을 둔다.
            maxWidth: 620,
            alignSelf: 'center',
            aspectRatio: 1.05,
            minHeight: 360,
          }}
        >
          <View style={StyleSheet.absoluteFill}>
            <BaseballField />
          </View>
          {Object.entries(LINEUP_FIELD_POSITIONS).map(([slot, pos]) => (
            <View
              key={slot}
              style={{
                position: 'absolute',
                left: `${pos.x * 100}%`,
                top: `${pos.y * 100}%`,
                width: FIELD_SLOT_WIDTH,
                marginLeft: -FIELD_SLOT_WIDTH / 2,
                marginTop: -FIELD_SLOT_HEIGHT / 2,
              }}
            >
              {slotChip(slot, true)}
            </View>
          ))}
        </View>
      </SectionCard>

      {/*
        타순은 야구장과 따로 둔다. 칩 위치는 수비 포지션이라 끌어 옮겨도 타순이 되지 않는다.
      */}
      <SectionCard title={t('deck_batting_order_title')} style={sideBySide ? { flex: 1, minWidth: 0 } : undefined}>
        <BattingOrderLane
          rows={editor.lineupByBattingOrder.map((slot) => {
            const player = editor.players[slot];
            const done = isPlayerComplete(player);
            return {
              slot,
              title: player?.playerName?.trim() || (done ? cardLabel(player) : t('deck_slot_empty')),
              subtitle: player?.playerName?.trim() && done ? cardLabel(player) : undefined,
              done,
            };
          })}
          onMove={editor.moveBattingOrder}
          onPress={setEditing}
        />
      </SectionCard>
      </View>

      {section('BENCH', BENCH_SLOTS)}
      {section('ROTATION', starterSlots)}

      {/* 중계와 마무리를 두 행에 걸쳐 세로로 짝지어 놓는다. 둘 사이는 간격을 넓게 준다. */}
      <SectionCard title={t(PART_LABEL_KEYS.BULLPEN)}>
        <View style={{ flexDirection: 'row', gap: spacing.xl, alignItems: 'flex-start' }}>
          {bullpenGroup(bullpenSlots.filter((slot) => slot.startsWith('RP')))}
          {bullpenGroup(bullpenSlots.filter((slot) => slot.startsWith('CP')))}
        </View>
      </SectionCard>

      <SectionCard title={t('deck_score_title')}>
        <View style={{ gap: spacing.md }}>
          <Text style={{ ...typography.body, color: colors.secondaryText }}>
            {editor.completedCount}
            {t('deck_progress_middle')}
            {editor.allSlots.length}
            {t('deck_progress_suffix')}
          </Text>
          {editor.score ? (
            <>
              <ScoreHero label={t('deck_score_title')} value={editor.score.total} />
              <View style={{ gap: spacing.xs }}>
                {editor.score.parts.map((part) => (
                  <View
                    key={part.part}
                    style={{ flexDirection: 'row', justifyContent: 'space-between' }}
                  >
                    <Text style={{ ...typography.body, color: colors.secondaryText }}>
                      {t(PART_LABEL_KEYS[part.part as keyof typeof PART_LABEL_KEYS] ?? 'deck_score_title')}
                    </Text>
                    <Text style={{ ...typography.body, color: colors.onSurface }}>
                      {part.total.toFixed(2)}
                    </Text>
                  </View>
                ))}
              </View>

              {/*
                컬렉션 버프. 점수에는 이미 반영돼 있지만 대개 총점을 움직이지 않는다.
                스탯 비례 스킬이 16종뿐이고 그마저 floor에 걸려서다. 그래서 얼마가
                걸렸는지 따로 보여 준다.
              */}
              <View style={{ gap: spacing.xs }}>
                <Text style={{ ...typography.label, color: colors.secondaryText }}>
                  {t('deck_buff_title')}
                </Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text style={{ ...typography.body, color: colors.secondaryText }}>
                    {t('deck_buff_summary')}
                  </Text>
                  <Text style={{ ...typography.body, color: colors.accentValue }}>
                    {t('deck_buff_batter')} +{editor.score.collectionBuff.batterBonus}
                    {'  '}
                    {t('deck_buff_pitcher')} +{editor.score.collectionBuff.pitcherBonus}
                  </Text>
                </View>
                {editor.score.collectionBuff.families
                  .filter((family) => family.count > 0)
                  .map((family) => (
                    <View
                      key={family.family}
                      style={{ flexDirection: 'row', justifyContent: 'space-between' }}
                    >
                      <Text style={{ ...typography.caption, color: colors.secondaryText }}>
                        {t(BUFF_FAMILY_LABEL_KEYS[family.family])} {family.count}
                        {t('deck_buff_count_suffix')}
                      </Text>
                      <Text style={{ ...typography.caption, color: colors.secondaryText }}>
                        {t('deck_buff_batter')} +{family.batterBonus} · {t('deck_buff_pitcher')} +
                        {family.pitcherBonus}
                      </Text>
                    </View>
                  ))}
              </View>
            </>
          ) : (
            <InfoBanner text={t('deck_error_incomplete')} />
          )}
          {editor.error ? <InfoBanner text={editor.error} tone="error" /> : null}
          <PrimaryActionButton
            text={t('deck_btn_save')}
            onPress={save}
            enabled={editor.isComplete && !!editor.name.trim() && !editor.saving}
            loading={editor.saving}
          />
          {initial && onDelete ? (
            <LinkAction text={t('deck_btn_delete')} onPress={() => onDelete(initial.id)} />
          ) : null}
        </View>
      </SectionCard>

      <Modal visible={!!editing} animationType="slide" transparent onRequestClose={() => setEditing(null)}>
        <View style={{ flex: 1, backgroundColor: 'rgba(7,8,11,0.88)', justifyContent: 'flex-end' }}>
          <View
            style={{
              maxHeight: '88%',
              backgroundColor: colors.background,
              borderTopLeftRadius: radius.shell,
              borderTopRightRadius: radius.shell,
              padding: spacing.lg,
            }}
          >
            <View style={{ alignItems: 'flex-end', marginBottom: spacing.sm }}>
              <LinkAction text={t('btn_close')} onPress={() => setEditing(null)} />
            </View>
            <ScrollView>
              {editing ? (
                <DeckPlayerEditor
                  slot={editing}
                  player={editor.players[editing]}
                  onChange={(patch) => editor.updatePlayer(editing, patch)}
                  onChangeBattingOrder={(order) => editor.moveBattingOrder(editing, order)}
                />
              ) : null}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}
