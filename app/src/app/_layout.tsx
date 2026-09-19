import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider } from '../lib/auth';

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      {/* 덱 탭이 로그인 상태를 봐야 하므로 화면보다 위에 둔다. */}
      <AuthProvider>
        <Stack screenOptions={{ headerShown: false }} />
      </AuthProvider>
    </SafeAreaProvider>
  );
}
