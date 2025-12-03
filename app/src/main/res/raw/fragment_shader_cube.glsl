#version 300 es
precision mediump float;
precision mediump sampler3D;

uniform sampler2D uTexture;
uniform sampler3D uLutTexture;
uniform float uIntensity;
uniform int uHasLut;

// [新增] 调色参数
uniform float uBrightness; // 默认 0.0 (-0.5 ~ 0.5)
uniform float uContrast;   // 默认 1.0 (0.0 ~ 2.0)
uniform float uSaturation; // 默认 1.0 (0.0 ~ 2.0)

in vec2 vTexCoord;
out vec4 FragColor;

void main() {
    vec4 textureColor = texture(uTexture, vTexCoord);
    vec3 rgb = textureColor.rgb;

    // 1. 应用 LUT (如果存在)
    if (uHasLut == 1) {
        vec3 lutColor = texture(uLutTexture, rgb).rgb;
        rgb = mix(rgb, lutColor, uIntensity);
    }

    // 2. 应用亮度 (Brightness)
    // 直接相加: RGB + val
    rgb += uBrightness;

    // 3. 应用对比度 (Contrast)
    // 公式: (Color - 0.5) * Contrast + 0.5
    rgb = (rgb - 0.5) * uContrast + 0.5;

    // 4. 应用饱和度 (Saturation)
    // 灰度加权值 (人眼对绿色的敏感度最高)
    const vec3 luminanceWeight = vec3(0.2125, 0.7154, 0.0721);
    float luminance = dot(rgb, luminanceWeight);
    rgb = mix(vec3(luminance), rgb, uSaturation);

    FragColor = vec4(rgb, textureColor.a);
}