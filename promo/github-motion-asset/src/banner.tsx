import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {GitHubBanner} from './GitHubBanner';

const Root: React.FC = () => <Composition
  id="GitHubBanner"
  component={GitHubBanner}
  durationInFrames={120}
  fps={30}
  width={1280}
  height={720}
/>;

registerRoot(Root);
