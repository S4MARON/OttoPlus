#version 150

// Sampler0 = Lower layer (full details)
// Sampler1 = Screen copy (Xaeros framebuffer, also Terrain, UI, Markers, alles)
// Sampler2 = Upper map layer (composited over lower)
// Sampler3 = Lower layer (no details)
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform float FadeScale;
uniform float HudGuardFB;
uniform float NightBrightness;
uniform float DetailBlend;  // 1.0 = full details, 0.0 = no details
uniform float UpperAlpha;   // 1.0 = upper layer fully visible, 0.0 = faded out
uniform float LowerAlpha;   // 1.0 = lower (colors full), 0.0 = darkened to black
uniform float OverallAlpha; // 1.0 = normal, 0.0 = entire map faded out (high zoom)

in vec2 texCoord;
out vec4 fragColor;

// Check if screen copy has loaded content at a position
float isLoaded(vec2 screenUV) {
    vec3 c = texture(Sampler1, screenUV).rgb;
    return step(0.03, dot(c, vec3(0.299, 0.587, 0.114)));
}

// Sample lower layer with detail fade
vec4 sampleLower(vec2 uv) {
    return mix(texture(Sampler3, uv), texture(Sampler0, uv), DetailBlend);
}

// Sample full composite (lower + upper) at a UV position
vec4 sampleComposite(vec2 uv) {
    vec4 lower = sampleLower(uv);
    vec4 upper = texture(Sampler2, uv);
    return vec4(mix(lower.rgb, upper.rgb, upper.a * UpperAlpha), lower.a);
}

