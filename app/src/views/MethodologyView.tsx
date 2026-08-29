import React, { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useMethodology } from '../lib/useMethodology';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { ErrorState, LoadingState, SectionCard, StatChip, WeightChip } from '../components/ui';
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

type SectionKey = 'static' | 'role' | 'batting' | 'reach' | 'gates';

const SECTION_ORDER: SectionKey[] = ['static', 'role', 'batting', 'reach', 'gates'];

export default function MethodologyView() {
  const { data, loading, error, refresh } = useMethodology();
  const { t } = useTranslation();
  const { colors, typography, radius, spacing, tabularNums } = useAppTheme();
  const tk = (key: string) => t(key as never);
  const { isSplit, isWide } = useResponsive();

  // 탭 안의 탭은 "내가 어디 있는지"를 잃게 만든다. 번호 붙은 아코디언 한 축으로 펴고,
  // 기본값은 첫 절만 열어 첫 화면 길이를 억제하면서 인터랙션 방식을 즉시 학습시킨다.
  const [open, setOpen] = useState<Record<SectionKey, boolean>>({
    static: true,
    role: false,
    batting: false,
    reach: false,
    gates: false,
  });

  const setAll = (value: boolean) =>
    setOpen({ static: value, role: value, batting: value, reach: value, gates: value });

  if (loading) {
    return <LoadingState text={t('score_loading_skills')} />;
  }

  if (error || !data) {
    return (
      <ErrorState
        message={error ? tk(error) : t('methodology_error_empty')}
        onRetry={refresh}
        retryText={t('btn_retry')}
      />
    );
  }

  const { formula, statWeights, conditionProbabilities } = data;

  // 가중치가 0인 스탯(제구·인내·구속·주루·수비·지구력)은 총점에 전혀 기여하지 않는다.
  // 목록에 두면 실제로 쓰이는 다섯 개가 묻히므로 화면에서는 뺀다. API는 전부 내려준다.
  const weightedStats = Object.entries(statWeights).filter(([, weight]) => weight > 0);

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

  /* ── 수식 카드 ── */

  const FormulaCard = ({ item }: { item: { displayText: string; descriptionKey: string } }) => (
    <View
      style={{
        flex: 1,
        backgroundColor: colors.surfaceVariant,
        borderWidth: 1,
        borderColor: colors.outline,
        borderRadius: radius.input,
        padding: spacing.lgx,
        gap: spacing.md,
      }}
    >
      <Text style={{ color: colors.secondaryText, fontSize: 16, fontWeight: '700', letterSpacing: 0.48 }}>
        {tk(item.descriptionKey)}
      </Text>
      {/* 수식은 가로로 길어질 수 있다. 넘치면 잘리지 않고 밀어 볼 수 있어야 한다. */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 4 }}>
        <Formula
          tex={TEX_BY_KEY[item.descriptionKey] ?? ''}
          fallback={UNICODE_BY_KEY[item.descriptionKey] ?? item.displayText}
        />
      </ScrollView>
    </View>
  );

  const formulaItems = [
    formula.perSkillFormula,
    formula.totalFormula,
    formula.percentEffectRule,
    formula.roundingRule,
    formula.conditionCombinationRule,
  ].filter(Boolean);

  /* ── 표형 절 ── */

  const valueColumnWidth = isWide ? 175 : 122;

  const Table = ({ rows }: { rows: { token: string; value: string; description: string }[] }) => (
    <View style={{ borderWidth: 1, borderColor: colors.outlineFaint, borderRadius: 13, overflow: 'hidden' }}>
      <View
        style={{
          flexDirection: 'row',
          gap: spacing.md,
          backgroundColor: colors.surfaceVariant,
          paddingHorizontal: isWide ? 26 : 22,
          paddingVertical: 17,
        }}
      >
        <Text style={{ flex: 1, color: colors.secondaryText, fontSize: 15.5, fontWeight: '700', letterSpacing: 0.46 }}>
          {t('methodology_col_description')}
        </Text>
        <Text
          style={{
            width: valueColumnWidth,
            textAlign: 'right',
            color: colors.secondaryText,
            fontSize: 15.5,
            fontWeight: '700',
            letterSpacing: 0.46,
          }}
        >
          {t('methodology_col_value')}
        </Text>
      </View>
      {rows.map((row) => (
        <View
          key={row.token}
          style={{
            flexDirection: 'row',
            gap: spacing.md,
            alignItems: 'flex-start',
            paddingHorizontal: isWide ? 26 : 22,
            paddingVertical: 19,
            borderTopWidth: 1,
            borderColor: colors.divider,
          }}
        >
          <View style={{ flex: 1, gap: 6 }}>
            <Text style={{ color: colors.onSurface, fontSize: 17.5, lineHeight: 21 }}>{row.description}</Text>
            {/* 내부 토큰은 보조 표기다. 사용자용 문구가 주 표기. */}
            <Text style={{ color: colors.mutedFaint, fontSize: 15, fontFamily: 'monospace' }}>{row.token}</Text>
          </View>
          <Text
            style={[
              { width: valueColumnWidth, textAlign: 'right', color: colors.accentValue, fontSize: 19, fontWeight: '800' },
              tabularNums,
            ]}
          >
            {row.value}
          </Text>
        </View>
      ))}
    </View>
  );

  /* ── 아코디언 ── */

  const SECTIONS: {
    key: SectionKey;
    title: string;
    summary: string;
    body: React.ReactNode;
  }[] = [
    {
      key: 'static',
      title: t('methodology_group_static'),
      summary: t('methodology_summary_static'),
      body: (
        <Table
          rows={conditionProbabilities.staticProbabilities.map((e) => ({
            token: e.token,
            value: e.value.toFixed(3),
            description: describeToken(e.token, e.descriptionKey),
          }))}
        />
      ),
    },
    {
      key: 'role',
      title: t('methodology_group_role'),
      summary: t('methodology_summary_role'),
      body: (
        <View style={{ gap: spacing.md }}>
          {conditionProbabilities.roleProbabilities.map((e) => (
            <View
              key={e.role}
              style={{
                backgroundColor: colors.surfaceVariant,
                borderWidth: 1,
                borderColor: colors.outline,
                borderRadius: radius.control,
                padding: spacing.md,
                gap: spacing.smd,
              }}
            >
              <Text style={{ color: colors.accentAction, fontSize: 18, fontWeight: '700' }}>
                {t('methodology_role_label')} {e.role}
              </Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 13 }}>
                <WeightChip label={t('condition_guts_probability')} value={e.gutsProbability.toFixed(2)} />
                <WeightChip
                  label={t('condition_patience_below_velocity_probability')}
                  value={e.patienceBelowVelocityProbability.toFixed(2)}
                />
                <WeightChip label={t('condition_nine_batter_duration')} value={e.nineBatterDuration.toFixed(2)} />
                <WeightChip label={t('condition_maestro_cumulative')} value={e.maestroCumulative.toFixed(3)} />
              </View>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 13 }}>
                {e.inningWeights.map((w, idx) => (
                  <StatChip key={idx} label={`${idx + 1}${t('methodology_inning_suffix')}`} value={w.toFixed(3)} />
                ))}
              </View>
            </View>
          ))}
        </View>
      ),
    },
    {
      key: 'batting',
      title: t('methodology_group_batting'),
      summary: t('methodology_summary_batting'),
      body: (
        <Table
          rows={conditionProbabilities.battingOrderProbabilities.map((e) => ({
            token: e.token,
            value: e.value.toFixed(3),
            description: describeToken(e.token, e.descriptionKey),
          }))}
        />
      ),
    },
    {
      key: 'reach',
      title: t('methodology_group_reach'),
      summary: t('methodology_summary_reach'),
      body: (
        <View style={{ gap: spacing.md }}>
          {conditionProbabilities.reachProbabilities.map((e) => (
            <View
              key={e.orderGroup}
              style={{
                backgroundColor: colors.surfaceVariant,
                borderWidth: 1,
                borderColor: colors.outline,
                borderRadius: radius.control,
                padding: spacing.md,
                gap: spacing.smd,
              }}
            >
              <Text style={{ color: colors.accentAction, fontSize: 18, fontWeight: '700' }}>{tk(e.descriptionKey)}</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 13 }}>
                {e.reachProbabilities.map((p, idx) => (
                  <StatChip key={idx} label={`${t('methodology_pa_prefix')}${idx + 1}`} value={p.toFixed(3)} />
                ))}
              </View>
            </View>
          ))}
        </View>
      ),
    },
    {
      key: 'gates',
      title: t('methodology_group_gates'),
      summary: t('methodology_summary_gates'),
      body: (
        <Table
          rows={conditionProbabilities.gates.map((e) => ({
            token: e.token,
            // 게이트는 통과 여부만 있는 이진 조건이라 확률값 대신 두 값을 병기한다.
            value: '1.0 / 0.0',
            description: describeToken(e.token, e.descriptionKey),
          }))}
        />
      ),
    },
  ];

  const GlobalButton = ({ text, onPress }: { text: string; onPress: () => void }) => (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => ({
        paddingHorizontal: spacing.mdl,
        paddingVertical: spacing.sm,
        borderRadius: 10,
        borderWidth: 1,
        borderColor: colors.outline,
        backgroundColor: colors.surface,
        opacity: pressed ? 0.72 : 1,
      })}
    >
      <Text style={{ color: colors.secondaryText, fontSize: 16, fontWeight: '600' }}>{text}</Text>
    </Pressable>
  );

  return (
    <View style={{ gap: spacing.md }}>
      <Text style={[typography.title, { color: colors.onSurface }]}>{t('methodology_title')}</Text>
      <Text style={{ color: colors.secondaryText, fontSize: 18, lineHeight: 24 }}>{t('methodology_desc')}</Text>

      {/* ── 산정 공식 ── */}
      <SectionCard
        title={t('methodology_section_formula')}
        padding={isWide ? spacing.xxl : spacing.lg}
      >
        <View style={{ flexDirection: isSplit ? 'row' : 'column', flexWrap: 'wrap', gap: spacing.mdl }}>
          {formulaItems.map((item, i) => (
            <View key={item.descriptionKey || i} style={isSplit ? { width: '48.5%' } : undefined}>
              <FormulaCard item={item} />
            </View>
          ))}
        </View>
      </SectionCard>

      {/* ── 능력치 가중치 ── */}
      <SectionCard
        title={t('methodology_section_stat_weights')}
        padding={isWide ? spacing.xxl : spacing.lg}
        right={
          <Text style={{ color: colors.muted, fontSize: 16 }}>
            {weightedStats.length}
            {t('methodology_weights_count_suffix')}
          </Text>
        }
      >
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 13 }}>
          {weightedStats.map(([stat, weight]) => (
            <WeightChip key={stat} label={tk(`stat_${stat}`)} value={weight.toFixed(2)} />
          ))}
        </View>
      </SectionCard>

      {/* ── 조건부 확률: 아코디언 5절 ── */}
      <View style={{ gap: spacing.md }}>
        <View
          style={{
            flexDirection: isWide ? 'row' : 'column',
            alignItems: isWide ? 'center' : 'flex-start',
            justifyContent: 'space-between',
            gap: spacing.smd,
          }}
        >
          <View style={{ gap: 5, flexShrink: 1 }}>
            <Text style={[typography.card, { color: colors.onSurface }]}>{t('methodology_section_conditions')}</Text>
            <Text style={{ color: colors.muted, fontSize: 16 }}>
              {SECTION_ORDER.length}
              {t('methodology_section_count_suffix')} · {t('methodology_sections_hint')}
            </Text>
          </View>
          <View style={{ flexDirection: 'row', gap: spacing.sm }}>
            <GlobalButton text={t('action_expand_all')} onPress={() => setAll(true)} />
            <GlobalButton text={t('action_collapse_all')} onPress={() => setAll(false)} />
          </View>
        </View>

        {SECTIONS.map((section, index) => {
          const isOpen = open[section.key];
          return (
            <View
              key={section.key}
              style={{
                backgroundColor: colors.surface,
                borderWidth: 1,
                // 열림 표시를 테두리·번호배지·caret 세 곳에 동시에 준다. 색 하나에 의존하지 않는다.
                borderColor: isOpen ? '#2A3D46' : colors.outlineFaint,
                borderRadius: radius.card,
                overflow: 'hidden',
              }}
            >
              <Pressable
                onPress={() => setOpen((prev) => ({ ...prev, [section.key]: !prev[section.key] }))}
                style={({ pressed }) => ({
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: spacing.lg,
                  paddingHorizontal: isWide ? 31 : 24,
                  paddingVertical: 26,
                  opacity: pressed ? 0.72 : 1,
                })}
              >
                <View
                  style={{
                    width: 38,
                    height: 26,
                    borderRadius: radius.chip,
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: isOpen ? colors.accentAction : colors.outline,
                  }}
                >
                  <Text
                    style={{ color: isOpen ? colors.onAccentAction : colors.secondaryText, fontSize: 16, fontWeight: '800' }}
                  >
                    {index + 1}
                  </Text>
                </View>
                <View style={{ flex: 1, gap: 5 }}>
                  <Text style={{ color: colors.onSurface, fontSize: 19, fontWeight: '700' }}>{section.title}</Text>
                  <Text style={{ color: colors.muted, fontSize: 16 }} numberOfLines={2}>
                    {section.summary}
                  </Text>
                </View>
                <Text
                  style={{ color: isOpen ? colors.accentAction : colors.muted, fontSize: 16, fontWeight: '700' }}
                  numberOfLines={1}
                >
                  {isOpen ? `${t('action_collapse')} ▲` : `${t('action_expand')} ▼`}
                </Text>
              </Pressable>

              {isOpen ? <View style={{ paddingHorizontal: isWide ? 31 : 24, paddingBottom: 31 }}>{section.body}</View> : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}
