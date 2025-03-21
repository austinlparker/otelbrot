// No need to import ViewState anymore

interface TileMessage {
  type: string
  jobId: string
  tileId: string
  x: number
  y: number
  width: number
  height: number
  imageDataBase64: string
  resolution?: number // Optional resolution level (1=low, 2=medium, 3=high)
}

interface TileData {
  imageData: ImageData
  x: number
  y: number
  width: number
  height: number
  resolution: number
  loaded: boolean // Whether the image data is fully loaded
  position: string // A string key like "x:y:width:height" to identify tiles covering the same area
  tileId: string   // The ID of the tile from the server
}

/**
 * Manages rendering tiles and the tile cache with proper resolution handling
 */
export class TileManager {
  private tileCache: Map<string, TileData>
  private pendingTiles: Set<string>
  
  constructor() {
    this.tileCache = new Map<string, TileData>()
    this.pendingTiles = new Set<string>()
  }
  
  /**
   * Process a tile message from WebSocket
   */
  addTile(message: TileMessage): { imageData: ImageData; x: number; y: number } | null {
    const { tileId, x, y, width, height, imageDataBase64, jobId } = message
    
    // Skip processing if we already have this tile (avoid duplicates)
    if (this.tileCache.has(tileId)) {
      return null;
    }
    
    // Determine the resolution level
    const resolution = this.getTileResolution(tileId, width, height, message.resolution);
    
    // Only log detailed info for early tiles to reduce console spam
    if (this.tileCache.size < 20) {
      console.log(`Processing tile ${tileId} at resolution level ${resolution}`);
    }
    
    // Create a position key to identify tiles that cover the same area
    const positionKey = `${x}:${y}:${width}:${height}`;
    
    // Decode the Base64 image data - pass the coordinates for async handling
    const imageData = this.decodeImageData(imageDataBase64, width, height, x, y, resolution, tileId);
    
    // Create the tile data object
    const tileData: TileData = {
      imageData,
      x,
      y,
      width,
      height,
      resolution,
      loaded: false,
      position: positionKey,
      tileId: tileId
    };
    
    // Add to the cache
    this.tileCache.set(tileId, tileData);
    this.pendingTiles.delete(tileId);
    
    // Periodically clean up old tiles
    // Check jobId first to avoid unnecessary cleanup calls
    if (jobId && this.tileCache.size > 50) {
      this.cleanupOldTiles(jobId);
    }
    
    // Return the tile data for immediate rendering
    return {
      imageData,
      x,
      y
    }
  }
  
  /**
   * Determine the resolution level of a tile
   */
  private getTileResolution(_tileId: string, width: number, height: number, providedResolution?: number): number {
    // If resolution is explicitly provided, use it
    if (providedResolution !== undefined) {
      return providedResolution;
    }
    
    // Otherwise try to infer from size or ID
    // Smaller tiles generally have higher resolution
    if (width < 200 || height < 200) {
      return 3; // High resolution
    } else if (width < 400 || height < 400) {
      return 2; // Medium resolution
    } else {
      return 1; // Low resolution
    }
  }
  
  // Track when we last did a full canvas redraw to avoid excessive redraws
  private lastRedrawTime = 0;
  
