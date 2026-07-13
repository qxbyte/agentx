import { BookOpen } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'

function Code({ children }: { children: string }) {
  return (
    <pre className="ax-scroll m-0 overflow-x-auto rounded-lg border border-[var(--ax-border-subtle)] bg-[var(--ax-code-bg)] px-3 py-2.5 font-mono text-[12px] leading-relaxed">
      {children}
    </pre>
  )
}

function H({ children }: { children: React.ReactNode }) {
  return <h3 className="mb-1.5 mt-5 text-sm font-semibold text-foreground">{children}</h3>
}

function P({ children }: { children: React.ReactNode }) {
  return <p className="my-1.5 text-[13px] leading-relaxed text-muted-foreground">{children}</p>
}

/** 外部知识库对接文档：任何知识库按此模板实现三个 HTTP API 即可被 AgentX 接入检索。 */
export default function ExternalKbGuide() {
  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button variant="outline">
          <BookOpen className="size-4" />
          对接文档
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-[680px]">
        <SheetHeader className="border-b border-border">
          <SheetTitle>外部知识库对接文档</SheetTitle>
          <SheetDescription>
            任何知识库系统实现以下三个 HTTP API，即可被 AgentX 接入并参与对话检索
          </SheetDescription>
        </SheetHeader>
        <div className="ax-scroll flex-1 overflow-y-auto px-5 pb-10 pt-2">
          <H>工作原理</H>
          <P>
            检索时 AgentX 用<b>本平台配置的默认向量模型</b>把用户问题向量化，携带查询向量调用你的
            向量查询 API；你的系统在自己的向量索引中做相似度检索并返回文本片段，AgentX
            将其与本地知识库结果按相似度合并后交给模型作答，并在回答下方展示引用来源。
          </P>
          <P>
            因此有一条硬性前提：<b>你的文档向量必须与 AgentX 的默认 EMBEDDING 模型出自同一个
            模型</b>（如 text-embedding-v4）。向量空间不同，相似度没有意义，检索必然失效。
            接入表单的「测试连接」会自动比对两侧模型并给出提醒。
          </P>

          <H>为什么必须指定 vault（仓库标识）</H>
          <P>
            一个知识库服务可能承载多个内容仓库（如多个 Obsidian vault）。不同仓库内容非同类，
            混在一起检索会互相污染。因此 info 与 search 都以 vault 参数锁定单一仓库；
            AgentX 每条接入配置只绑定一个 vault。
          </P>

          <H>API 一：心跳（存活探测）</H>
          <Code>{`GET /api/external-kb/heartbeat

响应 200:
{
  "ok": true,              // 必须为 true
  "service": "your-name",  // 服务名，展示用
  "templateVersion": 1     // 模板版本，当前为 1
}`}</Code>
          <P>用于「测试连接」的第一步。任何非 200 或 ok!=true 视为服务不可用。</P>

          <H>API 二：库信息</H>
          <Code>{`GET /api/external-kb/info            // 不带参数：列出全部仓库
GET /api/external-kb/info?vault=<id> // 指定仓库：单库详情

响应 200（不带 vault）:
{
  "service": "your-name",
  "vaults": [
    {
      "vaultId": "1fhwq68",        // 仓库唯一标识（检索时使用）
      "name": "Notes",             // 展示名
      "docCount": 335,             // 文档数
      "chunkCount": 5726,          // 已索引片段数
      "embedding": {
        "model": "text-embedding-v4", // 建索引用的 embedding 模型名（用于一致性比对）
        "dims": 1024                  // 向量维度
      }
    }
  ]
}

响应 200（带 vault）: 单个上述对象
响应 404: { "error": "未知 vault: xxx" }`}</Code>
          <P>
            AgentX 的「获取仓库列表」按钮调用不带参数的形式，用户点选后自动填入 vaultId；
            「测试连接」用带 vault 的形式取 embedding 模型做一致性比对。
            <b>embedding.model 请如实返回建索引时的模型名</b>，未建索引可返回 null / dims 0。
          </P>

          <H>API 三：向量查询（核心）</H>
          <Code>{`POST /api/external-kb/search
Content-Type: application/json

请求体:
{
  "vault": "1fhwq68",       // 必填，仓库标识
  "vector": [0.01, -0.03, ...], // 必填，查询向量（AgentX 已完成向量化）
  "topK": 5,                // 可选，默认 5，建议上限 50
  "threshold": 0.3          // 可选，相似度阈值，低于此分的命中不返回
}

响应 200:
{
  "hits": [
    {
      "text": "片段原文……",      // 必填，注入模型上下文的文本
      "score": 0.79,             // 必填，相似度（0~1，余弦）
      "title": "笔记标题",        // 建议提供，引用来源展示用
      "path": "dir/note.md"      // 建议提供，来源定位
    }
  ],
  "indexed": true,
  "embedding": { "model": "text-embedding-v4", "dims": 1024 }
}

约定的错误响应:
400 { "error": "必须指定 vault（...）" }        // 缺 vault
400 { "error": "向量维度不匹配：收到 1536，本库为 1024（模型 xxx）..." }
404 { "error": "未知 vault: xxx" }
未建索引时不要报错，返回: { "hits": [], "indexed": false }`}</Code>
          <P>实现要点：</P>
          <ul className="my-1 list-disc space-y-1 pl-5 text-[13px] text-muted-foreground">
            <li>
              <b>维度校验</b>：vector 长度 ≠ 本库维度时返回 400 并带清晰文案——这是两侧
              embedding 模型不一致最直接的信号。
            </li>
            <li>
              <b>向量归一化</b>：建议服务端对收到的查询向量做一次归一化再算点积/余弦，
              容忍调用方未归一化。
            </li>
            <li>
              <b>fail-open</b>：单次查询失败只影响本次；AgentX 侧也会兜底——你的服务超时或
              报错时该库结果被跳过、对话不中断。
            </li>
            <li>
              <b>无鉴权假设</b>：当前模板面向内网/本机部署，未定义鉴权头；暴露公网请自行
              加反向代理鉴权。
            </li>
          </ul>

          <H>调用时序</H>
          <Code>{`用户提问
  → AgentX: embed(question) 得到 query 向量（默认 EMBEDDING 模型）
  → AgentX → 你的服务: POST /search { vault, vector, topK, threshold }
  → 你的服务: 在该 vault 的向量索引中余弦检索 → 返回 hits
  → AgentX: 与本地知识库命中按 score 降序合并 → 注入模型上下文
  → 回答下方展示引用来源（title 会以「库名 · title」形式呈现）`}</Code>

          <H>自测（curl）</H>
          <Code>{`# 1. 心跳
curl http://localhost:4777/api/external-kb/heartbeat

# 2. 列出仓库、确认 vaultId 与 embedding 模型
curl http://localhost:4777/api/external-kb/info

# 3. 用一个假向量试查询（长度须等于本库 dims；此处示意 1024 维全 0.01）
python3 -c "import json;print(json.dumps({'vault':'<vaultId>','vector':[0.01]*1024,'topK':3}))" \\
  | curl -s -X POST http://localhost:4777/api/external-kb/search \\
      -H 'Content-Type: application/json' -d @-`}</Code>

          <H>参考实现</H>
          <P>
            Notopolis（Fastify/TypeScript）的完整实现约 100 行：
            <code className="mx-1 rounded bg-[var(--ax-code-bg)] px-1.5 py-0.5 font-mono text-[12px]">
              notopolis/src/server/rag/external.ts
            </code>
            ，含路由测试
            <code className="mx-1 rounded bg-[var(--ax-code-bg)] px-1.5 py-0.5 font-mono text-[12px]">
              tests/external-kb.test.ts
            </code>
            ，可直接照抄结构。
          </P>

          <H>接入清单（Checklist）</H>
          <ul className="my-1 list-disc space-y-1 pl-5 text-[13px] text-muted-foreground">
            <li>三个端点按上述路径与 JSON 结构实现（路径可自定义，接入表单可改）</li>
            <li>文档已用与 AgentX 默认 EMBEDDING 一致的模型建好向量索引</li>
            <li>info 如实返回 embedding.model / dims</li>
            <li>search 做维度校验与（建议）归一化；未建索引返回空 hits 不报错</li>
            <li>AgentX 设置页 → 接入外部知识库 → 填服务地址 → 获取仓库列表点选 → 测试连接无警告 → 保存启用</li>
          </ul>
        </div>
      </SheetContent>
    </Sheet>
  )
}
