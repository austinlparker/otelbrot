// Global type definitions

interface TileMessage {
  type: string;
  jobId: string;
  tileId: string;
  x: number;
  y: number;
  width: number;
  height: number;
  imageDataBase64: string;
  resolution?: number;
}

interface TileResult {
  imageData: ImageData;
  x: number;
  y: number;
}

// Extend the Window interface to include the TileManager
interface Window {
  tileManager: {
    drawAllTiles: (ctx: CanvasRenderingContext2D) => void;
    clearCache: (preservePreview?: boolean) => void;
    addTile: (message: TileMessage) => TileResult | null;
    getRenderedTileCount: () => number;
    getHighResTilesCount: () => number;
  }
}