  /**
   * Draw all the tiles with proper layering (high-res replaces low-res for same position)
   * Uses separate canvases for preview and detailed tiles
   */
  drawAllTiles(ctx: CanvasRenderingContext2D): void {
    if (this.tileCache.size === 0) return;
    
    // Throttle redraws to prevent excessive CPU usage
    // Skip if we did a redraw less than 20ms ago, unless there are very few tiles
    const now = Date.now();
    if (this.tileCache.size > 10 && now - this.lastRedrawTime < 20) {
      return;
    }
    
    this.lastRedrawTime = now;
    
    // Only log when not drawing too frequently
    if (this.tileCache.size < 10 || now % 500 === 0) {
      console.log(`Drawing ${this.tileCache.size} tiles (${this.getHighResTilesCount()} high-res)`);
    }
    
    // Ensure UI is in correct state for drawing
    // Check if this drawing is happening after a render is complete
    const isRenderComplete = document.body.classList.contains('render-complete');
    
    if (isRenderComplete && this.getHighResTilesCount() > 0) {
      // Hide preview canvas - render is complete with high-res tiles
      const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
      if (previewCanvas) {
        previewCanvas.style.display = 'none';
        previewCanvas.style.opacity = '0';
        previewCanvas.style.zIndex = '-1';
      }
      
      // Ensure main canvas is visible and on top
      const mainCanvas = ctx.canvas;
      if (mainCanvas) {
        mainCanvas.style.zIndex = '20';
        mainCanvas.style.opacity = '1';
        mainCanvas.style.visibility = 'visible';
      }
    }
    
    // Clear the main canvas
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    
    // Find the preview tile first
    let previewTile: TileData | undefined;
    
    // Check if there's a preview tile
    for (const tile of this.tileCache.values()) {
      if ((tile.tileId === "preview" || tile.tileId.includes("preview")) && tile.loaded) {
        previewTile = tile;
        break;
      }
    }
    
    // Create a position-based tracking map for tiles of each resolution level
    const positionToTilesMap = new Map<string, {
      low?: TileData,
      medium?: TileData,
      high?: TileData,
      // Track if a higher resolution tile is fully loaded for this position
      hasHigherResolution: boolean
    }>();
    
    // Group tiles by position and resolution level (excluding preview)
    this.tileCache.forEach(tile => {
      if (!tile.loaded) return;
      
      // Skip preview tile in regular tile handling
      if (tile.tileId === "preview" || tile.tileId.includes("preview")) {
        return;
      }
      
      // Get or create position entry
      let posEntry = positionToTilesMap.get(tile.position);
      if (!posEntry) {
        posEntry = { hasHigherResolution: false };
        positionToTilesMap.set(tile.position, posEntry);
      }
      
      // Add tile to the appropriate resolution slot
      if (tile.resolution === 1) {
        posEntry.low = tile;
      } else if (tile.resolution === 2) {
        posEntry.medium = tile;
        // Medium resolution means we have higher than low
        posEntry.hasHigherResolution = true;
      } else {
        posEntry.high = tile;
        // High resolution means we have higher than both low and medium
        posEntry.hasHigherResolution = true;
      }
    });
    
    // Create arrays of tiles to draw in order
    const lowResTiles: TileData[] = [];
    const medResTiles: TileData[] = [];
    const highResTiles: TileData[] = [];
    
    // Always draw all available tiles for maximum coverage
    // Start with low-res for base coverage, then layer medium and high on top
    positionToTilesMap.forEach(posEntry => {
      // Add all available tiles at each resolution level
      // This ensures we always have full coverage, with higher res tiles on top
      if (posEntry.low) {
        lowResTiles.push(posEntry.low);
      }
      
      if (posEntry.medium) {
        medResTiles.push(posEntry.medium);
      }
      
      if (posEntry.high) {
        highResTiles.push(posEntry.high);
      }
    });
    
    // Create a temporary canvas for scaling operations
    const tempCanvas = document.createElement('canvas');
    const tempCtx = tempCanvas.getContext('2d');
    if (!tempCtx) {
      console.error('Failed to create temporary canvas context');
      return;
    }
    
    // Function to draw a tile with proper scaling
    const drawTile = (tile: TileData) => {
      try {
        // Size the temporary canvas to match the tile
        tempCanvas.width = tile.width;
        tempCanvas.height = tile.height;
        
        // Draw the image data to the temporary canvas
        tempCtx.putImageData(tile.imageData, 0, 0);
        
        // Normal drawing at correct position
        ctx.drawImage(tempCanvas, tile.x, tile.y, tile.width, tile.height);
      } catch (error) {
        console.error(`Error drawing tile at x:${tile.x}, y:${tile.y}:`, error);
      }
    };
    
    // If we have a preview tile, ALWAYS draw it on the preview canvas
    // This is essential for zooming and panning operations
    const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
    if (previewTile && previewCanvas) {
      const previewCtx = previewCanvas.getContext('2d');
      if (previewCtx) {
        try {
          // Make the preview canvas visible during loading
          previewCanvas.style.display = 'block';
          previewCanvas.style.opacity = '1';
          previewCanvas.style.zIndex = '10';
          previewCanvas.style.visibility = 'visible';
          
          tempCanvas.width = previewTile.width;
          tempCanvas.height = previewTile.height;
          tempCtx.putImageData(previewTile.imageData, 0, 0);
          
          // Draw the preview tile scaled to fill the entire canvas
          // Clear the canvas first
          previewCtx.fillStyle = 'black';
          previewCtx.fillRect(0, 0, previewCanvas.width, previewCanvas.height);
          // Draw the preview scaled to fill the entire canvas
          previewCtx.drawImage(tempCanvas, 0, 0, previewCanvas.width, previewCanvas.height);
          
          // Log that we've drawn the preview
          console.log("Preview tile drawn");
        } catch (error) {
          console.error('Error drawing preview tile:', error);
        }
      }
    }
    
    // Draw detailed tiles on the main canvas
    lowResTiles.forEach(drawTile);
    medResTiles.forEach(drawTile);
    highResTiles.forEach(drawTile);
    
    // Only log detailed info when drawing small number of tiles to reduce console spam
    if (this.tileCache.size < 20 || now % 1000 === 0) {
      console.log(`Drew: ${lowResTiles.length} low, ${medResTiles.length} med, ${highResTiles.length} high tiles`);
    }
    
    // Let the FractalExplorer component handle showing/hiding the preview canvas
    // based on the tile count and expected total tiles
    // Rather than trying to make this decision here with incomplete information
  }
  
