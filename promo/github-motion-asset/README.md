# GitHub motion assets

This directory contains the re-renderable Remotion source for the Terminal SSH animated GitHub banner and vertical outro. The banner collapses the teal terminal prompt into `>_`, lands the Persian app name and subtitle, then types the exact repository URL and holds it at the end. The vertical outro follows the same brand motion in a 9:16 format.

The rendered MP4s and README GIF are in the repository root. `src/GitHubBanner.tsx` and `src/banner.tsx` compose the 1280×720 banner; `src/GitHubCta.tsx` and `src/index.tsx` compose the 1080×1920 outro. `public/vazirmatn.ttf` is the app's bundled font. See `Vazirmatn-OFL.txt` and `NOTICE.md` for licensing.

Re-render with Node.js and npm:

```sh
npm ci
npm run render:banner
npm run render:outro
```

The renders are 4 seconds, 30 fps, H.264 and silent. The README preview GIF is 960×540 at 12 fps. Promotional videos and GIFs are project media, not software artifacts distributed under the app's Apache-2.0 license.
