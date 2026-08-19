# 学生端首页 SideRays 背景与导航融合实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框语法跟踪进度。

**目标：** 移除学生端首页 Galaxy 粒子，接入性能受控的 SideRays 光束背景，并让顶部液态玻璃导航与 Hero 共用连续背景画布。

**架构：** 新建独立 TypeScript SideRays 组件封装 OGL shader、观察器、动画循环和资源清理；首页只负责按已确认参数挂载组件并建立背景层级。Navbar 只调整顶部和滚动两种视觉表面，Hero 通过负顶部偏移和等量补偿延伸到 Header 背后，不改变正文布局与交互。

**技术栈：** React 18、TypeScript 5 strict、OGL 1.0.11、React Router 6、Tailwind CSS 3、Vite 5、真实浏览器 Playwright 验收。

---

## 文件结构

- 创建：educloud-frontend/student-portal/src/components/SideRays/SideRays.tsx
  - 管理 OGL、IntersectionObserver、ResizeObserver、减少动态效果和完整清理。
- 创建：educloud-frontend/student-portal/src/components/SideRays/SideRays.css
  - 定义纯装饰全尺寸容器和 canvas 几何。
- 修改：educloud-frontend/student-portal/src/pages/Home.tsx
  - 解除 Galaxy 引用，挂载 SideRays，建立统一背景和导航重叠几何。
- 修改：educloud-frontend/student-portal/src/components/Navbar.tsx
  - 增加稳定测试标记并调整顶部玻璃底色，保留滚动与移动菜单逻辑。
- 参考：docs/superpowers/specs/2026-08-19-student-home-siderays-design.md

## 工作区保护

Home.tsx、Navbar.tsx 已有用户未提交修改，components/Galaxy/ 也是用户未跟踪内容。实施时必须：

- 先保存目标 diff，始终在当前内容上应用最小补丁；
- 禁止 reset、checkout、clean、stash、批量格式化和目录删除；
- 不修改 package.json 与 pnpm-lock.yaml，ogl@1.0.11 已存在；
- 只解除首页 Galaxy 引用，不删除 Galaxy 源文件；
- 不提交会混入用户既有差异的源代码；
- 最终交付区分本任务差异与用户原有差异。

### 任务 1：记录基线并建立真实缺陷红灯

**文件：**
- 修改：无
- 测试：真实浏览器首页背景与 Header 几何

- [ ] **步骤 1：记录目标文件和依赖基线**

从仓库根运行：

~~~powershell
$targets = @('educloud-frontend/student-portal/src/pages/Home.tsx','educloud-frontend/student-portal/src/components/Navbar.tsx','educloud-frontend/student-portal/src/components/SideRays','educloud-frontend/student-portal/src/components/Galaxy','educloud-frontend/student-portal/package.json','educloud-frontend/student-portal/pnpm-lock.yaml')
git status --short -- $targets
git diff -- 'educloud-frontend/student-portal/src/pages/Home.tsx' 'educloud-frontend/student-portal/src/components/Navbar.tsx'
Select-String -LiteralPath 'educloud-frontend/student-portal/package.json' -Pattern '"ogl"'
~~~

预期：Home 和 Navbar 显示已有修改；Galaxy 保持未跟踪；package.json 已声明 ogl，无需安装。

- [ ] **步骤 2：启动学生端开发服务器**

当前 Windows 环境的 pnpm 会因未批准 esbuild 构建脚本而中止，使用已有本地依赖运行等价 npm 脚本：

~~~powershell
npm run dev -- --host 127.0.0.1 --port 4179
~~~

工作目录：educloud-frontend/student-portal。

预期：Vite 输出 http://127.0.0.1:4179/ 并保持运行。

- [ ] **步骤 3：执行背景替换红灯**

使用 Playwright：

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  await page.waitForLoadState('networkidle');
  const result = await page.evaluate(() => ({
    galaxy: document.querySelectorAll('.galaxy-container').length,
    sideRays: document.querySelectorAll('.side-rays-container').length,
    canvases: document.querySelectorAll('.side-rays-container canvas').length,
  }));
  if (result.galaxy !== 0) {
    throw new Error('Galaxy background still mounted: ' + result.galaxy);
  }
  if (result.sideRays !== 1 || result.canvases !== 1) {
    throw new Error('SideRays background missing: ' + JSON.stringify(result));
  }
}
~~~

预期：FAIL，错误包含 Galaxy background still mounted: 1。

- [ ] **步骤 4：执行导航背景连续性红灯**

