import { useEffect, useRef } from 'react';
import { Mesh, Program, Renderer, Triangle } from 'ogl';
import './SideRays.css';

type RayOrigin =
  | 'top-right'
  | 'top-left'
  | 'bottom-right'
  | 'bottom-left';

interface SideRaysProps {
  speed?: number;
  rayColor1?: string;
  rayColor2?: string;
  intensity?: number;
  spread?: number;
  origin?: RayOrigin;
  tilt?: number;
  saturation?: number;
  blend?: number;
  falloff?: number;
  opacity?: number;
  className?: string;
}

type FloatUniform = { value: number };
type Vec2Uniform = { value: [number, number] };
type Vec3Uniform = { value: [number, number, number] };

interface SideRaysUniforms {
  iTime: FloatUniform;
  iResolution: Vec2Uniform;
  iSpeed: FloatUniform;
  iRayColor1: Vec3Uniform;
  iRayColor2: Vec3Uniform;
  iIntensity: FloatUniform;
  iSpread: FloatUniform;
  iFlipX: FloatUniform;
  iFlipY: FloatUniform;
  iTilt: FloatUniform;
  iSaturation: FloatUniform;
  iBlend: FloatUniform;
  iFalloff: FloatUniform;
  iOpacity: FloatUniform;
}

const hexToRgb = (hex: string): [number, number, number] => {
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!match) return [1, 1, 1];
  return [
    parseInt(match[1], 16) / 255,
    parseInt(match[2], 16) / 255,
    parseInt(match[3], 16) / 255,
  ];
};

const originToFlip = (origin: RayOrigin): [number, number] => {
  switch (origin) {
    case 'top-left':
      return [1, 0];
    case 'bottom-right':
      return [0, 1];
    case 'bottom-left':
      return [1, 1];
    default:
      return [0, 0];
  }
};

const vertexShader = [
  'attribute vec2 position;',
  'void main() {',
  '  gl_Position = vec4(position, 0.0, 1.0);',
  '}',
].join('\n');

const fragmentShader = [
  'precision highp float;',
  'uniform float iTime;',
  'uniform vec2 iResolution;',
  'uniform float iSpeed;',
  'uniform vec3 iRayColor1;',
  'uniform vec3 iRayColor2;',
  'uniform float iIntensity;',
  'uniform float iSpread;',
  'uniform float iFlipX;',
  'uniform float iFlipY;',
  'uniform float iTilt;',
  'uniform float iSaturation;',
  'uniform float iBlend;',
  'uniform float iFalloff;',
  'uniform float iOpacity;',
  'float rayStrength(vec2 raySource, vec2 rayRefDirection, vec2 coord, float seedA, float seedB, float speed) {',
  '  vec2 sourceToCoord = coord - raySource;',
  '  float cosAngle = dot(normalize(sourceToCoord), rayRefDirection);',
  '  return clamp(',
  '    (0.45 + 0.15 * sin(cosAngle * seedA + iTime * speed)) +',
  '    (0.3 + 0.2 * cos(-cosAngle * seedB + iTime * speed)),',
  '    0.0, 1.0',
  '  ) * clamp(',
  '    (iResolution.x - length(sourceToCoord)) / iResolution.x,',
  '    0.5, 1.0',
  '  );',
  '}',
  'void main() {',
  '  vec2 fragCoord = gl_FragCoord.xy;',
  '  if (iFlipX > 0.5) fragCoord.x = iResolution.x - fragCoord.x;',
  '  if (iFlipY > 0.5) fragCoord.y = iResolution.y - fragCoord.y;',
  '  vec2 coord = vec2(fragCoord.x, iResolution.y - fragCoord.y);',
  '  vec2 rayPos = vec2(iResolution.x * 1.1, -0.5 * iResolution.y);',
  '  float tiltRad = iTilt * 3.14159265 / 180.0;',
  '  float cs = cos(tiltRad);',
  '  float sn = sin(tiltRad);',
  '  vec2 rel = coord - rayPos;',
  '  vec2 tiltedCoord = vec2(',
  '    rel.x * cs - rel.y * sn,',
  '    rel.x * sn + rel.y * cs',
  '  ) + rayPos;',
  '  float halfSpread = iSpread * 0.275;',
  '  vec2 rayRefDir1 = normalize(vec2(',
  '    cos(0.785398 + halfSpread),',
  '    sin(0.785398 + halfSpread)',
  '  ));',
  '  vec2 rayRefDir2 = normalize(vec2(',
  '    cos(0.785398 - halfSpread),',
  '    sin(0.785398 - halfSpread)',
  '  ));',
  '  vec4 rays1 = vec4(iRayColor1, 1.0) * rayStrength(',
  '    rayPos, rayRefDir1, tiltedCoord, 36.2214, 21.11349, iSpeed',
  '  );',
  '  vec4 rays2 = vec4(iRayColor2, 1.0) * rayStrength(',
  '    rayPos, rayRefDir2, tiltedCoord, 22.3991, 18.0234, iSpeed * 0.2',
  '  );',
  '  vec4 color =',
  '    rays1 * (1.0 - iBlend) * 0.9 +',
  '    rays2 * iBlend * 0.9;',
  '  float distanceToLight = length(',
  '    fragCoord.xy - vec2(rayPos.x, iResolution.y - rayPos.y)',
  '  ) / iResolution.y;',
  '  float brightness =',
  '    iIntensity * 0.4 /',
  '    pow(max(distanceToLight, 0.001), iFalloff);',
  '  color.rgb *= brightness;',
  '  float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));',
  '  color.rgb = mix(vec3(gray), color.rgb, iSaturation);',
  '  color.a = max(color.r, max(color.g, color.b)) * iOpacity;',
  '  gl_FragColor = color;',
  '}',
].join('\n');

