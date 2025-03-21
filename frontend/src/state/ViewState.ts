/**
 * Represents the current view state of the fractal explorer
 */
export class ViewState {
  centerX: number
  centerY: number
  zoom: number
  maxIterations: number
  colorScheme: string
  tileSize: number
  currentJobId: string | null
  tileCache: Map<string, ImageData>

  constructor() {
    // Default to classic Mandelbrot view
    this.centerX = -0.5
    this.centerY = 0
    this.zoom = 2.0
    this.maxIterations = 100
    this.colorScheme = 'classic'
    this.tileSize = 256
    this.currentJobId = null
    this.tileCache = new Map<string, ImageData>()
  }

  /**
   * Reset to the default view, but preserve user's quality settings
   */
  reset(): void {
    // Store current quality settings
    const currentMaxIterations = this.maxIterations;
    const currentColorScheme = this.colorScheme;
    const currentTileSize = this.tileSize;
    
    // Reset view position
    this.centerX = -0.5
    this.centerY = 0
    this.zoom = 2.0
    
    // Preserve user's quality settings
    this.colorScheme = currentColorScheme;
    this.maxIterations = currentMaxIterations;
    this.tileSize = currentTileSize;
    
    // Clear the cache
    this.clearCache()
  }

  /**
   * Clear the tile cache
   */
  clearCache(): void {
    this.tileCache.clear()
    
    // Also clear the tile manager cache if it exists
    if (typeof window !== 'undefined') {
      const tileManager = window.tileManager;
      if (tileManager && typeof tileManager.clearCache === 'function') {
        tileManager.clearCache();
      }
    }
  }

  /**
   * Update the view coordinates and zoom level
   */
  updateView(centerX: number, centerY: number, zoom: number): void {
    // Ensure the zoom level stays within reasonable bounds
    const minZoom = 0.000001;  // Prevent zooming in too far
    const maxZoom = 10.0;      // Prevent zooming out too far
    
    const safeZoom = Math.max(minZoom, Math.min(zoom, maxZoom));
    
    console.log(`ViewState.updateView: centerX=${centerX}, centerY=${centerY}, zoom=${safeZoom} (original: ${zoom})`);
    
    this.centerX = centerX;
    this.centerY = centerY;
    this.zoom = safeZoom;
    
    // When view changes, we need a new render
    this.clearCache();
  }

  /**
   * Update the render parameters
   */
  updateParams(maxIterations: number, colorScheme: string, tileSize: number): void {
    this.maxIterations = maxIterations
    this.colorScheme = colorScheme
    this.tileSize = tileSize
    // When params change, we need a new render
    this.clearCache()
  }

  /**
   * Add a tile to the cache
   */
  addTile(tileId: string, imageData: ImageData): void {
    this.tileCache.set(tileId, imageData)
  }

  /**
   * Check if a tile is in the cache
   */
  hasTile(tileId: string): boolean {
    return this.tileCache.has(tileId)
  }

  /**
   * Get a tile from the cache
   */
  getTile(tileId: string): ImageData | undefined {
    return this.tileCache.get(tileId)
  }

  /**
   * Get the current render request parameters
   */
  getRenderParams(width: number, height: number): {
    centerX: number;
    centerY: number;
    zoom: number;
    maxIterations: number;
    width: number;
    height: number;
    colorScheme: string;
    tileSize: number;
  } {
    return {
      centerX: this.centerX,
      centerY: this.centerY,
      zoom: this.zoom,
      maxIterations: this.maxIterations,
      width,
      height,
      colorScheme: this.colorScheme,
      tileSize: this.tileSize
    }
  }
}