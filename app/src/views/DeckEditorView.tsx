'use client';

import { useEffect, useState } from 'react';
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
import {
  PITCHER_RANGES,
  PitcherField,
  isPlayerComplete,
  unavailableSkills,
  useDeckEditor,
} from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import { useResponsive } from '../lib/useResponsive';
import {
  BENCH_SLOTS,
  CardVariant,
  DeckDetail,
  DeckPlayer,
  DeckRules,
  LINEUP_FIELD_POSITIONS,
} from '../types';
import BaseballField, { fieldRatio } from '../components/BaseballField';
import BattingOrderLane from '../components/BattingOrderLane';
import DeckImportCard from './DeckImportCard';
import DeckPlayerEditor from './DeckPlayerEditor';
import DeckScoreTierSection from './DeckScoreTierSection';
import DeckTeamBuffCard from './DeckTeamBuffCard';
import DeckTrainingEditor from './DeckTrainingEditor';
import { fetchDeckRules } from '../lib/api';
import { useAuth } from '../lib/auth';
import { usePositionTraining } from '../lib/usePositionTraining';

/** 변형이 있으면 등급 뒤에 붙여 보여 준다. 예: Signature Black · WBC */
const cardLabel = (player: DeckPlayer) => {
  const grade = cardTypeLabel(String(player.cardGrade));
  const variant = player.cardVariant;
  return !variant || variant === CardVariant.NONE ? grade : `${grade} · ${variant}`;
};

/**
 * 야구장 위 칩의 크기.
 *
 * 높이를 고정해야 좌표의 정중앙에 놓을 수 있고, 줄 수가 선수마다 달라져 높이가 들쭉날쭉해
 * 이웃 칩과 겹치는 일도 없다. 그래서 야구장 칩만 두 줄로 고정하고 카드 등급은 글자 대신
 * 색으로 알린다. 폭은 칸을 재서 3열이 들어가게 맞추되 아래 범위를 벗어나지 않는다.
 */
const FIELD_CHIP_HEIGHT = 66;
const FIELD_CHIP_MIN_WIDTH = 76;
const FIELD_CHIP_MAX_WIDTH = 112;

/**
 * 야구장과 타순을 한 행에 두기 시작하는 폭.
 *
 * 야구장은 620px에서 칩이 겹치지 않는다. 좌우 여백 48x2와 가운데 간격을 빼고도 한 칸이
 * 620을 넘겨야 하므로 표를 좌우로 나누는 기준(1000)보다 높게 잡는다.
 * 그 아래에서는 세로로 떨어진다.
 */
const LINEUP_SPLIT = 1360;

/**
 * 세로로 쌓였을 때 야구장 칸의 최소 높이.
 *
 * 아홉 칩이 다섯 줄로 서므로 줄 간격이 칩 높이(66)보다 좁아지면 서로 가린다. 폭이 좁아도
 * 이 높이는 확보한다. 나란히 놓일 때는 타순 칸 높이에 맞춰 늘어나므로 쓰이지 않는다.
 */
