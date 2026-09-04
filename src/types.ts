export type ThemeMode = 'system' | 'light' | 'dark' | 'amoled';

export type TextScale = 'normal' | 'large' | 'xlarge';

export type NavTab = 'apps' | 'exceptions' | 'safety' | 'settings';

export type AppCategory = 'user' | 'system';

export type ProcessState = 'cached' | 'active' | 'syncing' | 'idle' | 'websocket' | 'stopped';

export interface ProcessApp {
  id: string;
  name: string;
  packageName: string;
  category: AppCategory;
  state: ProcessState;
  stateDetail: string;
  memoryMb: number;
  iconName: string;
  isProtected?: boolean;
  isWhitelisted?: boolean;
  canStop: boolean;
  oomScore?: number;
  pid?: number;
  uid?: number;
  selected?: boolean;
}

export interface WhitelistItem {
  id: string;
  name: string;
  packageName: string;
  reason: string;
  isSystemLock?: boolean;
  enabled: boolean;
}

export interface MemoryStats {
  memAvailableMb: number;
  activeFileMb: number;
  swapZramMb: number;
}
