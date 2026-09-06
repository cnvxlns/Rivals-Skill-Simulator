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
import { PitcherField, isPlayerComplete, useDeckEditor } from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import {
  BENCH_SLOTS,
  CardVariant,
  DeckDetail,
  DeckPlayer,
  LINEUP_FIELD_POSITIONS,
} from '../types';
import BaseballField from '../components/BaseballField';
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
                  options={editor.pitcherChoices(field)}
                  optionLabel={(v) => String(v)}
                  onSelect={(v) => editor.setPitcherCount(field, v)}
                />
                {/* 지금 자동으로 정해진 자리를 표시해 준다. 둘을 고르면 셋째는 따라온다. */}
                <Text
                  style={{
                    ...typography.label,
                    color: editor.derivedField === field ? colors.accentValue : 'transparent',
                    marginTop: 6,
                  }}
                >
                  {t('deck_pitcher_derived')}
                </Text>
              </View>
            ))}
          </View>
        </View>
      </SectionCard>

      <SectionCard title={t(PART_LABEL_KEYS.LINEUP)}>
        {/*
          야구장을 그리고 그 위 실제 수비 위치에 선수를 얹는다. 필드와 칩이 같은 비율
          좌표를 쓰기 때문에 화면 폭이 바뀌어도 어긋나지 않는다.
        */}
        <View style={{ width: '100%', aspectRatio: 1.15, minHeight: 380 }}>
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

      {section('BENCH', BENCH_SLOTS)}
      {section('ROTATION', starterSlots)}
      {section('BULLPEN', bullpenSlots)}

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
                />
              ) : null}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}
