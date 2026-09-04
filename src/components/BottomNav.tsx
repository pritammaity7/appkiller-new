import React from 'react';
import { NavTab } from '../types';

interface BottomNavProps {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  selectedCount?: number;
  hasPermissionAlert?: boolean;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  activeTab,
  onSelectTab,
  selectedCount = 0,
  hasPermissionAlert = false,
}) => {
  const navItems: { id: NavTab; label: string; icon: string; badge?: number | boolean }[] = [
    { id: 'apps', label: 'Apps', icon: 'grid_view', badge: selectedCount > 0 ? selectedCount : undefined },
    { id: 'exceptions', label: 'Exceptions', icon: 'verified_user' },
    { id: 'safety', label: 'Safety', icon: 'policy' },
    { id: 'settings', label: 'Settings', icon: 'settings', badge: hasPermissionAlert },
  ];

  return (
    <nav
      aria-label="Main Navigation"
      className="fixed bottom-0 inset-x-0 z-40 bg-surface-container-low/95 backdrop-blur-xl border-t border-outline/10 shadow-[0_-2px_12px_rgba(0,0,0,0.25)] transition-colors"
    >
      <div className="h-16 max-w-lg mx-auto flex items-center justify-around px-2 pb-safe">
        {navItems.map((item) => {
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelectTab(item.id)}
              aria-current={isActive ? 'page' : undefined}
              className={`flex-1 h-12 min-w-[64px] flex flex-col items-center justify-center gap-0.5 rounded-xl transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                isActive
                  ? 'text-primary font-bold bg-surface-container-highest/60 shadow-sm'
                  : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container/50'
              }`}
            >
              <div className="relative flex items-center justify-center">
                <span
                  className="material-symbols-outlined text-[22px] transition-transform duration-150"
                  style={{ fontVariationSettings: isActive ? "'FILL' 1" : "'FILL' 0" }}
                >
                  {item.icon}
                </span>
                {typeof item.badge === 'number' && item.badge > 0 && (
                  <span className="absolute -top-1.5 -right-2.5 min-w-[16px] h-4 px-1 rounded-full bg-primary text-on-primary text-[10px] font-mono font-bold flex items-center justify-center">
                    {item.badge}
                  </span>
                )}
                {typeof item.badge === 'boolean' && item.badge && (
                  <span
                    aria-label="Attention needed"
                    className="absolute -top-0.5 -right-1 w-2 h-2 rounded-full bg-error animate-pulse"
                  />
                )}
              </div>
              <span className="text-[11px] font-mono tracking-tight leading-none">
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