  /**
   * Clean up old tiles that are no longer needed
   * This keeps memory usage in check for long sessions
   */
  private cleanupOldTiles(currentJobId: string): void {
    // Remove tiles from previous jobs
    this.tileCache.forEach((tile, key) => {
      // Extract jobId from tileId (format: jobId:other:parts)
      const tileJobId = tile.tileId.split(':')[0];
      
      if (tileJobId && tileJobId !== currentJobId && tileJobId !== 'preview') {
        this.tileCache.delete(key);
      }
    });
  }
  
  /**
   * Check if a tile is already in the cache
   */
  hasTile(tileId: string): boolean {
    return this.tileCache.has(tileId);
  }
  
  /**
   * Get a tile from the cache
   */
  getTileData(tileId: string): TileData | undefined {
    return this.tileCache.get(tileId);
  }
  
  /**
   * Clear the tile cache
   */
  clearCache(preservePreview: boolean = false): void {
    if (preservePreview) {
      // Keep preview tiles but clear everything else
      this.tileCache.forEach((_, key) => {
        if (!key.includes('preview')) {
          this.tileCache.delete(key);
        }
      });
    } else {
      // Clear everything
      this.tileCache.clear();
    }
    
    this.pendingTiles.clear();
    
    // After clearing cache, ensure the preview canvas is visible (if we're preserving it)
    if (preservePreview) {
      const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
      if (previewCanvas) {
        previewCanvas.style.display = 'block';
        previewCanvas.style.opacity = '1';
        previewCanvas.style.zIndex = '10';
        previewCanvas.style.visibility = 'visible';
      }
    }
    
    console.log(`Cleared tile cache (preservePreview=${preservePreview})`);
  }
  
  // Batch processing for tiles
  private pendingRedraw = false;
  private redrawTimeoutId: number | null = null;
  
