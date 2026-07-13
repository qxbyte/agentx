import { AlertCircle, Loader2, PanelLeftOpen } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { UIEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import ChatInput from '../components/ChatInput'
import Logo from '../components/Logo'
import MessageItem from '../components/MessageItem'
import Sidebar from '../components/Sidebar'
import { useIsMobile } from '../hooks/useIsMobile'
import { useChatStore } from '../stores/chat'

/** Drawer 内的侧栏是全宽面板，去掉卡片形态 */
const DRAWER_SIDEBAR_STYLE = {
  width: '100%',
  height: '100%',
  border: 'none',
  borderRadius: 0,
  boxShadow: 'none',
} as const

export default function ChatPage() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const navigate = useNavigate()
  const isMobile = useIsMobile()

  const conversations = useChatStore((s) => s.conversations)
  const activeConversationId = useChatStore((s) => s.activeConversationId)
  const messages = useChatStore((s) => s.messages)
  const messagesLoading = useChatStore((s) => s.messagesLoading)
  const messagesError = useChatStore((s) => s.messagesError)
  const streaming = useChatStore((s) => s.streaming)
  const loadConversations = useChatStore((s) => s.loadConversations)
  const openConversation = useChatStore((s) => s.openConversation)
  const sendMessage = useChatStore((s) => s.sendMessage)
  const stopStreaming = useChatStore((s) => s.stopStreaming)

  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [drawerOpen, setDrawerOpen] = useState(false)

  const scrollRef = useRef<HTMLDivElement>(null)
  const stickToBottomRef = useRef(true)

  useEffect(() => {
    void loadConversations()
  }, [loadConversations])

  useEffect(() => {
    void openConversation(conversationId ?? null)
  }, [conversationId, openConversation])

  // 新消息 / 流式增量到达时，若用户停留在底部则跟随滚动
  useEffect(() => {
    const el = scrollRef.current
    if (el && stickToBottomRef.current) {
      el.scrollTop = el.scrollHeight
    }
  }, [messages])

  const handleScroll = useCallback((event: UIEvent<HTMLDivElement>) => {
    const el = event.currentTarget
    stickToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80
  }, [])

  const handleSend = useCallback(
    (content: string) => {
      stickToBottomRef.current = true
      void sendMessage(content, (newId) => {
        navigate(`/c/${newId}`, { replace: true })
      })
    },
    [sendMessage, navigate],
  )

  const activeTitle = conversations.find((c) => c.id === activeConversationId)?.title ?? '新对话'
  const isEmpty = !messagesLoading && !messagesError && messages.length === 0

  const sidebarVisible = !isMobile && sidebarOpen

  return (
    <div className="ax-app">
      {isMobile ? (
        <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
          <SheetContent side="left" className="w-[280px] max-w-[280px] p-0 [&>button]:hidden">
            <Sidebar
              style={DRAWER_SIDEBAR_STYLE}
              onCollapse={() => setDrawerOpen(false)}
              onNavigate={() => setDrawerOpen(false)}
            />
          </SheetContent>
        </Sheet>
      ) : (
        <Sidebar hidden={!sidebarOpen} onCollapse={() => setSidebarOpen(false)} />
      )}

      <main className="ax-main">
        <div className="ax-topbar">
          {!sidebarVisible && (
            <button
              type="button"
              className="ax-icon-btn"
              aria-label="展开侧栏"
              onClick={() => (isMobile ? setDrawerOpen(true) : setSidebarOpen(true))}
            >
              <PanelLeftOpen className="size-4" />
            </button>
          )}
          <span className="ax-topbar-title">{activeTitle}</span>
        </div>

        <div className="ax-chat-scroll ax-scroll" ref={scrollRef} onScroll={handleScroll}>
          {messagesLoading ? (
            <div className="flex justify-center p-12">
              <Loader2 className="size-5 animate-spin text-muted-foreground" />
            </div>
          ) : isEmpty ? (
            <div className="ax-welcome">
              <Logo size={56} />
              <h1 className="ax-welcome-title">你好，这里是 AgentX</h1>
              <p className="ax-welcome-sub">
                企业级智能体，支持多轮对话、深度思考、工具调用与知识库引用。
                <br />
                在下方输入你的问题，开始第一段对话。
              </p>
            </div>
          ) : (
            <div className="ax-message-list">
              {messagesError && (
                <div className="ax-msg-error mb-4 flex items-center gap-2" role="alert">
                  <AlertCircle className="size-4 shrink-0" />
                  <span>{messagesError}</span>
                </div>
              )}
              {messages.map((message) => (
                <MessageItem key={message.id} message={message} />
              ))}
            </div>
          )}
        </div>

        <div className="ax-composer-wrap">
          <ChatInput streaming={streaming} onSend={handleSend} onStop={stopStreaming} />
          <div className="ax-composer-hint">
            Enter 发送 · Shift + Enter 换行 · 内容由 AI 生成，请注意甄别
          </div>
        </div>
      </main>
    </div>
  )
}
