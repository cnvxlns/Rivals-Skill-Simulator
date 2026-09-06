'use client';

import { useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
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
  CLOSER_CHOICES,
  STARTER_CHOICES,
  isPlayerComplete,
  useDeckEditor,
} from '../lib/useDeckEditor';
import { useAppTheme } from '../theme/useTheme';
import { BENCH_SLOTS, DeckDetail, LINEUP_SLOTS } from '../types';
import DeckPlayerEditor from './DeckPlayerEditor';

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

  const slotChip = (slot: string) => {
    const player = editor.players[slot];
    const done = isPlayerComplete(player);
    return (
      <Pressable
        key={slot}
        onPress={() => setEditing(slot)}
        style={({ pressed }) => ({
          flexGrow: 1,
          flexBasis: 150,
          padding: spacing.smd,
          borderRadius: radius.control,
          backgroundColor: colors.surfaceVariant,
          borderWidth: 1,
          borderColor: done ? colors.accentValue : colors.outline,
          opacity: pressed ? 0.75 : 1,
          gap: 4,
        })}
      >
        <Text style={{ ...typography.label, color: colors.secondaryText }}>{slot}</Text>
        <Text style={{ ...typography.card, color: done ? colors.onSurface : colors.muted }} numberOfLines={1}>
          {done ? cardTypeLabel(player.cardType) : t('deck_slot_empty')}
        </Text>
      </Pressable>
    );
  };

  const section = (titleKey: keyof typeof PART_LABEL_KEYS, slots: readonly string[]) => (
    <SectionCard title={t(PART_LABEL_KEYS[titleKey])}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm }}>
        {slots.map(slotChip)}
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
            <View style={{ flexGrow: 1, flexBasis: 160 }}>
              <LabeledDropdown
                label={t('deck_label_starters')}
                selected={editor.starterCount}
                options={[...STARTER_CHOICES]}
                optionLabel={(v) => String(v)}
                onSelect={editor.setStarterCount}
              />
            </View>
            <View style={{ flexGrow: 1, flexBasis: 160 }}>
              <LabeledDropdown
                label={t('deck_label_closers')}
                selected={editor.closerCount}
                options={[...CLOSER_CHOICES]}
                optionLabel={(v) => String(v)}
                onSelect={editor.setCloserCount}
              />
            </View>
            <View style={{ flexGrow: 1, flexBasis: 160, gap: 11, justifyContent: 'flex-end' }}>
              <Text style={{ color: colors.secondaryText, fontSize: 16, fontWeight: '600' }}>
                {t('deck_label_relievers')}
              </Text>
              {/* 파생값이라 고를 수 없다. 총원이 12로 고정이기 때문이다. */}
              <Text style={{ ...typography.title, color: colors.accentValue }}>
                {editor.relieverCount}
              </Text>
            </View>
          </View>
        </View>
      </SectionCard>

      {section('LINEUP', LINEUP_SLOTS)}
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