const FIELD_MIN_HEIGHT = 520;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

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
  const { colors, typography, spacing, radius, cardGradeColor } = useAppTheme();
  const { width } = useResponsive();
  const sideBySide = width >= LINEUP_SPLIT;
  const { user } = useAuth();
  // 포지션 훈련은 덱이 아니라 구단에 붙는다. 덱 편집과 따로 읽고 쓰되 채점에는 함께 싣는다.
  const training = usePositionTraining(!!user);
  const editor = useDeckEditor(initial, training.training);
  // 덱 스코어 사다리와 카드별 성장 상한. 상수라 한 번만 받아 둔다.
  const [rules, setRules] = useState<DeckRules | null>(null);
  useEffect(() => {
    let alive = true;
    fetchDeckRules()
      .then((result) => {
        if (alive) setRules(result);
      })
      // 규칙을 못 받아도 편집은 된다. 덱 스코어 섹션만 비어 보인다.
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);
  const [editing, setEditing] = useState<string | null>(null);
  // 야구장 칸의 실제 크기. 칩을 픽셀로 놓아야 가장자리에서 칸 밖으로 삐져나가지 않는다.
  const [fieldBox, setFieldBox] = useState({ width: 0, height: 0 });

  const save = async () => {
    const saved = await editor.save(initial?.id);
    if (saved) onDone();
  };

  /**
   * 채워진 자리의 색. 카드 등급마다 다르다.
   *
   * 예전에는 완료 여부만 한 가지 초록으로 알렸다. 스물여섯 자리를 훑을 때 정작 궁금한
   * 것은 "채웠나"가 아니라 "무슨 카드인가"라서 등급을 색으로 옮긴다. 아직 덜 채운 자리는
   * 색을 주지 않아 회색으로 남는다.
   */
  const chipAccent = (player: DeckPlayer | undefined) => {
    // 이 카드에 못 쓰는 스킬이 남아 있으면 등급색보다 그 사실이 급하다.
    if (unavailableSkills(player).length > 0) return { hex: colors.error, soft: 'transparent' };
    return isPlayerComplete(player) ? cardGradeColor(String(player!.cardGrade)) : null;
  };

  const slotChip = (slot: string, compact = false) => {
    const player = editor.players[slot];
    const done = isPlayerComplete(player);
    const accent = chipAccent(player);
    return (
      <Pressable
        key={slot}
        onPress={() => setEditing(slot)}
        style={({ pressed }) => ({
          flexGrow: compact ? 0 : 1,
          flexBasis: compact ? 'auto' : 150,
          padding: compact ? spacing.xs : spacing.smd,
          borderRadius: radius.control,
          backgroundColor: accent ? accent.soft : colors.surfaceVariant,
          borderWidth: 1,
          borderColor: accent ? accent.hex : colors.outline,
          opacity: pressed ? 0.75 : 1,
          gap: 4,
        })}
      >
        <Text style={{ ...typography.label, color: accent ? accent.hex : colors.secondaryText }}>
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

  /**
   * 야구장 위에 놓는 칩. 크기가 고정이라 좌표의 정중앙에 놓인다.
   *
   * 목록 칩과 달리 두 줄만 쓴다. 세 줄짜리와 두 줄짜리가 섞이면 높이가 달라져 아래 줄
   * 칩을 가린다. 빠진 등급 이름은 테두리 색이 대신한다.
   */
  const fieldChip = (slot: string, chipWidth: number) => {
    const player = editor.players[slot];
    const done = isPlayerComplete(player);
    const accent = chipAccent(player);
    return (
      <Pressable
        onPress={() => setEditing(slot)}
        style={({ pressed }) => ({
          width: chipWidth,
          height: FIELD_CHIP_HEIGHT,
          justifyContent: 'center',
          paddingHorizontal: spacing.xs,
          borderRadius: radius.control,
          // 잔디 위에 얹히므로 배경은 불투명하게 둔다. 반투명이면 글자가 그림에 묻힌다.
          backgroundColor: colors.surfaceVariant,
          borderWidth: 1,
          borderColor: accent ? accent.hex : colors.outline,
          opacity: pressed ? 0.75 : 1,
          gap: 2,
        })}
      >
        <Text style={{ ...typography.caption, color: accent ? accent.hex : colors.secondaryText }}>
          {slot}
          {player?.battingOrder ? ` · ${player.battingOrder}${t('deck_batting_order_suffix')}` : ''}
        </Text>
        <Text
          style={{ ...typography.label, color: done ? colors.onSurface : colors.muted }}
          numberOfLines={1}
        >
          {player?.playerName?.trim() || (done ? cardLabel(player) : t('deck_slot_empty'))}
        </Text>
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
      <View
        style={
          sideBySide
            // stretch라서 두 카드의 높이가 늘 같다. 야구장이 남는 높이를 채우므로
            // 예전처럼 한쪽만 짧아 밑이 비지 않는다.
            ? { flexDirection: 'row', gap: spacing.md, alignItems: 'stretch' }
            : { gap: spacing.md }
        }
      >
      <SectionCard title={t(PART_LABEL_KEYS.LINEUP)} style={sideBySide ? { flex: 1, minWidth: 0 } : undefined}>
        {/*
          야구장을 그리고 그 위 실제 수비 위치에 선수를 얹는다. 그림이 칸을 정확히 채우고
          칩도 같은 상자를 기준으로 놓이므로 칸의 비율이 달라져도 어긋나지 않는다.
        */}
        <View
          onLayout={(event) => {
            const { width: w, height: h } = event.nativeEvent.layout;
            setFieldBox((prev) => (prev.width === w && prev.height === h ? prev : { width: w, height: h }));
          }}
          style={
            sideBySide
              // 타순 칸이 정한 높이를 그대로 받아 채운다.
              ? { width: '100%', flex: 1, minHeight: 360 }
              : { width: '100%', maxWidth: 620, alignSelf: 'center', minHeight: FIELD_MIN_HEIGHT }
          }
        >
          <View style={StyleSheet.absoluteFill}>
            <BaseballField />
          </View>
          {fieldBox.width > 0
            ? Object.entries(LINEUP_FIELD_POSITIONS).map(([slot, pos]) => {
                const chipWidth = clamp(fieldBox.width / 3.3, FIELD_CHIP_MIN_WIDTH, FIELD_CHIP_MAX_WIDTH);
                const ratio = fieldRatio(pos);
                // 가장자리 자리(3루수·지명타자)는 중앙 정렬만 하면 칸 밖으로 나간다. 잘라 넣는다.
                const left = clamp(
                  ratio.x * fieldBox.width - chipWidth / 2,
                  0,
                  Math.max(0, fieldBox.width - chipWidth),
                );
                const top = clamp(
                  ratio.y * fieldBox.height - FIELD_CHIP_HEIGHT / 2,
                  0,
                  Math.max(0, fieldBox.height - FIELD_CHIP_HEIGHT),
                );
                return (
                  <View key={slot} style={{ position: 'absolute', left, top }}>
                    {fieldChip(slot, chipWidth)}
                  </View>
                );
              })
            : null}
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
              accent: chipAccent(player)?.hex,
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

      <DeckImportCard onApply={editor.applyImport} onApplyTraining={training.mergeSlots} />

      <DeckScoreTierSection
        ladder="TEAM"
        rules={rules}
        chosen={editor.coordination}
        onChoose={editor.chooseTier}
        onClear={editor.clearTier}
      />
      <DeckScoreTierSection
        ladder="SPECIAL"
        rules={rules}
        chosen={editor.coordination}
        onChoose={editor.chooseTier}
        onClear={editor.clearTier}
      />

      <DeckTeamBuffCard buffs={editor.score?.teamBuffs ?? []} />

      <DeckTrainingEditor slots={editor.allSlots} state={training} signedIn={!!user} />

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
                    <Text
                      style={{
                        ...typography.body,
                        color: part.weight === 0 ? colors.muted : colors.onSurface,
                      }}
                    >
                      {part.average.toFixed(2)} × 10 × {part.weight} = {part.weighted.toFixed(2)}
                    </Text>
                  </View>
                ))}
                <Text style={{ ...typography.caption, color: colors.muted }}>
                  {t('deck_bench_excluded')}
                </Text>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                  <Text style={{ ...typography.body, color: colors.secondaryText }}>
                    {t('deck_score_stat')} · {t('deck_score_skill')}
                  </Text>
                  <Text style={{ ...typography.body, color: colors.onSurface }}>
                    {editor.score.statTotal.toFixed(2)} · {editor.score.skillTotal.toFixed(2)}
                  </Text>
                </View>
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
          {/* 어느 자리가 왜 막혔는지 먼저 알려 준다. 저장을 눌러야 서버 오류로 아는 것보다 빠르다. */}
          {editor.brokenSlots.length > 0 ? (
            <InfoBanner
              text={`${editor.brokenSlots.join(', ')}: ${t('skill_unavailable')}`}
              tone="error"
            />
          ) : null}
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
                  slots={editor.allSlots}
                  rules={rules}
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
