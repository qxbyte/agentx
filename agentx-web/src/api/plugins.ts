import type { InstalledPluginView, MarketplaceView } from '../types'
import { request } from './http'

export function addMarketplace(source: string): Promise<MarketplaceView> {
  return request<MarketplaceView>({
    url: '/v1/plugins/marketplaces',
    method: 'POST',
    data: { source },
    // git clone 可能较慢
    timeout: 200_000,
  })
}

export function listMarketplaces(): Promise<MarketplaceView[]> {
  return request<MarketplaceView[]>({ url: '/v1/plugins/marketplaces', method: 'GET' })
}

export function removeMarketplace(name: string): Promise<void> {
  return request<void>({ url: `/v1/plugins/marketplaces/${name}`, method: 'DELETE' })
}

export function installPlugin(name: string, marketplace: string): Promise<InstalledPluginView> {
  return request<InstalledPluginView>({
    url: '/v1/plugins/install',
    method: 'POST',
    data: { name, marketplace },
    timeout: 200_000,
  })
}

export function listPlugins(): Promise<InstalledPluginView[]> {
  return request<InstalledPluginView[]>({ url: '/v1/plugins', method: 'GET' })
}

export function setPluginEnabled(id: string, enabled: boolean): Promise<InstalledPluginView> {
  return request<InstalledPluginView>({
    url: `/v1/plugins/${encodeURIComponent(id)}/enabled`,
    method: 'PATCH',
    data: { enabled },
  })
}

export function uninstallPlugin(id: string): Promise<void> {
  return request<void>({ url: `/v1/plugins/${encodeURIComponent(id)}`, method: 'DELETE' })
}