export default function SideRays({
  speed = 1,
  rayColor1 = '#ffaa6e',
  rayColor2 = '#96c8ff',
  intensity = 1,
  spread = 1,
  origin = 'top-right',
  tilt = 0,
  saturation = 1,
  blend = 0.78,
  falloff = 2,
  opacity = 1,
  className = '',
}: SideRaysProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const uniformsRef = useRef<SideRaysUniforms | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let disposed = false;
    let visible = true;
    let reduceMotion = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches;
    let animationId: number | undefined;
    let renderer: Renderer | undefined;
    let mesh: Mesh | undefined;

    const stopLoop = () => {
      if (animationId !== undefined) {
        cancelAnimationFrame(animationId);
        animationId = undefined;
      }
    };

    const renderFrame = (time: number) => {
      if (disposed || !renderer || !mesh || !uniformsRef.current) return;
      uniformsRef.current.iTime.value = time * 0.001;
      renderer.render({ scene: mesh });
    };

    const loop = (time: number) => {
      if (disposed || !visible || reduceMotion) {
        animationId = undefined;
        return;
      }
      renderFrame(time);
      animationId = requestAnimationFrame(loop);
    };

    const startLoop = () => {
      stopLoop();
      if (reduceMotion) {
        renderFrame(0);
      } else if (visible) {
        animationId = requestAnimationFrame(loop);
      }
    };

    const resize = () => {
      if (!renderer || !uniformsRef.current) return;
      const width = Math.max(container.clientWidth, 1);
      const height = Math.max(container.clientHeight, 1);
      renderer.dpr = Math.min(window.devicePixelRatio, 2);
      renderer.setSize(width, height);
      uniformsRef.current.iResolution.value = [
        width * renderer.dpr,
        height * renderer.dpr,
      ];
      if (reduceMotion) renderFrame(0);
    };

    const [flipX, flipY] = originToFlip(origin);
    const uniforms: SideRaysUniforms = {
      iTime: { value: 0 },
      iResolution: { value: [1, 1] },
      iSpeed: { value: speed },
      iRayColor1: { value: hexToRgb(rayColor1) },
      iRayColor2: { value: hexToRgb(rayColor2) },
      iIntensity: { value: intensity },
      iSpread: { value: spread },
      iFlipX: { value: flipX },
      iFlipY: { value: flipY },
      iTilt: { value: tilt },
      iSaturation: { value: saturation },
      iBlend: { value: blend },
      iFalloff: { value: falloff },
      iOpacity: { value: opacity },
    };

    try {
      renderer = new Renderer({
        dpr: Math.min(window.devicePixelRatio, 2),
        alpha: true,
      });
      const gl = renderer.gl;
      const geometry = new Triangle(gl);
      const program = new Program(gl, {
        vertex: vertexShader,
        fragment: fragmentShader,
        uniforms,
      });
      mesh = new Mesh(gl, { geometry, program });
      uniformsRef.current = uniforms;
      container.replaceChildren(gl.canvas);
    } catch {
      container.dataset.webgl = 'unavailable';
      try {
        renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext();
      } catch {
        // Context creation may fail before the extension is available.
      }
      return;
    }

    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);

    const intersectionObserver = new IntersectionObserver(
      ([entry]) => {
        visible = entry.isIntersecting;
        if (visible) startLoop();
        else stopLoop();
      },
      { threshold: 0.05 },
    );
    intersectionObserver.observe(container);

    const motionQuery = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    );
    const onMotionChange = (event: MediaQueryListEvent) => {
      reduceMotion = event.matches;
      startLoop();
    };
    motionQuery.addEventListener('change', onMotionChange);

    resize();
    startLoop();

    return () => {
      disposed = true;
      stopLoop();
      resizeObserver.disconnect();
      intersectionObserver.disconnect();
      motionQuery.removeEventListener('change', onMotionChange);
      uniformsRef.current = null;
      try {
        const gl = renderer?.gl;
        gl?.getExtension('WEBGL_lose_context')?.loseContext();
        if (gl?.canvas.parentNode === container) {
          container.removeChild(gl.canvas);
        }
      } catch {
        // WebGL cleanup is best effort after a context loss.
      }
    };
  }, []);

  useEffect(() => {
    const uniforms = uniformsRef.current;
    if (!uniforms) return;
    const [flipX, flipY] = originToFlip(origin);
    uniforms.iSpeed.value = speed;
    uniforms.iRayColor1.value = hexToRgb(rayColor1);
    uniforms.iRayColor2.value = hexToRgb(rayColor2);
    uniforms.iIntensity.value = intensity;
    uniforms.iSpread.value = spread;
    uniforms.iFlipX.value = flipX;
    uniforms.iFlipY.value = flipY;
    uniforms.iTilt.value = tilt;
    uniforms.iSaturation.value = saturation;
    uniforms.iBlend.value = blend;
    uniforms.iFalloff.value = falloff;
    uniforms.iOpacity.value = opacity;
  }, [
    blend,
    falloff,
    intensity,
    opacity,
    origin,
    rayColor1,
    rayColor2,
    saturation,
    speed,
    spread,
    tilt,
  ]);

  return (
    <div
      ref={containerRef}
      aria-hidden="true"
      className={['side-rays-container', className]
        .filter(Boolean)
        .join(' ')}
    />
  );
}
