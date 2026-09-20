'use client';

import { Text, View } from 'react-native';
import { SectionCard } from '../components/ui';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { DeckTeamBuff } from '../types';

/**
 * 덱에서 유도한 팀 버프. 읽기 전용이다.
 *
 * 게임 설명문이 "라인업에 등록된 모든 타자/투수"라고 적은 스킬이 여섯 있다 — 타자·투수
 * 케미스트리, WBC 에이스 둘, 커맨더, 포수 리드다. 워크북은 이것을 드롭다운으로 따로 받지만
 * 우리는 덱이 이미 알고 있는 것을 다시 묻지 않는다. 손으로 고르게 하면 덱과 설정이 어긋나도
 * 아무도 모른다.
 *
 * 대신 무엇이 왜 걸렸는지는 보여 준다. 포수 리드를 가진 선수를 벤치로 내리면 여기서
 * 사라지는 것이 곧 설명이다.
 */
export default function DeckTeamBuffCard({ buffs }: { buffs: DeckTeamBuff[] }) {
  const { t } = useTranslation();
  const { colors, spacing, typography } = useAppTheme();

  return (
    <SectionCard title={t('deck_team_buff_title')}>
      <View style={{ gap: spacing.sm }}>
        <Text style={{ ...typography.label, color: colors.muted }}>{t('deck_team_buff_hint')}</Text>
        {buffs.length === 0 ? (
          <Text style={{ ...typography.body, color: colors.secondaryText }}>
            {t('deck_team_buff_none')}
          </Text>
        ) : (
          buffs.map((buff) => (
            <View
              key={`${buff.slot}-${buff.skillId}`}
              style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}
            >
              <Text style={{ ...typography.label, color: colors.muted, width: 56 }}>{buff.slot}</Text>
              <Text style={{ ...typography.body, color: colors.onSurface, flex: 1 }}>
                {buff.skillName}
              </Text>
              <Text style={{ ...typography.label, color: colors.secondaryText }}>
                {buff.scope === 'BATTER' ? t('deck_team_buff_batters') : t('deck_team_buff_pitchers')}
                {' · '}
                {buff.stats.map((stat) => `${stat.stat}+${stat.amount}`).join(' ')}
              </Text>
            </View>
          ))
        )}
      </View>
    </SectionCard>
  );
}
