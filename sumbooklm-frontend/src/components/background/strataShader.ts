/**
 * Vertex shader of the background.
 *
 * It emits a single triangle that covers the clip volume, derived from the vertex index, so the
 * renderer needs neither a vertex buffer nor an attribute binding.
 */
export const strataVertexShader = `#version 300 es

void main(void) {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
}
`;

/**
 * Fragment shader of the background.
 *
 * The image is a second order domain warp over fractional Brownian motion, squashed along one axis
 * so the result reads as sedimentary layers, with derivative based contour lines drawn on top. The
 * colour ramp is the grayscale of the JetBrains website, which keeps the background inside the same
 * palette as the card in front of it.
 *
 * The octave count is a uniform rather than a constant, because the renderer lowers it when the
 * measured frame time says the device cannot keep up.
 */
export const strataFragmentShader = `#version 300 es
precision highp float;

uniform vec2 uResolution;
uniform float uTime;
uniform int uOctaves;

out vec4 fragColor;

const vec3 JB_BLACK = vec3(0.098, 0.098, 0.110);
const vec3 JB_GREY_95 = vec3(0.145, 0.145, 0.157);
const vec3 JB_GREY_90 = vec3(0.188, 0.188, 0.200);
const vec3 JB_GREY_80 = vec3(0.278, 0.278, 0.286);

const int MAX_OCTAVES = 6;
const float CONTOUR_COUNT = 7.0;

// Rotation by roughly 37 degrees combined with a scale of two. Folding the domain with a rotation
// between octaves keeps the sum from lining up on the axes, which is what makes plain fractional
// noise look like a grid.
const mat2 FOLD = mat2(1.6, 1.2, -1.2, 1.6);

vec2 hash(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float gradientNoise(vec2 p) {
    const float K1 = 0.366025404;
    const float K2 = 0.211324865;

    vec2 i = floor(p + (p.x + p.y) * K1);
    vec2 a = p - i + (i.x + i.y) * K2;
    float m = step(a.y, a.x);
    vec2 o = vec2(m, 1.0 - m);
    vec2 b = a - o + K2;
    vec2 c = a - 1.0 + 2.0 * K2;

    vec3 h = max(0.5 - vec3(dot(a, a), dot(b, b), dot(c, c)), 0.0);
    vec3 n = h * h * h * h * vec3(dot(a, hash(i)), dot(b, hash(i + o)), dot(c, hash(i + 1.0)));
    return dot(n, vec3(70.0));
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

float strata(vec2 p, float time) {
    vec2 q = vec2(fbm(p), fbm(p + vec2(5.2, 1.3)));
    vec2 r = vec2(fbm(p + 4.0 * q + vec2(1.7, 9.2) + vec2(0.0, 0.08 * time)),
                  fbm(p + 4.0 * q + vec2(8.3, 2.8) + vec2(0.06 * time, 0.0)));
    return fbm(p + 4.0 * r);
}

float contour(float height) {
    float bands = height * CONTOUR_COUNT;
    float distanceToLine = abs(fract(bands) - 0.5);
    float width = max(fwidth(bands), 0.0001);
    return 1.0 - smoothstep(width * 0.5, width * 1.8, distanceToLine);
}

float dither(vec2 fragment) {
    return fract(sin(dot(fragment, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

void main(void) {
    vec2 uv = gl_FragCoord.xy / uResolution;
    float aspect = uResolution.x / max(uResolution.y, 1.0);

    // Squashing the vertical axis is what turns isotropic noise into layers.
    vec2 field = vec2(uv.x * aspect, uv.y * 0.34) * 2.6;

    float height = strata(field, uTime);
    float normalized = clamp(height * 0.5 + 0.5, 0.0, 1.0);

    vec3 colour = mix(JB_BLACK, JB_GREY_95, smoothstep(0.30, 0.62, normalized));
    colour = mix(colour, JB_GREY_90, smoothstep(0.60, 0.90, normalized));
    colour = mix(colour, JB_GREY_80, contour(height) * 0.5);

    // The card sits on the left, so the left edge stays dark for contrast and the layers gain
    // presence towards the right.
    colour *= mix(0.58, 1.18, smoothstep(-0.05, 0.85, uv.x));

    vec2 centered = (uv - 0.5) * vec2(aspect, 1.0);
    colour *= mix(0.70, 1.0, 1.0 - smoothstep(0.35, 1.05, length(centered)));

    // Eight bits per channel over a ramp this shallow bands visibly; one step of noise removes it.
    colour += dither(gl_FragCoord.xy) / 255.0;

    fragColor = vec4(colour, 1.0);
}
`;
