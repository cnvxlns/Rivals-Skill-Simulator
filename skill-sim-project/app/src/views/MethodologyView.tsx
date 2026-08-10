import React, { useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { useMethodology } from '../lib/useMethodology';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { InfoChip, PrimaryActionButton, SectionCard, SegmentedTabs } from '../components/ui';

type GroupKey = 'static' | 'role' | 'batting' | 'reach' | 'gates';

export default function MethodologyView() {
  const { data, loading, error, refresh } = useMethodology();
  const { t } = useTranslation();
  const { colors, typography, radius } = useAppTheme();
  const tk = (key: string) => t(key as never);
  const [group, setGroup] = useState<GroupKey>('static');

  if (loading) {
    return (
      <View style={{ paddingVertical: 80, alignItems: 'center', gap: 16 }}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('score_loading_skills')}</Text>
      </View>
    );
  }

  if (error || !data) {
    return (
      <SectionCard title="Error Loading Methodology">
        <Text style={[typography.bodyMedium, { color: colors.error }]}>{error ? tk(error) : 'No data returned'}</Text>
        <PrimaryActionButton text="Retry" onPress={refresh} />
      </SectionCard>
    );
  }

  const { formula, statWeights, conditionProbabilities } = data;
  const translateToken = (token: string, descriptionKey: string) => {
    if (descriptionKey) {
      const v = tk(descriptionKey);
      if (v && v !== descriptionKey) return v;
    }
    return token;
  };

  const FormulaCard = ({ item }: { item: { displayText: string; descriptionKey: string } }) => (
    <View
      style={{
        backgroundColor: colors.surfaceVariant,
        borderColor: colors.outline,
        borderWidth: 1,
        borderRadius: radius.small,
        padding: 12,
        gap: 6,
      }}
    >
      <Text style={[typography.labelLarge, { color: colors.primary }]}>{tk(item.descriptionKey)}</Text>
      <Text style={[typography.bodySmall, { color: colors.onSurface, fontFamily: 'monospace' }]}>{item.displayText}</Text>
    </View>
  );

  const Row3 = ({ a, b, c, mono }: { a: string; b: string; c: string; mono?: boolean }) => (
    <View style={{ flexDirection: 'row', gap: 8, paddingVertical: 6, borderBottomWidth: 1, borderColor: colors.outline }}>
      <Text style={[typography.bodySmall, { color: colors.primary, flex: 1, fontFamily: mono ? 'monospace' : undefined }]}>{a}</Text>
      <Text style={[typography.bodySmall, { color: colors.onSurface, width: 64, textAlign: 'center' }]}>{b}</Text>
      <Text style={[typography.bodySmall, { color: colors.secondaryText, flex: 2 }]}>{c}</Text>
    </View>
  );

  const groupTabs: { key: GroupKey; label: string }[] = (['static', 'role', 'batting', 'reach', 'gates'] as GroupKey[]).map(
    (g) => ({ key: g, label: tk(`methodology_group_${g}`) }),
  );

  return (
    <View style={{ gap: 12 }}>
      <Text style={[typography.headlineSmall, { color: colors.onBackground }]}>{t('methodology_title')}</Text>
      <Text style={[typography.bodyMedium, { color: colors.secondaryText }]}>{t('methodology_desc')}</Text>

      <SectionCard title={t('methodology_section_formula')}>
        <FormulaCard item={formula.perSkillFormula} />
        <FormulaCard item={formula.totalFormula} />
        <FormulaCard item={formula.percentEffectRule} />
        <FormulaCard item={formula.roundingRule} />
        <FormulaCard item={formula.conditionCombinationRule} />
      </SectionCard>

      <SectionCard title={t('methodology_section_stat_weights')}>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          {Object.entries(statWeights).map(([stat, weight]) => (
            <InfoChip key={stat} text={`${tk(`stat_${stat}`)} ${weight.toFixed(2)}`} />
          ))}
        </View>
      </SectionCard>

      <SectionCard title={t('methodology_section_conditions')}>
        <SegmentedTabs tabs={groupTabs} selected={group} onSelect={setGroup} />

        {group === 'static' ? (
          <View>
            <Row3 a={t('methodology_col_token')} b={t('methodology_col_value')} c={t('methodology_col_description')} />
            {conditionProbabilities.staticProbabilities.map((e) => (
              <Row3 key={e.token} a={e.token} b={e.value.toFixed(3)} c={translateToken(e.token, e.descriptionKey)} mono />
            ))}
          </View>
        ) : null}

        {group === 'batting' ? (
          <View>
            <Row3 a={t('methodology_col_token')} b={t('methodology_col_value')} c={t('methodology_col_description')} />
            {conditionProbabilities.battingOrderProbabilities.map((e) => (
              <Row3 key={e.token} a={e.token} b={e.value.toFixed(3)} c={translateToken(e.token, e.descriptionKey)} mono />
            ))}
          </View>
        ) : null}

        {group === 'gates' ? (
          <View>
            <Row3 a={t('methodology_col_token')} b={t('methodology_col_value')} c={t('methodology_col_description')} />
            {conditionProbabilities.gates.map((e) => (
              <Row3 key={e.token} a={e.token} b={'1.0 / 0.0'} c={translateToken(e.token, e.descriptionKey)} mono />
            ))}
          </View>
        ) : null}

        {group === 'role' ? (
          <View style={{ gap: 12 }}>
            {conditionProbabilities.roleProbabilities.map((e) => (
              <View
                key={e.role}
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.outline, borderWidth: 1, borderRadius: radius.small, padding: 12, gap: 8 }}
              >
                <Text style={[typography.titleSmall, { color: colors.primary }]}>Role: {e.role}</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  <InfoChip text={`${t('condition_guts_probability')}: ${e.gutsProbability.toFixed(2)}`} />
                  <InfoChip text={`${t('condition_patience_below_velocity_probability')}: ${e.patienceBelowVelocityProbability.toFixed(2)}`} />
                  <InfoChip text={`${t('condition_nine_batter_duration')}: ${e.nineBatterDuration.toFixed(2)}`} />
                  <InfoChip text={`${t('condition_maestro_cumulative')}: ${e.maestroCumulative.toFixed(3)}`} />
                </View>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
                  {e.inningWeights.map((w, idx) => (
                    <InfoChip key={idx} text={`${idx + 1}H ${w.toFixed(3)}`} />
                  ))}
                </View>
              </View>
            ))}
          </View>
        ) : null}

        {group === 'reach' ? (
          <View style={{ gap: 12 }}>
            {conditionProbabilities.reachProbabilities.map((e) => (
              <View
                key={e.orderGroup}
                style={{ backgroundColor: colors.surfaceVariant, borderColor: colors.outline, borderWidth: 1, borderRadius: radius.small, padding: 12, gap: 8 }}
              >
                <Text style={[typography.titleSmall, { color: colors.primary }]}>{tk(e.descriptionKey)}</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
                  {e.reachProbabilities.map((p, idx) => (
                    <InfoChip key={idx} text={`PA${idx + 1} ${p.toFixed(3)}`} />
                  ))}
                </View>
              </View>
            ))}
          </View>
        ) : null}
      </SectionCard>
    </View>
  );
}
