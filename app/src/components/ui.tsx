// 공용 UI 프리미티브. 원본 스펙: docs/design_handoff_mlb_rival_skill_tool/README.md
//
// 규칙 두 가지를 전 컴포넌트가 지킨다.
//  1. 그림자를 쓰지 않는다. 면 분리는 외곽선 + 배경 명도차로만 한다(플랫폼별 동작이 달라서).
//  2. hover에만 의존하는 정보를 두지 않는다. 웹과 앱이 같은 컴포넌트를 공유한다.
import React, { useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { PropsWithChildren } from 'react';
import {
  ActivityIndicator,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  useWindowDimensions,
  View,
  ViewStyle,
} from 'react-native';
import { useAppTheme } from '../theme/useTheme';
import { CaretDown, SearchIcon } from './icons';

/* ── 툴팁 ────────────────────────────────────────────────── */

type TooltipRect = { x: number; y: number; width: number; height: number };
type TooltipApi = { show: (text: string | null | undefined, rect: TooltipRect) => void; hide: () => void };

const TooltipContext = React.createContext<TooltipApi | null>(null);

const TOOLTIP_MAX_WIDTH = 340;
const TOOLTIP_EDGE = 8;
const TOOLTIP_OFFSET = 6;

/**
 * 설명 툴팁의 상태와 오버레이를 들고 있다.
 *
 * 티어 카드에 overflow:'hidden'이 걸려 있어(색 레일과 둥근 모서리를 자르려고) 카드 안에서
 * 절대 위치로 띄우면 가장자리 행에서 잘린다. 그래서 오버레이 한 장을 화면 최상단에 두고,
 * 열릴 때 measureInWindow로 잰 창 좌표에 그린다. 카드 경계와 스크롤 컨테이너에 무관해진다.
 *
 * 웹은 position:'fixed'라야 페이지 스크롤과 어긋나지 않는다. RN 스타일 타입에 없는 값이라
 * 웹에서만 넣고 캐스팅한다. 네이티브는 루트 View 기준 absolute면 창 좌표와 일치한다.
 */
export function TooltipProvider({ children }: PropsWithChildren) {
  const { colors, typography, radius, spacing } = useAppTheme();
  const { width: windowWidth } = useWindowDimensions();
  const [tip, setTip] = useState<{ text: string; x: number; y: number } | null>(null);

  const api = useMemo<TooltipApi>(
    () => ({
      show: (text, rect) => {
        const body = (text ?? '').trim();
        if (!body) return;
        // 오른쪽 끝 행에서 툴팁이 화면 밖으로 나가지 않게 가둔다.
        const maxLeft = Math.max(TOOLTIP_EDGE, windowWidth - TOOLTIP_MAX_WIDTH - TOOLTIP_EDGE);
        setTip({ text: body, x: Math.min(Math.max(rect.x, TOOLTIP_EDGE), maxLeft), y: rect.y + rect.height + TOOLTIP_OFFSET });
      },
      hide: () => setTip(null),
    }),
    [windowWidth],
  );

  return (
    <TooltipContext.Provider value={api}>
      {children}
      {tip ? (
        <View
          // 커서를 가려 hover가 끊기면 툴팁이 깜빡인다. 이벤트를 받지 않는다.
          pointerEvents="none"
          style={{
            position: (Platform.OS === 'web' ? 'fixed' : 'absolute') as 'absolute',
            left: tip.x,
            top: tip.y,
            maxWidth: TOOLTIP_MAX_WIDTH,
            zIndex: 1000,
            backgroundColor: colors.surfaceVariant,
            borderWidth: 1,
            borderColor: colors.outline,
            borderRadius: radius.card,
            paddingHorizontal: spacing.md,
            paddingVertical: spacing.sm,
          }}
        >
          <Text style={[typography.body, { color: colors.secondaryText }]}>{tip.text}</Text>
        </View>
      ) : null}
    </TooltipContext.Provider>
  );
}

/**
 * 감싼 내용에 설명 툴팁을 붙인다. 웹은 hover, 터치는 길게 누르기로 연다.
 *
 * hover에만 정보를 두지 않는다는 이 파일의 규칙 2를 지키려면 두 경로가 모두 필요하다.
 * text가 비면 아무것도 감싸지 않고 그대로 통과시킨다.
 */
export function TooltipTarget({
  text,
  style,
  children,
}: PropsWithChildren<{ text?: string | null; style?: ViewStyle }>) {
  const api = useContext(TooltipContext);
  const ref = useRef<View>(null);

  const open = useCallback(() => {
    if (!api || !text) return;
    ref.current?.measureInWindow((x, y, width, height) => api.show(text, { x, y, width, height }));
  }, [api, text]);
  const close = useCallback(() => api?.hide(), [api]);

  if (!text) return <View style={style}>{children}</View>;

  return (
    <View ref={ref} collapsable={false} style={style}>
      <Pressable onHoverIn={open} onHoverOut={close} onLongPress={open} onPressOut={close} delayLongPress={300}>
        {children}
      </Pressable>
    </View>
  );
}

/* ── 카드 ────────────────────────────────────────────────── */

export function SectionCard({
  title,
  right,
  children,
  style,
  padding,
}: {
  title?: string;
  right?: React.ReactNode;
  children: React.ReactNode;
  style?: ViewStyle;
  padding?: number;
}) {
  const { colors, radius, spacing, typography } = useAppTheme();
  return (
    <View
      style={[
        {
          backgroundColor: colors.surface,
          borderWidth: 1,
          borderColor: colors.outlineFaint,
          borderRadius: radius.cardLg,
          padding: padding ?? spacing.xxl,
          gap: spacing.lgx,
        },
        style,
      ]}
    >
      {title || right ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.md }}>
          {title ? <Text style={[typography.card, { color: colors.onSurface }]}>{title}</Text> : <View />}
          {right}
        </View>
      ) : null}
      {children}
    </View>
  );
}

