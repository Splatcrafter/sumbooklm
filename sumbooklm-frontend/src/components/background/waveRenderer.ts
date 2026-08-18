import { waveFragmentShader, waveVertexShader } from '@/components/background/waveShader';

/**
 * One step of the quality ladder. The renderer starts at the first entry and walks down while the
 * device fails to keep up.
 */
interface QualityLevel {
  readonly resolutionScale: number;
  readonly octaves: number;
}

const QUALITY_LEVELS: readonly QualityLevel[] = [
  { resolutionScale: 1, octaves: 3 },
  { resolutionScale: 0.75, octaves: 3 },
  { resolutionScale: 0.55, octaves: 2 },
  { resolutionScale: 0.4, octaves: 2 },
];

/** Frames per second the loop aims for. The motion is slow, so more would only cost battery. */
const TARGET_FRAMES_PER_SECOND = 30;

const TARGET_FRAME_INTERVAL_MS = 1000 / TARGET_FRAMES_PER_SECOND;

/**
 * Interval between two rendered frames that counts as too slow. The loop asks for a frame every
 * TARGET_FRAME_INTERVAL_MS, so a consistently larger interval means the device could not deliver
 * the frame in time rather than that the loop chose to wait.
 */
const SLOW_FRAME_INTERVAL_MS = TARGET_FRAME_INTERVAL_MS * 1.45;

/** Number of consecutive slow frames before the renderer gives up a quality level. */
const SLOW_FRAMES_BEFORE_DOWNGRADE = 40;

/** Upper bound on the device pixel ratio. Beyond this the extra pixels are not visible here. */
const MAX_PIXEL_RATIO = 2;

/** Time passed to the shader when motion is suppressed, chosen so the still frame is not the
 * degenerate composition at zero. */
const FROZEN_TIME_SECONDS = 12;

/**
 * A running background render loop.
 */
export interface WaveRenderer {
  dispose: () => void;
}

/**
 * Options of {@link createWaveRenderer}.
 */
export interface WaveRendererOptions {
  /**
   * Called when the background cannot be rendered, either because the context or the shader was
   * rejected, because the context was lost, or because the device stayed too slow at the lowest
   * quality level. The caller is expected to show the still image instead.
   */
  onUnsupported: () => void;
  /** When true a single frame is rendered and the loop does not start. */
  freeze: boolean;
}

function compileShader(gl: WebGL2RenderingContext, type: number, source: string): WebGLShader | null {
  const shader = gl.createShader(type);
  if (!shader) {
    return null;
  }
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.error('Background shader did not compile:', gl.getShaderInfoLog(shader));
    gl.deleteShader(shader);
    return null;
  }
  return shader;
}

function createProgram(gl: WebGL2RenderingContext): WebGLProgram | null {
  const vertexShader = compileShader(gl, gl.VERTEX_SHADER, waveVertexShader);
  const fragmentShader = compileShader(gl, gl.FRAGMENT_SHADER, waveFragmentShader);
  if (!vertexShader || !fragmentShader) {
    return null;
  }

  const program = gl.createProgram();
  let linked = false;
  if (program) {
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    linked = gl.getProgramParameter(program, gl.LINK_STATUS) as boolean;
    if (!linked) {
      console.error('Background program did not link:', gl.getProgramInfoLog(program));
    }
  }

  // The shaders are only needed until the program is linked, whether or not that succeeded.
  gl.deleteShader(vertexShader);
  gl.deleteShader(fragmentShader);

  if (!program || !linked) {
    if (program) {
      gl.deleteProgram(program);
    }
    return null;
  }
  return program;
}

/**
 * Starts rendering the background into a canvas.
 *
 * Returns null when the device offers no WebGL 2 context or rejects the shader; the caller then
 * shows the still image. Once running, the renderer lowers its own quality before it gives up, so a
 * weak device degrades in steps instead of dropping to the fallback at the first slow frame.
 */
export function createWaveRenderer(
  canvas: HTMLCanvasElement,
  options: WaveRendererOptions,
): WaveRenderer | null {
  const gl = canvas.getContext('webgl2', {
    alpha: false,
    antialias: false,
    depth: false,
    stencil: false,
    powerPreference: 'low-power',
  });
  if (!gl) {
    return null;
  }

  const program = createProgram(gl);
  if (!program) {
    return null;
  }

  const resolutionLocation = gl.getUniformLocation(program, 'uResolution');
  const timeLocation = gl.getUniformLocation(program, 'uTime');
  const octavesLocation = gl.getUniformLocation(program, 'uOctaves');

  let quality = 0;
  let disposed = false;
  let animationFrame = 0;
  let lastFrameAt = 0;
  let slowFrames = 0;
  let startedAt = 0;

  const applySize = () => {
    const scale = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO)
      * QUALITY_LEVELS[quality].resolutionScale;
    const width = Math.max(1, Math.round(canvas.clientWidth * scale));
    const height = Math.max(1, Math.round(canvas.clientHeight * scale));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
  };

  const draw = (elapsedSeconds: number) => {
    applySize();
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.useProgram(program);
    gl.uniform2f(resolutionLocation, canvas.width, canvas.height);
    gl.uniform1f(timeLocation, elapsedSeconds);
    gl.uniform1i(octavesLocation, QUALITY_LEVELS[quality].octaves);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  };


  const stop = () => {
    if (animationFrame !== 0) {
      cancelAnimationFrame(animationFrame);
      animationFrame = 0;
    }
  };

  /**
   * Drops one quality level, or gives up when there is none left. Returns false once the loop has
   * been stopped for good.
   */
  const downgrade = (): boolean => {
    slowFrames = 0;
    if (quality + 1 < QUALITY_LEVELS.length) {
      quality += 1;
      return true;
    }
    stop();
    options.onUnsupported();
    return false;
  };

  const frame = (now: number) => {
    if (disposed) {
      return;
    }
    animationFrame = requestAnimationFrame(frame);

    if (now - lastFrameAt < TARGET_FRAME_INTERVAL_MS) {
      return;
    }
    if (lastFrameAt !== 0) {
      slowFrames = now - lastFrameAt > SLOW_FRAME_INTERVAL_MS ? slowFrames + 1 : 0;
      if (slowFrames >= SLOW_FRAMES_BEFORE_DOWNGRADE && !downgrade()) {
        return;
      }
    }
    lastFrameAt = now;

    if (startedAt === 0) {
      startedAt = now;
    }
    draw((now - startedAt) / 1000);
  };

  const start = () => {
    if (disposed || animationFrame !== 0) {
      return;
    }
    // Restart the measurement so the gap created by a hidden tab is not read as a slow frame.
    lastFrameAt = 0;
    animationFrame = requestAnimationFrame(frame);
  };

  const onVisibilityChange = () => {
    if (document.visibilityState === 'hidden') {
      stop();
    } else {
      start();
    }
  };

  const onContextLost = (event: Event) => {
    event.preventDefault();
    stop();
    options.onUnsupported();
  };

  const resizeObserver = new ResizeObserver(() => {
    if (options.freeze) {
      draw(FROZEN_TIME_SECONDS);
    }
  });
  resizeObserver.observe(canvas);
  canvas.addEventListener('webglcontextlost', onContextLost);

  if (options.freeze) {
    draw(FROZEN_TIME_SECONDS);
  } else {
    document.addEventListener('visibilitychange', onVisibilityChange);
    start();
  }

  return {
    dispose: () => {
      disposed = true;
      stop();
      resizeObserver.disconnect();
      canvas.removeEventListener('webglcontextlost', onContextLost);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      gl.deleteProgram(program);
    },
  };
}
