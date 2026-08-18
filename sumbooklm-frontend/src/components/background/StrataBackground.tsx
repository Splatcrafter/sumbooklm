import { useEffect, useRef, useState } from 'react';

import { createStrataRenderer } from '@/components/background/strataRenderer';
import { strataStillImage } from '@/components/background/strataStillImage';

/**
 * Decides whether the animated background should be skipped entirely.
 *
 * Two cases qualify. A browser without WebGL 2 cannot run it at all. A small touch device can, but
 * a full screen procedural shader is a poor trade there: it costs battery for a decoration nobody
 * looks at. Everything in between starts the shader, because the renderer measures itself and steps
 * down before it gives up, which is a better judge of a device than any guess made up front.
 */
function prefersStillImage(): boolean {
  if (typeof window === 'undefined' || typeof WebGL2RenderingContext === 'undefined') {
    return true;
  }
  const smallTouchDevice =
    window.matchMedia('(pointer: coarse)').matches &&
    Math.min(window.innerWidth, window.innerHeight) < 768;
  const veryFewCores = (navigator.hardwareConcurrency ?? 8) <= 2;
  return smallTouchDevice || veryFewCores;
}

/**
 * The background of the account screens: animated layers where that is affordable, a generated
 * still image everywhere else.
 */
export function StrataBackground() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [showStillImage, setShowStillImage] = useState(prefersStillImage);

  useEffect(() => {
    if (showStillImage) {
      return;
    }
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    const renderer = createStrataRenderer(canvas, {
      // A visitor who asked for reduced motion still gets the image, just not the movement.
      freeze: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
      onUnsupported: () => setShowStillImage(true),
    });
    if (!renderer) {
      setShowStillImage(true);
      return;
    }
    return () => renderer.dispose();
  }, [showStillImage]);

  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden bg-jb-black">
      {showStillImage ? (
        <div
          className="absolute inset-0"
          style={{
            backgroundImage: strataStillImage,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
          }}
        />
      ) : (
        <canvas ref={canvasRef} className="absolute inset-0 block h-full w-full" />
      )}
    </div>
  );
}
