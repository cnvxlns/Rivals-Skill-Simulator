import React, { useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { useMethodology } from '../lib/useMethodology';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { InfoChip, PrimaryActionButton, SectionCard, SegmentedTabs } from '../components/ui';
import Formula from '../components/Formula';
import { useResponsive } from '../lib/useResponsive';


/** 산정 공식의 LaTeX 표기. 웹에서는 KaTeX로 조판된다. */
const TEX_BY_KEY: Record<string, string> = {
  formula_per_skill: String.raw`\text{스킬점수} = \sum_{i} w_i \cdot v_i \cdot P_i`,
  formula_total: String.raw`\text{총점} = \sum_{s} \text{스킬점수}_s`,
  formula_percent_effect: String.raw`\Delta = \left\lfloor \text{기준스탯} \times \frac{r}{100} \right\rfloor`,
  formula_rounding: String.raw`\text{표시값} = \frac{\operatorname{round}(x \times 100)}{100}`,
  formula_condition_combination: String.raw`P = \left(\prod_{k} p_k\right) \cdot \max_{m}(p_m) \cdot \max_{g}(p_g)`,
};

/** 네이티브용 유니코드 조판. LaTeX 렌더러가 없는 환경에서 대신 쓴다. */
const UNICODE_BY_KEY: Record<string, string> = {
  formula_per_skill: '스킬점수 = Σ (가중치 × 수치 × 조건확률)',
  formula_total: '총점 = Σ 스킬점수',
  formula_percent_effect: '증가량 = ⌊기준스탯 × 비율%⌋',
  formula_rounding: '표시값 = round(x × 100) / 100',
  formula_condition_combination: 'P = (∏ 일반조건) × max(모드조건) × max(포지션조건)',
};

/**
 * 조건 토큰 -> 설명 키. 복합 조건(A+B)을 분해해 문장으로 조합할 때 쓴다.
 * 백엔드가 복합 토큰에는 설명 키를 주지 않기 때문에 프론트에서 부분별로 찾는다.
 */
const TOKEN_DESCRIPTION_KEYS: Record<string, string> = {
  ALWAYS: 'condition_always',
  홈: 'condition_home',
  원정: 'condition_away',
  주자있음: 'condition_runners_on',
  주자없음: 'condition_runners_off',
  OVR열세: 'condition_ovr_inferior',
  OVR우세: 'condition_ovr_superior',
  좌투상대: 'condition_vs_left_pitcher',
  우투상대: 'condition_vs_right_pitcher',
  좌타상대: 'condition_vs_left_batter',
  우타상대: 'condition_vs_right_batter',
  풀카운트: 'condition_full_count',
  '2아웃': 'condition_two_outs',
  초구: 'condition_first_pitch',
  '1스트라이크': 'condition_one_strike',
  '2스트라이크': 'condition_two_strikes',
  상대팀홈런3: 'condition_opponent_three_homeruns',
  이닝출루2인이상: 'condition_inning_two_baserunners',
  발사각조건: 'condition_launch_angle',
  타순1: 'condition_batting_order_1',
  타순1_2: 'condition_batting_order_1_2',
  타순2_3: 'condition_batting_order_2_3',
  타순3_4_5: 'condition_batting_order_3_5',
  타순4_5: 'condition_batting_order_4_5',
  타순6_9: 'condition_batting_order_6_9',
  타순8_9: 'condition_batting_order_8_9',
  리드: 'condition_lead',
  리드아님: 'condition_not_leading',
  비김또는리드: 'condition_tie_or_lead',
  비김또는열세: 'condition_tie_or_behind',
  모드_랭킹대전: 'condition_mode_ranking_match',
  모드_랭킹토너먼트: 'condition_mode_ranking_tournament',
  모드_라이브매치: 'condition_mode_live_match',
  모드_리그: 'condition_mode_league',
  모드_클럽: 'condition_mode_club',
  모드_타점배틀: 'condition_mode_rbi_battle',
  포지션_SP: 'condition_gate_position_sp',
  포지션_RP_CP: 'condition_gate_position_rp_cp',
  포지션_DH: 'condition_gate_position_dh',
  포지션_SS: 'condition_gate_position_ss',
  포지션_OF: 'condition_gate_position_of',
  포지션_C: 'condition_gate_position_c',
  좌완: 'condition_left_handed_throw',
  우완: 'condition_right_handed_throw',
  좌타: 'condition_left_handed_bat',
  우타: 'condition_right_handed_bat',
};

type GroupKey = 'static' | 'role' | 'batting' | 'reach' | 'gates';

export default function MethodologyView() {
  const { data, loading, error, refresh } = useMethodology();
  const { t } = useTranslation();
  const { colors, typography, radius } = useAppTheme();
  const tk = (key: string) => t(key as never);
  const [group, setGroup] = useState<GroupKey>('static');
  const { isSplit } = useResponsive();

  // 수식 카드는 폭을 다 쓰지 않는다. 넓은 화면에서는 좌우로 나눠 스크롤을 줄인다.
  const formulaGrid = isSplit ? { flexDirection: 'row' as const, flexWrap: 'wrap' as const, gap: 12 } : undefined;
  const formulaItem = isSplit ? { width: '49%' as const } : undefined;

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
      <SectionCard title={t('methodology_error_title')}>
        <Text style={[typography.bodyMedium, { color: colors.error }]}>{error ? tk(error) : t('methodology_error_empty')}</Text>
        <PrimaryActionButton text={t('btn_retry')} onPress={refresh} />
      </SectionCard>
    );
  }

  const { formula, statWeights, conditionProbabilities } = data;
  // 복합 조건(A+B)은 각 부분의 설명을 찾아 이어 붙인다.
  // 예전에는 설명 키가 없으면 토큰 자체를 그대로 내보내 '타순3_4_5+타순4_5' 같은
  // 내부 문자열이 사용자에게 노출됐다.
  const describeToken = (token: string, descriptionKey: string): string => {
    if (descriptionKey) {
      const v = tk(descriptionKey);
      if (v && v !== descriptionKey) return v;
    }
    if (token.includes('+')) {
      const parts = token.split('+').map((part) => {
        const key = TOKEN_DESCRIPTION_KEYS[part.trim()];
        const v = key ? tk(key) : '';
        return v && v !== key ? v : part.trim();
      });
      return parts.join(t('methodology_condition_join'));
    }
    const key = TOKEN_DESCRIPTION_KEYS[token];
    if (key) {
      const v = tk(key);
      if (v && v !== key) return v;
    }
    return token;
  };

  const FormulaCard = ({ item }: { item: { displayText: string; descriptionKey: string } }) => (
    <View
      style={[{
        backgroundColor: colors.surfaceVariant,
        borderColor: colors.outline,
        borderWidth: 1,
        borderRadius: radius.small,
        padding: 12,
        gap: 6,
      }, formulaItem]}
    >
      <Text style={[typography.labelLarge, { color: colors.primary }]}>{tk(item.descriptionKey)}</Text>
      <Formula tex={TEX_BY_KEY[item.descriptionKey] ?? ''} fallback={UNICODE_BY_KEY[item.descriptionKey] ?? item.displayText} />
    </View>
  );

  /** 설명을 주 표기로, 내부 토큰은 아래 작은 글씨로 보조 표기한다. */
  const ConditionRow = ({ token, value, description }: { token: string; value: string; description: string }) => (
    <View style={{ flexDirection: 'row', gap: 10, alignItems: 'flex-start', paddingVertical: 8, borderBottomWidth: 1, borderColor: colors.outline }}>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[typography.bodyMedium, { color: colors.onSurface }]}>{description}</Text>
        <Text style={[typography.bodySmall, { color: colors.muted, fontFamily: 'monospace' }]}>{token}</Text>
      </View>
      <Text style={[typography.titleSmall, { color: colors.primary, width: 68, textAlign: 'right' }]}>{value}</Text>
    </View>
  );

  const HeaderRow = () => (
    <View style={{ flexDirection: 'row', gap: 10, paddingBottom: 6, borderBottomWidth: 1, borderColor: colors.outline }}>
      <Text style={[typography.labelMedium, { color: colors.secondaryText, flex: 1 }]}>{t('methodology_col_description')}</Text>
      <Text style={[typography.labelMedium, { color: colors.secondaryText, width: 68, textAlign: 'right' }]}>{t('methodology_col_value')}</Text>
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
        <View style={formulaGrid}>
        <FormulaCard item={formula.perSkillFormula} />
        <FormulaCard item={formula.totalFormula} />
        <FormulaCard item={formula.percentEffectRule} />
        <FormulaCard item={formula.roundingRule} />
        <FormulaCard item={formula.conditionCombinationRule} />
        </View>
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
            <HeaderRow />
            {conditionProbabilities.staticProbabilities.map((e) => (
              <ConditionRow key={e.token} token={e.token} value={e.value.toFixed(3)} description={describeToken(e.token, e.descriptionKey)} />
            ))}
          </View>
        ) : null}

        {group === 'batting' ? (
          <View>
            <HeaderRow />
            {conditionProbabilities.battingOrderProbabilities.map((e) => (
              <ConditionRow key={e.token} token={e.token} value={e.value.toFixed(3)} description={describeToken(e.token, e.descriptionKey)} />
            ))}
          </View>
        ) : null}

        {group === 'gates' ? (
          <View>
            <HeaderRow />
            {conditionProbabilities.gates.map((e) => (
              <ConditionRow key={e.token} token={e.token} value={'1.0 / 0.0'} description={describeToken(e.token, e.descriptionKey)} />
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
                <Text style={[typography.titleSmall, { color: colors.primary }]}>{t('methodology_role_label')} {e.role}</Text>
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