  /**
   * Decode Base64 image data to ImageData
   */
  private decodeImageData(
    base64: string, 
    width: number, 
    height: number, 
    x: number = 0, 
    y: number = 0, 
    resolution: number = 1,
    tileId: string
  ): ImageData {
    // Only log for the first few tiles to reduce console spam
    if (this.tileCache.size < 10) {
      console.log(`Decoding image data for tile at x:${x}, y:${y}, size:${width}x${height}, resolution:${resolution}`);
    }
    
    // Create a new canvas for the image
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    
    if (!ctx) {
      console.error('Failed to get 2D context');
      return new ImageData(width, height);
    }
    
    // Create and set up the image
    const img = new Image();
    
    // This will happen synchronously if the image is loaded from cache
    let imageData: ImageData | null = null;
    
    // Create the position key for this tile
    const positionKey = `${x}:${y}:${width}:${height}`;
    
    // Create a promise to handle both synchronous and asynchronous image loading
    new Promise<ImageData>((resolve) => {
      // Set up what happens when the image loads
      img.onload = () => {
        // Draw the image on the canvas
        ctx.drawImage(img, 0, 0);
        
        // Get the image data from the canvas
        try {
          const data = ctx.getImageData(0, 0, width, height);
          
          // Mark the tile as loaded and update all its properties
          const tile = this.tileCache.get(tileId);
          if (tile) {
            tile.loaded = true;
            tile.imageData = data;
            tile.x = x;
            tile.y = y;
            tile.position = positionKey;
            // No need to update tileId as it's already set
          }
          
          // Schedule a batch redraw instead of immediate redraw
          this.scheduleBatchRedraw();
          
          resolve(data);
        } catch (e) {
          console.error('Error getting image data:', e);
          resolve(new ImageData(width, height));
        }
      };
      
      // Handle errors
      img.onerror = () => {
        console.error('Failed to load image');
        resolve(new ImageData(width, height));
      };
    });
    
    // Start loading the image
    img.src = 'data:image/png;base64,' + base64;
    
    // Force the tile to be positioned correctly even before the image loads
    const existingTile = this.tileCache.get(tileId);
    if (existingTile) {
      existingTile.x = x;
      existingTile.y = y;
      existingTile.position = positionKey;
    }
    
    // If the image is already loaded (e.g., from cache), img.onload will have fired
    // Otherwise, create an empty ImageData to return for now
    if (img.complete) {
      try {
        ctx.drawImage(img, 0, 0);
        imageData = ctx.getImageData(0, 0, width, height);
        
        // Mark the tile as loaded right away and ensure coordinates are set
        const tile = this.tileCache.get(tileId);
        if (tile) {
          tile.loaded = true;
          tile.x = x;
          tile.y = y;
          tile.position = positionKey;
        }
        
        // Checking if this is a preview tile - prioritize drawing immediately
        if (tileId === "preview" || tileId.includes("preview")) {
          this.scheduleBatchRedraw(true); // Force immediate redraw for preview
        } else {
          this.scheduleBatchRedraw(); // Schedule batch redraw
        }
        
        return imageData;
      } catch {
        console.warn('Image not fully loaded yet, creating empty ImageData');
        imageData = new ImageData(width, height);
        return imageData;
      }
    } else {
      imageData = new ImageData(width, height);
      return imageData;
    }
  }
  
  /**
   * Schedule a batch redraw to minimize canvas updates
   */
  private scheduleBatchRedraw(immediate: boolean = false): void {
    if (this.pendingRedraw && !immediate) {
      // Already scheduled, no need to schedule again unless immediate
      return;
    }
    
    this.pendingRedraw = true;
    
    // Clear existing timeout if it exists
    if (this.redrawTimeoutId !== null) {
      window.clearTimeout(this.redrawTimeoutId);
      this.redrawTimeoutId = null;
    }
    
    // For immediate redraws (like preview tiles), do it right away
    if (immediate) {
      this.processBatchRedraw();
      return;
    }
    
    // Schedule a redraw with a short delay to batch multiple tile updates
    this.redrawTimeoutId = window.setTimeout(() => {
      this.processBatchRedraw();
    }, 100); // 100ms delay to batch updates
  }
  
  /**
   * Process the batched redraw
   */
  private processBatchRedraw(): void {
    this.pendingRedraw = false;
    this.redrawTimeoutId = null;
    
    // Find the main canvas and update it
    const mainCanvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
    if (mainCanvas) {
      const mainCtx = mainCanvas.getContext('2d');
      if (mainCtx) {
        this.drawAllTiles(mainCtx);
      }
    }
  }
  
  /**
   * Get all tiles as an array
   */
  getAllTiles(): TileData[] {
    return Array.from(this.tileCache.values());
  }
  
  /**
   * Get count of loaded, non-preview tiles
   * This is useful for determining when enough tiles have been rendered
   * to hide the preview canvas
   */
  getRenderedTileCount(): number {
    let count = 0;
    this.tileCache.forEach(tile => {
      if (tile.loaded && !tile.tileId.includes('preview')) {
        count++;
      }
    });
    return count;
  }

  /**
   * Get count of loaded high-resolution tiles
   * This helps determine when to hide the preview
   */
  getHighResTilesCount(): number {
    let count = 0;
    this.tileCache.forEach(tile => {
      // Count only loaded, high resolution (level 3) tiles that aren't preview tiles
      if (tile.loaded && tile.resolution === 3 && !tile.tileId.includes('preview')) {
        count++;
      }
    });
    return count;
  }
}