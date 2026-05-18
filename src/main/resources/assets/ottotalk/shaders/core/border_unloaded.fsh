#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float lum = dot(texture(Sampler0, uv).rgb, vec3(0.299, 0.587, 0.114));
    if (lum >= 0.03) discard;  // show only on dark (undiscovered) areas
    fragColor = vertexColor;
}
