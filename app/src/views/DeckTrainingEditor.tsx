'use client';

import { useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import {
  InfoBanner,
  LabeledDropdown,
  LinkAction,
  NumberField,
  PrimaryActionButton,
  SectionCard,
} from '../components/ui';
import { fetchScoreSkills } from '../lib/api';
import { useTranslation } from '../lib/i18n';
import { positionForSlot } from '../lib/useDeckEditor';
import {
  TRAINING_BATTER_STATS,
  TRAINING_PITCHER_STATS,
  usePositionTraining,
} from '../lib/usePositionTraining';
import { useAppTheme } from '../theme/useTheme';
import { CardGrade, CardVariant, ScoreSkillOption } from '../types';

/**
 * 보너스를 걸 수 있는 스킬을 받아 오는 기준 카드.
 *
 * 시그니처는 일반 풀만 본다. 포지션 훈련 보너스로 나오는 티어(아이언~골드)가 곧 그 풀이라
 * 등급을 무엇으로 고르든 같은 목록이 나온다.
 */
const BONUS_SKILL_GRADE = CardGrade.SIGNATURE;

/**
 * 구단의 포지션 훈련을 자리마다 적는다.
 *
 * 훈련은 선수가 아니라 자리에 붙고 모든 라인업에 공통이라 덱이 아니라 계정에 저장된다.
 * 능력치는 레벨이 아니라 게임의 '포지션 능력치' 탭에서 읽은 증가치를 그대로 받는다 —
 * 레벨별 수치표가 공개된 적이 없어 우리가 표를 흉내 내면 틀린 값을 퍼뜨리게 된다.
 */
export default function DeckTrainingEditor({
  slots,
  state,
  signedIn,
}: {
  slots: string[];
  state: ReturnType<typeof usePositionTraining>;
  signedIn: boolean;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing } = useAppTheme();
  const [slot, setSlot] = useState(slots[0] ?? 'C');
  const [skills, setSkills] = useState<ScoreSkillOption[]>([]);

  // 자리가 사라지면(선발 수를 줄이는 등) 남은 자리 중 첫 칸으로 돌아간다.
  useEffect(() => {
    if (!slots.includes(slot)) setSlot(slots[0] ?? 'C');
  }, [slots, slot]);

  const position = positionForSlot(slot, 'C');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const loaded = await fetchScoreSkills(BONUS_SKILL_GRADE, CardVariant.NONE, position);
        if (!cancelled) setSkills(loaded);
      } catch {
        if (!cancelled) setSkills([]);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [position]);

  const stats = useMemo(
    () => (/^(SP|RP|CP)/.test(slot) ? TRAINING_PITCHER_STATS : TRAINING_BATTER_STATS),
    [slot],
  );
  const bonuses = state.bonusesOf(slot);
  const chosen = bonuses.filter((entry) => entry.skillId).map((entry) => entry.skillId);
  const slotStats = state.slotOf(slot).stats ?? {};

  return (
    <SectionCard title={t('deck_training_title')}>
      <View style={{ gap: spacing.lg }}>
        <Text style={{ ...typography.body, color: colors.secondaryText }}>
          {t('deck_training_desc')}
        </Text>

        {!signedIn ? <InfoBanner text={t('deck_training_login')} /> : null}

        <View style={{ flexBasis: 200, maxWidth: 260 }}>
          <LabeledDropdown
            label={t('deck_training_slot')}
            selected={slot}
            options={slots}
            optionLabel={(value) => value}
            onSelect={setSlot}
          />
        </View>

        <View style={{ gap: spacing.sm }}>
          <Text style={{ ...typography.label, color: colors.secondaryText }}>
            {t('deck_training_stats')}
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            {stats.map((stat) => (
              <View key={stat} style={{ flexGrow: 1, flexBasis: 120, maxWidth: 200 }}>
                <NumberField
                  label={stat}
                  value={slotStats[stat] != null ? String(slotStats[stat]) : ''}
                  onChangeText={(raw) =>
                    state.updateStat(slot, stat, raw.trim() === '' ? null : parseFloat(raw))
                  }
                />
              </View>
            ))}
          </View>
        </View>

        <View style={{ gap: spacing.sm }}>
          <Text style={{ ...typography.label, color: colors.secondaryText }}>
            {t('deck_training_skills')}
          </Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
            {bonuses.map((entry, index) => (
              <View key={`bonus-${index}`} style={{ flexGrow: 1, flexBasis: 220, gap: spacing.sm }}>
                <LabeledDropdown
                  label={`${t('training_slot_label')} ${index + 1}`}
                  selected={entry.skillId}
                  options={[
                    '',
                    ...skills
                      .filter(
                        (skill) => skill.skillId === entry.skillId || !chosen.includes(skill.skillId),
                      )
                      .map((skill) => skill.skillId),
                  ]}
                  optionLabel={(skillId) =>
                    skills.find((skill) => skill.skillId === skillId)?.name ??
                    (skillId ? skillId : t('training_none'))
                  }
                  onSelect={(skillId) =>
                    skillId
                      ? state.updateBonus(slot, index, { skillId })
                      : state.clearBonus(slot, index)
                  }
                  searchable
                  searchPlaceholder={t('score_table_search_placeholder')}
                />
                <LabeledDropdown
                  label={t('training_bonus_label')}
                  selected={entry.bonus}
                  options={[1, 2]}
                  optionLabel={(bonus) => (entry.skillId ? `+${bonus}` : '')}
                  placeholder={t('training_none')}
                  disabled={!entry.skillId}
                  onSelect={(bonus) => state.updateBonus(slot, index, { bonus })}
                />
              </View>
            ))}
          </View>
        </View>

        {state.error ? <InfoBanner text={state.error} tone="error" /> : null}

        {signedIn ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md, flexWrap: 'wrap' }}>
            <View style={{ flexGrow: 1, flexBasis: 200 }}>
              <PrimaryActionButton
                text={t('deck_training_save')}
                onPress={state.save}
                enabled={!state.saving && !state.loading}
                loading={state.saving}
              />
            </View>
            {state.saved ? (
              <Text style={{ ...typography.label, color: colors.muted }}>{t('deck_training_saved')}</Text>
            ) : null}
          </View>
        ) : null}
      </View>
    </SectionCard>
  );
}
