'use client';

import AsyncStorage from '@react-native-async-storage/async-storage';
import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  apiErrorMessage,
  setAuthToken,
  setUnauthorizedHandler,
  signIn as signInRequest,
  signUp as signUpRequest,
} from './api';
import { AuthUser } from '../types';

/**
 * 로그인 상태.
 *
 * 토큰 보관에 AsyncStorage를 쓴다. Expo 문서는 인증 토큰처럼 민감한 값에는 암호화되는
 * expo-secure-store를 권하지만, 이 앱은 웹(Vercel)이 주 배포처이고 SecureStore는 웹에서
 * 동작하지 않는다. 웹에서는 어차피 브라우저 저장소가 한계이므로, 플랫폼마다 다른 저장소를
 * 두는 대신 양쪽 모두 AsyncStorage를 쓰고 토큰 수명을 14일로 제한했다.
 *
 * 안드로이드만 암호화하려면 Formula.web/native 같은 플랫폼 분리로 SecureStore를 붙이면
 * 되지만, 네이티브 모듈이라 OTA로는 못 나가고 APK를 새로 빌드해야 한다.
 */
const TOKEN_KEY = 'rivals.auth.token';
const USER_KEY = 'rivals.auth.user';

type AuthState = {
  user: AuthUser | null;
  /** 저장된 토큰을 읽어 오는 동안 true. 이때 로그인 화면을 깜빡이지 않게 한다. */
  loading: boolean;
  signUp: (email: string, password: string) => Promise<void>;
  signIn: (email: string, password: string) => Promise<void>;
  signOut: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  const clear = useCallback(() => {
    setAuthToken(null);
    setUser(null);
    void AsyncStorage.multiRemove([TOKEN_KEY, USER_KEY]);
  }, []);

  // 401이 오면 토큰이 만료됐거나 서버 재시작으로 서명 키가 바뀐 것이다. 조용히 로그아웃한다.
  useEffect(() => {
    setUnauthorizedHandler(clear);
    return () => setUnauthorizedHandler(null);
  }, [clear]);

  // 앱을 다시 열었을 때 저장된 토큰으로 복구한다.
  useEffect(() => {
    let cancelled = false;
    const restore = async () => {
      try {
        const [[, token], [, rawUser]] = await AsyncStorage.multiGet([TOKEN_KEY, USER_KEY]);
        if (cancelled || !token || !rawUser) return;
        setAuthToken(token);
        setUser(JSON.parse(rawUser) as AuthUser);
      } catch {
        // 저장소를 못 읽으면 로그아웃 상태로 시작한다. 사용자는 다시 로그인하면 된다.
        if (!cancelled) clear();
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void restore();
    return () => {
      cancelled = true;
    };
  }, [clear]);

  const persist = useCallback(async (token: string, nextUser: AuthUser) => {
    setAuthToken(token);
    setUser(nextUser);
    try {
      await AsyncStorage.multiSet([
        [TOKEN_KEY, token],
        [USER_KEY, JSON.stringify(nextUser)],
      ]);
    } catch {
      // 저장에 실패해도 이번 세션은 그대로 쓸 수 있다. 다음 실행에 다시 로그인하면 된다.
    }
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      signUp: async (email, password) => {
        const res = await signUpRequest(email, password);
        await persist(res.token, res.user);
      },
      signIn: async (email, password) => {
        const res = await signInRequest(email, password);
        await persist(res.token, res.user);
      },
      signOut: clear,
    }),
    [user, loading, persist, clear],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

export { apiErrorMessage };
