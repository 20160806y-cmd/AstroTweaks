#version 120
varying vec3 vPos;
varying vec3 vView;
varying vec3 vNormal;
uniform float uTime;
uniform float uHorizon;
uniform float uGravityRange;
uniform float uMass;
uniform float uMode;
void main(){
  vec3 n = normalize(vNormal);
  vec3 viewDir = normalize(-vView + vec3(0.0001, 0.0, 0.0));
  float ndv = max(0.0, dot(n, viewDir));
  float fresnel = pow(1.0 - ndv, 2.5);
  if (uMode < 0.5) {
    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
    return;
  } else if (uMode < 1.5) {
    float alpha = pow(fresnel, 2.0);
    float ang = atan(vPos.z, vPos.x);
    float shimmer = 0.9 + 0.1 * sin(uTime * 1.1 + ang * 2.0);
    alpha *= 0.65 * shimmer;
    if (alpha < 0.003) discard;
    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
    return;
  } else {
    float alpha = pow(fresnel, 2.0);
    float ang = atan(vPos.z, vPos.x);
    float shimmer = 0.9 + 0.1 * sin(uTime * 1.1 + ang * 2.0 + 1.5);
    alpha *= 0.325 * shimmer;
    if (alpha < 0.0025) discard;
    gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
    return;
  }
}
