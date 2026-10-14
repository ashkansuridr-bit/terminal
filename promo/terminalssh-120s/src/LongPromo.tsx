import React from 'react';
import {AbsoluteFill, Img, Sequence, staticFile, useCurrentFrame, interpolate, Easing, spring, useVideoConfig} from 'remotion';

const FPS = 30;
const PART = 900;
const C = {ink:'#07090c',surface:'#101317',surface2:'#171b20',stroke:'#2a3037',white:'#f4f7f8',muted:'#9aa4ac',teal:'#35d7ae',cyan:'#5ac8fa'};
const CLAMP={extrapolateLeft:'clamp' as const, extrapolateRight:'clamp' as const};
const ease=Easing.bezier(.2,.7,.2,1);

type Screen='form'|'home'|'settings'|'terminal'|'files'|'github';
type Motion='spotlight'|'list'|'ripple'|'deal'|'brand';
interface Beat {screen:Screen; motion:Motion; title:string; copy:string; tag?:string;}
interface Chapter {id:number; name:string; beats:Beat[];}
export const CHAPTERS:Chapter[]=[
 {id:1,name:'اتصال SSH',beats:[
  {screen:'form',motion:'spotlight',title:'سرورت، همیشه همراهت',copy:'اتصال SSH را با اطلاعات میزبان خودت تعریف کن',tag:'SSH · CONNECTION'},
  {screen:'form',motion:'spotlight',title:'مشخصات اتصال را وارد کن',copy:'فیلدهای میزبان و کاربر در یک فرم روشن',tag:'HOST · PORT · USER'},
  {screen:'home',motion:'spotlight',title:'همه‌چیز از یک نمای ساده',copy:'از خانه‌ی برنامه به میزبان‌ها و ترمینال برو',tag:'TERMINAL SSH'},
  {screen:'form',motion:'spotlight',title:'یک اتصال؛ یک نقطه‌ی شروع',copy:'رابط فارسی برای کار با سرور از گوشی',tag:'ANDROID · PERSIAN'},
 ]},
 {id:2,name:'مدیریت میزبان‌ها',beats:[
  {screen:'home',motion:'list',title:'میزبان‌ها، یک‌جا',copy:'فهرست اتصال‌ها را از صفحه‌ی اصلی مرور کن',tag:'HOSTS'},
  {screen:'home',motion:'list',title:'از فهرست تا ترمینال',copy:'مسیر کار را با چند لمس روی گوشی ادامه بده',tag:'HOST → TERMINAL'},
  {screen:'form',motion:'list',title:'جزئیات هر میزبان',copy:'فرم اتصال، ورودی‌های شبکه را کنار هم دارد',tag:'SSH SETTINGS'},
  {screen:'home',motion:'list',title:'نمای آشنای سرورها',copy:'اتصال موردنظرت را از خانه‌ی برنامه پیدا کن',tag:'CONNECTIONS'},
 ]},
 {id:3,name:'ترمینال و کنترل‌ها',beats:[
  {screen:'terminal',motion:'ripple',title:'ترمینال در جیب تو',copy:'با رابط ترمینال، از گوشی به سشن SSH دسترسی بگیر',tag:'REMOTE SHELL'},
  {screen:'settings',motion:'ripple',title:'ظاهر ترمینال را تنظیم کن',copy:'گزینه‌های نمایشی موجود را در تنظیمات ببین',tag:'PERSONALIZE'},
  {screen:'terminal',motion:'ripple',title:'کلیدهای کمکی دم دست',copy:'نوارکلید برای ورودی‌های رایج ترمینال',tag:'TERMINAL KEYS'},
  {screen:'settings',motion:'ripple',title:'برای شیوه‌ی کار خودت',copy:'رنگ و اندازه‌ی نمایش را با گزینه‌های برنامه هماهنگ کن',tag:'DISPLAY'},
 ]},
 {id:4,name:'فایل‌ها و متن‌باز',beats:[
  {screen:'files',motion:'deal',title:'فایل‌های سرور، روی گوشی',copy:'فهرست راه‌دور را از راه SFTP مرور کن',tag:'SFTP BROWSER'},
  {screen:'files',motion:'deal',title:'ساختار پوشه‌ها را ببین',copy:'نام‌ها و جزئیات فایل در یک نمای موبایلی',tag:'REMOTE FILES'},
  {screen:'files',motion:'deal',title:'کار با فایل از راه دور',copy:'مرورگر فایل SFTP داخل Terminal SSH',tag:'SFTP'},
  {screen:'github',motion:'brand',title:'متن‌باز · Apache 2.0',copy:'سورس برنامه را در GitHub ببین',tag:'SOURCE CODE'},
 ]},
];
const screenAsset:Record<Screen,string>={form:'form.png',home:'home.png',settings:'settings.png',terminal:'terminal.png',files:'files.png',github:'terminal-logo.jpg'};
const sectionFrom=(index:number)=>index*PART;
const alpha=(f:number,a:number,b:number)=>interpolate(f,[a,b],[0,1],{...CLAMP,easing:ease});
const mark=<>›_</>;

