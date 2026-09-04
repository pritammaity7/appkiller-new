import React, { useState, useEffect } from 'react';

interface DeviceFrameProps {
  children: React.ReactNode;
  isActive: boolean;
  theme: string;
}

export const DeviceFrame: React.FC<DeviceFrameProps> = ({
  children,
  isActive,
  theme,
}) => {
  const [time, setTime] = useState('9:41');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  if (!isActive) {
    return <div className="w-full min-h-screen flex flex-col">{children}</div>;
  }

  return (
    <div className="w-full min-h-screen flex items-center justify-center p-0 sm:p-6 bg-[#06090b]">
      {/* Android Device Mockup Frame */}
      <div className="w-full max-w-[430px] h-[100dvh] sm:h-[890px] bg-surface sm:rounded-[44px] sm:border-[10px] sm:border-[#202529] shadow-[0_0_50px_rgba(0,0,0,0.8)] flex flex-col relative overflow-hidden ring-1 ring-white/10">
        {/* Android Status Bar */}
        <div className="h-10 px-6 pt-1 flex items-center justify-between text-xs font-mono select-none shrink-0 z-50 bg-surface/90 backdrop-blur-md">
          <span className="font-semibold text-on-surface text-[12px]">{time}</span>
          {/* Camera Hole Punch */}
          <div className="w-3.5 h-3.5 rounded-full bg-black border border-white/10 shrink-0 mx-auto" />
          <div className="flex items-center gap-1.5 text-on-surface-variant text-[13px]">
            <span className="material-symbols-outlined text-[15px]">wifi</span>
            <span className="material-symbols-outlined text-[15px]">signal_cellular_4_bar</span>
            <span className="material-symbols-outlined text-[17px] text-primary">battery_full</span>
          </div>
        </div>

        {/* Child Screen Content */}
        <div className="flex-1 flex flex-col overflow-y-auto relative overscroll-contain">
          {children}
        </div>

        {/* Android Gesture Navigation Bar Pill */}
        <div className="h-4 pb-1 w-full flex items-center justify-center shrink-0 z-50 bg-surface-container-low/95 pointer-events-none">
          <div className="w-32 h-1 rounded-full bg-on-surface-variant/40" />
        </div>
      </div>
    </div>
  );
};
