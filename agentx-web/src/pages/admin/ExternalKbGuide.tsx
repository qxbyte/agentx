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
            检索时 AgentX 把用户问题（经多轮上下文化与多查询扩展后的<b>查询文本</b>）携 vault
            发给你的查询 API；你的系统用<b>自己的 embedding 模型</b>把文本向量化、在自己的向量
            索引里做相似检索、返回文本片段。AgentX 将各查询、各来源的命中做 RRF 融合（并可选
            重排）后交给模型作答，并在回答下方展示引用来源。
          </P>
          <P>
            <b>关键：你无需与 AgentX 用同一个 embedding 模型</b>。你用自己的模型向量化查询——
            查询向量与你的索引天然同空间。检索的「质量层」（多轮改写、多查询扩展、RRF 融合、
            重排）全部由 AgentX 承担，<b>你只需实现最小能力：把一段查询文本向量化 + 向量检索 +
            返回片段</b>。不要求你实现混合检索或重排，任何有「向量库 + embedding 模型」的系统
            几十行即可接入。
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
  "templateVersion": 2     // 模板版本，当前为 2（v2 查询载荷为文本）
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
        "model": "your-embed-model", // 建索引用的 embedding 模型名（仅展示，无需与 AgentX 一致）
        "dims": 1024                 // 向量维度（仅展示）
      }
    }
  ]
}

响应 200（带 vault）: 单个上述对象
响应 404: { "error": "未知 vault: xxx" }`}</Code>
          <P>
            AgentX 的「获取仓库列表」按钮调用不带参数的形式，用户点选后自动填入 vaultId；
            「测试连接」用带 vault 的形式确认库可达、是否已建索引。embedding 字段仅用于展示，
            <b>不再要求与 AgentX 的模型一致</b>；未建索引可返回 chunkCount 0 / model null。
          </P>

          <H>API 三：文本查询（核心）</H>
          <Code>{`POST /api/external-kb/search
Content-Type: application/json

请求体:
{
  "vault": "1fhwq68",            // 必填，仓库标识
  "query": "用户的自然语言查询", // 必填，查询文本（由你的系统向量化）
  "topK": 5,                     // 可选，默认 5，建议上限 50
  "threshold": 0.3               // 可选，相似度阈值，低于此分的命中不返回
}

响应 200:
{
  "hits": [
    {
      "text": "片段原文……",      // 必填，注入模型上下文的文本
      "score": 0.79,             // 必填，相似度（0~1，余弦）
      "title": "笔记标题",        // 建议提供，引用来源展示用
      "path": "dir/note.md",     // 建议提供，来源定位（引用卡片展示文件路径）
      "headings": ["架构", "检索层"], // 可选，片段所属章节链（展示为 架构 › 检索层）
      "startLine": 120,          // 可选，片段在原文中的起始行（1-based）
      "endLine": 168             // 可选，结束行；与 startLine 一起展示为「第 120–168 行」
    }
  ],
  "indexed": true,
  "embedding": { "model": "your-embed-model", "dims": 1024 } // 可选，展示用
}

约定的错误响应:
400 { "error": "必须指定 vault（...）" }        // 缺 vault
400 { "error": "必须提供查询文本 query" }        // 缺 query
404 { "error": "未知 vault: xxx" }
409 { "error": "索引模型与当前配置不一致，请重新入库" } // 可选，内部自保
未建索引时不要报错，返回: { "hits": [], "indexed": false }`}</Code>
          <P>实现要点：</P>
          <ul className="my-1 list-disc space-y-1 pl-5 text-[13px] text-muted-foreground">
            <li>
              <b>用自己的模型向量化 query</b>：必须与你建索引时用的模型一致（你自己的内部
              一致性）。若配置换了 embedding 模型却没重建索引，建议直接返回 409，别返回错误结果。
            </li>
            <li>
              <b>只做裸向量检索即可</b>：不要求实现混合检索/重排——多路召回与融合由 AgentX
              以多查询 + RRF 完成，你的检索质量不必"聪明"。
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
  → AgentX: 多轮上下文化 + 多查询扩展（原问题 + N 个不同角度变体）
  → AgentX → 你的服务: 每个查询变体各发一次
            POST /search { vault, query, topK, threshold }
  → 你的服务: embed(query)（你自己的模型）→ 向量检索 → 返回 hits
  → AgentX: 跨查询/跨来源 RRF 融合（+可选 rerank）→ 注入模型上下文
  → 回答下方展示引用来源（title 以「库名 · title」呈现）`}</Code>

          <H>自测（curl）</H>
          <Code>{`# 1. 心跳
curl http://localhost:4777/api/external-kb/heartbeat

# 2. 列出仓库、确认 vaultId
curl http://localhost:4777/api/external-kb/info

# 3. 文本查询（由你的服务自行向量化）
curl -s -X POST http://localhost:4777/api/external-kb/search \\
  -H 'Content-Type: application/json' \\
  -d '{"vault":"<vaultId>","query":"你的问题","topK":3}'`}</Code>

          <H>参考实现</H>
          <P>
            Notopolis（Fastify/TypeScript）的完整实现约 90 行：
            <code className="mx-1 rounded bg-[var(--ax-code-bg)] px-1.5 py-0.5 font-mono text-[12px]">
              notopolis/src/server/rag/external.ts
            </code>
            ——一个纯适配模块，只复用其已有的 embed 与向量检索，不改动其自身检索逻辑；含路由测试
            <code className="mx-1 rounded bg-[var(--ax-code-bg)] px-1.5 py-0.5 font-mono text-[12px]">
              tests/external-kb.test.ts
            </code>
            ，可直接照抄结构。
          </P>

          <H>接入清单（Checklist）</H>
          <ul className="my-1 list-disc space-y-1 pl-5 text-[13px] text-muted-foreground">
            <li>三个端点按上述路径与 JSON 结构实现（路径可自定义，接入表单可改）</li>
            <li>文档已用你自己的 embedding 模型建好向量索引（查询时用同一模型向量化）</li>
            <li>info 如实返回 name / chunkCount（embedding 字段仅展示，可选）</li>
            <li>search：用自己的模型 embed(query) + 向量检索；未建索引返回空 hits 不报错</li>
            <li>AgentX 设置页 → 接入外部知识库 → 填服务地址 → 获取仓库列表点选 → 测试连接 → 保存启用</li>
          </ul>
        </div>
      </SheetContent>
    </Sheet>
  )
}
