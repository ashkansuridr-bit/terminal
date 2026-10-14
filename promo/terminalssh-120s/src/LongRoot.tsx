import React from 'react';
import {Composition} from 'remotion';
import {ChapterComp, CompletePromo, PART} from './LongPromo';
export const LongRoot:React.FC=()=> <>
 <Composition id="TerminalPromo120" component={CompletePromo} width={1080} height={1920} fps={30} durationInFrames={PART*4}/>
 <Composition id="TerminalPromo30A" component={ChapterComp} width={1080} height={1920} fps={30} durationInFrames={PART} defaultProps={{chapterId:1}}/>
 <Composition id="TerminalPromo30B" component={ChapterComp} width={1080} height={1920} fps={30} durationInFrames={PART} defaultProps={{chapterId:2}}/>
 <Composition id="TerminalPromo30C" component={ChapterComp} width={1080} height={1920} fps={30} durationInFrames={PART} defaultProps={{chapterId:3}}/>
 <Composition id="TerminalPromo30D" component={ChapterComp} width={1080} height={1920} fps={30} durationInFrames={PART} defaultProps={{chapterId:4}}/>
</>;
