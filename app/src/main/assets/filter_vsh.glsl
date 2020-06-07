attribute vec4 a_FilterPosition;
attribute vec2 a_FilterTexCoord;
varying vec2 v_TexCoord;

void main() {
  v_TexCoord = a_FilterTexCoord;
  gl_Position = a_FilterPosition;
}