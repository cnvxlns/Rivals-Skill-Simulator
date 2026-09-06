'use client';

import React, { useRef, useState } from 'react';
import { PanResponder, PanResponderInstance, Platform, Pressable, Text, View } from 'react-native';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';

/**
 * 타순 레인.
 *
 * 야구장 그림 위의 칩은 **수비 위치**에 고정돼 있어서 끌어 옮겨도 타순을 표현할 수 없다.
 * 그래서 타순만 다루는 자리를 따로 둔다. 행 순서가 곧 1~9번이다.
 *
 * 끌어 놓으면 그 자리에 **끼워 넣고** 나머지가 한 칸씩 밀린다. 맞바꾸기가 아니다.
 *
 * 드래그는 제스처 라이브러리 없이 RN 코어 [PanResponder]로 한다. reanimated를 쓰려면
 * 이 저장소에 없는 babel 설정을 새로 만들어야 하는데, reactCompiler가 켜져 있고 프런트
 * 테스트가 없어서 빌드 설정을 건드리는 값이 비싸다. 행이 아홉 개뿐인 세로 목록이라
 * PanResponder로 충분하다.
 */

/** 행 하나의 높이. 고정이라 끌린 거리를 이 값으로 나누면 목표 칸이 바로 나온다. */
const ROW_HEIGHT = 52;

/** 이만큼 움직여야 드래그로 본다. 그 아래는 탭으로 넘긴다. */
const DRAG_THRESHOLD = 4;

/** 웹 전용 스타일. RN의 ViewStyle에는 없어서 캐스팅해 넣는다(ui.tsx의 position:'fixed'와 같은 방식). */
const webHandleStyle =
  Platform.OS === 'web'
    ? ({
        // 없으면 손가락으로 핸들을 끌 때 페이지가 같이 스크롤된다.
        touchAction: 'none',
        // 없으면 마우스로 끌 때 옆 텍스트가 드래그 선택돼 화면이 파랗게 된다.
        userSelect: 'none',
        cursor: 'grab',
      } as object)
    : null;

export type BattingOrderRow = {
  slot: string;
  /** 화면에 쓸 이름. 선수 이름이 없으면 카드 등급 등으로 채워 넘긴다. */
  title: string;
  /** 보조 설명. 비어 있으면 표시하지 않는다. */
  subtitle?: string;
  done: boolean;
  /** 카드 등급색. 야구장 칩과 같은 색이라 두 화면이 같은 선수를 가리키는 것이 보인다. */
  accent?: string;
};

export default function BattingOrderLane({
  rows,
  onMove,
  onPress,
}: {
  /** 타순 순서로 정렬된 주전 아홉 자리. */
  rows: BattingOrderRow[];
  /** 1부터 시작하는 목표 타순으로 옮긴다. */
  onMove: (slot: string, order: number) => void;
  onPress: (slot: string) => void;
}) {
  const { t } = useTranslation();
  const { colors, typography, spacing, radius } = useAppTheme();

  // 드래그 중에는 화면만 움직이고 상태는 손대지 않는다. 놓을 때 한 번만 커밋한다.
  // 매 프레임 커밋하면 완성된 덱에서 프리뷰 채점 요청이 쏟아진다.
  const [drag, setDrag] = useState<{ from: number; to: number; residual: number } | null>(null);

  const view = drag ? move(rows, drag.from, drag.to) : rows;

  return (
    <View style={{ gap: 2 }}>
      <Text style={{ ...typography.label, color: colors.secondaryText, marginBottom: spacing.xs }}>
        {t('deck_batting_order_hint')}
      </Text>

      {view.map((row, index) => {
        const dragged = drag != null && index === drag.to;
        return (
          <View
            key={row.slot}
            style={{
              height: ROW_HEIGHT,
              flexDirection: 'row',
              alignItems: 'center',
              gap: spacing.smd,
              paddingHorizontal: spacing.xs,
              borderRadius: radius.control,
              backgroundColor: dragged ? colors.surfaceVariant : 'transparent',
              borderWidth: 1,
              borderColor: dragged ? colors.accentAction : 'transparent',
              transform: dragged ? [{ translateY: drag!.residual }] : undefined,
              zIndex: dragged ? 1 : 0,
            }}
          >
            <DragHandle
              index={index}
              rowCount={rows.length}
              onStart={(at) => setDrag({ from: at, to: at, residual: 0 })}
              onMove={(dy) =>
                setDrag((prev) => {
                  if (!prev) return prev;
                  const steps = Math.round(dy / ROW_HEIGHT);
                  const to = clamp(prev.from + steps, 0, rows.length - 1);
                  return { ...prev, to, residual: dy - (to - prev.from) * ROW_HEIGHT };
                })
              }
              onEnd={() => {
                setDrag((prev) => {
                  if (prev && prev.to !== prev.from) onMove(rows[prev.from].slot, prev.to + 1);
                  return null;
                });
              }}
              onCancel={() => setDrag(null)}
            />

            {/* 등급색 레일. 폭이 좁아 글자를 더 넣기 어려운 자리라 색으로만 알린다. */}
            <View
              style={{
                width: 4,
                height: 30,
                borderRadius: 2,
                backgroundColor: row.accent ?? colors.outline,
              }}
            />

            <Text style={{ ...typography.card, color: colors.onSurface, width: 28 }}>
              {index + 1}
            </Text>

            <Pressable
              onPress={() => onPress(row.slot)}
              style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: spacing.smd }}
            >
              <Text
                style={{ ...typography.label, color: colors.secondaryText, width: 44 }}
                numberOfLines={1}
              >
                {row.slot}
              </Text>
              <Text
                style={{ ...typography.card, color: row.done ? colors.onSurface : colors.muted, flex: 1 }}
                numberOfLines={1}
              >
                {row.title}
              </Text>
              {row.subtitle ? (
                <Text
                  style={{ ...typography.label, color: colors.secondaryText }}
                  numberOfLines={1}
                >
                  {row.subtitle}
                </Text>
              ) : null}
            </Pressable>

            {/*
              드래그만 두면 키보드 사용자와 좁은 화면에서 길이 없다. 화살표로도 한 칸씩
              옮길 수 있게 둔다. 같은 onMove를 부르므로 결과가 갈리지 않는다.
            */}
            <StepButton
              label="▲"
              hint={t('deck_batting_order_up')}
              disabled={drag != null || index === 0}
              onPress={() => onMove(row.slot, index)}
            />
            <StepButton
              label="▼"
              hint={t('deck_batting_order_down')}
              disabled={drag != null || index === rows.length - 1}
              onPress={() => onMove(row.slot, index + 2)}
            />
          </View>
        );
      })}
    </View>
  );
}