使用 Playwright：

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  const result = await page.evaluate(() => {
    const header = document.querySelector('header');
    const hero = document.querySelector('main section');
    if (!header || !hero) throw new Error('header or hero missing');
    return {
      headerTop: header.getBoundingClientRect().top,
      heroTop: hero.getBoundingClientRect().top,
    };
  });
  if (result.heroTop > result.headerTop + 1) {
    throw new Error('Hero background does not extend behind header: ' +
      JSON.stringify(result));
  }
}
~~~

预期：FAIL；当前 Hero 从 Header 之后开始，存在独立顶部底色带。

### 任务 2：实现可清理、可暂停的 SideRays

**文件：**
- 创建：educloud-frontend/student-portal/src/components/SideRays/SideRays.tsx
- 创建：educloud-frontend/student-portal/src/components/SideRays/SideRays.css
- 测试：TypeScript 类型检查和浏览器生命周期

- [ ] **步骤 1：创建容器 CSS**

~~~css
.side-rays-container {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  pointer-events: none;
}

.side-rays-container canvas {
  display: block;
  width: 100%;
  height: 100%;
}
~~~

- [ ] **步骤 2：创建组件类型和纯转换函数**

SideRays.tsx 必须从 React 导入 useEffect、useRef，从 ogl 直接导入 Mesh、Program、Renderer、Triangle，并导入同目录 CSS。

~~~tsx
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
~~~

- [ ] **步骤 3：移植 React Bits shader 和 uniform**

在组件外定义以下完整 shader。uniform 名称必须与 SideRaysUniforms 完全一致，不能在页面层散落 shader 参数。

~~~tsx
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
  '    0.0, 1.0) *',
  '    clamp((iResolution.x - length(sourceToCoord)) / iResolution.x, 0.5, 1.0);',
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
  '  vec2 tiltedCoord = vec2(rel.x * cs - rel.y * sn, rel.x * sn + rel.y * cs) + rayPos;',
  '  float halfSpread = iSpread * 0.275;',
  '  vec2 rayRefDir1 = normalize(vec2(cos(0.785398 + halfSpread), sin(0.785398 + halfSpread)));',
  '  vec2 rayRefDir2 = normalize(vec2(cos(0.785398 - halfSpread), sin(0.785398 - halfSpread)));',
  '  vec4 rays1 = vec4(iRayColor1, 1.0) * rayStrength(rayPos, rayRefDir1, tiltedCoord, 36.2214, 21.11349, iSpeed);',
  '  vec4 rays2 = vec4(iRayColor2, 1.0) * rayStrength(rayPos, rayRefDir2, tiltedCoord, 22.3991, 18.0234, iSpeed * 0.2);',
  '  vec4 color = rays1 * (1.0 - iBlend) * 0.9 + rays2 * iBlend * 0.9;',
  '  float distanceToLight = length(fragCoord.xy - vec2(rayPos.x, iResolution.y - rayPos.y)) / iResolution.y;',
  '  float brightness = iIntensity * 0.4 / pow(max(distanceToLight, 0.001), iFalloff);',
  '  color.rgb *= brightness;',
  '  float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));',
  '  color.rgb = mix(vec3(gray), color.rgb, iSaturation);',
  '  color.a = max(color.r, max(color.g, color.b)) * iOpacity;',
  '  gl_FragColor = color;',
  '}',
].join('\n');
~~~

在初始化 Effect 中创建以下 uniform：

~~~tsx
const [flipX, flipY] = originToFlip(origin);
const uniforms: SideRaysUniforms = {
  iTime: { value: 0 },
  iResolution: { value: [1, 1] as [number, number] },
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
~~~

默认值保持组件通用默认，不写死首页设计值：

~~~tsx
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
className = ''
~~~

- [ ] **步骤 4：实现单次初始化、暂停和清理**

初始化 Effect 必须只创建一个 Renderer。实现以下生命周期结构：

~~~tsx
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
    if (reduceMotion) renderFrame(0);
    else if (visible) animationId = requestAnimationFrame(loop);
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
      // Context loss 后清理只做 best effort。
    }
  };
}, []);
~~~

初始化代码不得使用异步 setTimeout，避免组件卸载后再次插入 canvas。

- [ ] **步骤 5：实现属性更新和装饰语义**

使用独立 Effect 更新全部 uniform，不因属性变化重建 WebGL。返回值必须是：

~~~tsx
return (
  <div
    ref={containerRef}
    aria-hidden="true"
    className={['side-rays-container', className]
      .filter(Boolean)
      .join(' ')}
  />
);
~~~

属性 Effect 的依赖必须包含 blend、falloff、intensity、opacity、origin、两种颜色、saturation、speed、spread 和 tilt。

- [ ] **步骤 6：运行组件类型检查**

~~~powershell
npm run typecheck
~~~

工作目录：educloud-frontend/student-portal。

预期：退出码 0。若 OGL 声明存在签名差异，只按安装版本做最窄类型调整，不使用全局 any。

