import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.trolley.marketplace',
  appName: 'Trolley',
  webDir: 'dist',
  server: { androidScheme: 'https' },
};

export default config;
