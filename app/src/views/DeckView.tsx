'use client';

import { useCallback, useEffect, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import {
  EmptyState,
  ErrorState,
  LinkAction,
  LoadingState,
  PrimaryActionButton,
  SectionCard,
} from '../components/ui';
import { apiErrorMessage, deleteDeck, fetchDeck, fetchDecks } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';
import { DeckDetail, DeckSummary } from '../types';
import DeckEditorView from './DeckEditorView';
import LoginView from './LoginView';

type Mode = { kind: 'list' } | { kind: 'new' } | { kind: 'edit'; deck: DeckDetail };

/**
 * 덱 탭. 로그인 여부에 따라 로그인 화면 / 목록 / 편집기를 보여 준다.
 *
 * 라우트를 새로 파지 않고 기존 단일 라우트 + 탭 구조를 유지한다.
 */
export default function DeckView() {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();
  const { user, loading: authLoading, signOut } = useAuth();

  const [decks, setDecks] = useState<DeckSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>({ kind: 'list' });

  const load = useCallback(async () => {
    if (!user) return;
    setLoading(true);
    setError(null);
    try {
      setDecks(await fetchDecks());
    } catch (err) {
      setError(apiErrorMessage(err) ?? t('deck_error_load'));
    } finally {
      setLoading(false);
    }
  }, [user, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const openDeck = async (id: number) => {
    setLoading(true);
    try {
      setMode({ kind: 'edit', deck: await fetchDeck(id) });
    } catch (err) {
      setError(apiErrorMessage(err) ?? t('deck_error_load'));
    } finally {
      setLoading(false);
    }
  };

  const removeDeck = async (id: number) => {
    try {
      await deleteDeck(id);
      setMode({ kind: 'list' });
      await load();
    } catch (err) {
      setError(apiErrorMessage(err) ?? t('deck_error_save'));
    }
  };

  const backToList = () => {
    setMode({ kind: 'list' });
    void load();
  };

  if (authLoading) return <LoadingState />;
  if (!user) return <LoginView />;

  if (mode.kind === 'new') {
    return <DeckEditorView onDone={backToList} />;
  }
  if (mode.kind === 'edit') {
    return <DeckEditorView initial={mode.deck} onDone={backToList} onDelete={removeDeck} />;
  }

  return (
    <View style={{ gap: spacing.lg }}>
      <SectionCard
        title={t('deck_list_title')}
        right={<LinkAction text={t('auth_btn_signout')} onPress={signOut} />}
      >
        <View style={{ gap: spacing.md }}>
          <Text style={{ ...typography.body, color: colors.secondaryText }}>{user.email}</Text>
          <PrimaryActionButton text={t('deck_btn_new')} onPress={() => setMode({ kind: 'new' })} />
        </View>
      </SectionCard>

      {loading ? <LoadingState /> : null}
      {error ? <ErrorState message={error} onRetry={load} retryText={t('btn_retry')} /> : null}

      {!loading && !error && decks.length === 0 ? <EmptyState text={t('deck_list_empty')} /> : null}

      {decks.map((deck) => (
        <Pressable
          key={deck.id}
          onPress={() => openDeck(deck.id)}
          style={({ pressed }) => ({
            padding: spacing.lg,
            borderRadius: radius.card,
            backgroundColor: colors.surface,
            borderWidth: 1,
            borderColor: colors.outline,
            opacity: pressed ? 0.75 : 1,
            gap: spacing.xs,
          })}
        >
          <Text style={{ ...typography.card, color: colors.onSurface }}>{deck.name}</Text>
          <Text style={{ ...typography.label, color: colors.secondaryText }}>
            {/* 저장 시점 값이다. 열어 보면 지금 기준으로 다시 계산된 점수가 나온다. */}
            {t('deck_score_stale')} {deck.totalScore?.toFixed(2) ?? '-'}
          </Text>
        </Pressable>
      ))}
    </View>
  );
}
