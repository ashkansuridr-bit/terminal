import React from 'react';
import {Composition, registerRoot} from 'remotion';
import {GitHubCta} from './GitHubCta';

const Root: React.FC = () => <Composition
  id="GitHubCta"
  component={GitHubCta}
  durationInFrames={120}
  fps={30}
  width={1080}
  height={1920}
/>;

registerRoot(Root);
