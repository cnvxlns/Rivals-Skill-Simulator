import {
  CardGrade,
  CardVariant,
  DeckPlayer,
  DeckPlayerScore,
  Handedness,
  PositionTraining,
  ScoreSelection,
  SkillLevelBonus,
  SubPosition,
} from '../types';
import { isPitcher, positionForSlot } from './useDeckEditor';

export type LineupCalculatorInput = {
  playerName?: string;
  slot: string;
  cardGrade: CardGrade;
  cardVariant: CardVariant;
  subPosition: SubPosition;
  selections: ScoreSelection[];
  battingOrder?: number | null;
  pitcherSlot?: number | null;
  batHand?: Handedness | null;
  throwHand?: Handedness | null;
  userStats?: Record<string, number>;
  baseStats?: Record<string, number>;
  trainingBonuses?: SkillLevelBonus[];
  transcendenceLevel?: number | null;
};

export function lineupCalculatorInput(
  player: DeckPlayer,
  training: PositionTraining,
  score?: DeckPlayerScore,
): LineupCalculatorInput {
  const { slot } = player;
  const currentTraining = training.slots[slot];
  const inputTraining = training.slots[player.statsSlot || slot];
  const userStats = { ...player.stats };
  for (const stat of Object.keys(userStats)) {
    userStats[stat] += (currentTraining?.stats?.[stat] ?? 0) - (inputTraining?.stats?.[stat] ?? 0);
  }
  for (const [stat, base] of Object.entries(player.baseStats ?? {})) {
    userStats[stat] = base + (player.trainingStats?.[stat] ?? 0) +
      (player.specialTrainingStats?.[stat] ?? 0) + (currentTraining?.stats?.[stat] ?? 0);
  }
  const tacticBuff = new Map(score?.tacticBuff.map(({ stat, amount }) => [stat, amount]));
  for (const { stat, amount } of score?.finalStats ?? []) {
    userStats[stat] = amount + (tacticBuff.get(stat) ?? 0);
  }

  return {
    playerName: player.playerName?.trim(),
    slot,
    cardGrade: player.cardGrade as CardGrade,
    cardVariant: (player.cardVariant || CardVariant.NONE) as CardVariant,
    subPosition: positionForSlot(slot, player.position) as SubPosition,
    selections: player.skills.map(({ skillId, level }) => ({ skillId, level })),
    battingOrder: player.battingOrder,
    pitcherSlot: isPitcher(slot) ? Number(slot.slice(2)) : undefined,
    batHand: player.batHand,
    throwHand: player.throwHand,
    userStats,
    baseStats: player.baseStats,
    trainingBonuses: currentTraining?.skills,
    transcendenceLevel: player.transcendenceLevel,
  };
}
