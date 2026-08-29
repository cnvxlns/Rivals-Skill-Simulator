'use client';

import { useCallback, useMemo, useState } from 'react';
import { Handedness, Position, SubPosition } from '../types';

export const BASE_SLOT_COUNT = 3;
export const DEFAULT_USER_STAT = 120;
export const DEFAULT_DECK_SCORE = 500;
export const BATTER_STATS = ['파워', '정확', '선구', '인내', '주루', '수비'];
export const PITCHER_STATS = ['구속', '변화', '구위', '제구', '지구력', '수비'];
export const DECK_STATS = ['스페셜덱', '팀덱'];

export const defaultUserStats = () =>
  [...BATTER_STATS, ...PITCHER_STATS, ...DECK_STATS].reduce<Record<string, number>>((acc, stat) => {
    acc[stat] = DECK_STATS.includes(stat) ? DEFAULT_DECK_SCORE : DEFAULT_USER_STAT;
    return acc;
  }, {});

export const defaultValueForStat = (stat: string) =>
  DECK_STATS.includes(stat) ? DEFAULT_DECK_SCORE : DEFAULT_USER_STAT;

/**
 * 계산기와 점수표가 공유하는 "선수 설정" 상태.
 *
 * 탭마다 별도 인스턴스를 갖는다. 한쪽에서 바꾼 값이 다른 탭에 튀면 혼란스럽기 때문이다.
 */
/**
 * 세부 포지션 기본값.
 *
 * 비워두면 포지션 게이트가 걸린 스킬이 0점으로 빠져 표가 절반만 의미를 갖는다.
 * 목록의 첫 구체 포지션을 쓴다. 투수 옵션에는 C가 없으므로 SP다.
 */
export const defaultSubPosition = (position: Position): SubPosition =>
  position === Position.PITCHER ? 'SP' : 'C';

export function useScoreContext() {
  const [position, setPosition] = useState<Position>(Position.BATTER);
  const [subPosition, setSubPosition] = useState<SubPosition | ''>(defaultSubPosition(Position.BATTER));
  const [battingOrder, setBattingOrder] = useState<number | null>(null);
  const [pitcherSlot, setPitcherSlot] = useState<number | null>(null);
  const [throwHand, setThrowHand] = useState<Handedness>(Handedness.RIGHT);
  const [batHand, setBatHand] = useState<Handedness>(Handedness.RIGHT);
  const [userStats, setUserStats] = useState<Record<string, number>>(defaultUserStats);

  const visibleStats = useMemo(
    () => [...(position === Position.PITCHER ? PITCHER_STATS : BATTER_STATS), ...DECK_STATS],
    [position],
  );

  const updateUserStat = useCallback((stat: string, value: number) => {
    setUserStats((prev) => ({ ...prev, [stat]: Number.isFinite(value) ? value : defaultValueForStat(stat) }));
  }, []);

  const resetUserStats = useCallback(() => setUserStats(defaultUserStats()), []);

  // 포지션이 바뀌면 세부 포지션도 따라가야 한다. 그대로 두면 타자용 C가 남은 채
  // 투수 옵션 목록(SP·RP·CP)에 없는 값이 선택돼 있는 상태가 된다.
  const changePosition = useCallback((next: Position) => {
    setPosition(next);
    setSubPosition(defaultSubPosition(next));
  }, []);

  /** 세부 포지션이 지정돼야 포지션 게이트 조건이 제대로 평가된다. */
  const hasSpecificPosition = Boolean(subPosition) && subPosition !== 'ALL';

  return {
    position,
    setPosition: changePosition,
    subPosition,
    setSubPosition,
    battingOrder,
    setBattingOrder,
    pitcherSlot,
    setPitcherSlot,
    throwHand,
    setThrowHand,
    batHand,
    setBatHand,
    userStats,
    updateUserStat,
    resetUserStats,
    visibleStats,
    hasSpecificPosition,
  };
}
