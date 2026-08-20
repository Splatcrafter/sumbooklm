/*
 * Copyright (c) 2026 Erik Pförtner
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { useEffect, useRef, useState } from 'react';

import stillBackground from '@/assets/account-background.png';
import { createWaveRenderer } from '@/components/background/waveRenderer';

/**
 * Decides whether the animated background should be skipped entirely.
 *
 * Two cases qualify. A browser without WebGL 2 cannot run it at all. A small touch device can, but a
 * full screen procedural shader is a poor trade there: it costs battery for a decoration nobody looks
 * at. Everything in between starts the shader, because the renderer measures itself and steps down
 * before it gives up, which is a better judge of a device than any guess made up front.
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
 * The background of the account screens: animated waves where that is affordable, and the same waves
 * as a pre-rendered image everywhere else.
 *
 * The container carries a data-background attribute naming which of the two is on screen, so the
 * choice can be read off the element instead of being inferred from how the page looks.
 */
export function WaveBackground() {
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

    const renderer = createWaveRenderer(canvas, {
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
    <div
      aria-hidden
      data-background={showStillImage ? 'still' : 'shader'}
      className="pointer-events-none absolute inset-0 overflow-hidden bg-nb-ground"
    >
      {showStillImage ? (
        <img src={stillBackground} alt="" className="absolute inset-0 size-full object-cover" />
      ) : (
        <canvas ref={canvasRef} className="absolute inset-0 block size-full" />
      )}
    </div>
  );
}