/* ── 칩 ──────────────────────────────────────────────────── */

export function TierChip({ tier, label }: { tier: string; label: string }) {
  const { radius, tierColor } = useAppTheme();
  const c = tierColor(tier);
  return (
    <View style={{ backgroundColor: c.soft, borderRadius: radius.chip, paddingHorizontal: 11, paddingVertical: 4 }}>
      <Text style={{ color: c.hex, fontSize: 12, fontWeight: '800' }} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
}

/** 데스크톱은 고정폭 52px로 점수 열과 나란히 정렬된다. 모바일(compact)은 내용폭. */
export function GradeChip({
  grade,
  inactive = false,
  compact = false,
}: {
  grade: string;
  inactive?: boolean;
  compact?: boolean;
}) {
  const { gradeStyle } = useAppTheme();
  const g = String(grade || '').toUpperCase();
  const s = gradeStyle(g, inactive);
  return (
    <View
      style={{
        backgroundColor: s.fill,
        borderWidth: 1,
        borderColor: s.border,
        borderRadius: compact ? 6 : 7,
        paddingVertical: compact ? 2 : 4,
        paddingHorizontal: compact ? 7 : 0,
        width: compact ? undefined : 52,
        alignItems: 'center',
      }}
    >
      <Text style={{ color: s.text, fontSize: compact ? 10.5 : 11.5, fontWeight: '800' }} numberOfLines={1}>
        {g}
      </Text>
    </View>
  );
}

/** 라벨 + 값 배지. 능력치 가중치처럼 이름과 수치가 짝을 이룰 때. */
export function WeightChip({ label, value }: { label: string; value: string }) {
  const { colors, radius, spacing, tabularNums } = useAppTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 9,
        backgroundColor: colors.surfaceVariant,
        borderWidth: 1,
        borderColor: colors.outline,
        borderRadius: radius.pill,
        paddingLeft: spacing.mdl,
        paddingRight: spacing.sm,
        paddingVertical: spacing.sm,
      }}
    >
      <Text style={{ color: colors.secondaryText, fontSize: 12.5, fontWeight: '600' }}>{label}</Text>
      <View
        style={{
          backgroundColor: colors.background,
          borderRadius: radius.pill,
          paddingHorizontal: 9,
          paddingVertical: 3,
        }}
      >
        <Text style={[{ color: colors.accentValue, fontSize: 12, fontWeight: '800' }, tabularNums]}>{value}</Text>
      </View>
    </View>
  );
}

