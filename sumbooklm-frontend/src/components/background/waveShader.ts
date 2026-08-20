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

/**
 * Vertex shader of the background.
 *
 * It emits a single triangle that covers the clip volume, derived from the vertex index, so the
 * renderer needs neither a vertex buffer nor an attribute binding.
 */
export const waveVertexShader = `#version 300 es

void main(void) {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
`;

/**
 * Fragment shader of the background.
 *
 * A stack of sine bands is bent by low frequency fractal noise, which produces flowing waves. The
 * order matters: warping bands gives waves, whereas warping the noise itself gives crumpled marble.
 * The height of the field samples a six stop colour ramp and wave crests catch a warm light. The
 * brightness is even across the width: the form stays readable through the opacity of its card
 * rather than by darkening the side it sits on.
 *
 * The octave count is a uniform rather than a constant, because the renderer lowers it when the
 * measured frame time says the device cannot keep up.
 *
 * The same construction is implemented on the CPU in scripts/accountBackground.mjs, which renders
 * the still image used where this shader cannot run. Changes belong on both sides.
 */
export const waveFragmentShader = `#version 300 es
precision highp float;

uniform vec2 uResolution;
uniform float uTime;
uniform int uOctaves;

out vec4 fragColor;

const float PI = 3.1415926535897932;
const int MAX_OCTAVES = 3;

const float SPAN = 1.5;
const float BANDS = 1.9;
const float WARP_AMOUNT = 3.6;
const float DRIFT_WEIGHT = 0.26;
const float WAVE_WEIGHT = 0.4;
const float CREST_LIGHT = 0.05;

// Pulls the sine towards its extremes, which widens the plateaus and steepens the crossings between
// them. One is the plain sine; below one the bands gain defined edges.
const float SHARPNESS = 0.45;

// Rotation combined with a scale of two, applied between octaves so the sum does not line up on the
// axes. Written in column order, so this is the matrix whose first row is (1.6, 1.2).
const mat2 FOLD = mat2(1.6, -1.2, 1.2, 1.6);

const vec3 RAMP[6] = vec3[6](
    vec3(0.023529, 0.011765, 0.058824),
    vec3(0.101961, 0.039216, 0.290196),
    vec3(0.294118, 0.070588, 0.658824),
    vec3(0.627451, 0.101961, 0.847059),
    vec3(0.941176, 0.184314, 0.627451),
    vec3(1.000000, 0.603922, 0.823529)
);

/**
 * Bit mixing hash over the integer lattice. A sine based hash loses precision once the folded
 * coordinates grow large, and that shows up as grain across the whole image.
 */
vec2 hash(vec2 p) {
    uvec2 lattice = uvec2(ivec2(floor(p)));
    uint h = (lattice.x * 0x27d4eb2du) ^ (lattice.y * 0x165667b1u);
    h = (h ^ (h >> 15u)) * 0x2545f491u;
    float a = float((h >> 8u) & 0xffffu) / 32768.0 - 1.0;
    h = (h ^ (h >> 13u)) * 0x27d4eb2du;
    float b = float((h >> 8u) & 0xffffu) / 32768.0 - 1.0;
    return vec2(a, b);
}

float gradientNoise(vec2 p) {
    const float K1 = 0.366025404;
    const float K2 = 0.211324865;

    vec2 lattice = floor(p + (p.x + p.y) * K1);
    vec2 a = p - lattice + (lattice.x + lattice.y) * K2;
    float m = step(a.y, a.x);
    vec2 o = vec2(m, 1.0 - m);
    vec2 b = a - o + K2;
    vec2 c = a - 1.0 + 2.0 * K2;

    vec3 falloff = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
    vec3 contribution = falloff * falloff * falloff * falloff
        * vec3(dot(a, hash(lattice)), dot(b, hash(lattice + o)), dot(c, hash(lattice + 1.0)));
    return 70.0 * (contribution.x + contribution.y + contribution.z);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < MAX_OCTAVES; octave++) {
        if (octave >= uOctaves) {
            break;
        }
        sum += amplitude * gradientNoise(p);
        p = FOLD * p;
        amplitude *= 0.5;
    }
    return sum;
}

struct Wave {
    float height;
    float crest;
};

Wave waveField(vec2 p, float time) {
    float drift = fbm(p * 0.55);
    float bend = fbm(p * 0.55 + vec2(3.7, 8.1));
    float phase = p.y * BANDS + bend * WARP_AMOUNT + p.x * 0.55 + time * 0.05;
    float raw = sin(PI * phase);
    float wave = sign(raw) * pow(abs(raw), SHARPNESS);

    Wave result;
    result.height = 0.5 + WAVE_WEIGHT * wave + DRIFT_WEIGHT * drift;
    result.crest = wave;
    return result;
}

vec3 sampleRamp(float height) {
    float position = clamp(height, 0.0, 1.0) * float(RAMP.length() - 1);
    int index = int(min(floor(position), float(RAMP.length() - 2)));
    float blend = position - float(index);
    return mix(RAMP[index], RAMP[index + 1], blend * blend * (3.0 - 2.0 * blend));
}

float dither(vec2 fragment) {
    return fract(sin(dot(fragment, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main(void) {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / max(uResolution.y, 1.0);

    Wave field = waveField(vec2(uv.x * aspect, uv.y) * SPAN, uTime);

    vec3 colour = sampleRamp(field.height);
    colour += smoothstep(0.7, 1.0, field.crest) * CREST_LIGHT * vec3(1.0, 0.9, 0.84);

    float distance = length(vec2((uv.x - 0.5) * aspect, uv.y - 0.5));
    colour *= 0.82 + 0.18 * (1.0 - smoothstep(0.42, 1.18, distance));

    // Eight bits per channel over a ramp this shallow bands visibly; one step of noise removes it.
    colour += dither(gl_FragCoord.xy) / 255.0;

    fragColor = vec4(colour, 1.0);
}
`;
