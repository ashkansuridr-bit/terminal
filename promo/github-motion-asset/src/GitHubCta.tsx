import React from 'react';
import {AbsoluteFill, Easing, interpolate, staticFile, useCurrentFrame} from 'remotion';

const INK = '#07090c';
const TEAL = '#35d7ae';
const WHITE = '#f4f7f8';
const MUTED = '#9aa4ac';
const URL = 'github.com/ashkansuridr-bit/terminal';
const clamp = {extrapolateLeft: 'clamp' as const, extrapolateRight: 'clamp' as const};
const ease = Easing.bezier(0.2, 0.7, 0.2, 1);

export const GitHubCta: React.FC = () => {
  const f = useCurrentFrame();
  const collapse = interpolate(f, [0, 34], [5.4, 1], {...clamp, easing: Easing.inOut(Easing.cubic)});
  const brake = 1 + Math.sin(interpolate(f, [29, 44], [0, Math.PI], {...clamp})) * 0.055;
  const markIn = interpolate(f, [34, 43], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
  const titleIn = interpolate(f, [41, 57], [0, 1], {...clamp, easing: ease});
  const licenseIn = interpolate(f, [53, 65], [0, 1], {...clamp, easing: ease});
  const linkIn = interpolate(f, [62, 73], [0, 1], {...clamp, easing: ease});
  const typed = Math.floor(interpolate(f, [65, 88], [0, URL.length], {...clamp, easing: Easing.out(Easing.cubic)}));
  const underline = interpolate(f, [86, 90], [0, 1], {...clamp, easing: Easing.out(Easing.cubic)});
  const glyphScale = collapse * (f < 43 ? brake : 1);

  return <AbsoluteFill style={{background: INK, color: WHITE, fontFamily: 'Vazirmatn, sans-serif', overflow: 'hidden'}}>
    <style>{`@font-face{font-family:Vazirmatn;src:url('${staticFile('vazirmatn.ttf')}')}`}</style>
    <div style={{position: 'absolute', inset: 0, background: 'radial-gradient(ellipse at 50% 42%, rgba(53,215,174,.14), transparent 43%)'}} />
    {[0, 1, 2].map((i) => <div key={i} style={{position: 'absolute', left: '50%', top: '37%', width: 760 + i * 210, height: 760 + i * 210, marginLeft: -(380 + i * 105), marginTop: -(380 + i * 105), borderRadius: '50%', border: `1px solid rgba(53,215,174,${0.08 - i * 0.018})`, transform: `scale(${1 + 0.016 * Math.sin((f + i * 13) / 42)})`}} />)}

    <div style={{position: 'absolute', top: 286, width: '100%', textAlign: 'center', direction: 'rtl', color: TEAL, fontSize: 27, fontWeight: 700, letterSpacing: 1.2, opacity: titleIn}}>اپلیکیشن اندروید</div>

    {/* The cut ring collapses into the app's terminal prompt mark, following the
        calibrated logo-shrink-wordmark-lockup motion arc. */}
    <div style={{position: 'absolute', left: '50%', top: 650, width: 32, height: 32, marginLeft: -16, marginTop: -16, transform: `scale(${glyphScale})`, transformOrigin: 'center'}}>
      <svg viewBox="0 0 32 32" width="32" height="32" style={{overflow: 'visible'}}>
        <circle cx="16" cy="16" r="11" fill="none" stroke={TEAL} strokeWidth="2.2" strokeDasharray="48 22" strokeLinecap="round" transform={`rotate(${interpolate(f, [0, 34], [-145, 0], clamp)} 16 16)`} opacity={1 - markIn} />
        <path d="M5 10 L13 16 L5 22 M15 23 H25" fill="none" stroke={TEAL} strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" opacity={markIn} />
      </svg>
      <div style={{position: 'absolute', inset: -13, border: `1px solid rgba(53,215,174,${0.44 * (1 - interpolate(f, [42, 66], [0, 1], clamp))})`, borderRadius: 18, opacity: markIn}} />
    </div>

    <div style={{position: 'absolute', top: 790, width: '100%', textAlign: 'center', direction: 'rtl', fontSize: 88, fontWeight: 800, opacity: titleIn, transform: `translateY(${(1 - titleIn) * 14}px)`}}>ترمینال</div>
    <div style={{position: 'absolute', top: 910, width: '100%', textAlign: 'center', direction: 'rtl', color: MUTED, fontSize: 32, opacity: titleIn}}>محیط ترمینال و تنظیمات به سلیقهٔ تو</div>

    <div style={{position: 'absolute', left: '50%', top: 1084, padding: '18px 30px', borderRadius: 18, background: TEAL, color: INK, fontSize: 28, fontWeight: 800, whiteSpace: 'nowrap', opacity: licenseIn, transform: `translate(-50%, ${(1 - licenseIn) * 10}px) scale(${0.96 + 0.04 * licenseIn})`}}>متن‌باز · Apache 2.0</div>

    <div style={{position: 'absolute', top: 1246, width: '100%', textAlign: 'center', direction: 'rtl', fontSize: 27, color: MUTED, fontWeight: 700, opacity: linkIn}}>سورس در گیت‌هاب</div>
    <div style={{position: 'absolute', top: 1304, left: 66, right: 66, minHeight: 76, display: 'flex', justifyContent: 'center', alignItems: 'center', fontFamily: 'ui-monospace, SFMono-Regular, Consolas, monospace', fontSize: 29, fontWeight: 600, letterSpacing: 0.15, color: WHITE, opacity: linkIn, whiteSpace: 'nowrap'}}>
      <span>{URL.slice(0, typed)}</span><span style={{width: 3, height: 33, marginLeft: 5, background: TEAL, opacity: f < 99 ? (f % 16 < 10 ? 1 : 0.2) : 0}} />
    </div>
    <div style={{position: 'absolute', top: 1396, left: 150, width: 780, height: 2, background: `linear-gradient(90deg, transparent, ${TEAL}, transparent)`, transform: `scaleX(${underline})`, opacity: underline}} />
  </AbsoluteFill>;
};
