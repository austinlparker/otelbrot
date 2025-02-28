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
      console.log(`Drawing ${this.tileCache.size} tiles`);
    }
    
    // Clear the canvas first to ensure we start fresh
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    
    // Create a position-based tracking map for tiles of each resolution level
    const positionToTilesMap = new Map<string, {
      low?: TileData,
      medium?: TileData,
      high?: TileData,
      // Track if a higher resolution tile is fully loaded for this position
      hasHigherResolution: boolean
    }>();
    
    // Group tiles by position and resolution level
    this.tileCache.forEach(tile => {
      if (!tile.loaded) return;
      
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
    
    // Determine which tiles to draw based on coverage
    positionToTilesMap.forEach(posEntry => {
      // Always add high-res tiles if available
      if (posEntry.high) {
        highResTiles.push(posEntry.high);
      }
      
      // Add medium-res tiles if no high-res OR if high-res isn't fully loaded
      if (posEntry.medium && (!posEntry.high)) {
        medResTiles.push(posEntry.medium);
      }
      
      // Add low-res tiles if no higher resolution tiles for this position
      if (posEntry.low && (!posEntry.medium && !posEntry.high)) {
        lowResTiles.push(posEntry.low);
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
        
        // Special handling for preview tiles
        if (tile.tileId === "preview" || tile.tileId.includes("preview")) {
          // Always scale preview tiles to fill the whole canvas
          ctx.drawImage(tempCanvas, 0, 0, ctx.canvas.width, ctx.canvas.height);
        }
        // Or if this is a low-res tile (level 1) and we're early in the rendering process
        else if (tile.resolution === 1 && this.tileCache.size <= 3) {
          // Scale up initial low-res tiles
          ctx.drawImage(tempCanvas, 0, 0, ctx.canvas.width, ctx.canvas.height);
        } else {
          // Normal drawing at correct position
          ctx.drawImage(tempCanvas, tile.x, tile.y, tile.width, tile.height);
        }
      } catch (error) {
        console.error(`Error drawing tile at x:${tile.x}, y:${tile.y}:`, error);
      }
    };
    
    // Draw in order of increasing resolution
    lowResTiles.forEach(drawTile);
    medResTiles.forEach(drawTile);
    highResTiles.forEach(drawTile);
    
    // Only log detailed info when drawing small number of tiles to reduce console spam
    if (this.tileCache.size < 20 || now % 1000 === 0) {
      console.log(`Drew: ${lowResTiles.length} low, ${medResTiles.length} med, ${highResTiles.length} high tiles`);
    }
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
    console.log(`Cleared tile cache (preservePreview=${preservePreview})`);
  }
  
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
    console.log(`Decoding image data for tile at x:${x}, y:${y}, size:${width}x${height}, resolution:${resolution}`);
    
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
          console.log(`Successfully decoded image for tile ${tileId} at x:${x}, y:${y}, resolution:${resolution}`);
          
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
          
          // Trigger a redraw of the main canvas
          const mainCanvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
          if (mainCanvas) {
            const mainCtx = mainCanvas.getContext('2d');
            if (mainCtx) {
              // Request to draw all tiles in the correct order
              this.drawAllTiles(mainCtx);
              console.log(`Refreshed canvas after loading tile ${tileId}`);
            }
          }
          
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
        console.log(`Image was already loaded for tile ${tileId}`);
        
        // Mark the tile as loaded right away and ensure coordinates are set
        const tile = this.tileCache.get(tileId);
        if (tile) {
          tile.loaded = true;
          tile.x = x;
          tile.y = y;
          tile.position = positionKey;
        }
        
        return imageData;
      } catch {
        console.warn('Image not fully loaded yet, creating empty ImageData');
        imageData = new ImageData(width, height);
        return imageData;
      }
    } else {
      console.log(`Creating empty ImageData for tile ${tileId} while waiting for image to load`);
      imageData = new ImageData(width, height);
      return imageData;
    }
  }
  
  /**
   * Get all tiles as an array
   */
  getAllTiles(): TileData[] {
    return Array.from(this.tileCache.values());
  }
}