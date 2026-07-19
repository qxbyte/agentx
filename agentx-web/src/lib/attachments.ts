/** 附件收集与过滤：文件夹选择（webkitdirectory）与拖拽（webkitGetAsEntry）共用规则 */

export interface AttachmentEntry {
  file: File
  /** 文件夹上传/拖拽时的相对路径（如 src/utils/date.ts） */
  relPath?: string
}

/** 单次消息附件上限（与后端 AttachmentService 一致） */
export const MAX_ATTACHMENT_FILES = 50

const EXCLUDED_DIRS = new Set([
  'node_modules', '.git', 'dist', 'build', 'target', 'out', '.next',
  '.venv', 'venv', '__pycache__', '.idea', '.vscode', 'coverage',
])

/** 二进制/媒体扩展名：解析无意义，收集阶段直接跳过 */
export const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif'])

const BINARY_EXTENSIONS = new Set([
  'bmp', 'ico', 'svg', 'heic',
  'mp3', 'mp4', 'mov', 'avi', 'mkv', 'wav', 'flac', 'ogg',
  'zip', 'tar', 'gz', 'bz2', '7z', 'rar', 'jar', 'war',
  'exe', 'dll', 'so', 'dylib', 'class', 'bin', 'dat',
  'woff', 'woff2', 'ttf', 'otf', 'eot',
  'db', 'sqlite', 'parquet', 'pyc', 'lock',
])

function extOf(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

export function isImage(name: string): boolean {
  return IMAGE_EXTENSIONS.has(extOf(name))
}

/** 收集阶段的可收条件：非隐藏、非二进制媒体（具体格式白名单由后端把关）。
    图片默认可收（视觉模型）；文件夹遍历传 allowImage=false 跳过（避免项目
    icon 噪声吃掉文件配额）。 */
export function isCollectible(name: string, opts?: { allowImage?: boolean }): boolean {
  if (name.startsWith('.')) return false
  const ext = extOf(name)
  if (IMAGE_EXTENSIONS.has(ext)) return opts?.allowImage !== false
  return !BINARY_EXTENSIONS.has(ext)
}

function isExcludedDir(name: string): boolean {
  return name.startsWith('.') || EXCLUDED_DIRS.has(name)
}

/** webkitdirectory 输入：按 webkitRelativePath 过滤目录规则 */
export function collectFromFileList(files: FileList): AttachmentEntry[] {
  const entries: AttachmentEntry[] = []
  for (const file of Array.from(files)) {
    const relPath = file.webkitRelativePath || undefined
    if (relPath) {
      const parts = relPath.split('/')
      // 去掉所选根目录名本身，只检查中间目录
      if (parts.slice(1, -1).some(isExcludedDir)) continue
      if (!isCollectible(parts[parts.length - 1] ?? file.name, { allowImage: false })) continue
      // 相对路径去掉根目录前缀，更贴近项目内路径
      entries.push({ file, relPath: parts.slice(1).join('/') || file.name })
    } else {
      if (!isCollectible(file.name)) continue
      entries.push({ file })
    }
  }
  return entries
}

/** 拖拽：DataTransferItem.webkitGetAsEntry 递归遍历文件夹 */
export async function collectFromDataTransfer(items: DataTransferItemList): Promise<AttachmentEntry[]> {
  const roots = Array.from(items)
    .filter((item) => item.kind === 'file')
    .map((item) => item.webkitGetAsEntry())
    .filter((e): e is FileSystemEntry => e !== null)
  const collected: AttachmentEntry[] = []
  for (const root of roots) {
    await walkEntry(root, '', collected)
    if (collected.length >= MAX_ATTACHMENT_FILES) break
  }
  return collected
}

async function walkEntry(entry: FileSystemEntry, prefix: string, out: AttachmentEntry[]): Promise<void> {
  if (out.length >= MAX_ATTACHMENT_FILES) return
  if (entry.isFile) {
    // 顶层直接拖入的图片可收；文件夹内部的图片跳过
    if (!isCollectible(entry.name, { allowImage: prefix === '' })) return
    const file = await new Promise<File>((resolve, reject) =>
      (entry as FileSystemFileEntry).file(resolve, reject),
    )
    out.push({ file, relPath: prefix ? `${prefix}${entry.name}` : undefined })
    return
  }
  if (entry.isDirectory) {
    if (prefix && isExcludedDir(entry.name)) return
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    // readEntries 每批最多 100 条，需循环读到空
    let batch: FileSystemEntry[]
    do {
      batch = await new Promise<FileSystemEntry[]>((resolve, reject) =>
        reader.readEntries(resolve, reject),
      )
      for (const child of batch) {
        await walkEntry(child, `${prefix}${entry.name}/`, out)
      }
    } while (batch.length > 0 && out.length < MAX_ATTACHMENT_FILES)
  }
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 图片上传前预处理：长边压到 1568px（主流视觉模型的推荐输入尺寸）、
    JPEG q0.85，canvas 重绘天然烘焙 EXIF 方向；GIF 不重绘（保留动图字节，模型取首帧）。 */
const IMAGE_MAX_EDGE = 1568

export async function preprocessImage(file: File): Promise<File> {
  const ext = file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase()
  if (ext === 'gif') return file
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
    const longEdge = Math.max(bitmap.width, bitmap.height)
    if (longEdge <= IMAGE_MAX_EDGE && file.size <= 2 * 1024 * 1024) {
      bitmap.close()
      return file
    }
    const scale = Math.min(1, IMAGE_MAX_EDGE / longEdge)
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      bitmap.close()
      return file
    }
    // PNG 透明区域垫白底，避免转 JPEG 后变黑
    ctx.fillStyle = '#fff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    bitmap.close()
    const blob = await new Promise((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', 0.85),
    )
    if (!(blob instanceof Blob)) return file
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' })
  } catch {
    return file
  }
}