/** 라벨 위 · 값 아래. 이닝 가중치처럼 같은 축의 수치가 여럿 나열될 때. */
export function StatChip({ label, value }: { label: string; value: string }) {
  const { colors, radius, tabularNums } = useAppTheme();
  return (
    <View
      style={{
        backgroundColor: colors.surfaceVariant,
        borderWidth: 1,
        borderColor: colors.outline,
        borderRadius: radius.control,
        paddingHorizontal: 15,
        paddingVertical: 11,
        minWidth: 84,
        gap: 5,
      }}
    >
      <Text style={{ color: colors.muted, fontSize: 11, fontWeight: '600' }} numberOfLines={1}>
        {label}
      </Text>
      <Text style={[{ color: colors.accentValue, fontSize: 15, fontWeight: '800' }, tabularNums]} numberOfLines={1}>
        {value}
      </Text>
    </View>
  );
}

/* ── 배너 ────────────────────────────────────────────────── */

export function InfoBanner({ text, tone = 'warn' }: { text: string; tone?: 'warn' | 'error' }) {
  const { banner, radius, spacing } = useAppTheme();
  const c = tone === 'error' ? banner.error : banner.warn;
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'flex-start',
        gap: spacing.mdl,
        backgroundColor: c.background,
        borderWidth: 1,
        borderColor: c.border,
        borderRadius: radius.control,
        paddingHorizontal: spacing.lg,
        paddingVertical: 13,
      }}
    >
      <View
        style={{
          width: 20,
          height: 20,
          borderRadius: 999,
          backgroundColor: c.badge,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Text style={{ color: c.onBadge, fontSize: 13, fontWeight: '800' }}>!</Text>
      </View>
      <Text style={{ color: c.text, fontSize: 13, lineHeight: 21, flex: 1 }}>{text}</Text>
    </View>
  );
}

/* ── 탭 ──────────────────────────────────────────────────── */