const Atmosphere:React.FC<{frame:number;mode:Motion}> = ({frame,mode}) => <AbsoluteFill style={{pointerEvents:'none',overflow:'hidden'}}>
  <div style={{position:'absolute',inset:0,background:'radial-gradient(ellipse at 50% 48%,rgba(53,215,174,.13),transparent 58%)'}}/>
  {mode==='ripple' ? [0,1,2,3].map(i=>{const phase=frame/52+i*1.7;const scale=1+.035*Math.sin(phase);return <div key={i} style={{position:'absolute',left:'50%',top:'51%',width:680+i*390,height:680+i*390,marginLeft:-340-i*195,marginTop:-340-i*195,border:`1px solid rgba(53,215,174,${.12-i*.02})`,borderRadius:'50%',transform:`scale(${scale})`}}/>;}) : [0,1,2].map(i=><div key={i} style={{position:'absolute',left:'50%',top:'49%',width:570+i*270,height:570+i*270,marginLeft:-285-i*135,marginTop:-285-i*135,border:`1px solid rgba(53,215,174,${.065-i*.012})`,borderRadius:'50%',transform:`scale(${1+.012*Math.sin(frame/58+i*1.8)})`}}/>)}
 </AbsoluteFill>;

const Phone:React.FC<{beat:Beat;local:number}> = ({beat,local}) => {
 const {fps}=useVideoConfig();
 const enter=alpha(local,0,24);
 const slow=local/224;
 const tilt=beat.motion==='spotlight'?interpolate(local,[0,70,150,224],[3,0,-1.5,0],CLAMP):interpolate(local,[0,224],[1.7,-1.7],CLAMP);
 const zoom=interpolate(local,[0,22,70,130,170,224],[.88,1.02,1.015,1.07,1.02,1.04],{...CLAMP,easing:Easing.inOut(Easing.cubic)});
 const glow=beat.motion==='spotlight'?alpha(local,0,42):0;
 const pulse=spring({frame:Math.max(0,local-30),fps,config:{damping:20,stiffness:50}});
 if (beat.screen==='github') return <div style={{position:'absolute',left:170,top:505,width:740,height:730,borderRadius:54,background:'linear-gradient(145deg,#17201f,#0b0e10)',border:`1px solid rgba(53,215,174,${.25+.2*pulse})`,boxShadow:'0 60px 130px rgba(0,0,0,.6),0 0 85px rgba(53,215,174,.10)',display:'flex',alignItems:'center',justifyContent:'center',transform:`translateY(${interpolate(local,[0,40],[60,0],CLAMP)}px) scale(${.88+.12*enter})`,opacity:enter}}>
  <div style={{textAlign:'center',fontFamily:'Vazir',direction:'rtl'}}><div style={{fontFamily:'Arial',fontSize:108,color:C.teal,fontWeight:700}}>{mark}</div><div style={{fontSize:70,fontWeight:800,color:C.white,marginTop:28}}>ترمینال</div><div style={{fontSize:28,color:C.muted,marginTop:22}}>محیط ترمینال و تنظیمات به سلیقهٔ تو</div><div style={{display:'inline-block',marginTop:50,padding:'14px 26px',borderRadius:15,background:C.teal,color:C.ink,fontSize:24,fontWeight:800}}>مجوز سورس · Apache 2.0</div><div style={{direction:'ltr',fontFamily:'monospace',fontSize:17,color:C.muted,marginTop:24}}>github.com/ashkansuridr-bit/terminal</div></div>
 </div>;
 return <div style={{position:'absolute',left:250,top:390,width:580,height:1198,borderRadius:70,padding:12,background:'linear-gradient(150deg,#68727a,#171b20 14%,#323a40 55%,#101317)',boxShadow:'0 80px 150px rgba(0,0,0,.68),0 12px 28px rgba(0,0,0,.55)',opacity:enter,transform:`translateY(${(48*(1-enter)).toFixed(2)}px) rotate(${tilt}deg) scale(${zoom})`,transformOrigin:'50% 50%'}}>
  <div style={{position:'relative',width:'100%',height:'100%',overflow:'hidden',borderRadius:58,background:C.ink,border:'2px solid #384149'}}>
   <Img src={staticFile(screenAsset[beat.screen])} style={{width:'100%',height:'100%',objectFit:'cover'}}/>
   {beat.motion==='spotlight'&&<div style={{position:'absolute',inset:0,background:`radial-gradient(ellipse at ${35+52*interpolate(local,[0,160],[0,1],CLAMP)}% 44%, transparent 6%, rgba(5,8,10,${.66*(1-glow)}) 65%)`,mixBlendMode:'multiply'}}/>}
   {beat.motion==='deal'&&<div style={{position:'absolute',top:'23%',right:24,width:8,height:'59%',borderRadius:20,background:`linear-gradient(180deg,transparent,rgba(53,215,174,${.85*Math.sin(Math.PI*slow)}),transparent)`,filter:'blur(2px)'}}/>}
  </div>
  <div style={{position:'absolute',left:-5,top:240,width:6,height:90,borderRadius:4,background:'#8d989e'}}/><div style={{position:'absolute',left:-5,top:350,width:6,height:90,borderRadius:4,background:'#8d989e'}}/><div style={{position:'absolute',right:-5,top:320,width:6,height:135,borderRadius:4,background:'#8d989e'}}/>
 </div>;
};