/**
 * 끌기 손잡이.
 *
 * PanResponder를 렌더마다 새로 만들면 responder가 매번 갈리고, ref에 한 번만 담으면
 * 핸들러가 첫 렌더의 값을 영원히 본다. 콜백만 ref로 갱신해 둘 다 피한다.
 */
function DragHandle({
  index,
  rowCount,
  onStart,
  onMove,
  onEnd,
  onCancel,
}: {
  index: number;
  rowCount: number;
  onStart: (index: number) => void;
  onMove: (dy: number) => void;
  onEnd: () => void;
  onCancel: () => void;
}) {
  const { colors, typography, spacing } = useAppTheme();
  const latest = useRef({ index, onStart, onMove, onEnd, onCancel });
  latest.current = { index, onStart, onMove, onEnd, onCancel };

  const responder = useRef<PanResponderInstance>(undefined);
  if (!responder.current) {
    responder.current = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_, gesture) => Math.abs(gesture.dy) > DRAG_THRESHOLD,
      // 한 번 잡으면 스크롤에 뺏기지 않는다.
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: () => latest.current.onStart(latest.current.index),
      onPanResponderMove: (_, gesture) => latest.current.onMove(gesture.dy),
      onPanResponderRelease: () => latest.current.onEnd(),
      // 브라우저 탭 전환처럼 제스처가 끊길 때. 없으면 행이 뜬 채로 굳는다.
      onPanResponderTerminate: () => latest.current.onCancel(),
    });
  }

  return (
    <View
      {...responder.current.panHandlers}
      accessibilityRole="adjustable"
      accessibilityLabel={`${index + 1} / ${rowCount}`}
      style={[
        {
          width: 28,
          height: ROW_HEIGHT,
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: spacing.xs,
        },
        webHandleStyle,
      ]}
    >
      <Text style={{ ...typography.card, color: colors.secondaryText }}>⠿</Text>
    </View>
  );
}

function StepButton({
  label,
  hint,
  disabled,
  onPress,
}: {
  label: string;
  hint: string;
  disabled: boolean;
  onPress: () => void;
}) {
  const { colors, typography, radius } = useAppTheme();
  return (
    <Pressable
      onPress={disabled ? undefined : onPress}
      accessibilityRole="button"
      accessibilityLabel={hint}
      style={({ pressed }) => ({
        width: 30,
        height: 30,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.control,
        backgroundColor: colors.surfaceVariant,
        opacity: disabled ? 0.3 : pressed ? 0.7 : 1,
      })}
    >
      <Text style={{ ...typography.label, color: colors.secondaryText }}>{label}</Text>
    </Pressable>
  );
}

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

/** from 자리를 뽑아 to 자리에 끼워 넣은 새 배열. 화면 미리보기에만 쓴다. */
function move<T>(items: T[], from: number, to: number): T[] {
  if (from === to) return items;
  const next = [...items];
  next.splice(to, 0, ...next.splice(from, 1));
  return next;
}
