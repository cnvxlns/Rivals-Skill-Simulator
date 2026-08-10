import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { LanguageProvider } from '@/lib/i18n';

export default function RootLayout() {
  const scheme = useColorScheme();
  return (
    <SafeAreaProvider>
      <LanguageProvider>
        <StatusBar style={scheme === 'light' ? 'dark' : 'light'} />
        <Stack screenOptions={{ headerShown: false }} />
      </LanguageProvider>
    </SafeAreaProvider>
  );
}
