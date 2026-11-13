#version 150

// Drunk eyes (Vine & Barrel, Tipsy): a second image drifting slowly apart and back (double vision), and soft, slightly
// warm edges. How strong each is comes from the client every frame as uniforms:
// Split = how far apart the two images are (a share of MAX_SPLIT of the width), Ghost = how strong the second image is,
// Edges = how soft the edges are, Glare = how much daylight glares (the morning after; a share of MOST_GLARE).
// (In Minecraft's own namespace with the mod's prefix: this Minecraft looks up post shader programs there only.)

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
// The day's time, once round per day (24000 ticks), as later Minecraft versions hand it to every shader
uniform float GameTime;
uniform float Split;
uniform float Ghost;
uniform float Edges;
uniform float Glare;

in vec2 texCoord;

out vec4 fragColor;

const float MAX_SPLIT = 0.03;
const float MOST_GLARE = 1.5;
const float PERIOD = 4.6;
const float TAU = 6.2831853;
const int TAPS = 12;

vec3 doubled(vec2 uv, vec2 offset, float ghost) {
    vec3 base = texture(DiffuseSampler, uv).rgb;
    if (ghost <= 0.0) {
        return base;
    }
    vec3 second = texture(DiffuseSampler, clamp(uv - offset, vec2(0.0), vec2(1.0))).rgb;
    return mix(base, second, ghost);
}

void main() {
    vec4 params = clamp(vec4(Split, Ghost, Edges, Glare), vec4(0.0), vec4(1.0));
    float split = params.r * MAX_SPLIT;
    float ghost = params.g;
    float edge = params.b;
    float glare = params.a * MOST_GLARE;
    float aspect = OutSize.x / max(OutSize.y, 1.0);

    // GameTime runs once round per day (24000 ticks): in seconds
    float t = GameTime * 1200.0;
    // the second image drifts between a third of the split and all of it, never quite closing
    vec2 offset = vec2(split * (0.62 + 0.38 * sin(TAU * t / PERIOD)), split * 0.18 * sin(TAU * t / (PERIOD * 1.7) + 1.1) * aspect);

    vec3 color = doubled(texCoord, offset, ghost);

    // soft edges: an oval, sharp in the middle, blurred and a little warm toward the edges
    vec2 centered = (texCoord - 0.5) / vec2(0.38, 0.40);
    float mask = edge * smoothstep(0.7, 1.35, length(centered));
    if (mask > 0.002) {
        float radius = 0.011 * mask;
        vec3 blurred = vec3(0.0);
        for (int i = 0; i < TAPS; i++) {
            float a = TAU * float(i) / float(TAPS);
            float r = (i % 2 == 0) ? radius : radius * 0.55;
            blurred += doubled(texCoord + vec2(cos(a), sin(a) * aspect) * r, offset, ghost);
        }
        blurred /= float(TAPS);
        color = mix(color, blurred, min(1.0, mask * 1.6));
        color *= mix(vec3(1.0), vec3(1.03, 1.0, 0.95), mask);
    }
    if (glare > 0.002) {
        // the morning after: bright things burn out to a pale white, and everything is a little too bright
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        float bright = smoothstep(0.3, 0.95, luma);
        color = mix(color, vec3(1.0, 0.985, 0.95), min(0.95, glare * 0.85 * smoothstep(0.15, 0.9, luma))) + glare * 0.09;
    }
    fragColor = vec4(min(color, vec3(1.0)), 1.0);
}
