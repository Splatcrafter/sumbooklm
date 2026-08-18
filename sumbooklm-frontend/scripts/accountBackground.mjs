/**
 * Renders the still version of the account background.
 *
 * The field below is the same construction as the fragment shader in
 * src/components/background/waveShader.ts, evaluated on the CPU. Keeping one algorithm in two
 * places is deliberate: it is what makes the fallback look like the animated background instead of
 * merely being dark. Both sides have to be changed together, and the constants are named the same
 * on both sides so a difference is easy to spot.
 *
 * Run with: npm run background:generate
 */
import { deflateSync } from 'node:zlib';
import { writeFileSync } from 'node:fs';

const WIDTH = 800;
const HEIGHT = 450;
const TIME = 18;
const OCTAVES = 3;
const SPAN = 1.5;
const BANDS = 1.9;
const WARP_AMOUNT = 3.6;
const DRIFT_WEIGHT = 0.26;
const WAVE_WEIGHT = 0.4;
const CREST_LIGHT = 0.05;

// Pulls the sine towards its extremes, which widens the plateaus and steepens the crossings between
// them. One is the plain sine; below one the bands gain defined edges.
const SHARPNESS = 0.45;

/** Colour ramp of the background, sampled by the height of the field. */
const PALETTE = ['#06030f', '#1a0a4a', '#4b12a8', '#a01ad8', '#f02fa0', '#ff9ad2'];

const FOLD = [1.6, 1.2, -1.2, 1.6];
const K1 = 0.366025404;
const K2 = 0.211324865;

/**
 * Bit mixing hash over the integer lattice. It replaces the usual sine based hash, which loses
 * precision once the folded coordinates grow large and then shows up as grain.
 */
function hash(ix, iy) {
  let h = ((Math.imul(ix | 0, 0x27d4eb2d) ^ Math.imul(iy | 0, 0x165667b1)) >>> 0);
  h = Math.imul(h ^ (h >>> 15), 0x2545f491) >>> 0;
  const a = ((h >>> 8) & 0xffff) / 32768 - 1;
  h = Math.imul(h ^ (h >>> 13), 0x27d4eb2d) >>> 0;
  const b = ((h >>> 8) & 0xffff) / 32768 - 1;
  return [a, b];
}

function noise(px, py) {
  const skew = (px + py) * K1;
  const ix = Math.floor(px + skew);
  const iy = Math.floor(py + skew);
  const unskew = (ix + iy) * K2;
  const ax = px - ix + unskew;
  const ay = py - iy + unskew;
  const m = ay < ax ? 1 : 0;
  const ox = m;
  const oy = 1 - m;
  const bx = ax - ox + K2;
  const by = ay - oy + K2;
  const cx = ax - 1 + 2 * K2;
  const cy = ay - 1 + 2 * K2;
  const ha = Math.max(0.5 - (ax * ax + ay * ay), 0);
  const hb = Math.max(0.5 - (bx * bx + by * by), 0);
  const hc = Math.max(0.5 - (cx * cx + cy * cy), 0);
  const ga = hash(ix, iy);
  const gb = hash(ix + ox, iy + oy);
  const gc = hash(ix + 1, iy + 1);
  return 70 * (
    ha ** 4 * (ax * ga[0] + ay * ga[1]) +
    hb ** 4 * (bx * gb[0] + by * gb[1]) +
    hc ** 4 * (cx * gc[0] + cy * gc[1])
  );
}

function fbm(px, py) {
  let sum = 0;
  let amplitude = 0.5;
  let x = px;
  let y = py;
  for (let octave = 0; octave < OCTAVES; octave++) {
    sum += amplitude * noise(x, y);
    const foldedX = FOLD[0] * x + FOLD[1] * y;
    const foldedY = FOLD[2] * x + FOLD[3] * y;
    x = foldedX;
    y = foldedY;
    amplitude *= 0.5;
  }
  return sum;
}

/**
 * Bends a stack of sine bands with low frequency noise. Warping bands rather than warping noise is
 * what makes the result read as flowing waves instead of crumpled marble.
 */
