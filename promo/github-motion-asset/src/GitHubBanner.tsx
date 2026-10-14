import React from 'react';
import {AbsoluteFill, Easing, interpolate, staticFile, useCurrentFrame} from 'remotion';

const INK = '#07090c';
const TEAL = '#35d7ae';
const WHITE = '#f4f7f8';
const MUTED = '#9aa4ac';
const URL = 'github.com/ashkansuridr-bit/terminal';
const clamp = {extrapolateLeft: 'clamp' as const, extrapolateRight: 'clamp' as const};
const ease = Easing.bezier(0.2, 0.7, 0.2, 1);

export const GitHubBanner: React.FC = () => {
  const f = useCurrentFrame();
  const collapse = interpolate(f, [0, 34], [5.2, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
  const brake = 1 + Math.sin(interpolate(f, [29, 44], [0, Math.PI], clamp)) * 0.055;
  const markIn = interpolate(f, [34, 43], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
  const lockupIn = interpolate(f, [40, 56], [0, 1], {...clamp, easing: ease});
  const licenseIn = interpolate(f, [52, 64], [0, 1], {...clamp, easing: ease});
  const linkIn = interpolate(f, [61, 72], [0, 1], {...clamp, easing: ease});
  const typed = Math.floor(interpolate(f, [65, 88], [0, URL.length], {...clamp, easing: Easing.out(Easing.cubic)}));
  const underline = interpolate(f, [86, 90], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
  const iconScale = collapse * (f < 43 ? brake : 1);

  return <AbsoluteFill style={{background: INK, color: WHITE, fontFamily: 'Vazirmatn, sans-serif', overflow: 'hidden'}}>
    <style>{`@font-face{font-family:Vazirmatn;src:url('${staticFile('vazirmatn.ttf')}')}`}</style>
    <div style={{position: 'absolute', inset: 0, background: 'radial-gradient(ellipse at 50% 45%, rgba(53,215,174,.16), transparent 50%)'}} />
    {[0, 1, 2].map((i) => <div key={i} style={{position: 'absolute', left: '50%', top: '47%', width: 490 + i * 230, height: 490 + i * 230, marginLeft: -(245 + i * 115), marginTop: -(245 + i * 115), borderRadius: '50%', border: `1px solid rgba(53,215,174,${0.09 - i * 0.02})`, transform: `scale(${1 + 0.012 * Math.sin((f + i * 13) / 42)})`}} />)}

    <div style={{position: 'absolute', left: '50%', top: 235, width: 32, height: 32, marginLeft: -16, marginTop: -16, transform: `scale(${iconScale})`}}>
      <svg viewBox="0 0 32 32" width="32" height="32" style={{overflow: 'visible'}}>
        <circle cx="16" cy="16" r="11" fill="none" stroke={TEAL} strokeWidth="2.2" strokeDasharray="48 22" strokeLinecap="round" transform={`rotate(${interpolate(f, [0, 34], [-145, 0], clamp)} 16 16)`} opacity={1 - markIn} />
        <path d="M5 10 L13 16 L5 22 M15 23 H25" fill="none" stroke={TEAL} strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" opacity={markIn} />
      </svg>
      <div style={{position: 'absolute', inset: -11, border: `1px solid rgba(53,215,174,${0.42 * (1 - interpolate(f, [42, 66], [0, 1], clamp))})`, borderRadius: 14, opacity: markIn}} />
    </div>

    <div style={{position: 'absolute', top: 284, width: '100%', textAlign: 'center', direction: 'rtl', fontSize: 64, lineHeight: 1.3, fontWeight: 800, opacity: lockupIn, transform: `translateY(${(1 - lockupIn) * 12}px)`}}>ترمینال</div>
    <div style={{position: 'absolute', top: 378, width: '100%', textAlign: 'center', direction: 'rtl', color: MUTED, fontSize: 23, opacity: lockupIn}}>محیط ترمینال و تنظیمات به سلیقهٔ تو</div>

    <div style={{position: 'absolute', top: 458, left: '50%', padding: '10px 24px', borderRadius: 14, background: TEAL, color: INK, fontSize: 22, fontWeight: 800, whiteSpace: 'nowrap', opacity: licenseIn, transform: `translateX(-50%) translateY(${(1 - licenseIn) * 8}px) scale(${0.97 + 0.03 * licenseIn})`}}>متن‌باز · Apache 2.0</div>

    <div style={{position: 'absolute', top: 535, left: 64, right: 64, display: 'flex', justifyContent: 'center', alignItems: 'center', fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 29, fontWeight: 600, color: WHITE, opacity: linkIn, whiteSpace: 'nowrap'}}>
      <span>{URL.slice(0, typed)}</span><span style={{width: 3, height: 30, marginLeft: 5, background: TEAL, opacity: f < 99 ? (f % 16 < 10 ? 1 : 0.2) : 0}} />
    </div>
    <div style={{position: 'absolute', top: 584, left: 245, width: 790, height: 2, background: `linear-gradient(90deg, transparent, ${TEAL}, transparent)`, transform: `scaleX(${underline})`, opacity: underline}} />
  </AbsoluteFill>;
};
