import React from 'react';
import { Text, TextInput, View } from 'react-native';
import { CardType, Position, SubPosition } from '../types';
import { useScoreCalculator } from '../lib/useScoreCalculator';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { cardTypeLabel } from '../lib/format';
import { LabeledDropdown, PrimaryActionButton, ScoreHero, SectionCard } from '../components/ui';

const cardTypeOptions = Object.values(CardType);

export default function CalculatorView({ onViewMethodology }: { onViewMethodology?: () => void }) {
  const calc = useScoreCalculator();
  const { t } = useTranslation();
  const { colors, typography, radius } = useAppTheme();
  const tk = (key: string) => t(key as never);

  const pitcherSubs: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubs: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subOptions = calc.position === Position.PITCHER ? pitcherSubs : batterSubs;

  const opponentStats =
    calc.position === Position.PITCHER
      ? new Set(['파워', '정확', '선구', '인내', '주루'])
      : new Set(['구속', '변화', '구위', '제구', '지구력']);

  return (
    <View style={{ gap: 12 }}>
      <Text style={[typography.headlineSmall, { color: colors.onBackground }]}>{t('calculator_title')}</Text>

      <SectionCard title={t('score_settings')}>
        <LabeledDropdown
          label={t('label_card_type')}
          selected={calc.cardType}
          options={cardTypeOptions}
          optionLabel={(o) => cardTypeLabel(o)}
          onSelect={(o) => calc.setCardType(o)}
        />
        <LabeledDropdown
          label={t('label_position')}
          selected={calc.position}
          options={[Position.PITCHER, Position.BATTER]}
          optionLabel={(o) => (o === Position.PITCHER ? 'Pitcher' : 'Batter')}
          onSelect={(o) => calc.setPosition(o)}
        />
        <LabeledDropdown
          label={t('label_sub_position')}
          selected={(calc.subPosition || 'ALL') as SubPosition}
          options={subOptions}
          optionLabel={(o) => (o === 'ALL' ? t('option_all_sub_positions') : o)}
          onSelect={(o) => calc.setSubPosition(o === 'ALL' ? '' : o)}
        />
        {calc.position === Position.BATTER ? (
          <LabeledDropdown
            label={t('label_batting_order')}
            selected={calc.battingOrder}
            options={[null, 1, 2, 3, 4, 5, 6, 7, 8, 9]}
            optionLabel={(o) => (o == null ? t('option_average_batting_order') : String(o))}
            onSelect={(o) => calc.updateBattingOrder(o)}
          />
        ) : null}
        {calc.position === Position.PITCHER && (calc.subPosition === 'SP' || calc.subPosition === 'RP') ? (
          <LabeledDropdown
            label={t('label_position')}
            selected={calc.pitcherSlot}
            options={calc.subPosition === 'SP' ? [null, 1, 2, 3, 4, 5] : [null, 1, 2, 3, 4, 5, 6]}
            optionLabel={(o) => (o == null ? '-' : String(o))}
            onSelect={(o) => calc.updatePitcherSlot(o)}
          />
        ) : null}
      </SectionCard>

      <Text style={[typography.titleMedium, { color: colors.onSurface }]}>{t('score_skill_slots')}</Text>

      {Array.from({ length: calc.slotCount }, (_, i) => i).map((index) => {
        const selection = calc.selections[index];
        const selectedSkill = calc.skills.find((s) => s.skillId === selection?.skillId);
        const maxLevel = selectedSkill?.maxLevel ?? 1;
        const levelOptions = Array.from({ length: maxLevel }, (_, i) => i + 1);
        return (
          <View
            key={`slot-${index}`}
            style={{
              backgroundColor: colors.surface,
              borderRadius: radius.medium,
              borderWidth: 1,
              borderColor: colors.outline,
              padding: 14,
              gap: 8,
            }}
          >
            <Text style={[typography.titleSmall, { color: colors.onSurface }]}>
              {t('slot_label')} {index + 1}
            </Text>
            <LabeledDropdown
              label={t('score_select_skill')}
              selected={selection?.skillId ?? ''}
              options={['', ...calc.skills.map((s) => s.skillId)]}
              optionLabel={(id) => calc.skills.find((s) => s.skillId === id)?.name ?? t('score_select_skill')}
              onSelect={(id) => calc.updateSkill(index, id)}
            />
            <LabeledDropdown
              label={t('score_level')}
              selected={Math.min(Math.max(selection?.level ?? 1, 1), maxLevel)}
              options={levelOptions}
              optionLabel={(lv) => selectedSkill?.levelLabels?.[lv - 1] ?? `Lv ${lv}`}
              onSelect={(lv) => calc.updateLevel(index, lv)}
            />
            <Text onPress={() => calc.clearSlot(index)} style={[typography.labelLarge, { color: colors.primary }]}>
              {t('score_clear_slot')}
            </Text>
          </View>
        );
      })}

      <SectionCard title={t('score_user_stats')}>
        {calc.visibleStats.map((stat) => (
          <View key={stat} style={{ gap: 4 }}>
            <Text style={[typography.labelMedium, { color: colors.secondaryText }]}>{tk(`stat_${stat}`)}</Text>
            <TextInput
              value={String(Math.round(calc.userStats[stat] ?? 0))}
              onChangeText={(raw) => calc.updateUserStat(stat, parseFloat(raw) || 0)}
              keyboardType="numeric"
              style={{
                backgroundColor: colors.surfaceVariant,
                borderColor: colors.outline,
                borderWidth: 1,
                borderRadius: radius.small,
                paddingHorizontal: 14,
                paddingVertical: 10,
                color: colors.onSurface,
              }}
              placeholderTextColor={colors.muted}
            />
          </View>
        ))}
        <Text onPress={calc.resetUserStats} style={[typography.labelLarge, { color: colors.primary }]}>
          {t('score_reset_stats')}
        </Text>
      </SectionCard>

      {calc.error ? <Text style={{ color: colors.error }}>{tk(calc.error)}</Text> : null}
      <PrimaryActionButton
        text={t('score_calculate')}
        onPress={calc.calculate}
        enabled={!calc.calculating}
        loading={calc.calculating}
      />

      {calc.result ? (
        <SectionCard title={t('score_result_ready')}>
          <ScoreHero label={t('score_total')} value={calc.result.total} />

          {calc.result.perSkill.length ? (
            <>
              <Text style={[typography.titleSmall, { color: colors.onSurface, marginTop: 4 }]}>{t('score_by_skill')}</Text>
              {calc.result.perSkill.map((skill) => (
                <Text key={skill.skillId} style={[typography.bodyMedium, { color: colors.onSurface }]}>
                  {skill.name}: {skill.score.toFixed(2)}
                </Text>
              ))}
            </>
          ) : null}

          {calc.result.perStat.length ? (
            <>
              <Text style={[typography.titleSmall, { color: colors.onSurface, marginTop: 4 }]}>{t('score_by_stat')}</Text>
              {calc.result.perStat.filter((s) => !opponentStats.has(s.stat)).length ? (
                <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('score_stat_mine')}</Text>
              ) : null}
              {calc.result.perStat
                .filter((s) => !opponentStats.has(s.stat))
                .map((s) => (
                  <Text key={s.stat} style={[typography.bodyMedium, { color: colors.primary }]}>
                    {tk(`stat_${s.stat}`)}: +{s.value.toFixed(2)}
                  </Text>
                ))}
              {calc.result.perStat.filter((s) => opponentStats.has(s.stat)).length ? (
                <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('score_stat_opponent')}</Text>
              ) : null}
              {calc.result.perStat
                .filter((s) => opponentStats.has(s.stat))
                .map((s) => (
                  <Text key={s.stat} style={[typography.bodyMedium, { color: colors.opponentStat }]}>
                    {tk(`stat_${s.stat}`)}: -{s.value.toFixed(2)}
                  </Text>
                ))}
            </>
          ) : null}

          {onViewMethodology ? (
            <Text onPress={onViewMethodology} style={[typography.labelLarge, { color: colors.primary, marginTop: 8 }]}>
              {t('tab_methodology')} →
            </Text>
          ) : null}
        </SectionCard>
      ) : null}
    </View>
  );
}