function waveField(px, py, time) {
  const drift = fbm(px * 0.55, py * 0.55);
  const bend = fbm(px * 0.55 + 3.7, py * 0.55 + 8.1);
  const phase = py * BANDS + bend * WARP_AMOUNT + px * 0.55 + time * 0.05;
  const raw = Math.sin(Math.PI * phase);
  const wave = Math.sign(raw) * Math.abs(raw) ** SHARPNESS;
  return { height: 0.5 + WAVE_WEIGHT * wave + DRIFT_WEIGHT * drift, crest: wave };
}

function smoothstep(edge0, edge1, x) {
  const t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
  return t * t * (3 - 2 * t);
}

const RAMP = PALETTE.map((hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255));

function sampleRamp(height) {
  const position = Math.min(Math.max(height, 0), 1) * (RAMP.length - 1);
  const index = Math.min(Math.floor(position), RAMP.length - 2);
  const blend = position - index;
  const eased = blend * blend * (3 - 2 * blend);
  return RAMP[index].map((channel, i) => channel + (RAMP[index + 1][i] - channel) * eased);
}

function shade(u, v, aspect) {
  const field = waveField(u * aspect * SPAN, v * SPAN, TIME);
  const crest = smoothstep(0.7, 1, field.crest);
  const highlight = [1, 0.9, 0.84];
  const colour = sampleRamp(field.height).map((channel, i) => channel + crest * CREST_LIGHT * highlight[i]);

  const distance = Math.hypot((u - 0.5) * aspect, v - 0.5);
  const vignette = 0.82 + 0.18 * (1 - smoothstep(0.42, 1.18, distance));
  return colour.map((channel) => channel * vignette);
}

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  let crc = 0xffffffff;
  for (const byte of body) {
    crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
  return Buffer.concat([length, body, checksum]);
}

/**
 * Paeth predictor of the PNG specification: the neighbour closest to left plus above minus corner.
 */
function paeth(left, above, corner) {
  const estimate = left + above - corner;
  const dLeft = Math.abs(estimate - left);
  const dAbove = Math.abs(estimate - above);
  const dCorner = Math.abs(estimate - corner);
  if (dLeft <= dAbove && dLeft <= dCorner) {
    return left;
  }
  return dAbove <= dCorner ? above : corner;
}

function encodePng(width, height, pixels) {
  const stride = width * 3;
  const raw = Buffer.alloc((stride + 1) * height);
  // Paeth filtering rather than no filtering. The image is almost entirely smooth gradients, where
  // storing the difference to a predicted neighbour compresses several times better than storing
  // the samples themselves.
  for (let y = 0; y < height; y++) {
    const rowStart = y * (stride + 1);
    raw[rowStart] = 4;
    for (let x = 0; x < stride; x++) {
      const current = pixels[y * stride + x];
      const left = x >= 3 ? pixels[y * stride + x - 3] : 0;
      const above = y > 0 ? pixels[(y - 1) * stride + x] : 0;
      const corner = x >= 3 && y > 0 ? pixels[(y - 1) * stride + x - 3] : 0;
      raw[rowStart + 1 + x] = (current - paeth(left, above, corner)) & 0xff;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 2;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const pixels = Buffer.alloc(WIDTH * HEIGHT * 3);
const aspect = WIDTH / HEIGHT;
for (let y = 0; y < HEIGHT; y++) {
  for (let x = 0; x < WIDTH; x++) {
    const colour = shade((x + 0.5) / WIDTH, 1 - (y + 0.5) / HEIGHT, aspect);
    const offset = (y * WIDTH + x) * 3;
    for (let i = 0; i < 3; i++) {
      pixels[offset + i] = Math.max(0, Math.min(255, Math.round(colour[i] * 255)));
    }
  }
}

const target = new URL('../src/assets/account-background.png', import.meta.url);
writeFileSync(target, encodePng(WIDTH, HEIGHT, pixels));
console.log(`account-background.png written, ${WIDTH}x${HEIGHT}`);