export function SegmentedTabs<T extends string>({
  tabs,
  selected,
  onSelect,
  stretch = false,
}: {
  tabs: { key: T; label: string }[];
  selected: T;
  onSelect: (key: T) => void;
  /** 모바일: 각 탭이 균등 분할. 데스크톱: 내용폭으로 좌측 정렬. */
  stretch?: boolean;
}) {
  const { colors, radius, controlHeight } = useAppTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignSelf: stretch ? 'stretch' : 'flex-start',
        backgroundColor: colors.surface,
        borderWidth: 1,
        borderColor: colors.outlineFaint,
        borderRadius: radius.pill,
        padding: stretch ? 4 : 5,
        gap: stretch ? 4 : 6,
      }}
    >
      {tabs.map((tab) => {
        const active = tab.key === selected;
        return (
          <Pressable
            key={tab.key}
            onPress={() => onSelect(tab.key)}
            style={({ pressed }) => ({
              flex: stretch ? 1 : undefined,
              height: stretch ? controlHeight.tabMobile : undefined,
              paddingVertical: stretch ? 0 : 11,
              paddingHorizontal: stretch ? 8 : 26,
              borderRadius: radius.pill,
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: active ? colors.accentAction : 'transparent',
              opacity: pressed ? 0.72 : 1,
            })}
          >
            <Text
              style={{
                color: active ? colors.onAccentAction : colors.secondaryText,
                fontSize: stretch ? 13.5 : 14,
                fontWeight: active ? '700' : '600',
              }}
              numberOfLines={1}
            >
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/* ── 버튼 ────────────────────────────────────────────────── */

export function PrimaryActionButton({
  text,
  onPress,
  enabled = true,
  loading = false,
  style,
}: {
  text: string;
  onPress: () => void;
  enabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
}) {
  const { colors, radius, controlHeight } = useAppTheme();
  const disabled = !enabled || loading;
  const bg = disabled && !loading ? colors.surfaceVariant : colors.accentAction;
  const fg = disabled && !loading ? colors.disabled : colors.onAccentAction;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        {
          height: controlHeight.button,
          borderRadius: radius.input,
          alignItems: 'center',
          justifyContent: 'center',
          flexDirection: 'row',
          gap: 9,
          backgroundColor: bg,
          opacity: pressed ? 0.72 : 1,
        },
        style,
      ]}
    >
      {loading ? <ActivityIndicator size="small" color={fg} /> : null}
      <Text style={{ color: fg, fontSize: 14, fontWeight: '700' }}>{text}</Text>
    </Pressable>
  );
}

/** 인라인 텍스트 액션. 링크는 시안(인터랙션 색)으로만 쓴다. */
export function LinkAction({ text, onPress, style }: { text: string; onPress: () => void; style?: ViewStyle }) {
  const { colors } = useAppTheme();
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [{ opacity: pressed ? 0.72 : 1 }, style]}>
      <Text style={{ color: colors.accentAction, fontSize: 12.5, fontWeight: '600' }}>{text}</Text>
    </Pressable>
  );
}

/* ── 입력 ────────────────────────────────────────────────── */

export function SearchInput({
  value,
  onChangeText,
  placeholder,
  height,
}: {
  value: string;
  onChangeText: (v: string) => void;
  placeholder: string;
  height?: number;
}) {
  const { colors, radius, controlHeight, spacing } = useAppTheme();
  const [focused, setFocused] = useState(false);
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.md,
        height: height ?? controlHeight.input,
        paddingHorizontal: spacing.lgx,
        borderRadius: radius.input,
        backgroundColor: colors.surface,
        borderWidth: 1,
        borderColor: focused ? colors.accentAction : colors.outlineFaint,
      }}
    >
      <SearchIcon size={17} color={focused ? colors.accentAction : colors.muted} />
      <TextInput
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.muted}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        style={[{ flex: 1, color: colors.onSurface, fontSize: 14, fontWeight: '600' }, NO_OUTLINE] as never}
      />
    </View>
  );
}

export function NumberField({
  label,
  value,
  onChangeText,
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
}) {
  const { colors, radius, controlHeight, tabularNums } = useAppTheme();
  const [focused, setFocused] = useState(false);
  return (
    <View style={{ gap: 7 }}>
      <Text style={{ color: colors.secondaryText, fontSize: 12, fontWeight: '600' }} numberOfLines={1}>
        {label}
      </Text>
      <TextInput
        value={value}
        onChangeText={onChangeText}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        keyboardType="numeric"
        style={
          [
            {
              height: controlHeight.dropdown,
              paddingHorizontal: 14,
              borderRadius: radius.control,
              backgroundColor: colors.surfaceVariant,
              borderWidth: 1,
              borderColor: focused ? colors.accentAction : colors.outline,
              color: colors.onSurface,
              fontSize: 14,
              fontWeight: '600',
            },
            tabularNums,
            NO_OUTLINE,
          ] as never
        }
      />
    </View>
  );
}

/** 웹 전용 속성이라 네이티브에서는 무시된다. 포커스 링을 우리가 직접 그리기 위해 끈다. */
const NO_OUTLINE = { outlineStyle: 'none' } as unknown as ViewStyle;

