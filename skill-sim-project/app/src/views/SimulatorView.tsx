import React from 'react';
import { Text, View } from 'react-native';
import { CardType, Position, SubPosition, TicketType } from '../types';
import { useSkillSimulator } from '../lib/useSkillSimulator';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { cardTypeLabel } from '../lib/format';
import {
  GradeChip,
  InfoChip,
  LabeledDropdown,
  PrimaryActionButton,
  ScoreHero,
  SectionCard,
  Toggle,
} from '../components/ui';

const cardTypeOptions = Object.values(CardType);
const ticketOptions = Object.values(TicketType);

export default function SimulatorView() {
  const sim = useSkillSimulator();
  const { t } = useTranslation();
  const { colors, typography, radius, spacing } = useAppTheme();

  const ticketLabel = (tk: TicketType) =>
    tk === TicketType.SKILL_CHANGE
      ? t('ticket_skill_change')
      : tk === TicketType.PREMIUM_SKILL_CHANGE
      ? t('ticket_premium')
      : t('ticket_supreme');

  const pitcherSubs: SubPosition[] = ['ALL', 'SP', 'RP', 'CP'];
  const batterSubs: SubPosition[] = ['ALL', 'C', '1B', '2B', '3B', 'SS', 'LF', 'CF', 'RF', 'DH'];
  const subOptions = sim.position === Position.PITCHER ? pitcherSubs : batterSubs;

  return (
    <View style={{ gap: 12 }}>
      <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('hdr_subtitle')}</Text>

      <SectionCard title={t('section_roll_settings')}>
        <LabeledDropdown
          label={t('label_card_type')}
          selected={sim.cardType}
          options={cardTypeOptions}
          optionLabel={(o) => cardTypeLabel(o)}
          onSelect={(o) => sim.setCardType(o)}
        />
        <LabeledDropdown
          label={t('label_ticket_type')}
          selected={sim.ticketType}
          options={ticketOptions}
          optionLabel={ticketLabel}
          onSelect={(o) => sim.setTicketType(o)}
        />
        <LabeledDropdown
          label={t('label_position')}
          selected={sim.position ?? Position.PITCHER}
          options={[Position.PITCHER, Position.BATTER]}
          optionLabel={(o) => (o === Position.PITCHER ? 'Pitcher' : 'Batter')}
          onSelect={(o) => sim.setPosition(o)}
        />
        <LabeledDropdown
          label={t('label_sub_position')}
          selected={sim.subPosition}
          options={subOptions}
          optionLabel={(o) => (o === 'ALL' ? t('option_all_sub_positions') : o)}
          onSelect={(o) => sim.setSubPosition(o)}
        />
        {sim.cardType === CardType.MOMENT ? (
          <>
            <LabeledDropdown
              label={t('label_theme')}
              selected={sim.selectedTheme ?? ''}
              options={sim.availableThemes.length ? sim.availableThemes : ['']}
              optionLabel={(o) => o || '-'}
              onSelect={(o) => sim.setSelectedTheme(o)}
              disabled={!sim.availableThemes.length}
            />
            {!sim.availableThemes.length ? (
              <Text style={[typography.bodySmall, { color: colors.error }]}>{t('no_theme')}</Text>
            ) : null}
          </>
        ) : null}
      </SectionCard>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {Object.entries(sim.ticketUsageCounts).map(([tk, count]) => (
          <InfoChip key={tk} text={`${ticketLabel(tk as TicketType)}: ${count}`} />
        ))}
        <InfoChip text={`${t('protection_used')}: ${sim.protectionUsageCount}`} />
      </View>

      <ScoreHero label={t('score_total')} value={sim.totalScore} />
      {sim.error ? <Text style={{ color: colors.error }}>{sim.error}</Text> : null}

      {Array.from({ length: sim.slotCount }, (_, i) => i).map((index) => {
        const slot = sim.slots[index];
        const locked = index === 0 && sim.isSlot1Locked;
        return (
          <View
            key={`slot-${index}`}
            style={{
              backgroundColor: colors.surface,
              borderRadius: radius.medium,
              borderWidth: 1,
              borderColor: locked ? colors.primary : colors.outline,
              padding: 14,
              gap: 8,
            }}
          >
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text style={[typography.titleSmall, { color: colors.onSurface, flex: 1 }]}>
                {t('slot_label')} {index + 1}
              </Text>
              {index === 0 ? (
                <Text
                  onPress={sim.canLockSlot1 ? sim.toggleLockSlot1 : undefined}
                  style={[
                    typography.bodySmall,
                    { color: locked ? colors.primary : colors.secondaryText, opacity: sim.canLockSlot1 ? 1 : 0.5 },
                  ]}
                >
                  {locked ? `🔒 ${t('locked')}` : `🔓 ${t('lock_slot1')}`}
                </Text>
              ) : (
                <Text style={[typography.bodySmall, { color: colors.secondaryText }]}>{t('unlocked')}</Text>
              )}
            </View>

            <Text style={[typography.titleMedium, { color: colors.onSurface }]}>
              {slot?.skill?.name ?? t('no_skill')}
            </Text>

            {slot ? (
              <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
                <GradeChip grade={slot.grade} />
                {slot.skill?.tier ? <InfoChip text={slot.skill.tier} /> : null}
                <InfoChip text={typeof slot.score === 'number' ? slot.score.toFixed(2) : '-'} />
              </View>
            ) : null}

            {sim.cardType !== CardType.MOMENT ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Toggle
                  value={sim.useLevelProtectionSlots[index] ?? false}
                  onToggle={() => sim.toggleLevelProtection(index)}
                />
                <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('protect_label')}</Text>
              </View>
            ) : null}
          </View>
        );
      })}

      {sim.candidateSkills ? (
        <SectionCard title={t('new_skills')}>
          {sim.candidateSkills.map((slot, index) => (
            <View
              key={`cand-${index}`}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}
            >
              <Text style={[typography.bodyMedium, { color: colors.onSurface, flex: 1 }]}>
                {index + 1}. {slot.skill?.name ?? t('no_skill')}
              </Text>
              <GradeChip grade={slot.grade} />
            </View>
          ))}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: spacing.sm }}>
            <PrimaryActionButton
              text={t('current_skills')}
              onPress={sim.keepCurrentSkills}
              style={{ flex: 1, backgroundColor: colors.surfaceVariant }}
            />
            <PrimaryActionButton text={t('confirm_action')} onPress={sim.applyCandidateSkills} style={{ flex: 1 }} />
          </View>
        </SectionCard>
      ) : null}

      <PrimaryActionButton
        text={t('btn_roll')}
        onPress={sim.roll}
        enabled={!sim.loading}
        loading={sim.loading}
      />
    </View>
  );
}