const FeatureRows:React.FC<{beat:Beat;local:number}> = ({beat,local}) => {
 const copy=beat.screen==='form'?['demo-server','demo.example.net','Port 22','operator']:beat.screen==='home'?['prod-sample','staging-lab','build-box']:beat.screen==='settings'?['تم رنگی','اندازه متن','کلیدهای میانبر']:beat.screen==='terminal'?['Ctrl','Alt','Esc']:['config','logs','releases','README.md','deploy.sh','health-check.txt'];
 if (beat.motion!=='list'&&beat.motion!=='ripple'&&beat.motion!=='deal') return null;
 return <div style={{position:'absolute',left:22,width:190,top:595,direction:'ltr',display:'flex',flexDirection:'column',gap:12,transform:`translateY(${interpolate(local,[0,224],[26,-12],CLAMP)}px)`}}>
  {copy.map((x,i)=>{const a=alpha(local,8+i*10,34+i*10); const drift=beat.motion==='deal'?interpolate(local,[22+i*9,50+i*9],[120,0],{...CLAMP,easing:Easing.bezier(.3,0,.2,1)}):0;return <div key={x} style={{height:48,borderRadius:13,border:`1px solid rgba(53,215,174,${.17*a})`,background:`rgba(16,19,23,${.88*a})`,color:C.white,padding:'0 10px',display:'flex',alignItems:'center',justifyContent:'center',fontFamily:x.charCodeAt(0)<128?'monospace':'Vazir',fontSize:17,opacity:a,transform:`translateX(${drift}px) scale(${.91+.09*a})`,boxShadow:beat.motion==='deal'&&a>.8?'0 16px 28px rgba(0,0,0,.22)':'none',whiteSpace:'nowrap'}}>{x}</div>})}
 </div>;
};

