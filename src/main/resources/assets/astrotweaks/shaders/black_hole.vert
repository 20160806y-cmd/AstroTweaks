#version 120
varying vec3 vPos;
varying vec3 vView;
varying vec3 vNormal;
void main(){
  vPos = gl_Vertex.xyz;
  vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
  vView = viewPos.xyz;
  vec3 nObj = normalize(gl_Vertex.xyz + vec3(0.0001, 0.0, 0.0));
  vNormal = normalize(gl_NormalMatrix * nObj);
  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
