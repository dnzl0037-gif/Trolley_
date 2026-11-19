import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.trolley.marketplace',
  appName: 'Trolley',
  webDir: 'dist',
  backgroundColor: '#000000',
  server: { androidScheme: 'https' },
};

export default config;
