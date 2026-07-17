import { http } from './http'

/** 生成文件下载：axios blob（自动携带 Bearer + 401 刷新），前端触发浏览器保存 */
export async function downloadGeneratedFile(fileId: string, filename: string): Promise<void> {
  const res = await http.get<Blob>(`/v1/files/${fileId}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
