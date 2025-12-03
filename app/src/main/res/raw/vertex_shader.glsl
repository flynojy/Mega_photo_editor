#version 300 es

in vec4 aPosition;
in vec2 aTexCoord;

// [新增] 变换矩阵 (控制缩放、平移)
uniform mat4 uMVPMatrix;

out vec2 vTexCoord;

void main() {
    // 矩阵乘法：变换矩阵 * 原始坐标
    gl_Position = uMVPMatrix * aPosition;
    vTexCoord = aTexCoord;
}
