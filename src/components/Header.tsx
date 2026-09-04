import React from 'react';
import { AppLogo } from './AppLogo';
import { NavTab } from '../types';

interface HeaderProps {
  activeTab: NavTab;
  onSearchClick?: () => void;
  onFilterClick?: () => void;
  onAvatarClick?: () => void;
  onExportAndroidClick?: () => void;
  shizukuReady?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  onSearchClick,
  onFilterClick,
  onAvatarClick,
  onExportAndroidClick,
  shizukuReady = true,
}) => {
  const getTabSubtitle = () => {
    switch (activeTab) {
      case 'apps':
        return 'Apps';
      case 'exceptions':
        return 'Exceptions';
      case 'safety':
        return 'Safety Policies';
      case 'settings':
        return 'Settings';
      default:
        return 'Apps';
    }
  };

  return (
    <header className="sticky top-0 w-full z-40 bg-surface/90 backdrop-blur-md border-b border-outline/10 transition-colors">
      <div className="h-16 px-4 flex items-center justify-between gap-3 max-w-4xl mx-auto">
        {/* Branding & Screen title */}
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-10 h-10 rounded-xl bg-surface-container-high flex items-center justify-center shrink-0 border border-outline/15 shadow-sm">
            <AppLogo size={28} variant="badge" />
          </div>
          <div className="flex flex-col min-w-0">
            <span className="font-semibold text-base text-on-surface leading-tight tracking-tight truncate">
              App Controller
            </span>
            <span className="text-xs text-on-surface-variant font-mono tracking-wide truncate">
              {getTabSubtitle()}
            </span>
          </div>
        </div>

        {/* Action Controls with minimum 48px touch targets */}
        <div className="flex items-center gap-1.5">
          {onExportAndroidClick && (
            <button
              type="button"
              onClick={onExportAndroidClick}
              aria-label="Export Android APK and Studio Project"
              className="h-9 px-3 rounded-full bg-primary/15 hover:bg-primary/25 text-primary border border-primary/30 text-xs font-mono font-bold flex items-center gap-1.5 active:scale-95 transition-all shadow-sm"
            >
              <span className="material-symbols-outlined text-[16px]">android</span>
              <span className="hidden xs:inline">APK & Studio</span>
            </button>
          )}

          <button
            type="button"
            onClick={onSearchClick}
            aria-label="Search apps and packages"
            className="w-12 h-12 flex items-center justify-center rounded-full text-on-surface-variant hover:text-on-surface hover:bg-surface-container active:scale-95 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[22px]">search</span>
          </button>

          <button
            type="button"
            onClick={onFilterClick}
            aria-label="Filter packages and view options"
            className="w-12 h-12 flex items-center justify-center rounded-full text-on-surface-variant hover:text-on-surface hover:bg-surface-container active:scale-95 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[22px]">tune</span>
          </button>

          <button
            type="button"
            onClick={onAvatarClick}
            aria-label="System status and profile details"
            className="w-10 h-10 rounded-full bg-primary/20 border border-primary/40 flex items-center justify-center shrink-0 ml-1 text-primary hover:bg-primary/30 active:scale-95 transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="material-symbols-outlined text-[18px]">person</span>
          </button>
        </div>
      </div>
    </header>
  );
};