### 任务 3：接入首页并融合导航

**文件：**
- 修改：educloud-frontend/student-portal/src/pages/Home.tsx
- 修改：educloud-frontend/student-portal/src/components/Navbar.tsx
- 测试：任务 1 的两个浏览器红灯

- [ ] **步骤 1：替换首页背景导入**

删除 Galaxy import，添加：

~~~tsx
import SideRays from '@/components/SideRays/SideRays';
~~~

不删除 components/Galaxy/。

- [ ] **步骤 2：建立连续 Hero 背景**

Hero 使用以下外层和参数：

~~~tsx
<section
  data-home-hero
  className="relative -mt-[68px] overflow-hidden border-b border-ink-100 bg-paper pt-[68px] dark:border-ink-800 dark:bg-ink-900"
>
  <SideRays
    speed={1.1}
    rayColor1="#EAB308"
    rayColor2="#96c8ff"
    intensity={1.25}
    spread={1.8}
    origin="top-right"
    tilt={0}
    saturation={1.2}
    blend={0.72}
    falloff={1.7}
    opacity={0.62}
  />
  <div
    aria-hidden="true"
    className="pointer-events-none absolute inset-0 z-10 bg-gradient-to-b from-paper/20 via-paper/35 to-paper/95 dark:from-ink-900/20 dark:via-ink-900/35 dark:to-ink-900/95"
  />
  <div
    aria-hidden="true"
    className="pointer-events-none absolute inset-0 z-10 bg-gradient-to-br from-transparent via-transparent to-amber-50/20 dark:to-indigo-900/10"
  />
  <div className="relative z-20 mx-auto max-w-7xl px-4 py-20 sm:px-6 md:py-32 lg:px-8">
~~~

只替换到现有内容容器的起始标签；其下现有 grid、文案、按钮、进度卡及闭合标签保持原样。负偏移和顶部补偿必须相等，保证只移动背景，不移动正文起点。

- [ ] **步骤 3：调整顶部导航玻璃表面**

Header 增加 data-site-header，内层表面增加 data-navbar-surface。未滚动状态替换为：

~~~tsx
!scrolled && [
  'mx-auto h-14 max-w-7xl rounded-full',
  'border border-white/75 bg-paper/55 backdrop-blur-2xl',
  'dark:border-white/10 dark:bg-ink-900/55',
  'shadow-lg shadow-ink-900/[0.06] dark:shadow-black/20',
]
~~~

滚动状态继续使用现有全宽 bg-paper/90 和 dark:bg-ink-900/90。禁止修改 SCROLL_THRESHOLD、滚动 RAF、导航链接、搜索和移动菜单。

- [ ] **步骤 4：重跑两个原始红灯**

执行任务 1 步骤 3、步骤 4。

预期：Galaxy 为 0；SideRays 容器和 canvas 均为 1；Hero 顶边覆盖 Header 区域。

- [ ] **步骤 5：验证正文没有被导航遮挡**

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  const result = await page.evaluate(() => {
    const header = document.querySelector('[data-site-header]');
    const hero = document.querySelector('[data-home-hero]');
    const label = hero?.querySelector('.section-label');
    if (!header || !hero || !label) throw new Error('required nodes missing');
    return {
      heroTop: hero.getBoundingClientRect().top,
      headerTop: header.getBoundingClientRect().top,
      headerBottom: header.getBoundingClientRect().bottom,
      labelTop: label.getBoundingClientRect().top,
    };
  });
  if (result.heroTop > result.headerTop + 1) {
    throw new Error('background seam remains: ' + JSON.stringify(result));
  }
  if (result.labelTop < result.headerBottom + 32) {
    throw new Error('Hero content is covered: ' + JSON.stringify(result));
  }
}
~~~

预期：PASS。

### 任务 4：验证生命周期、滚动状态和减少动态效果

**文件：**
- 修改：仅在暴露问题时最小修改上述目标文件
- 测试：真实浏览器

- [ ] **步骤 1：验证路由切换不残留 canvas**

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  await page.locator('.side-rays-container canvas').waitFor();
  if (await page.locator('.side-rays-container canvas').count() !== 1) {
    throw new Error('initial canvas count is not one');
  }
  await page.goto('http://127.0.0.1:4179/courses');
  if (await page.locator('.side-rays-container').count() !== 0) {
    throw new Error('SideRays leaked outside home');
  }
  await page.goto('http://127.0.0.1:4179/');
  await page.locator('.side-rays-container canvas').waitFor();
  if (await page.locator('.side-rays-container canvas').count() !== 1) {
    throw new Error('SideRays duplicated after remount');
  }
}
~~~

预期：PASS。