/* ── 드롭다운 = 바텀 시트 ───────────────────────────────── */

export function LabeledDropdown<T>({
  label,
  selected,
  options,
  optionLabel,
  onSelect,
  disabled = false,
  /** 옵션이 많을 때(스킬 선택 200개+) 시트 상단에 검색을 고정한다. */
  searchable = false,
  searchPlaceholder = '검색',
  placeholder,
}: {
  label: string;
  selected: T;
  options: T[];
  optionLabel: (opt: T) => string;
  onSelect: (opt: T) => void;
  disabled?: boolean;
  searchable?: boolean;
  searchPlaceholder?: string;
  placeholder?: string;
}) {
  const { colors, radius, controlHeight, spacing } = useAppTheme();
  const { height: windowHeight } = useWindowDimensions();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');

  const selectedLabel = optionLabel(selected);
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!searchable || !q) return options;
    return options.filter((o) => optionLabel(o).toLowerCase().includes(q));
  }, [options, query, searchable, optionLabel]);

  const close = () => {
    setOpen(false);
    setQuery('');
  };

  // 시트 높이를 직접 계산한다. RN Web의 ScrollView는 flexBasis가 0이라 부모에 확정
  // 높이가 없으면 0으로 잡히고, 그러면 시트가 헤더+검색 높이(약 96px)로 눌린다.
  // maxHeight만으로는 해결되지 않는다.
  const OPTION_ROW = 48;
  const chromeHeight = 64 + (searchable ? 58 : 0) + spacing.xxl;
  const sheetHeight = Math.min(
    Math.round(windowHeight * 0.78),
    chromeHeight + Math.max(shown.length, 1) * OPTION_ROW,
  );

  return (
    <View style={{ gap: 7 }}>
      <Text style={{ color: colors.secondaryText, fontSize: 12, fontWeight: '600' }} numberOfLines={1}>
        {label}
      </Text>
      <Pressable
        onPress={() => !disabled && setOpen(true)}
        style={({ pressed }) => ({
          height: controlHeight.dropdown,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: spacing.sm,
          paddingHorizontal: 14,
          borderRadius: radius.control,
          backgroundColor: colors.surfaceVariant,
          borderWidth: 1,
          borderColor: colors.outline,
          opacity: disabled ? 0.5 : pressed ? 0.72 : 1,
        })}
      >
        <Text style={{ color: colors.onSurface, fontSize: 14, fontWeight: '600', flex: 1 }} numberOfLines={1}>
          {selectedLabel || placeholder || '선택 안 함'}
        </Text>
        <CaretDown size={12} color={colors.muted} />
      </Pressable>

      <Modal transparent visible={open} animationType="slide" onRequestClose={close}>
        <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(7,8,11,0.72)' }}>
          <Pressable style={{ flex: 1 }} onPress={close} />
          <View
            style={{
              height: sheetHeight,
              backgroundColor: colors.surface,
              borderTopLeftRadius: radius.cardLg,
              borderTopRightRadius: radius.cardLg,
              borderTopWidth: 1,
              borderColor: colors.outline,
              overflow: 'hidden',
            }}
          >
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                paddingHorizontal: spacing.xl,
                paddingTop: spacing.xl,
                paddingBottom: spacing.md,
              }}
            >
              <Text style={{ color: colors.onSurface, fontSize: 16, fontWeight: '700' }}>{label}</Text>
              <LinkAction text="닫기" onPress={close} />
            </View>

            {searchable ? (
              <View style={{ paddingHorizontal: spacing.xl, paddingBottom: spacing.md }}>
                <SearchInput value={query} onChangeText={setQuery} placeholder={searchPlaceholder} height={46} />
              </View>
            ) : null}

            <ScrollView style={{ flex: 1 }} keyboardShouldPersistTaps="handled">
              {shown.map((opt, idx) => {
                const l = optionLabel(opt);
                const isSel = l === selectedLabel;
                return (
                  <Pressable
                    key={`${l}-${idx}`}
                    onPress={() => {
                      onSelect(opt);
                      close();
                    }}
                    style={({ pressed }) => ({
                      minHeight: 48,
                      justifyContent: 'center',
                      paddingHorizontal: spacing.xl,
                      paddingVertical: spacing.md,
                      borderLeftWidth: 3,
                      borderLeftColor: isSel ? colors.accentAction : 'transparent',
                      backgroundColor: isSel
                        ? 'rgba(76,201,240,0.12)'
                        : pressed
                          ? 'rgba(255,255,255,0.03)'
                          : 'transparent',
                    })}
                  >
                    <Text
                      style={{ color: isSel ? colors.accentAction : colors.onSurface, fontSize: 15, fontWeight: '600' }}
                    >
                      {l}
                    </Text>
                  </Pressable>
                );
              })}
              {!shown.length ? (
                <Text style={{ color: colors.muted, fontSize: 13, padding: spacing.xl }}>일치하는 항목이 없습니다.</Text>
              ) : null}
              <View style={{ height: spacing.xxl }} />
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