void main() {
    vec4 mapColor = sampleComposite(texCoord);

    // Sample screen copy at this fragment's position
    vec2 screenSize = vec2(textureSize(Sampler1, 0));
    vec2 screenUV = gl_FragCoord.xy / screenSize;
    vec3 screenColor = texture(Sampler1, screenUV).rgb;
    float screenLum = dot(screenColor, vec3(0.299, 0.587, 0.114));

    vec3 fillColor = vec3(0.302, 0.337, 0.380) * NightBrightness; // #4d5661 darkened at night

    // HUD guard FIRST (applies everywhere, including outside map bounds)
    // Preserves Xaero's coordinate display (top) and zoom display (bottom)
    float normX = gl_FragCoord.x / screenSize.x;
    bool inTopBand = normX > 0.30 && normX < 0.70;
    bool inBotBand = normX > 0.35 && normX < 0.65;
    bool topHit = gl_FragCoord.y < HudGuardFB && inTopBand && screenLum > 0.15;
    bool botHit = (screenSize.y - gl_FragCoord.y) < (HudGuardFB * 4.0) && inBotBand && screenLum > 0.15;
    if (topHit || botHit) {
        fragColor = vec4(0.0);
        return;
    }

    // Outside map geographic bounds: fill tracks NightBrightness + LowerAlpha like the map
    // Base value (0.431, 0.481, 0.543) = original (0.302, 0.337, 0.380) / 0.70 (undarkened)
    if (texCoord.x < 0.0 || texCoord.x > 1.0 || texCoord.y < 0.0 || texCoord.y > 1.0) {
        vec3 fillBase = vec3(0.431, 0.481, 0.543);
        fragColor = vec4(fillBase * NightBrightness * LowerAlpha, OverallAlpha);
        return;
    }

    // --- Edge fade: sample nearby pixels in screen copy to detect chunk boundary ---
    vec2 px = 1.0 / screenSize;
    float loaded = 0.0;

    float fs = clamp(FadeScale, 0.2, 4.0);
    float r1 = 8.0 * fs; float r2 = 16.0 * fs; float r3 = 24.0 * fs;
    float r4 = 32.0 * fs; float r5 = 40.0 * fs;

    vec2 d0 = vec2(1.0, 0.0); vec2 d1 = vec2(0.707, 0.707);
    vec2 d2 = vec2(0.0, 1.0); vec2 d3 = vec2(-0.707, 0.707);
    vec2 d4 = vec2(-1.0, 0.0); vec2 d5 = vec2(-0.707, -0.707);
    vec2 d6 = vec2(0.0, -1.0); vec2 d7 = vec2(0.707, -0.707);

    // Ring 1
    loaded += isLoaded(screenUV + d0 * r1 * px);
    loaded += isLoaded(screenUV + d1 * r1 * px);
    loaded += isLoaded(screenUV + d2 * r1 * px);
    loaded += isLoaded(screenUV + d3 * r1 * px);
    loaded += isLoaded(screenUV + d4 * r1 * px);
    loaded += isLoaded(screenUV + d5 * r1 * px);
    loaded += isLoaded(screenUV + d6 * r1 * px);
    loaded += isLoaded(screenUV + d7 * r1 * px);
    // Ring 2
    loaded += isLoaded(screenUV + d0 * r2 * px);
    loaded += isLoaded(screenUV + d1 * r2 * px);
    loaded += isLoaded(screenUV + d2 * r2 * px);
    loaded += isLoaded(screenUV + d3 * r2 * px);
    loaded += isLoaded(screenUV + d4 * r2 * px);
    loaded += isLoaded(screenUV + d5 * r2 * px);
    loaded += isLoaded(screenUV + d6 * r2 * px);
    loaded += isLoaded(screenUV + d7 * r2 * px);
    // Ring 3
    loaded += isLoaded(screenUV + d0 * r3 * px);
    loaded += isLoaded(screenUV + d1 * r3 * px);
    loaded += isLoaded(screenUV + d2 * r3 * px);
    loaded += isLoaded(screenUV + d3 * r3 * px);
    loaded += isLoaded(screenUV + d4 * r3 * px);
    loaded += isLoaded(screenUV + d5 * r3 * px);
    loaded += isLoaded(screenUV + d6 * r3 * px);
    loaded += isLoaded(screenUV + d7 * r3 * px);
    // Ring 4
    loaded += isLoaded(screenUV + d0 * r4 * px);
    loaded += isLoaded(screenUV + d1 * r4 * px);
    loaded += isLoaded(screenUV + d2 * r4 * px);
    loaded += isLoaded(screenUV + d3 * r4 * px);
    loaded += isLoaded(screenUV + d4 * r4 * px);
    loaded += isLoaded(screenUV + d5 * r4 * px);
    loaded += isLoaded(screenUV + d6 * r4 * px);
    loaded += isLoaded(screenUV + d7 * r4 * px);
    // Ring 5
    loaded += isLoaded(screenUV + d0 * r5 * px);
    loaded += isLoaded(screenUV + d1 * r5 * px);
    loaded += isLoaded(screenUV + d2 * r5 * px);
    loaded += isLoaded(screenUV + d3 * r5 * px);
    loaded += isLoaded(screenUV + d4 * r5 * px);
    loaded += isLoaded(screenUV + d5 * r5 * px);
    loaded += isLoaded(screenUV + d6 * r5 * px);
    loaded += isLoaded(screenUV + d7 * r5 * px);

    float ratio = loaded / 40.0; // 0=all neighbors dark, 1=all neighbors loaded

    // Early out: deep in loaded terrain (saves work for the majority of pixels)
    if (ratio > 0.95 && screenLum > 0.05) {
        fragColor = vec4(0.0);
        return;
    }

    // proximityAlpha: small edge1 (0.45) = high alpha even at boundary = strong visible fade
    float nearUnloaded = 1.0 - ratio;
    float proximityAlpha = smoothstep(0.0, 0.45, nearUnloaded);

    float darkBoost = 1.0 - smoothstep(0.0, 0.04, screenLum);

    float alpha = max(darkBoost, proximityAlpha);

    // LowerAlpha darkens map towards black; OverallAlpha fades entire map at high zoom
    // NightBrightness already carries the 0.70 base brightness factor from Java
    // mapColor.a = texture alpha, sonst geht die Transparenz vom Upper Layer flöten (borders overlay)
    vec3 finalColor = mapColor.rgb * NightBrightness * LowerAlpha;
    fragColor = vec4(finalColor, alpha * OverallAlpha * mapColor.a);
}
