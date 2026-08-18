import type { AssistantReply } from './types';

const endpoint = import.meta.env.VITE_AI_ASSISTANT_ENDPOINT?.trim();

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const buildMockReply = (question: string) => {
  const normalized = question.toLowerCase();

  if (/数学|高数|极限|导数|积分/.test(normalized)) {
    return '复习高等数学可以按“概念理解 → 公式整理 → 典型例题 → 限时训练”四步推进。建议先画出极限、导数和积分之间的知识关系，再分别整理常见题型；每天完成一组基础题和一道综合题，并把错误原因记录到错题本。';
  }
  if (/代码|编程|python|react|java|spring/.test(normalized)) {
    return '建议先把问题缩小到一个可以运行的最小示例，再依次检查输入、状态变化和输出。学习编程时可以采用“读一段、写一遍、改一个条件、解释结果”的循环，并为关键函数补充测试。';
  }
  if (/考试|复习|备考|计划/.test(normalized)) {
    return '可以先根据考试范围列出知识清单，并按“掌握、模糊、未掌握”分级。优先补齐高频且薄弱的部分，最后两天以模拟题和错题回顾为主，同时保留固定休息时间。';
  }

  return '我建议先明确你的课程、当前进度和最困惑的知识点。你可以把具体题目、代码片段或复习目标发给我，我会帮你拆解成更容易执行的学习步骤。';
};

const parseRemoteReply = (data: unknown): string => {
  if (
    typeof data === 'object'
    && data !== null
    && 'content' in data
    && typeof data.content === 'string'
    && data.content.trim()
  ) {
    return data.content.trim();
  }
  throw new Error('AI 助教返回了无法识别的数据');
};

export const assistantClient = {
  mode: endpoint ? 'remote' as const : 'mock' as const,
  async ask(question: string): Promise<AssistantReply> {
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion) throw new Error('请输入学习问题');

    if (!endpoint) {
      await wait(500);
      return { content: buildMockReply(trimmedQuestion), mode: 'mock' };
    }

    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: trimmedQuestion }),
    });
    if (!response.ok) throw new Error(`AI 助教服务暂时不可用（${response.status}）`);

    return { content: parseRemoteReply(await response.json()), mode: 'remote' };
  },
};