/* ── 결과 ────────────────────────────────────────────────── */

export function ScoreHero({ label, value }: { label: string; value: number }) {
  const { colors, radius, spacing, typography, tabularNums } = useAppTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.lg,
        backgroundColor: colors.surfaceVariant,
        borderWidth: 1,
        borderColor: colors.outline,
        borderRadius: radius.input,
        padding: spacing.xl,
      }}
    >
      <View style={{ width: 4, height: 54, borderRadius: 999, backgroundColor: colors.accentValue }} />
      <View style={{ gap: 2, flex: 1 }}>
        <Text style={{ color: colors.secondaryText, fontSize: 12, fontWeight: '600' }} numberOfLines={1}>
          {label}
        </Text>
        <Text style={[typography.hero, { color: colors.accentValue }, tabularNums]} numberOfLines={1}>
          {value.toFixed(2)}
        </Text>
      </View>
    </View>
  );
}

/* ── 상태 ────────────────────────────────────────────────── */

/** 로딩 중 티어 카드 자리를 채운다. 행 높이를 유지해 레이아웃이 튀지 않게 한다. */
export function Skeleton({ rows = 5 }: { rows?: number }) {
  const { colors, spacing } = useAppTheme();
  return (
    <View style={{ gap: spacing.smd }}>
      {Array.from({ length: rows }, (_, i) => (
        <View
          key={i}
          style={{ height: 42, borderRadius: 13, backgroundColor: colors.surfaceVariant, opacity: 1 - i * 0.12 }}
        />
      ))}
    </View>
  );
}

export function ErrorState({
  message,
  onRetry,
  retryText,
}: {
  message: string;
  onRetry: () => void;
  retryText: string;
}) {
  const { spacing } = useAppTheme();
  return (
    <View style={{ gap: spacing.lg }}>
      <InfoBanner text={message} tone="error" />
      <PrimaryActionButton text={retryText} onPress={onRetry} style={{ alignSelf: 'flex-start', paddingHorizontal: 24 }} />
    </View>
  );
}

export function EmptyState({ text }: { text: string }) {
  const { colors, spacing } = useAppTheme();
  return (
    <View style={{ paddingVertical: spacing.huge, alignItems: 'center' }}>
      <Text style={{ color: colors.muted, fontSize: 13, textAlign: 'center' }}>{text}</Text>
    </View>
  );
}

export function LoadingState({ text }: { text?: string }) {
  const { colors, spacing } = useAppTheme();
  return (
    <View style={{ paddingVertical: spacing.huge, alignItems: 'center', gap: spacing.md }}>
      <ActivityIndicator size="large" color={colors.accentAction} />
      {text ? <Text style={{ color: colors.secondaryText, fontSize: 13 }}>{text}</Text> : null}
    </View>
  );
}
