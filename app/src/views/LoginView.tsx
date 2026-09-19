'use client';

import { useState } from 'react';
import { Text, View } from 'react-native';
import { InfoBanner, LinkAction, PrimaryActionButton, SectionCard, TextField } from '../components/ui';
import { apiErrorMessage, useAuth } from '../lib/auth';
import { useTranslation } from '../lib/i18n';
import { useAppTheme } from '../theme/useTheme';

/** 로그인과 회원가입을 한 화면에서 전환한다. */
export default function LoginView() {
  const { t } = useTranslation();
  const { colors, typography, spacing } = useAppTheme();
  const { signIn, signUp } = useAuth();

  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isSignUp = mode === 'signup';

  const submit = async () => {
    if (busy) return;
    if (!email.trim() || !password) {
      setError(t('auth_error_empty'));
      return;
    }
    if (isSignUp && password.length < 8) {
      setError(t('auth_error_password_short'));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (isSignUp) {
        await signUp(email.trim(), password);
      } else {
        await signIn(email.trim(), password);
      }
    } catch (err) {
      // 백엔드가 이유를 실어 준다(중복 이메일, 비밀번호 불일치 등). 있으면 그대로 보여 준다.
      setError(apiErrorMessage(err) ?? t('auth_error_generic'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={{ gap: spacing.lg }}>
      <SectionCard title={isSignUp ? t('auth_title_signup') : t('auth_title_signin')}>
        <View style={{ gap: spacing.lg }}>
          <Text style={{ ...typography.body, color: colors.secondaryText }}>{t('auth_gate_body')}</Text>

          <TextField
            label={t('auth_label_email')}
            value={email}
            onChangeText={setEmail}
            placeholder={t('auth_placeholder_email')}
            keyboardType="email-address"
            autoComplete="email"
            editable={!busy}
          />
          <TextField
            label={t('auth_label_password')}
            value={password}
            onChangeText={setPassword}
            placeholder={t('auth_placeholder_password')}
            secure
            autoComplete={isSignUp ? 'new-password' : 'password'}
            onSubmitEditing={submit}
            editable={!busy}
          />

          {error ? <InfoBanner text={error} tone="error" /> : null}

          <PrimaryActionButton
            text={isSignUp ? t('auth_btn_signup') : t('auth_btn_signin')}
            onPress={submit}
            enabled={!busy}
            loading={busy}
          />

          <LinkAction
            text={isSignUp ? t('auth_switch_to_signin') : t('auth_switch_to_signup')}
            onPress={() => {
              setMode(isSignUp ? 'signin' : 'signup');
              setError(null);
            }}
          />
        </View>
      </SectionCard>
    </View>
  );
}
