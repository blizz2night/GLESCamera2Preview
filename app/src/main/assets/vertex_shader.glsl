attribute vec4 a_Position;
attribute vec4 a_TexCoord;
uniform mat4 u_MVPMatrix;
uniform mat4 u_TextureMatrix;
varying vec2 v_TexCoord;

void main() {
    v_TexCoord = (u_TextureMatrix * a_TexCoord).xy;
    gl_Position = a_Position;
}
