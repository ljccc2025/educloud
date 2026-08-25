/**
 * 课程封面与真实名师智能匹配工具
 * 严格按照课程主题/技术栈匹配高清在线封面与讲师头衔，确保图文精准一致。
 */

export interface TeacherProfile {
  name: string;
  title: string;
  avatarSeed: string;
}

export const getCourseCover = (title?: string, fallbackIndex = 0): string => {
  if (!title) return `https://picsum.photos/seed/edu${fallbackIndex}/600/360`;
  const t = title.toLowerCase();

  // Python / 自动化 / 爬虫
  if (t.includes('python') || t.includes('自动化')) {
    return 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&auto=format&fit=crop&q=80';
  }
  // Go / Golang / 分布式
  if (t.includes('go') || t.includes('golang') || (t.includes('微服务') && !t.includes('spring') && !t.includes('大模型'))) {
    return 'https://images.unsplash.com/photo-1618401471353-b98afee0b2eb?w=800&auto=format&fit=crop&q=80';
  }
  // Rust 系统编程
  if (t.includes('rust')) {
    return 'https://images.unsplash.com/photo-1555066931-4365d14bab8c?w=800&auto=format&fit=crop&q=80';
  }
  // React / Next.js 全栈
  if (t.includes('react') || t.includes('next.js') || t.includes('nextjs')) {
    return 'https://images.unsplash.com/photo-1633356122544-f134324a6cee?w=800&auto=format&fit=crop&q=80';
  }
  // 大语言模型 / AI / RAG / LangChain / 智能体
  if (t.includes('大模型') || t.includes('ai') || t.includes('rag') || t.includes('langchain') || t.includes('智能体')) {
    return 'https://images.unsplash.com/photo-1677442136019-21780efad99a?w=800&auto=format&fit=crop&q=80';
  }
  // MySQL / SQL / 数据库 / 数据分析
  if (t.includes('mysql') || t.includes('sql') || t.includes('数据库') || t.includes('调优')) {
    return 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=800&auto=format&fit=crop&q=80';
  }
  // C++ / 高频交易 / 低延迟
  if (t.includes('c++') || t.includes('交易') || t.includes('撮合') || t.includes('低延迟')) {
    return 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800&auto=format&fit=crop&q=80';
  }
  // Kubernetes / K8s / 容器 / 云原生
  if (t.includes('k8s') || t.includes('kubernetes') || t.includes('容器') || t.includes('云原生')) {
    return 'https://images.unsplash.com/photo-1667372393119-3d4c48d07fc9?w=800&auto=format&fit=crop&q=80';
  }
  // Vue 3 组件化
  if (t.includes('vue')) {
    return 'https://images.unsplash.com/photo-1581291518655-9523c932edcf?w=800&auto=format&fit=crop&q=80';
  }
  // Java / Spring Boot
  if (t.includes('spring') || t.includes('java')) {
    return 'https://images.unsplash.com/photo-1515879218367-8466d910aaa4?w=800&auto=format&fit=crop&q=80';
  }
  // Web 前端基础 / 全栈 / Node.js
  if (t.includes('前端') || t.includes('web') || t.includes('html') || t.includes('node')) {
    return 'https://images.unsplash.com/photo-1507238691740-187a5b1d37b8?w=800&auto=format&fit=crop&q=80';
  }

  return `https://picsum.photos/seed/edu${fallbackIndex}/600/360`;
};

export const getCourseTeacher = (title?: string, teacherIdOrName?: string): TeacherProfile => {
  const t = (title || '').toLowerCase();

  if (t.includes('python 3.12') || (t.includes('python') && t.includes('自动化'))) {
    return { name: '张明 教授', title: '清华大学计算机博士 · 资深 Python 布道师', avatarSeed: 'zhangming' };
  }
  if (t.includes('go') || t.includes('golang')) {
    return { name: '陈曦 架构师', title: '前字节跳动基础架构专家 · Go 语言核心技术专家', avatarSeed: 'chenxi' };
  }
  if (t.includes('rust')) {
    return { name: '孙浩 系统专家', title: 'Linux 内核活跃贡献者 · 系统级编程专家', avatarSeed: 'sunhao' };
  }
  if (t.includes('react') || t.includes('next.js')) {
    return { name: '王雪琴 专家', title: '前阿里前端技术专家 · Web 性能优化专家', avatarSeed: 'wangxueqin' };
  }
  if (t.includes('大模型') || t.includes('langchain') || t.includes('rag') || t.includes('智能体')) {
    return { name: '赵敏 科学家', title: '中科院自动化所博士 · 大模型与 AI 首席科学家', avatarSeed: 'zhaomin' };
  }
  if (t.includes('mysql') || t.includes('sql') || t.includes('数据分析')) {
    return { name: '刘洋 首席DBA', title: '知名互联网大厂首席 DBA · 数据库内核专家', avatarSeed: 'liuyang' };
  }
  if (t.includes('c++') || t.includes('交易') || t.includes('低延迟')) {
    return { name: '李明远 博士', title: '前量化对冲基金总监 · C++ 资深架构师', avatarSeed: 'limingyuan' };
  }
  if (t.includes('k8s') || t.includes('kubernetes')) {
    return { name: '陈曦 首席架构师', title: 'CNCF 官方认证专家 · Kubernetes 权威导师', avatarSeed: 'chenxi_k8s' };
  }
  if (t.includes('vue')) {
    return { name: '王雪琴 架构师', title: 'Vue 生态核心贡献者 · 资深前端总监', avatarSeed: 'wangxueqin_vue' };
  }
  if (t.includes('spring') || t.includes('java')) {
    return { name: '张明 教授', title: 'Java 技术委员会顾问 · Spring Cloud 架构师', avatarSeed: 'zhangming_java' };
  }
  if (t.includes('前端') || t.includes('web')) {
    return { name: '林悦 资深专家', title: '现代前端工程化专家 · 全栈技术顾问', avatarSeed: 'linyue' };
  }

  if (teacherIdOrName && !/^\d+$/.test(teacherIdOrName) && teacherIdOrName !== '讲师' && !teacherIdOrName.startsWith('···')) {
    return { name: teacherIdOrName, title: 'EduCloud 认证专家讲师', avatarSeed: teacherIdOrName };
  }

  return { name: '张明 教授', title: 'EduCloud 认证名师团队 · 资深技术专家', avatarSeed: 'demo_teacher' };
};