- [ ] **步骤 2：验证导航顶部和滚动状态**

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  const surface = page.locator('[data-navbar-surface]');
  const top = await surface.evaluate((node) => {
    const style = getComputedStyle(node);
    return {
      borderRadius: style.borderRadius,
      backdropFilter: style.backdropFilter,
    };
  });
  if (top.borderRadius === '0px' || top.backdropFilter === 'none') {
    throw new Error('top glass state mismatch: ' + JSON.stringify(top));
  }
  await page.evaluate(() => window.scrollTo(0, 180));
  await page.waitForTimeout(550);
  const scrolled = await surface.evaluate((node) => {
    const style = getComputedStyle(node);
    return { borderRadius: style.borderRadius, height: style.height };
  });
  if (scrolled.borderRadius !== '0px' || scrolled.height !== '64px') {
    throw new Error('scrolled state mismatch: ' + JSON.stringify(scrolled));
  }
}
~~~

预期：PASS。

- [ ] **步骤 3：验证减少动态效果为静态首帧**

~~~ts
async (page) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('http://127.0.0.1:4179/');
  const canvas = page.locator('.side-rays-container canvas');
  await canvas.waitFor();
  const first = await canvas.screenshot();
  await page.waitForTimeout(500);
  const second = await canvas.screenshot();
  if (!first.equals(second)) {
    throw new Error('SideRays kept animating under reduced motion');
  }
}
~~~

预期：PASS。

- [ ] **步骤 4：验证纯装饰语义和按钮交互**

~~~ts
async (page) => {
  await page.goto('http://127.0.0.1:4179/');
  const rays = page.locator('.side-rays-container');
  if (await rays.getAttribute('aria-hidden') !== 'true') {
    throw new Error('SideRays exposed to assistive technology');
  }
  const pointerEvents = await rays.evaluate(
    (node) => getComputedStyle(node).pointerEvents,
  );
  if (pointerEvents !== 'none') {
    throw new Error('SideRays intercepts pointer events');
  }
  await page.getByRole('link', { name: '浏览全部课程' }).click();
  await page.waitForURL('**/courses');
}
~~~

预期：PASS。

### 任务 5：完成响应式、控制台与工程验收

**文件：**
- 修改：仅在验收暴露问题时最小修复目标文件
- 测试：真实浏览器、TypeScript、Vite、Git

- [ ] **步骤 1：验证桌面端**

在导航前注册 console error 监听，使用 1440 × 900 打开首页，断言：

~~~ts
const result = await page.evaluate(() => ({
  scrollWidth: document.documentElement.scrollWidth,
  clientWidth: document.documentElement.clientWidth,
  canvases: document.querySelectorAll('.side-rays-container canvas').length,
  galaxy: document.querySelectorAll('.galaxy-container').length,
}));
~~~

预期：无横向溢出、canvases 为 1、galaxy 为 0、新增 console error 为 0。项目原有 React Router warning 必须单独如实记录。

- [ ] **步骤 2：验证移动端**

使用 390 × 844，断言页面无横向溢出，Hero section-label 顶边至少位于 Header 底边下方 24px，移动菜单仍可打开并点击。

- [ ] **步骤 3：运行类型检查和生产构建**

工作目录：educloud-frontend/student-portal。

~~~powershell
npm run typecheck
npm run build
~~~

预期：两个命令退出码均为 0，Vite 成功生成 dist。

- [ ] **步骤 4：检查差异与残留引用**

~~~powershell
$targets = @('educloud-frontend/student-portal/src/components/SideRays/SideRays.tsx','educloud-frontend/student-portal/src/components/SideRays/SideRays.css','educloud-frontend/student-portal/src/pages/Home.tsx','educloud-frontend/student-portal/src/components/Navbar.tsx')
git diff --check -- $targets
Select-String -LiteralPath 'educloud-frontend/student-portal/src/pages/Home.tsx' -Pattern 'Galaxy|components/Galaxy'
git status --short -- $targets 'educloud-frontend/student-portal/src/components/Galaxy' 'educloud-frontend/student-portal/package.json' 'educloud-frontend/student-portal/pnpm-lock.yaml'
~~~

预期：diff check 退出码 0；Home 的 Galaxy 扫描无输出；Galaxy 目录仍在；依赖文件没有本任务新增差异。

- [ ] **步骤 5：自审并停止服务器**

复核 Home 只更换背景和层级，Navbar 只增加标记及玻璃样式，用户现有头像、暗色模式、胶囊导航、滚动 RAF 均保留。向 Vite 会话发送 Ctrl+C。

由于 Home 和 Navbar 在任务开始前已经是脏文件，而新增 SideRays 必须与它们一起工作，源代码整体保持未暂存、未提交。最终交付必须列出实际浏览器结果、工程命令退出码、依赖未变化、Galaxy 目录保留以及未提交边界。
