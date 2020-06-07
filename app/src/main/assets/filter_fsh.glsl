#extension GL_OES_EGL_image_external : require
precision highp float;

varying vec2 v_TexCoord;
//uniform samplerExternalOES u_FilterTextureUnit;
uniform sampler2D u_FilterTextureUnit;

// LUT颜色查找表
uniform sampler2D u_FilterLookupTable;

const float c_LutCount = 9.0;

// 3dlut 16*16*16
const float c_LutSize = 16.0;

const float ROW = 3.0;
const float COL = 3.0;

vec3 applyLut(vec3 color, sampler2D lookup_table, float index) {
    float blue = color.b * (c_LutSize - 1.0);
    float green = color.g * (c_LutSize - 1.0);
    float red = color.r * (c_LutSize - 1.0);

    float blue_low = clamp(floor(blue), 0.0, c_LutSize - 2.0);

    float lower_y = (0.5 + blue_low * c_LutSize + green) / (c_LutSize * c_LutSize);
    float upper_y = lower_y + 1.0 / c_LutSize;

    float x = (0.5 + c_LutSize * index + red) / (c_LutSize * c_LutCount);

    vec3 lower_rgb = texture2D(lookup_table, vec2(x, lower_y)).rgb;
    vec3 upper_rgb = texture2D(lookup_table, vec2(x, upper_y)).rgb;
    float frac_b = blue - blue_low;

    color =  vec3(0.3 * color.r + 0.59 * color.g + 0.11 * color.b);
    //线性插值x*(1-a)+a*y
    return mix(lower_rgb, upper_rgb, frac_b);
}

// 滤镜index
//0  1  2
//3  4  5
//6  7  8
float getLutIndex(vec2 texCoord) {
    float xPosScaled = floor(texCoord.x * COL);
    float yPosScaled = floor(texCoord.y * ROW);
    return xPosScaled + yPosScaled * COL;
}

// 把预览变换成3*3画中画
vec2 getScaledCoord(vec2 texCoord) {
    float xPosScaled = texCoord.x * COL;
    float yPosScaled = texCoord.y * ROW;

    xPosScaled = xPosScaled - floor(xPosScaled);
    yPosScaled = yPosScaled - floor(yPosScaled);
    return vec2(xPosScaled, yPosScaled);
}

void main() {
    vec2 relativeTextureCoordinates = v_TexCoord;
    relativeTextureCoordinates = getScaledCoord(v_TexCoord);
    float filterIndex = getLutIndex(v_TexCoord);
    vec3 color = texture2D(u_FilterTextureUnit, relativeTextureCoordinates).rgb;
    color = applyLut(color,u_FilterLookupTable,filterIndex);
    gl_FragColor = vec4(color, 1.0);
}
