#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES u_TextureUnit;
//uniform sampler2D u_TextureUnit;

varying vec2 v_TexCoord;

void main() {
    gl_FragColor = texture2D(u_TextureUnit, v_TexCoord);
}