const Chip:React.FC<{text:string;local:number;side:'left'|'right'}> = ({text,local,side})=>{
 const {fps}=useVideoConfig();const start=side==='left'?42:72;const p=spring({frame:local-start,fps,config:{damping:13,stiffness:190}});const visible=alpha(local,start,start+8);const float=Math.sin((local-start-26)/20)*4*Math.max(0,alpha(local,start+26,start+52));
 return <div style={{position:'absolute',top:side==='left'?1490:1570,[side]:48,padding:'16px 20px',border:'1px solid rgba(53,215,174,.34)',borderRadius:17,background:'#10181a',fontFamily:'Vazir',fontSize:22,fontWeight:650,color:C.white,opacity:visible,transform:`translateY(${(24*(1-p)+float).toFixed(2)}px) scale(${(.86+.14*p).toFixed(4)})`,boxShadow:'0 18px 35px rgba(0,0,0,.34)',whiteSpace:'nowrap'}}><span style={{color:C.teal,marginInlineEnd:9}}>◆</span>{text}</div>
};

const BeatScene:React.FC<{beat:Beat;index:number;chapter:Chapter;progressStart:number}> = ({beat,index,chapter,progressStart})=>{
 const local=useCurrentFrame();
 const cutIn=alpha(local,0,18);const cutOut=1-alpha(local,208,224);
 return <AbsoluteFill style={{background:C.ink,overflow:'hidden',color:C.white}}>
  <Atmosphere frame={local} mode={beat.motion}/>
  <div style={{position:'absolute',inset:0,background:`linear-gradient(180deg,rgba(7,9,12,.04),rgba(7,9,12,${.38*cutIn}) 68%,rgba(7,9,12,.88))`}}/>
  <div style={{position:'absolute',top:76,left:72,right:72,display:'flex',justifyContent:'space-between',alignItems:'center',fontFamily:'Vazir',opacity:cutIn}}><span style={{fontSize:22,color:C.teal,fontWeight:700}}>TERMINAL SSH</span><span style={{fontSize:18,color:C.muted}}>{String(chapter.id).padStart(2,'0')} / 04</span></div>
  <Phone beat={beat} local={local}/>
  <FeatureRows beat={beat} local={local}/>
  {(beat.motion==='list'||beat.motion==='deal')&&<Chip text={beat.tag??''} local={local} side="left"/>}
  {(beat.motion==='ripple'||beat.motion==='spotlight')&&<Chip text={beat.tag??''} local={local} side="right"/>}
  <div style={{position:'absolute',left:74,right:74,top:177,direction:'rtl',fontFamily:'Vazir',textAlign:'right',opacity:cutIn*cutOut,transform:`translateY(${(20*(1-cutIn)).toFixed(2)}px)`}}>
   <div style={{fontSize:23,color:C.teal,fontWeight:700,marginBottom:12}}>{chapter.name}</div>
   <div style={{fontSize:51,lineHeight:1.3,fontWeight:800,color:C.white,textShadow:'0 8px 30px rgba(0,0,0,.42)'}}>{beat.title}</div>
   <div style={{fontSize:27,lineHeight:1.5,color:C.muted,marginTop:12}}>{beat.copy}</div>
  </div>
  <div style={{position:'absolute',left:72,right:72,bottom:64,height:3,background:'rgba(255,255,255,.12)',borderRadius:2}}><div style={{height:'100%',width:`${100*(progressStart+local)/(CHAPTERS.length*PART)}%`,background:C.teal,boxShadow:'0 0 14px rgba(53,215,174,.48)'}}/></div>
  <div style={{position:'absolute',inset:0,background:C.ink,opacity:1-alpha(local,0,10),pointerEvents:'none'}}/>
 </AbsoluteFill>;
};

const ChapterComp:React.FC<{chapterId:number}> = ({chapterId})=>{
 const chapter=CHAPTERS[chapterId-1];
 return <AbsoluteFill style={{background:C.ink}}>
  {chapter.beats.map((beat,i)=><Sequence key={`${chapterId}-${i}`} from={i*225} durationInFrames={225}><BeatScene beat={beat} index={chapterId-1} chapter={chapter} progressStart={(chapterId-1)*PART+i*225}/></Sequence>)}
 </AbsoluteFill>;
};

const CompletePromo:React.FC=()=> <AbsoluteFill style={{background:C.ink}}>{CHAPTERS.map((ch,i)=><Sequence key={ch.id} from={sectionFrom(i)} durationInFrames={PART}><ChapterComp chapterId={ch.id}/></Sequence>)}</AbsoluteFill>;

export {PART, CompletePromo, ChapterComp};
